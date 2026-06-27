package com.petfilament.recycler;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.petfilament.recycler.ble.BleConstants;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

public class BluetoothManager {

    private static final String TAG = "BluetoothManager";

    private final Context context;
    private final BluetoothCallback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DatabaseHelper databaseHelper;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private BluetoothGatt bluetoothGatt;

    private BluetoothGattCharacteristic controlCharacteristic;
    private BluetoothGattCharacteristic statusCharacteristic;
    private BluetoothGattCharacteristic logCharacteristic;

    private final ArrayList<String> discoveredDevices = new ArrayList<>();
    private final Map<String, BluetoothDevice> deviceMap = new HashMap<>();

    private final Queue<Runnable> descriptorQueue = new LinkedList<>();
    private boolean descriptorWriteInProgress = false;

    private boolean isScanning = false;
    private boolean isConnected = false;
    private boolean intentionalDisconnect = false;

    private Runnable filteredScanTimeoutRunnable;
    private Runnable fallbackScanTimeoutRunnable;

    private String connectedDeviceLabel = "";
    private boolean notificationsReadyPosted = false;
    private int pendingNotificationEnables = 0;
    private boolean serviceDiscoveryStarted = false;

    public interface BluetoothCallback {
        void onDeviceFound(ArrayList<String> devices);

        void onConnected();

        void onDisconnected();

        void onConnectionFailed(String error);

        void onDataReceived(UUID characteristicUuid, String data);
    }

    public BluetoothManager(Context context, BluetoothCallback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
        this.databaseHelper = DatabaseHelper.getInstance(this.context);

        android.bluetooth.BluetoothManager systemBluetoothManager =
                (android.bluetooth.BluetoothManager) this.context.getSystemService(Context.BLUETOOTH_SERVICE);

        if (systemBluetoothManager != null) {
            bluetoothAdapter = systemBluetoothManager.getAdapter();
        }

        if (bluetoothAdapter == null) {
            postConnectionFailed("Bluetooth is not supported on this device");
        }
    }

    public boolean isBluetoothEnabled() {
        if (bluetoothAdapter == null) {
            return false;
        }
        if (!hasConnectPermission()) {
            postConnectionFailed("Missing BLUETOOTH_CONNECT permission");
            return false;
        }
        try {
            return bluetoothAdapter.isEnabled();
        } catch (SecurityException e) {
            postConnectionFailed("Cannot check Bluetooth state: " + e.getMessage());
            Log.e(TAG, "isBluetoothEnabled failed", e);
            return false;
        }
    }

    public boolean enableBluetooth() {
        if (bluetoothAdapter == null) {
            return false;
        }
        if (!hasConnectPermission()) {
            postConnectionFailed("Missing BLUETOOTH_CONNECT permission");
            return false;
        }
        try {
            if (!bluetoothAdapter.isEnabled()) {
                bluetoothAdapter.enable();
            }
            return true;
        } catch (SecurityException e) {
            postConnectionFailed("Cannot enable Bluetooth: " + e.getMessage());
            Log.e(TAG, "enableBluetooth failed", e);
            return false;
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    @SuppressLint("MissingPermission")
    public void startDiscovery() {
        if (bluetoothAdapter == null || isConnected) {
            return;
        }
        if (!hasScanPermission()) {
            postConnectionFailed("Missing scan permission");
            return;
        }

        stopDiscovery();
        discoveredDevices.clear();
        deviceMap.clear();

        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bleScanner == null) {
            postConnectionFailed("BLE scanner unavailable");
            return;
        }

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(BleConstants.SERVICE_UUID))
                .build());

        try {
            bleScanner.startScan(filters, settings, scanCallback);
            isScanning = true;
            Log.d(TAG, "Started filtered BLE scan");
        } catch (SecurityException e) {
            postConnectionFailed("BLE scan failed: " + e.getMessage());
            Log.e(TAG, "startScan failed", e);
            return;
        }

        filteredScanTimeoutRunnable = () -> {
            if (!isScanning) {
                return;
            }
            if (discoveredDevices.isEmpty()) {
                Log.d(TAG, "No UUID-filter results, retrying without filter");
                restartDiscoveryWithoutFilter(settings);
            } else {
                stopDiscovery();
                callback.onDeviceFound(new ArrayList<>(discoveredDevices));
            }
        };
        handler.postDelayed(filteredScanTimeoutRunnable, BleConstants.SCAN_TIMEOUT_MS);
    }

    @SuppressLint("MissingPermission")
    private void restartDiscoveryWithoutFilter(ScanSettings settings) {
        stopDiscovery();
        try {
            bleScanner.startScan(null, settings, scanCallback);
            isScanning = true;
        } catch (SecurityException e) {
            postConnectionFailed("Fallback scan failed: " + e.getMessage());
            Log.e(TAG, "fallback scan failed", e);
            return;
        }

        fallbackScanTimeoutRunnable = () -> {
            if (isScanning) {
                stopDiscovery();
                callback.onDeviceFound(new ArrayList<>(discoveredDevices));
            }
        };
        handler.postDelayed(fallbackScanTimeoutRunnable, BleConstants.SCAN_TIMEOUT_MS);
    }

    @SuppressLint("MissingPermission")
    public void stopDiscovery() {
        if (filteredScanTimeoutRunnable != null) {
            handler.removeCallbacks(filteredScanTimeoutRunnable);
            filteredScanTimeoutRunnable = null;
        }
        if (fallbackScanTimeoutRunnable != null) {
            handler.removeCallbacks(fallbackScanTimeoutRunnable);
            fallbackScanTimeoutRunnable = null;
        }
        if (bleScanner != null && hasScanPermission()) {
            try {
                bleScanner.stopScan(scanCallback);
            } catch (Exception e) {
                Log.e(TAG, "stopScan failed", e);
            }
        }
        isScanning = false;
    }

    @SuppressLint("MissingPermission")
    public void connect(String macAddress) {
        if (bluetoothAdapter == null) {
            return;
        }
        if (!hasConnectPermission()) {
            postConnectionFailed("Missing connect permission");
            return;
        }

        BluetoothDevice device = deviceMap.get(macAddress);
        if (device == null) {
            try {
                device = bluetoothAdapter.getRemoteDevice(macAddress);
            } catch (IllegalArgumentException e) {
                postConnectionFailed("Device not found: " + macAddress);
                return;
            }
        }

        stopDiscovery();
        disconnectInternal(false);

        connectedDeviceLabel = resolveDeviceLabel(macAddress);
        intentionalDisconnect = false;
        handler.postDelayed(connectTimeoutRunnable, BleConstants.CONNECT_TIMEOUT_MS);

        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback);
        } catch (SecurityException e) {
            handler.removeCallbacks(connectTimeoutRunnable);
            postConnectionFailed("BLE connect failed: " + e.getMessage());
            Log.e(TAG, "connectGatt failed", e);
        }
    }

    public void disconnect() {
        disconnectInternal(true);
    }

    @SuppressLint("MissingPermission")
    private void disconnectInternal(boolean intentional) {
        intentionalDisconnect = intentional;
        connectedDeviceLabel = "";
        notificationsReadyPosted = false;
        pendingNotificationEnables = 0;
        serviceDiscoveryStarted = false;
        stopStatusPolling();
        handler.removeCallbacks(connectTimeoutRunnable);

        controlCharacteristic = null;
        statusCharacteristic = null;
        logCharacteristic = null;
        descriptorQueue.clear();
        descriptorWriteInProgress = false;

        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
            } catch (Exception e) {
                Log.e(TAG, "disconnect failed", e);
            }
            bluetoothGatt = null;
        }

        if (isConnected) {
            isConnected = false;
            if (!intentional) {
                handler.post(callback::onDisconnected);
            }
        }
        intentionalDisconnect = false;
    }

    @SuppressLint("MissingPermission")
    public void sendData(String data) {
        if (!isConnected || bluetoothGatt == null) {
            postConnectionFailed("Not connected");
            return;
        }
        if (controlCharacteristic == null) {
            postConnectionFailed("Control characteristic not ready");
            return;
        }

        try {
            controlCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            controlCharacteristic.setValue(data.getBytes(StandardCharsets.UTF_8));
            boolean success = bluetoothGatt.writeCharacteristic(controlCharacteristic);
            Log.d(TAG, "writeCharacteristic(" + data + ") => " + success);
            if (success) {
                databaseHelper.insertLog(DatabaseHelper.DIRECTION_OUT, data, connectedDeviceLabel);
            }
        } catch (SecurityException e) {
            postConnectionFailed("Cannot send data: " + e.getMessage());
            Log.e(TAG, "writeCharacteristic failed", e);
        }
    }

    private final Runnable connectTimeoutRunnable = () -> {
        if (!isConnected) {
            disconnectInternal(false);
            postConnectionFailed("Connection timed out");
        }
    };

    private final Runnable statusPollRunnable = new Runnable() {
        @Override
        public void run() {
            if (isConnected) {
                sendData(BleConstants.CMD_GET_STATUS);
                handler.postDelayed(this, BleConstants.STATUS_POLL_INTERVAL_MS);
            }
        }
    };

    private void startStatusPolling() {
        stopStatusPolling();
        handler.postDelayed(statusPollRunnable, BleConstants.STATUS_POLL_INTERVAL_MS);
    }

    private void stopStatusPolling() {
        handler.removeCallbacks(statusPollRunnable);
    }

    private void postConnectionFailed(String error) {
        handler.post(() -> callback.onConnectionFailed(error));
    }

    private boolean hasScanPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private String buildDisplayName(BluetoothDevice device, int rssi) {
        String name = "Unknown";
        String address = device.getAddress() != null ? device.getAddress() : "Unknown";

        try {
            if (device.getName() != null && !device.getName().trim().isEmpty()) {
                name = device.getName().trim();
            }
        } catch (SecurityException ignored) {
        }

        return String.format(Locale.getDefault(), "%s - %s (RSSI:%d)", name, address, rssi);
    }

    private String resolveDeviceLabel(String macAddress) {
        for (String item : discoveredDevices) {
            if (extractMacAddress(item).equalsIgnoreCase(macAddress)) {
                String[] parts = item.split(" - ");
                if (parts.length > 0 && !parts[0].trim().isEmpty()) {
                    return parts[0].trim();
                }
                break;
            }
        }
        return macAddress;
    }

    public static String extractMacAddress(String displayName) {
        if (displayName == null) {
            return "";
        }
        String[] parts = displayName.split(" - ");
        if (parts.length < 2) {
            return "";
        }
        String addressPart = parts[1].trim();
        int rssiIndex = addressPart.indexOf(" (RSSI:");
        if (rssiIndex > 0) {
            return addressPart.substring(0, rssiIndex).trim();
        }
        int spaceIndex = addressPart.indexOf(' ');
        return spaceIndex > 0 ? addressPart.substring(0, spaceIndex).trim() : addressPart;
    }

    private boolean shouldKeepDevice(BluetoothDevice device, ScanResult result) {
        if (device == null) {
            return false;
        }

        try {
            String name = device.getName();
            if (name != null && name.startsWith(BleConstants.DEVICE_NAME_PREFIX)) {
                return true;
            }
        } catch (SecurityException ignored) {
        }

        if (result.getScanRecord() != null && result.getScanRecord().getServiceUuids() != null) {
            for (ParcelUuid uuid : result.getScanRecord().getServiceUuids()) {
                if (uuid != null && BleConstants.SERVICE_UUID.equals(uuid.getUuid())) {
                    return true;
                }
            }
        }

        return false;
    }

    @SuppressLint("MissingPermission")
    private void readStatusCharacteristic(BluetoothGatt gatt) {
        if (gatt == null || statusCharacteristic == null || !hasConnectPermission()) {
            return;
        }
        try {
            gatt.readCharacteristic(statusCharacteristic);
        } catch (SecurityException e) {
            Log.e(TAG, "readCharacteristic failed", e);
        }
    }

    private void markNotificationEnableStarted() {
        pendingNotificationEnables++;
    }

    private void markNotificationEnableFinished(BluetoothGatt gatt) {
        if (pendingNotificationEnables > 0) {
            pendingNotificationEnables--;
        }
        tryCompleteConnectionSetup(gatt);
    }

    private void tryCompleteConnectionSetup(BluetoothGatt gatt) {
        if (notificationsReadyPosted || descriptorWriteInProgress || !descriptorQueue.isEmpty()) {
            return;
        }
        if (pendingNotificationEnables > 0) {
            return;
        }

        notificationsReadyPosted = true;
        isConnected = true;
        handler.post(() -> {
            callback.onConnected();
            readStatusCharacteristic(gatt);
            sendData(BleConstants.CMD_GET_STATUS);
            startStatusPolling();
        });
    }

    @SuppressLint("MissingPermission")
    private void enqueueEnableNotification(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        markNotificationEnableStarted();
        descriptorQueue.add(() -> {
            try {
                gatt.setCharacteristicNotification(characteristic, true);
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(BleConstants.CLIENT_CONFIG_UUID);
                if (descriptor != null) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    descriptorWriteInProgress = true;
                    gatt.writeDescriptor(descriptor);
                } else {
                    descriptorWriteInProgress = false;
                    markNotificationEnableFinished(gatt);
                    processNextDescriptorWrite(gatt);
                }
            } catch (SecurityException e) {
                Log.e(TAG, "enableNotification failed", e);
                descriptorWriteInProgress = false;
                markNotificationEnableFinished(gatt);
                processNextDescriptorWrite(gatt);
            }
        });
        processNextDescriptorWrite(gatt);
    }

    private void processNextDescriptorWrite(BluetoothGatt gatt) {
        if (descriptorWriteInProgress || descriptorQueue.isEmpty()) {
            tryCompleteConnectionSetup(gatt);
            return;
        }
        Runnable next = descriptorQueue.poll();
        if (next != null) {
            next.run();
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device == null || !shouldKeepDevice(device, result)) {
                return;
            }

            String address = device.getAddress();
            if (address == null || address.isEmpty()) {
                return;
            }

            deviceMap.put(address, device);
            String item = buildDisplayName(device, result.getRssi());

            boolean exists = false;
            for (String existing : discoveredDevices) {
                if (extractMacAddress(existing).equals(address)) {
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                discoveredDevices.add(item);
                handler.post(() -> callback.onDeviceFound(new ArrayList<>(discoveredDevices)));
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            isScanning = false;
            postConnectionFailed("BLE scan failed: " + errorCode);
        }
    };

    @SuppressLint("MissingPermission")
    private void beginServiceDiscovery(BluetoothGatt gatt) {
        if (gatt == null || serviceDiscoveryStarted || !hasConnectPermission()) {
            return;
        }
        serviceDiscoveryStarted = true;
        try {
            gatt.discoverServices();
        } catch (SecurityException e) {
            serviceDiscoveryStarted = false;
            postConnectionFailed("Service discovery failed: " + e.getMessage());
        }
    }

    @SuppressLint("MissingPermission")
    private void requestHighMtu(BluetoothGatt gatt) {
        if (gatt == null || !hasConnectPermission()) {
            return;
        }
        try {
            boolean requested = gatt.requestMtu(BleConstants.REQUESTED_MTU);
            Log.d(TAG, "requestMtu(" + BleConstants.REQUESTED_MTU + ") => " + requested);
            if (!requested) {
                beginServiceDiscovery(gatt);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "requestMtu failed, continuing with default MTU", e);
            beginServiceDiscovery(gatt);
        }
    }

    private void deliverCharacteristicData(BluetoothGattCharacteristic characteristic, byte[] value) {
        if (value == null || characteristic == null) {
            return;
        }

        String data = new String(value, StandardCharsets.UTF_8).trim();
        UUID uuid = characteristic.getUuid();
        databaseHelper.insertLog(DatabaseHelper.DIRECTION_IN, data, connectedDeviceLabel);
        handler.post(() -> callback.onDataReceived(uuid, data));
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handler.removeCallbacks(connectTimeoutRunnable);
                disconnectInternal(false);
                postConnectionFailed("BLE connection error: " + status);
                return;
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                handler.removeCallbacks(connectTimeoutRunnable);
                serviceDiscoveryStarted = false;
                requestHighMtu(gatt);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                handler.removeCallbacks(connectTimeoutRunnable);
                boolean wasConnected = isConnected;
                isConnected = false;
                stopStatusPolling();
                if (wasConnected && !intentionalDisconnect) {
                    handler.post(callback::onDisconnected);
                }
                intentionalDisconnect = false;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                postConnectionFailed("Service discovery failed: " + status);
                return;
            }

            BluetoothGattService service = gatt.getService(BleConstants.SERVICE_UUID);
            if (service == null) {
                postConnectionFailed("BLE service not found");
                return;
            }

            controlCharacteristic = service.getCharacteristic(BleConstants.CONTROL_UUID);
            statusCharacteristic = service.getCharacteristic(BleConstants.STATUS_UUID);
            logCharacteristic = service.getCharacteristic(BleConstants.LOG_UUID);

            if (controlCharacteristic == null || statusCharacteristic == null) {
                postConnectionFailed("BLE characteristics incomplete");
                return;
            }

            notificationsReadyPosted = false;
            pendingNotificationEnables = 0;

            enqueueEnableNotification(gatt, statusCharacteristic);
            if (logCharacteristic != null) {
                enqueueEnableNotification(gatt, logCharacteristic);
            } else {
                tryCompleteConnectionSetup(gatt);
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Negotiated MTU: " + mtu);
            } else {
                Log.w(TAG, "MTU negotiation failed: " + status);
            }
            beginServiceDiscovery(gatt);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            descriptorWriteInProgress = false;
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Descriptor write failed: " + status);
            }
            markNotificationEnableFinished(gatt);
            processNextDescriptorWrite(gatt);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] value = characteristic.getValue();
            if (value == null) {
                return;
            }
            deliverCharacteristicData(characteristic, value);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                deliverCharacteristicData(characteristic, characteristic.getValue());
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Characteristic write failed: " + status);
            }
        }
    };
}