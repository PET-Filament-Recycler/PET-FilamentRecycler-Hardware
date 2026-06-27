#include <Arduino.h>
#include <Preferences.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#define SERVICE_UUID "94a9c8c1-9b2c-41e6-a662-e5728e07008a"
#define CONTROL_UUID "94a9c8c1-9b2c-41e6-a662-e5728e07008b"
#define STATUS_UUID  "94a9c8c1-9b2c-41e6-a662-e5728e07008c"
#define LOG_UUID     "94a9c8c1-9b2c-41e6-a662-e5728e07008d"

#define DEVICE_NAME_PREFIX "PET-Recycle-"
#define DEFAULT_DEVICE_ID 1
#define DEVICE_ID_MIN 1
#define DEVICE_ID_MAX 99

#define TEMP_MIN 0
#define TEMP_MAX 300
#define SPEED_MIN 0
#define SPEED_MAX 4096

#define AMBIENT_TEMP_C 27.0f
#define STATUS_NOTIFY_INTERVAL_MS 1500
#define BLE_MTU 517

#define PREFS_NAMESPACE "pet_ble"
#define PREFS_KEY_DEVICE_ID "device_id"

Preferences prefs;
String deviceBleName;

BLECharacteristic *controlChar = nullptr;
BLECharacteristic *statusChar = nullptr;
BLECharacteristic *logChar = nullptr;
BLEAdvertising *advertising = nullptr;

bool bleClientConnected = false;
bool machineRunning = false;
float targetTempC = 30.0f;
float currentTempC = 27.5f;
int currentSpeed = 0;
int deviceId = DEFAULT_DEVICE_ID;
String lastLogMessage = "BLE Ready";

int clampDeviceId(int value) {
  if (value < DEVICE_ID_MIN) return DEVICE_ID_MIN;
  if (value > DEVICE_ID_MAX) return DEVICE_ID_MAX;
  return value;
}

float clampTemp(float value) {
  if (value < TEMP_MIN) return TEMP_MIN;
  if (value > TEMP_MAX) return TEMP_MAX;
  return value;
}

int clampSpeed(int value) {
  if (value < SPEED_MIN) return SPEED_MIN;
  if (value > SPEED_MAX) return SPEED_MAX;
  return value;
}

String buildBleName(int id) {
  return String(DEVICE_NAME_PREFIX) + String(id);
}

int loadDeviceId() {
  prefs.begin(PREFS_NAMESPACE, true);
  int storedId = prefs.getInt(PREFS_KEY_DEVICE_ID, DEFAULT_DEVICE_ID);
  prefs.end();
  return clampDeviceId(storedId);
}

void saveDeviceId(int id) {
  id = clampDeviceId(id);
  prefs.begin(PREFS_NAMESPACE, false);
  prefs.putInt(PREFS_KEY_DEVICE_ID, id);
  prefs.end();
  deviceId = id;
  deviceBleName = buildBleName(deviceId);
}

String buildStatusString() {
  String msg = "TEMP:";
  msg += String((int)round(currentTempC));
  msg += ",SPEED:";
  msg += String(currentSpeed);
  msg += ",STATUS:";
  msg += (machineRunning ? "ON" : "OFF");
  return msg;
}

void notifyStatus() {
  if (statusChar == nullptr) return;

  String msg = buildStatusString();
  statusChar->setValue(msg.c_str());

  if (bleClientConnected) {
    statusChar->notify();
  }

  Serial.print("STATUS -> ");
  Serial.println(msg);
}

void notifyLog(String msg) {
  lastLogMessage = msg;

  if (logChar != nullptr) {
    logChar->setValue(lastLogMessage.c_str());
    if (bleClientConnected) {
      logChar->notify();
    }
  }

  Serial.print("LOG -> ");
  Serial.println(msg);
}

void processBleCommand(String cmd) {
  cmd.trim();
  Serial.print("CMD <- ");
  Serial.println(cmd);

  if (cmd == "START") {
    machineRunning = true;
    notifyLog("Machine started");
    notifyStatus();
  } else if (cmd == "STOP") {
    machineRunning = false;
    notifyLog("Machine stopped");
    notifyStatus();
  } else if (cmd.startsWith("SET_TEMP:")) {
    float value = clampTemp(cmd.substring(9).toFloat());
    targetTempC = value;
    currentTempC = value - 2.0f;
    if (currentTempC < AMBIENT_TEMP_C) {
      currentTempC = AMBIENT_TEMP_C;
    }
    notifyLog("Temp set to " + String(targetTempC, 1) + " C");
    notifyStatus();
  } else if (cmd.startsWith("SET_SPEED:")) {
    int value = clampSpeed(cmd.substring(10).toInt());
    currentSpeed = value;
    notifyLog("Speed set to " + String(currentSpeed) + " mm/s");
    notifyStatus();
  } else if (cmd == "GET_STATUS") {
    notifyStatus();
  } else {
    notifyLog("Unknown command: " + cmd);
  }
}

void printSerialHelp() {
  Serial.println();
  Serial.println("Serial commands (115200 baud):");
  Serial.println("  GET_ID        Show saved device ID and BLE name");
  Serial.println("  SET_ID:2      Save device ID and restart board");
  Serial.println("  HELP          Show this help");
  Serial.println();
}

void processSerialCommand(String cmd) {
  cmd.trim();
  if (cmd.length() == 0) return;

  Serial.print("SERIAL <- ");
  Serial.println(cmd);

  if (cmd == "HELP") {
    printSerialHelp();
  } else if (cmd == "GET_ID") {
    Serial.print("Device ID: ");
    Serial.println(deviceId);
    Serial.print("BLE name: ");
    Serial.println(deviceBleName);
  } else if (cmd.startsWith("SET_ID:")) {
    int newId = clampDeviceId(cmd.substring(7).toInt());
    if (newId == deviceId) {
      Serial.println("Device ID unchanged.");
      return;
    }

    saveDeviceId(newId);
    Serial.print("Device ID saved as ");
    Serial.print(newId);
    Serial.println(". Restarting...");
    delay(300);
    ESP.restart();
  } else {
    Serial.println("Unknown serial command. Type HELP for options.");
  }
}

void readSerialCommands() {
  static String serialBuffer;

  while (Serial.available() > 0) {
    char ch = Serial.read();
    if (ch == '\r') continue;

    if (ch == '\n') {
      processSerialCommand(serialBuffer);
      serialBuffer = "";
    } else {
      serialBuffer += ch;
    }
  }
}

class MyServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *pServer) override {
    bleClientConnected = true;
    Serial.println("BLE client connected");
    notifyLog("BLE client connected");
    notifyStatus();
  }

  void onDisconnect(BLEServer *pServer) override {
    bleClientConnected = false;
    Serial.println("BLE client disconnected");
    notifyLog("BLE client disconnected");

    delay(100);
    if (advertising != nullptr) {
      advertising->start();
      Serial.println("BLE advertising restarted");
    }
  }
};

class ControlCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pCharacteristic) override {
    String rx = pCharacteristic->getValue();
    if (rx.length() > 0) {
      processBleCommand(rx);
    }
  }
};

void setupBLE() {
  BLEDevice::init(deviceBleName.c_str());
  BLEDevice::setMTU(BLE_MTU);

  BLEServer *server = BLEDevice::createServer();
  server->setCallbacks(new MyServerCallbacks());

  BLEService *service = server->createService(SERVICE_UUID);

  controlChar = service->createCharacteristic(
    CONTROL_UUID,
    BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR
  );
  controlChar->setCallbacks(new ControlCallbacks());

  statusChar = service->createCharacteristic(
    STATUS_UUID,
    BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
  );
  statusChar->addDescriptor(new BLE2902());

  logChar = service->createCharacteristic(
    LOG_UUID,
    BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
  );
  logChar->addDescriptor(new BLE2902());

  statusChar->setValue(buildStatusString().c_str());
  logChar->setValue("BLE Ready");

  service->start();

  advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  advertising->setScanResponse(true);
  advertising->start();

  Serial.print("BLE advertising started as ");
  Serial.println(deviceBleName);
  Serial.print("BLE MTU set to ");
  Serial.println(BLE_MTU);
}

void setup() {
  Serial.begin(115200);
  delay(1000);

  deviceId = loadDeviceId();
  deviceBleName = buildBleName(deviceId);

  Serial.println("ESP32-C6 BLE test starting...");
  Serial.print("Loaded device ID: ");
  Serial.println(deviceId);
  printSerialHelp();

  setupBLE();
}

void loop() {
  readSerialCommands();

  static unsigned long lastUpdate = 0;
  if (millis() - lastUpdate > STATUS_NOTIFY_INTERVAL_MS) {
    lastUpdate = millis();

    if (machineRunning && currentTempC < targetTempC) {
      currentTempC += 0.3f;
      if (currentTempC > targetTempC) {
        currentTempC = targetTempC;
      }
    } else if (!machineRunning && currentTempC > AMBIENT_TEMP_C) {
      currentTempC -= 0.2f;
      if (currentTempC < AMBIENT_TEMP_C) {
        currentTempC = AMBIENT_TEMP_C;
      }
    }

    notifyStatus();
  }
}