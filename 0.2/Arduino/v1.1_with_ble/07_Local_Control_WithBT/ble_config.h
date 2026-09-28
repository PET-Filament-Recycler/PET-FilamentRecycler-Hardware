#pragma once

#define BLE_SERVICE_UUID "94a9c8c1-9b2c-41e6-a662-e5728e07008a"
#define BLE_CONTROL_UUID "94a9c8c1-9b2c-41e6-a662-e5728e07008b"
#define BLE_STATUS_UUID  "94a9c8c1-9b2c-41e6-a662-e5728e07008c"
#define BLE_LOG_UUID     "94a9c8c1-9b2c-41e6-a662-e5728e07008d"

#define BLE_DEVICE_NAME_PREFIX "PET-Recycle-"
#define BLE_DEFAULT_DEVICE_ID 1
#define BLE_DEVICE_ID_MIN 1
#define BLE_DEVICE_ID_MAX 99

#define BLE_MTU 517
#define BLE_STATUS_NOTIFY_INTERVAL_MS 1500

#define BLE_PREFS_NAMESPACE "pet_ble"
#define BLE_PREFS_KEY_DEVICE_ID "device_id"

#define BLE_APP_TEMP_MIN 0
#define BLE_APP_TEMP_MAX 270
#define BLE_APP_SPEED_MIN 0
#define BLE_APP_SPEED_MAX 4096

String buildBleStatusString(float measuredTempC);

void setupBle();
void loopBle(float measuredTempC);
void notifyBleStatus(float measuredTempC);
void notifyBleLog(const String& msg);
void printBleSerialHelp();
void logEvent(const char* msg);
void logSetTarget(float tempC);
void logHeatPercent(uint8_t duty);
void logBtState(bool connected);
void logMachineStatus(float measuredTempC);