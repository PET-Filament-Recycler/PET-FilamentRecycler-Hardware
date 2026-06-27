void setDriverEnable(bool en) {
  digitalWrite(EN_PIN, en ? DRIVER_ENABLE_LEVEL : DRIVER_DISABLE_LEVEL);
}

void setFan(bool on) {
  fanState = on;
  digitalWrite(FAN_PIN, on ? HIGH : LOW);
}

void updateStepperSpeedFromPot() {
  unsigned long now = millis();
  if (now - lastPotMs < POT_UPDATE_MS) return;
  lastPotMs = now;

  int raw = analogRead(POT_PIN);

  float ratio = (float)raw / (float)ADC_MAX;
  currentStepperSpeed = STEPPER_SPEED_MIN + ratio * (STEPPER_SPEED_MAX - STEPPER_SPEED_MIN);

  if (currentStepperSpeed < STEPPER_SPEED_MIN) currentStepperSpeed = STEPPER_SPEED_MIN;
  if (currentStepperSpeed > STEPPER_SPEED_MAX) currentStepperSpeed = STEPPER_SPEED_MAX;

  if (machineRunning) {
    stepper.setSpeed(currentStepperSpeed);
  }
}

void stopAll() {
  machineRunning = false;
  setHeater(false);
  setFan(false);
  stepper.setSpeed(0);
  setDriverEnable(false);
}

void startAll() {
  machineRunning = true;
  setDriverEnable(true);
  stepper.setSpeed(currentStepperSpeed);
  setFan(true);
}

void handleFanControl() {
  if (!machineRunning) {
    setFan(false);
    return;
  }

  setFan(true);
}

void sensorFaultStop(const char* msg) {
  digitalWrite(HEATER_PIN, LOW);
  digitalWrite(FAN_PIN, LOW);
  stepper.setSpeed(0);
  setDriverEnable(false);
  machineRunning = false;
  heaterState = false;
  fanState = false;

  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("EMERGENCY STOP");
  lcd.setCursor(0, 1);
  lcd.print(msg);

  while (1) {
    delay(100);
  }
}

void overTempStop(const char* msg) {
  digitalWrite(HEATER_PIN, LOW);
  digitalWrite(FAN_PIN, HIGH);
  stepper.setSpeed(0);
  setDriverEnable(false);
  machineRunning = false;
  heaterState = false;
  fanState = true;

  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("OVER TEMP");
  lcd.setCursor(0, 1);
  lcd.print(msg);

  while (1) {
    delay(100);
  }
}