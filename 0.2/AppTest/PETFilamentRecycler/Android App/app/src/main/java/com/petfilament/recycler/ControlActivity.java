package com.petfilament.recycler;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.petfilament.recycler.ble.BleConstants;
import com.petfilament.recycler.ble.MachineState;
import com.petfilament.recycler.ble.StatusParser;

import java.util.ArrayList;
import java.util.UUID;

public class ControlActivity extends AppCompatActivity implements BluetoothManager.BluetoothCallback {

    private static final String TAG = "ControlActivity";
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 100;

    private BluetoothManager bluetoothManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Spinner spinnerBluetoothDevices;
    private TextView textViewConnectionStatus;
    private TextView textViewTempStatus;
    private TextView textViewSpeedStatus;
    private TextView textViewPowerStatus;
    private Button buttonConnect;
    private Button buttonDisconnect;
    private Button buttonRefresh;
    private Button buttonStart;
    private Button buttonStop;
    private Button buttonSave;
    private EditText editTextTemperature;
    private EditText editTextSpeed;
    private Button buttonViewLogs;

    private ArrayAdapter<String> deviceAdapter;
    private MachineState machineState = new MachineState();
    private boolean isConnected = false;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_control);

        applySystemBarInsets();
        setupToolbar();
        initializeViews();

        bluetoothManager = new BluetoothManager(this, this);

        deviceAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new ArrayList<>()
        );
        deviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBluetoothDevices.setAdapter(deviceAdapter);

        setupButtonListeners();
        setControlButtonsEnabled(false);
        updateDisconnectedUI();
        updateStatusDisplay();
        checkAndRequestPermissions();
    }

    private void applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.app_bar), (view, windowInsets) -> {
            Insets statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            view.setPadding(0, statusBars.top, 0, 0);
            return windowInsets;
        });
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_control);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_language) {
                LocaleHelper.toggleLanguage(this);
                recreate();
                return true;
            }
            return false;
        });
    }

    private void initializeViews() {
        spinnerBluetoothDevices = findViewById(R.id.spinner_bluetooth_devices);
        textViewConnectionStatus = findViewById(R.id.textview_connection_status);
        textViewTempStatus = findViewById(R.id.textview_temp_status);
        textViewSpeedStatus = findViewById(R.id.textview_speed_status);
        textViewPowerStatus = findViewById(R.id.textview_power_status);
        buttonConnect = findViewById(R.id.button_connect);
        buttonDisconnect = findViewById(R.id.button_disconnect);
        buttonRefresh = findViewById(R.id.button_refresh);
        buttonStart = findViewById(R.id.button_start);
        buttonStop = findViewById(R.id.button_stop);
        buttonSave = findViewById(R.id.button_save);
        editTextTemperature = findViewById(R.id.edittext_temperature);
        editTextSpeed = findViewById(R.id.edittext_speed);
        buttonViewLogs = findViewById(R.id.button_view_logs);
    }

    private void setupButtonListeners() {
        buttonRefresh.setOnClickListener(v -> {
            if (isConnected) {
                showToast(getString(R.string.already_connected_no_scan));
                return;
            }
            startBluetoothDiscovery();
            showToast(getString(R.string.refreshing_devices));
        });

        buttonConnect.setOnClickListener(v -> {
            Object selected = spinnerBluetoothDevices.getSelectedItem();
            if (selected == null) {
                showToast(getString(R.string.select_device_first));
                return;
            }

            String mac = BluetoothManager.extractMacAddress(selected.toString());
            if (mac.isEmpty()) {
                showToast(getString(R.string.invalid_device_format));
                return;
            }

            textViewConnectionStatus.setText(R.string.connecting);
            bluetoothManager.connect(mac);
        });

        buttonDisconnect.setOnClickListener(v -> {
            bluetoothManager.disconnect();
            isConnected = false;
            machineState = new MachineState();
            updateDisconnectedUI();
            updateStatusDisplay();
            showToast(getString(R.string.disconnected));
        });

        buttonStart.setOnClickListener(v -> {
            bluetoothManager.sendData(BleConstants.CMD_START);
            machineState.setStatus(MachineState.STATUS_ON);
            updateStatusDisplay();
        });
        buttonStop.setOnClickListener(v -> {
            bluetoothManager.sendData(BleConstants.CMD_STOP);
            machineState.setStatus(MachineState.STATUS_OFF);
            updateStatusDisplay();
        });

        buttonSave.setOnClickListener(v -> {
            String tempText = editTextTemperature.getText().toString().trim();
            String speedText = editTextSpeed.getText().toString().trim();

            if (tempText.isEmpty() || speedText.isEmpty()) {
                showToast(getString(R.string.enter_temp_and_speed));
                return;
            }

            int temp;
            int speed;
            try {
                temp = Integer.parseInt(tempText);
                speed = Integer.parseInt(speedText);
            } catch (NumberFormatException e) {
                showToast(getString(R.string.invalid_number));
                return;
            }

            if (temp < BleConstants.TEMP_MIN || temp > BleConstants.TEMP_MAX) {
                showToast(getString(R.string.temp_range_error, BleConstants.TEMP_MIN, BleConstants.TEMP_MAX));
                return;
            }
            if (speed < BleConstants.SPEED_MIN || speed > BleConstants.SPEED_MAX) {
                showToast(getString(R.string.speed_range_error, BleConstants.SPEED_MIN, BleConstants.SPEED_MAX));
                return;
            }

            bluetoothManager.sendData(BleConstants.CMD_SET_TEMP_PREFIX + temp);
            handler.postDelayed(
                    () -> bluetoothManager.sendData(BleConstants.CMD_SET_SPEED_PREFIX + speed),
                    250
            );
            showToast(getString(R.string.settings_sent));
        });

        buttonViewLogs.setOnClickListener(v ->
                startActivity(new Intent(ControlActivity.this, LogActivity.class))
        );
    }

    private void checkAndRequestPermissions() {
        ArrayList<String> permissionsNeeded = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsNeeded.toArray(new String[0]),
                    REQUEST_BLUETOOTH_PERMISSIONS
            );
        } else {
            startBluetoothDiscovery();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
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
                showToast(getString(R.string.permission_denied_bluetooth));
            }
        }
    }

    private void startBluetoothDiscovery() {
        if (isConnected) {
            return;
        }

        if (bluetoothManager.isBluetoothEnabled() || bluetoothManager.enableBluetooth()) {
            bluetoothManager.startDiscovery();
        } else {
            showToast(getString(R.string.enable_bluetooth));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isConnected) {
            startBluetoothDiscovery();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isConnected) {
            bluetoothManager.stopDiscovery();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bluetoothManager.stopDiscovery();
        bluetoothManager.disconnect();
    }

    @Override
    public void onDeviceFound(ArrayList<String> devices) {
        if (isConnected) {
            return;
        }

        deviceAdapter.clear();
        deviceAdapter.addAll(devices);
        deviceAdapter.notifyDataSetChanged();
    }

    @Override
    public void onConnected() {
        isConnected = true;
        textViewConnectionStatus.setText(R.string.connected);
        textViewConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.success));
        setControlButtonsEnabled(true);
        showToast(getString(R.string.ble_connected));
    }

    @Override
    public void onDisconnected() {
        isConnected = false;
        machineState = new MachineState();
        updateDisconnectedUI();
        updateStatusDisplay();
        showToast(getString(R.string.connection_lost));
    }

    @Override
    public void onConnectionFailed(String error) {
        isConnected = false;
        updateDisconnectedUI();
        showToast(error);
    }

    @Override
    public void onDataReceived(UUID characteristicUuid, String data) {
        Log.d(TAG, "Received (" + characteristicUuid + "): " + data);
        if (!BleConstants.STATUS_UUID.equals(characteristicUuid)) {
            return;
        }
        if (data == null || data.trim().isEmpty()) {
            return;
        }

        applyMachineState(StatusParser.parse(data));
    }

    private void applyMachineState(MachineState parsed) {
        if (parsed.hasTemperature()) {
            machineState.setTemperature(parsed.getTemperature());
        }
        if (parsed.hasSpeed()) {
            machineState.setSpeed(parsed.getSpeed());
        }
        if (parsed.hasStatus()) {
            machineState.setStatus(parsed.getStatus());
        }
        updateStatusDisplay();
    }

    private void updateStatusDisplay() {
        if (machineState.hasTemperature()) {
            textViewTempStatus.setText(
                    getString(R.string.status_temp_value, machineState.getTemperature())
            );
        } else {
            textViewTempStatus.setText(R.string.status_temp_placeholder);
        }

        if (machineState.hasSpeed()) {
            textViewSpeedStatus.setText(
                    getString(R.string.status_speed_value, machineState.getSpeed())
            );
        } else {
            textViewSpeedStatus.setText(R.string.status_speed_placeholder);
        }

        textViewTempStatus.setBackgroundResource(R.drawable.bg_status_badge);
        textViewSpeedStatus.setBackgroundResource(R.drawable.bg_status_badge);

        String status = machineState.getStatus();
        if (MachineState.STATUS_ON.equalsIgnoreCase(status)) {
            textViewPowerStatus.setText(R.string.status_power_on);
            textViewPowerStatus.setTextColor(ContextCompat.getColor(this, R.color.on_success_container));
            textViewPowerStatus.setBackgroundResource(R.drawable.bg_status_on);
        } else if (MachineState.STATUS_OFF.equalsIgnoreCase(status)) {
            textViewPowerStatus.setText(R.string.status_power_off);
            textViewPowerStatus.setTextColor(ContextCompat.getColor(this, R.color.on_warning_container));
            textViewPowerStatus.setBackgroundResource(R.drawable.bg_status_off);
        } else {
            textViewPowerStatus.setText(R.string.status_power_unknown);
            textViewPowerStatus.setTextColor(ContextCompat.getColor(this, R.color.on_surface));
            textViewPowerStatus.setBackgroundResource(R.drawable.bg_status_badge);
        }
    }

    private void updateDisconnectedUI() {
        textViewConnectionStatus.setText(R.string.not_connected);
        textViewConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.error));
        setControlButtonsEnabled(false);
    }

    private void setControlButtonsEnabled(boolean enabled) {
        buttonStart.setEnabled(enabled);
        buttonStop.setEnabled(enabled);
        buttonSave.setEnabled(enabled);
        buttonDisconnect.setEnabled(enabled);

        float alpha = enabled ? 1.0f : 0.5f;
        buttonStart.setAlpha(alpha);
        buttonStop.setAlpha(alpha);
        buttonSave.setAlpha(alpha);
        buttonDisconnect.setAlpha(alpha);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}