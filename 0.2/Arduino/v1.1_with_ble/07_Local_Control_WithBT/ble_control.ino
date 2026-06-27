#include <Preferences.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include "ble_config.h"

Preferences blePrefs;
String bleDeviceName;

BLECharacteristic *bleControlChar = nullptr;
BLECharacteristic *bleStatusChar = nullptr;
BLECharacteristic *bleLogChar = nullptr;
BLEAdvertising *bleAdvertising = nullptr;

bool bleClientConnected = false;
int bleDeviceId = BLE_DEFAULT_DEVICE_ID;
unsigned long bleLastStatusNotifyMs = 0;

int clampBleDeviceId(int value) {
  if (value < BLE_DEVICE_ID_MIN) return BLE_DEVICE_ID_MIN;
  if (value > BLE_DEVICE_ID_MAX) return BLE_DEVICE_ID_MAX;
  return value;
}

float clampBleTemp(float value) {
  if (value < BLE_APP_TEMP_MIN) return BLE_APP_TEMP_MIN;
  if (value > BLE_APP_TEMP_MAX) return BLE_APP_TEMP_MAX;
  if (value < TARGET_TEMP_MIN) return TARGET_TEMP_MIN;
  if (value > TARGET_TEMP_MAX) return TARGET_TEMP_MAX;
  return value;
}

float clampBleSpeed(float value) {
  if (value < BLE_APP_SPEED_MIN) value = BLE_APP_SPEED_MIN;
  if (value > BLE_APP_SPEED_MAX) value = BLE_APP_SPEED_MAX;
  if (value < STEPPER_SPEED_MIN) value = STEPPER_SPEED_MIN;
  if (value > STEPPER_SPEED_MAX) value = STEPPER_SPEED_MAX;
  return value;
}

String buildBleDeviceName(int id) {
  return String(BLE_DEVICE_NAME_PREFIX) + String(id);
}

int loadBleDeviceId() {
  blePrefs.begin(BLE_PREFS_NAMESPACE, true);
  int storedId = blePrefs.getInt(BLE_PREFS_KEY_DEVICE_ID, BLE_DEFAULT_DEVICE_ID);
  blePrefs.end();
  return clampBleDeviceId(storedId);
}

void saveBleDeviceId(int id) {
  id = clampBleDeviceId(id);
  blePrefs.begin(BLE_PREFS_NAMESPACE, false);
  blePrefs.putInt(BLE_PREFS_KEY_DEVICE_ID, id);
  blePrefs.end();
  bleDeviceId = id;
  bleDeviceName = buildBleDeviceName(bleDeviceId);
}

String buildBleStatusString(float measuredTempC) {
  String msg = "TEMP:";
  if (isnan(measuredTempC)) {
    msg += "ERR";
  } else {
    msg += String((int)round(measuredTempC));
  }
  msg += ",SPEED:";
  msg += String((int)round(currentStepperSpeed));
  msg += ",STATUS:";
  msg += (machineRunning ? "ON" : "OFF");
  return msg;
}

void notifyBleStatus(float measuredTempC) {
  if (bleStatusChar == nullptr) return;

  String msg = buildBleStatusString(measuredTempC);
  bleStatusChar->setValue(msg.c_str());

  if (bleClientConnected) {
    bleStatusChar->notify();
  }
}

void notifyBleLog(const String& msg) {
  if (bleLogChar == nullptr) return;

  bleLogChar->setValue(msg.c_str());
  if (bleClientConnected) {
    bleLogChar->notify();
  }

  Serial.print("BLE LOG -> ");
  Serial.println(msg);
}

void applyBleSpeed(float speed) {
  currentStepperSpeed = clampBleSpeed(speed);
  bleSpeedOverride = true;

  if (machineRunning) {
    stepper.setSpeed(currentStepperSpeed);
  }
}

void processBleCommand(const String& cmdRaw, float measuredTempC) {
  String cmd = cmdRaw;
  cmd.trim();
  if (cmd.length() == 0) return;

  Serial.print("BLE CMD <- ");
  Serial.println(cmd);

  if (cmd == "START") {
    applyAppRunCommand(true);
    notifyBleLog("Machine started");
    notifyBleStatus(measuredTempC);
  } else if (cmd == "STOP") {
    applyAppRunCommand(false);
    notifyBleLog("Machine stopped");
    notifyBleStatus(measuredTempC);
  } else if (cmd.startsWith("SET_TEMP:")) {
    float value = clampBleTemp(cmd.substring(9).toFloat());
    targetTempC = value;
    notifyBleLog("Temp set to " + String(targetTempC, 0) + " C");
    notifyBleStatus(measuredTempC);
  } else if (cmd.startsWith("SET_SPEED:")) {
    float value = cmd.substring(10).toFloat();
    applyBleSpeed(value);
    notifyBleLog("Speed set to " + String((int)round(currentStepperSpeed)) + " mm/s");
    notifyBleStatus(measuredTempC);
  } else if (cmd == "GET_STATUS") {
    notifyBleStatus(measuredTempC);
  } else {
    notifyBleLog("Unknown command: " + cmd);
  }
}

void printBleSerialHelp() {
  Serial.println();
  Serial.println("BLE setup commands (115200 baud):");
  Serial.println("  GET_ID        Show saved device ID and BLE name");
  Serial.println("  SET_ID:2      Save device ID and restart board");
  Serial.println("  HELP          Show this help");
  Serial.println();
}

void processBleSerialCommand(const String& cmdRaw) {
  String cmd = cmdRaw;
  cmd.trim();
  if (cmd.length() == 0) return;

  Serial.print("SERIAL <- ");
  Serial.println(cmd);

  if (cmd == "HELP") {
    printBleSerialHelp();
  } else if (cmd == "GET_ID") {
    Serial.print("Device ID: ");
    Serial.println(bleDeviceId);
    Serial.print("BLE name: ");
    Serial.println(bleDeviceName);
  } else if (cmd.startsWith("SET_ID:")) {
    int newId = clampBleDeviceId(cmd.substring(7).toInt());
    if (newId == bleDeviceId) {
      Serial.println("Device ID unchanged.");
      return;
    }

    saveBleDeviceId(newId);
    Serial.print("Device ID saved as ");
    Serial.print(newId);
    Serial.println(". Restarting...");
    delay(300);
    ESP.restart();
  } else {
    Serial.println("Unknown serial command. Type HELP for options.");
  }
}

void readBleSerialCommands() {
  static String serialBuffer;

  while (Serial.available() > 0) {
    char ch = Serial.read();
    if (ch == '\r') continue;

    if (ch == '\n') {
      processBleSerialCommand(serialBuffer);
      serialBuffer = "";
    } else {
      serialBuffer += ch;
    }
  }
}

class BleServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *pServer) override {
    bleClientConnected = true;
    Serial.println("BLE client connected");
    notifyBleLog("BLE client connected");
  }

  void onDisconnect(BLEServer *pServer) override {
    bleClientConnected = false;
    Serial.println("BLE client disconnected");
    notifyBleLog("BLE client disconnected");
    releaseAppRunControl();

    delay(100);
    if (bleAdvertising != nullptr) {
      bleAdvertising->start();
      Serial.println("BLE advertising restarted");
    }
  }
};

class BleControlCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pCharacteristic) override {
    String rx = pCharacteristic->getValue();
    if (rx.length() > 0) {
      processBleCommand(rx, lastMeasuredTempC);
    }
  }
};

void setupBle() {
  bleDeviceId = loadBleDeviceId();
  bleDeviceName = buildBleDeviceName(bleDeviceId);

  BLEDevice::init(bleDeviceName.c_str());
  BLEDevice::setMTU(BLE_MTU);

  BLEServer *server = BLEDevice::createServer();
  server->setCallbacks(new BleServerCallbacks());

  BLEService *service = server->createService(BLE_SERVICE_UUID);

  bleControlChar = service->createCharacteristic(
    BLE_CONTROL_UUID,
    BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR
  );
  bleControlChar->setCallbacks(new BleControlCallbacks());

  bleStatusChar = service->createCharacteristic(
    BLE_STATUS_UUID,
    BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
  );
  bleStatusChar->addDescriptor(new BLE2902());

  bleLogChar = service->createCharacteristic(
    BLE_LOG_UUID,
    BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
  );
  bleLogChar->addDescriptor(new BLE2902());

  bleStatusChar->setValue("TEMP:0,SPEED:0,STATUS:OFF");
  bleLogChar->setValue("BLE Ready");

  service->start();

  bleAdvertising = BLEDevice::getAdvertising();
  bleAdvertising->addServiceUUID(BLE_SERVICE_UUID);
  bleAdvertising->setScanResponse(true);
  bleAdvertising->start();

  Serial.print("BLE advertising started as ");
  Serial.println(bleDeviceName);
  Serial.print("BLE MTU set to ");
  Serial.println(BLE_MTU);
}

void loopBle(float measuredTempC) {
  lastMeasuredTempC = measuredTempC;
  readBleSerialCommands();

  unsigned long now = millis();
  if (bleClientConnected && (now - bleLastStatusNotifyMs >= BLE_STATUS_NOTIFY_INTERVAL_MS)) {
    bleLastStatusNotifyMs = now;
    notifyBleStatus(measuredTempC);
  }
}