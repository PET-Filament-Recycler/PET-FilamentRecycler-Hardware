#include <Wire.h>
#include <hd44780.h>
#include <hd44780ioClass/hd44780_I2Cexp.h>
#include <AccelStepper.h>
#include <math.h>
#include "pins_config.h"
#include "ble_config.h"

void waitForSerial(unsigned long timeoutMs = 3000) {
  unsigned long start = millis();
  while (!Serial && (millis() - start) < timeoutMs) {
    delay(10);
  }
}

void setup() {
  Serial.begin(115200);
  waitForSerial();
  delay(200);

  Serial.println();
  Serial.println("=== PET Recycle v1.1 (Local + BLE) ===");
  Serial.println("Boot: POWERON");

  pinMode(BTN_START_END, INPUT_PULLUP);
  pinMode(BTN_TEMP_PLUS, INPUT_PULLUP);
  pinMode(BTN_TEMP_MINUS, INPUT_PULLUP);
  pinMode(FAN_PIN, OUTPUT);
  pinMode(EN_PIN, OUTPUT);
  pinMode(DIR_PIN, OUTPUT);
  pinMode(STEP_PIN, OUTPUT);

  setupHeaterPwm();
  digitalWrite(FAN_PIN, LOW);
  setDriverEnable(false);
  Serial.println("GPIO init OK");

  analogReadResolution(12);
  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
  Serial.println("I2C init OK");

  int status = lcd.begin(16, 2);
  if (status) {
    lcdReady = false;
    Serial.print("LCD init FAILED, error=");
    Serial.println(status);
    Serial.println("Continue without LCD.");
  } else {
    lcdReady = true;
    Serial.println("LCD init OK");
    lcd.backlight();
    lcd.clear();
    lcd.setCursor(0, 0);
    lcd.print("LOCAL+BLE MODE");
    lcd.setCursor(0, 1);
    lcd.print("SET:");
    lcd.print(targetTempC, 0);
    lcd.print("C");
    delay(1200);
  }

  stepper.setMaxSpeed(STEPPER_SPEED_MAX);
  stepper.setSpeed(0);
  Serial.println("Stepper init OK");

  lastRunSwitchOn = isRunSwitchOn();
  if (lastRunSwitchOn) {
    startAll();
    Serial.println("Run switch ON -> started");
  } else {
    stopAll();
    Serial.println("Run switch OFF -> stopped");
  }

  setupBle();
  printBleSerialHelp();
  Serial.println("Setup complete.");

  if (lcdReady) {
    lcd.clear();
  }
}

void loop() {
  handleRunSwitch();
  handleTempPlusButton();
  handleTempMinusButton();
  updateStepperSpeedFromPot();

  int raw = readAveragedADC();
  double tempC = adcToTempC(raw);

  if (isnan(tempC)) {
    Serial.println("ERROR: Thermistor read failed");
    sensorFaultStop("Sensor Error");
  }

  if (tempC >= MAX_SAFE_TEMP_C) {
    Serial.print("ERROR: Over temp ");
    Serial.println(tempC, 1);
    overTempStop("Over Temp");
  }

  updatePidHeater(tempC);
  handleFanControl();

  if (machineRunning) {
    stepper.runSpeed();
  }

  updateLCD(tempC);
  loopBle((float)tempC);
}