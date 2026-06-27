#include <Wire.h>
#include <hd44780.h>
#include <hd44780ioClass/hd44780_I2Cexp.h>
#include <AccelStepper.h>
#include <math.h>
#include "pins_config.h"

void setup() {
  pinMode(BTN_START_END, INPUT_PULLUP);
  pinMode(BTN_TEMP_PLUS, INPUT_PULLUP);
  pinMode(BTN_TEMP_MINUS, INPUT_PULLUP);
  pinMode(HEATER_PIN, OUTPUT);
  pinMode(FAN_PIN, OUTPUT);
  pinMode(EN_PIN, OUTPUT);
  pinMode(DIR_PIN, OUTPUT);
  pinMode(STEP_PIN, OUTPUT);

  digitalWrite(HEATER_PIN, LOW);
  digitalWrite(FAN_PIN, LOW);
  setDriverEnable(false);

  analogReadResolution(12);
  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);

  int status = lcd.begin(16, 2);
  if (status) {
    while (1) delay(100);
  }

  lcd.backlight();
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("LOCAL CTRL MODE");
  lcd.setCursor(0, 1);
  lcd.print("SET:");
  lcd.print(targetTempC, 0);
  lcd.print("C");
  delay(1200);

  stepper.setMaxSpeed(STEPPER_SPEED_MAX);
  stepper.setSpeed(0);

  if (isRunSwitchOn()) {
    startAll();
  } else {
    stopAll();
  }

  lcd.clear();
}

void loop() {
  handleRunSwitch();
  handleTempPlusButton();
  handleTempMinusButton();
  updateStepperSpeedFromPot();

  int raw = readAveragedADC();
  double tempC = adcToTempC(raw);

  if (isnan(tempC)) {
    sensorFaultStop("Sensor Error");
  }

  if (tempC >= MAX_SAFE_TEMP_C) {
    overTempStop("Over Temp");
  }

  handleHeaterControl(tempC);
  handleFanControl();

  if (machineRunning) {
    stepper.runSpeed();
  }

  updateLCD(tempC);
}