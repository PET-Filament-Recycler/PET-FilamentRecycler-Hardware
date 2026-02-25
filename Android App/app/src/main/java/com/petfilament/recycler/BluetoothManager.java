package com.petfilament.recycler;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.content.ContextCompat;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

/**
 * BluetoothManager class manages Bluetooth operations including scanning, connecting, disconnecting, and data transmission/reception.
 * It provides a callback interface for upper-layer applications to receive events related to device discovery, connection status, and data.
 */
public class BluetoothManager {

    /**
     * Constant for logging tag.
     */
    private static final String TAG = "BluetoothManager";

    /**
     * Standard SPP UUID for RFCOMM connections.
     */
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    /**
     * Context for permission checks and broadcast registrations.
     */
    private final Context context;

    /**
     * Bluetooth adapter instance.
     */
    private final BluetoothAdapter bluetoothAdapter;

    /**
     * Bluetooth socket for connections.
     */
    private BluetoothSocket bluetoothSocket;

    /**
     * Thread for handling connected data transmission.
     */
    private ConnectedThread connectedThread;

    /**
     * List of discovered devices.
     */
    private final ArrayList<String> discoveredDevices = new ArrayList<>();

    /**
     * Callback for Bluetooth events.
     */
    private final BluetoothCallback callback;

    /**
     * Handler for main thread operations.
     */
    private final Handler handler = new Handler(Looper.getMainLooper());

    /**
     * Flag indicating if the broadcast receiver is registered.
     */
    private boolean isReceiverRegistered = false;

    /**
     * Database helper for logging.
     */
    private DatabaseHelper databaseHelper;

    /**
     * Interface for Bluetooth event callbacks.
     */
    public interface BluetoothCallback {
        /**
         * Called when new devices are found or paired device list is updated.
         * @param devices List of device names and MAC addresses.
         */
        void onDeviceFound(ArrayList<String> devices);

        /**
         * Called when Bluetooth connection is successful.
         */
        void onConnected();

        /**
         * Called when connection fails or disconnects.
         * @param error Error message.
         */
        void onConnectionFailed(String error);

        /**
         * Called when data is received.
         * @param data Received string data.
         */
        void onDataReceived(String data);
    }

    /**
     * Constructor to initialize the Bluetooth manager.
     * @param context Application context, usually an Activity.
     * @param callback Interface for receiving Bluetooth events.
     */
    public BluetoothManager(Context context, BluetoothCallback callback) {
        this.context = context;
        databaseHelper = new DatabaseHelper(context);
        this.callback = callback;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            callback.onConnectionFailed("設備不支援藍牙");
        }
    }

    /**
     * Checks if Bluetooth is enabled.
     * @return true if enabled, false otherwise.
     */
    public boolean isBluetoothEnabled() {
        if (bluetoothAdapter == null) return false;
        if (!hasConnectPermission()) {
            callback.onConnectionFailed("無 BLUETOOTH_CONNECT 權限，無法檢查藍牙狀態");
            return false;
        }
        try {
            return bluetoothAdapter.isEnabled();
        } catch (SecurityException e) {
            callback.onConnectionFailed("安全異常，無法檢查藍牙狀態: " + e.getMessage());
            Log.e(TAG, "檢查藍牙啟用狀態失敗", e);
            return false;
        }
    }

    /**
     * Enables Bluetooth if not already enabled.
     * Requires BLUETOOTH_CONNECT permission on Android 12+.
     * @return true if enabled or already enabled, false if permission insufficient.
     */
    public boolean enableBluetooth() {
        if (bluetoothAdapter == null) return false;
        if (!hasConnectPermission()) {
            callback.onConnectionFailed("無 BLUETOOTH_CONNECT 權限，無法啟用藍牙");
            return false;
        }
        try {
            if (!bluetoothAdapter.isEnabled()) {
                bluetoothAdapter.enable();
            }
            return true;
        } catch (SecurityException e) {
            callback.onConnectionFailed("安全異常，無法啟用藍牙: " + e.getMessage());
            Log.e(TAG, "啟用藍牙失敗", e);
            return false;
        }
    }

    /**
     * Starts Bluetooth device discovery and shows bonded devices.
     */
    public void startDiscovery() {
        if (bluetoothAdapter == null) return;
        if (!hasScanPermission()) {
            callback.onConnectionFailed("無掃描權限");
            return;
        }
        discoveredDevices.clear();
        try {
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
            bluetoothAdapter.startDiscovery();
            showBondedDevices();
        } catch (SecurityException e) {
            callback.onConnectionFailed("安全異常，無法開始掃描: " + e.getMessage());
            Log.e(TAG, "開始掃描失敗", e);
        }
    }

    /**
     * Stops Bluetooth discovery.
     */
    public void stopDiscovery() {
        if (bluetoothAdapter == null) return;
        if (!hasScanPermission()) return;
        try {
            bluetoothAdapter.cancelDiscovery();
        } catch (SecurityException e) {
            Log.e(TAG, "停止掃描失敗", e);
        }
    }

    /**
     * Connects to a device with the given MAC address.
     * @param macAddress MAC address of the device.
     */
    public void connect(String macAddress) {
        if (bluetoothAdapter == null) return;
        if (!hasConnectPermission()) {
            callback.onConnectionFailed("無連接權限");
            return;
        }
        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
            bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            bluetoothAdapter.cancelDiscovery();
            bluetoothSocket.connect();
            connectedThread = new ConnectedThread(bluetoothSocket);
            connectedThread.start();
            callback.onConnected();
        } catch (IOException | SecurityException e) {
            callback.onConnectionFailed("連接失敗: " + e.getMessage());
            Log.e(TAG, "連接失敗", e);
        }
    }

    /**
     * Disconnects the Bluetooth connection.
     */
    public void disconnect() {
        try {
            if (connectedThread != null) {
                connectedThread.cancel();
                connectedThread = null;
            }
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
                bluetoothSocket = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "斷開連接失敗", e);
        }
    }

    /**
     * Sends data to the connected device and logs it.
     * @param data Data to send.
     */
    public void sendData(String data) {
        if (connectedThread != null) {
            connectedThread.write((data + "\n").getBytes());
            databaseHelper.insertLog("OUT", data);
        }
    }

    /**
     * Registers the broadcast receiver for device discovery.
     */
    public void registerReceiver() {
        if (!isReceiverRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothDevice.ACTION_FOUND);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            context.registerReceiver(discoveryReceiver, filter);
            isReceiverRegistered = true;
        }
    }

    /**
     * Unregisters the broadcast receiver.
     */
    public void unregisterReceiver() {
        if (isReceiverRegistered) {
            context.unregisterReceiver(discoveryReceiver);
            isReceiverRegistered = false;
        }
    }

    /**
     * Checks if scan permission is granted, depending on Android version.
     * @return true if permission granted.
     */
    private boolean hasScanPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    /**
     * Checks if connect permission is granted.
     * @return true if permission granted.
     */
    private boolean hasConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /**
     * Shows bonded (paired) devices.
     */
    private void showBondedDevices() {
        if (!hasConnectPermission()) {
            callback.onConnectionFailed("無 BLUETOOTH_CONNECT 權限，無法獲取已配對設備");
            return;
        }
        try {
            Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
            for (BluetoothDevice device : bondedDevices) {
                String name = device.getName() != null ? device.getName() : "Unknown";
                String address = device.getAddress();
                String item = name + " - " + address;
                if (!discoveredDevices.contains(item)) {
                    discoveredDevices.add(item);
                }
            }
            callback.onDeviceFound(new ArrayList<>(discoveredDevices));
            if (bondedDevices.isEmpty()) {
                callback.onConnectionFailed("未找到已配對的設備");
            }
        } catch (SecurityException e) {
            callback.onConnectionFailed("安全異常，無法獲取已配對設備: " + e.getMessage());
            Log.e(TAG, "獲取已配對設備失敗", e);
        }
    }

    /**
     * Broadcast receiver for Bluetooth device discovery.
     */
    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && hasConnectPermission()) {
                    String name = "Unknown";
                    String address = "Unknown";
                    try {
                        name = device.getName() != null ? device.getName() : "Unknown";
                        address = device.getAddress();
                    } catch (SecurityException e) {
                        Log.e(TAG, "獲取設備資訊失敗", e);
                        callback.onConnectionFailed("安全異常，無法獲取設備資訊: " + e.getMessage());
                    }
                    String item = name + " - " + address;
                    if (!discoveredDevices.contains(item)) {
                        discoveredDevices.add(item);
                        callback.onDeviceFound(new ArrayList<>(discoveredDevices));
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                callback.onDeviceFound(new ArrayList<>(discoveredDevices));
            }
        }
    };

    /**
     * Inner thread class for handling data input/output after connection.
     */
    private class ConnectedThread extends Thread {
        /**
         * Input stream for reading data.
         */
        private final InputStream mmInStream;

        /**
         * Output stream for writing data.
         */
        private final OutputStream mmOutStream;

        /**
         * Constructor to initialize streams.
         * @param socket Bluetooth socket.
         */
        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "初始化流失敗", e);
            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        /**
         * Run method to continuously read data.
         */
        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;
            while (true) {
                try {
                    bytes = mmInStream.read(buffer);
                    String data = new String(buffer, 0, bytes);
                    databaseHelper.insertLog("IN", data);
                    handler.post(() -> callback.onDataReceived(data));
                } catch (IOException e) {
                    handler.post(() -> callback.onConnectionFailed("連接斷開: " + e.getMessage()));
                    break;
                }
            }
        }

        /**
         * Writes bytes to the output stream.
         * @param bytes Data to write.
         */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Log.e(TAG, "發送數據失敗", e);
                handler.post(() -> callback.onConnectionFailed("發送失敗: " + e.getMessage()));
            }
        }

        /**
         * Cancels the thread by closing streams.
         */
        public void cancel() {
            try {
                mmInStream.close();
                mmOutStream.close();
            } catch (IOException e) {
                Log.e(TAG, "關閉流失敗", e);
            }
        }
    }
}