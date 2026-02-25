package com.petfilament.recycler;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import android.content.Intent;

/**
 * ControlActivity class handles Bluetooth control interface.
 */
public class ControlActivity extends AppCompatActivity implements BluetoothManager.BluetoothCallback {

    /**
     * Bluetooth manager instance.
     */
    private BluetoothManager bluetoothManager;

    /**
     * Spinner for Bluetooth devices.
     */
    private Spinner spinnerBluetoothDevices;

    /**
     * TextView for connection status.
     */
    private TextView textViewConnectionStatus;

    /**
     * TextView for machine status.
     */
    private TextView textViewMachineStatus;

    /**
     * Button to connect.
     */
    private Button buttonConnect;

    /**
     * Button to disconnect.
     */
    private Button buttonDisconnect;

    /**
     * Button to refresh devices.
     */
    private Button buttonRefresh;

    /**
     * Button to start machine.
     */
    private Button buttonStart;

    /**
     * Button to stop machine.
     */
    private Button buttonStop;

    /**
     * Button to save settings.
     */
    private Button buttonSave;

    /**
     * EditText for temperature.
     */
    private EditText editTextTemperature;

    /**
     * EditText for speed.
     */
    private EditText editTextSpeed;

    /**
     * Adapter for device spinner.
     */
    private ArrayAdapter<String> deviceAdapter;

    /**
     * TextView for current temperature.
     */
    private TextView textViewCurrentTemperature;

    /**
     * TextView for current speed.
     */
    private TextView textViewCurrentSpeed;

    /**
     * Button to view logs.
     */
    private Button buttonViewLogs;

    /**
     * Permission request code.
     */
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 100;

    /**
     * Machine status variable.
     */
    private String machineStatus = "IDLE";

    /**
     * Current temperature.
     */
    private float currentTemperature = 0;

    /**
     * Current speed.
     */
    private int currentSpeed = 0;

    /**
     * onCreate method initializes UI, Bluetooth, adapter, listeners, and permissions.
     * @param savedInstanceState Saved instance state.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);

        // Initialize all UI components
        initializeViews();

        // Initialize Bluetooth manager
        bluetoothManager = new BluetoothManager(this, this);

        // Initialize device list adapter
        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        deviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBluetoothDevices.setAdapter(deviceAdapter);

        // Set button click listeners
        setupButtonListeners();

        // Check and request necessary permissions
        checkAndRequestPermissions();
    }

    /**
     * Initializes all view components from layout.
     */
    private void initializeViews() {
        spinnerBluetoothDevices = findViewById(R.id.spinner_bluetooth_devices);
        textViewConnectionStatus = findViewById(R.id.textview_connection_status);
        textViewMachineStatus = findViewById(R.id.textview_machine_status);
        buttonConnect = findViewById(R.id.button_connect);
        buttonDisconnect = findViewById(R.id.button_disconnect);
        buttonRefresh = findViewById(R.id.button_refresh);
        buttonStart = findViewById(R.id.button_start);
        buttonStop = findViewById(R.id.button_stop);
        buttonSave = findViewById(R.id.button_save);
        editTextTemperature = findViewById(R.id.edittext_temperature);
        editTextSpeed = findViewById(R.id.edittext_speed);
        textViewCurrentTemperature = findViewById(R.id.textview_current_temperature);
        textViewCurrentSpeed = findViewById(R.id.textview_current_speed);
        buttonViewLogs = findViewById(R.id.button_view_logs);
    }

    /**
     * Sets up listeners for all buttons.
     */
    private void setupButtonListeners() {
        buttonRefresh.setOnClickListener(v -> {
            startBluetoothDiscovery();
            showToast("刷新設備列表");
        });

        buttonConnect.setOnClickListener(v -> {
            Object selected = spinnerBluetoothDevices.getSelectedItem();
            if (selected == null) {
                showToast("請先選擇設備");
                return;
            }
            String mac = selected.toString().split(" - ")[1];
            bluetoothManager.connect(mac);
        });

        buttonDisconnect.setOnClickListener(v -> {
            bluetoothManager.disconnect();
            textViewConnectionStatus.setText("Not Connected");
            textViewConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            setControlButtonsEnabled(false);
        });

        buttonStart.setOnClickListener(v -> {
            bluetoothManager.sendData("START");
            showToast("機器啟動");
        });

        buttonStop.setOnClickListener(v -> {
            bluetoothManager.sendData("STOP");
            showToast("機器停止");
        });

        buttonSave.setOnClickListener(v -> {
            String temp = editTextTemperature.getText().toString();
            String speed = editTextSpeed.getText().toString();
            if (!temp.isEmpty() && !speed.isEmpty()) {
                bluetoothManager.sendData("SET_TEMP:" + temp);
                bluetoothManager.sendData("SET_SPEED:" + speed);
                bluetoothManager.sendData("SAVE");
                showToast("設定已保存");
            } else {
                showToast("請輸入溫度與速度");
            }
        });

        buttonViewLogs.setOnClickListener(v -> {
            Intent intent = new Intent(ControlActivity.this, LogActivity.class);
            startActivity(intent);
        });
    }

    /**
     * Checks and requests Bluetooth and location permissions.
     */
    private void checkAndRequestPermissions() {
        // Check current permissions
        boolean hasAllPermissions = true;
        ArrayList<String> permissionsNeeded = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ needs BLUETOOTH_SCAN and BLUETOOTH_CONNECT
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                hasAllPermissions = false;
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                hasAllPermissions = false;
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }

        // All Android versions need location for Bluetooth scan
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            hasAllPermissions = false;
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (!hasAllPermissions && !permissionsNeeded.isEmpty()) {
            // Request missing permissions
            ActivityCompat.requestPermissions(
                    this,
                    permissionsNeeded.toArray(new String[0]),
                    REQUEST_BLUETOOTH_PERMISSIONS
            );
        } else {
            // Has all permissions, start discovery
            startBluetoothDiscovery();
        }
    }

    /**
     * Handles permission request results.
     * @param requestCode Request code.
     * @param permissions Permissions.
     * @param grantResults Grant results.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                startBluetoothDiscovery();
            } else {
                showToast("權限被拒絕，無法使用藍牙功能");
            }
        }
    }

    /**
     * Starts Bluetooth discovery if enabled.
     */
    private void startBluetoothDiscovery() {
        if (bluetoothManager.isBluetoothEnabled() || bluetoothManager.enableBluetooth()) {
            bluetoothManager.registerReceiver();
            bluetoothManager.startDiscovery();
        } else {
            showToast("請啟用藍牙");
        }
    }

    /**
     * onResume method registers receiver and refreshes devices.
     */
    @Override
    protected void onResume() {
        super.onResume();
        bluetoothManager.registerReceiver();
        startBluetoothDiscovery();
    }

    /**
     * onStop method unregisters receiver.
     */
    @Override
    protected void onStop() {
        super.onStop();
        bluetoothManager.unregisterReceiver();
    }

    /**
     * onDestroy method disconnects Bluetooth.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        bluetoothManager.disconnect();
        bluetoothManager.unregisterReceiver();
    }

    /**
     * Callback when devices are found.
     * @param devices List of devices.
     */
    @Override
    public void onDeviceFound(ArrayList<String> devices) {
        deviceAdapter.clear();
        deviceAdapter.addAll(devices);
        deviceAdapter.notifyDataSetChanged();
    }

    /**
     * Callback when connected.
     */
    @Override
    public void onConnected() {
        textViewConnectionStatus.setText("Connected");
        textViewConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        setControlButtonsEnabled(true);
        bluetoothManager.sendData("GET_STATUS");
    }

    /**
     * Callback when connection failed.
     * @param error Error message.
     */
    @Override
    public void onConnectionFailed(String error) {
        textViewConnectionStatus.setText("Not Connected");
        textViewConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        setControlButtonsEnabled(false);
        showToast(error);
    }

    /**
     * Callback when data received.
     * @param data Received data.
     */
    @Override
    public void onDataReceived(String data) {
        if (data.contains("TEMP:") && data.contains("SPEED:") && data.contains("STATUS:")) {
            parseStatusData(data);
        } else if (data.contains("TEMP:")) {
            // Temperature info
            String temp = data.replace("TEMP:", "").trim();
            try {
                currentTemperature = Float.parseFloat(temp);
                textViewCurrentTemperature.setText("Current: " + (int)currentTemperature + "°C");
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        } else if (data.contains("SPEED:")) {
            // Speed info
            String speed = data.replace("SPEED:", "").trim();
            try {
                currentSpeed = Integer.parseInt(speed);
                textViewCurrentSpeed.setText("Current: " + currentSpeed + " mm/s");
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        } else if (data.contains("STATUS:")) {
            // Status info
            String status = data.replace("STATUS:", "").trim();
            machineStatus = status;
            updateMachineStatusUI();
        } else {
            // Other data, log it
            Log.d("BluetoothData", "收到數據: " + data);
        }
    }

    /**
     * Parses status data string.
     * @param data Data to parse.
     */
    private void parseStatusData(String data) {
        try {
            String[] parts = data.split(",");
            for (String part : parts) {
                if (part.startsWith("TEMP:")) {
                    String tempStr = part.replace("TEMP:", "").trim();
                    currentTemperature = Float.parseFloat(tempStr);
                    textViewCurrentTemperature.setText("Current: " + (int)currentTemperature + "°C");
                } else if (part.startsWith("SPEED:")) {
                    String speedStr = part.replace("SPEED:", "").trim();
                    currentSpeed = Integer.parseInt(speedStr);
                    textViewCurrentSpeed.setText("Current: " + currentSpeed + " mm/s");
                } else if (part.startsWith("STATUS:")) {
                    String status = part.replace("STATUS:", "").trim();
                    machineStatus = status.equals("ON") ? "RUNNING" : "IDLE";
                    updateMachineStatusUI();
                }
            }
            showToast("狀態更新完成");
        } catch (Exception e) {
            e.printStackTrace();
            showToast("解析狀態數據失敗");
        }
    }

    /**
     * Updates machine status UI.
     */
    private void updateMachineStatusUI() {
        String displayText = "機器狀態: " + machineStatus + "\n"
                + "溫度: " + (int)currentTemperature + "°C\n"
                + "速度: " + currentSpeed + " mm/s";

        textViewMachineStatus.setText(displayText);

        // Change text color based on status
        if (machineStatus.equalsIgnoreCase("RUNNING") || machineStatus.equalsIgnoreCase("ON")) {
            textViewMachineStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else if (machineStatus.equalsIgnoreCase("IDLE") || machineStatus.equalsIgnoreCase("OFF")) {
            textViewMachineStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
        } else if (machineStatus.contains("ERROR") || machineStatus.contains("STOPPED")) {
            textViewMachineStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        }
    }

    /**
     * Enables or disables control buttons.
     * @param enabled True to enable, false to disable.
     */
    private void setControlButtonsEnabled(boolean enabled) {
        buttonStart.setEnabled(enabled);
        buttonStop.setEnabled(enabled);
        buttonSave.setEnabled(enabled);
        buttonDisconnect.setEnabled(enabled);

        // Set button alpha
        float alpha = enabled ? 1.0f : 0.5f;
        buttonStart.setAlpha(alpha);
        buttonStop.setAlpha(alpha);
        buttonSave.setAlpha(alpha);
        buttonDisconnect.setAlpha(alpha);
    }

    /**
     * Shows a toast message.
     * @param message Message to show.
     */
    private void showToast(String message) {
        Toast.makeText(ControlActivity.this, message, Toast.LENGTH_SHORT).show();
    }
}