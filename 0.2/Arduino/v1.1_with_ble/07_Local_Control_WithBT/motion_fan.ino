void setDriverEnable(bool en) {
  digitalWrite(EN_PIN, en ? DRIVER_ENABLE_LEVEL : DRIVER_DISABLE_LEVEL);
}

void setFan(bool on) {
  fanState = on;
  digitalWrite(FAN_PIN, on ? HIGH : LOW);
}

int readAveragedPotRaw() {
  long sum = 0;
  for (int i = 0; i < POT_AVG_SAMPLES; i++) {
    sum += analogRead(POT_PIN);
  }
  return (int)(sum / POT_AVG_SAMPLES);
}

int readStablePotRaw() {
  int newRaw = readAveragedPotRaw();
  if (abs(newRaw - stablePotRaw) > POT_HYSTERESIS) {
    stablePotRaw = newRaw;
  }
  return stablePotRaw;
}

float mapPotRawToSpeed(int raw) {
  int clamped = constrain(raw, POT_RAW_MIN, POT_RAW_MAX);
  float percent = (clamped - POT_RAW_MIN) * 100.0f / (float)(POT_RAW_MAX - POT_RAW_MIN);

  if (percent <= 0.5f) {
    return STEPPER_SPEED_MIN;
  }

  float speed = STEPPER_MIN_RUN_SPEED +
                (percent / 100.0f) * (STEPPER_SPEED_MAX - STEPPER_MIN_RUN_SPEED);

  if (speed < STEPPER_MIN_RUN_SPEED) speed = STEPPER_MIN_RUN_SPEED;
  if (speed > STEPPER_SPEED_MAX) speed = STEPPER_SPEED_MAX;
  return speed;
}

void updateStepperSpeedFromPot() {
  unsigned long now = millis();
  if (now - lastPotMs < POT_UPDATE_MS) return;
  lastPotMs = now;

  int raw = readStablePotRaw();

  static int potBaselineRaw = -1;
  if (bleSpeedOverride) {
    if (potBaselineRaw < 0) {
      potBaselineRaw = raw;
      return;
    }
    if (abs(raw - potBaselineRaw) < 80) {
      return;
    }
    bleSpeedOverride = false;
    potBaselineRaw = -1;
  } else {
    potBaselineRaw = -1;
  }

  currentStepperSpeed = mapPotRawToSpeed(raw);

  if (machineRunning) {
    stepper.setSpeed(currentStepperSpeed);
  }
}

void serviceStepper() {
  if (!machineRunning) return;
  stepper.runSpeed();
}

void stopAll() {
  machineRunning = false;
  // Force heater gate off via both helpers (app Stop + hardware run switch).
  setHeater(false);
  setHeaterPwm(0);
  setFan(false);
  stepper.setSpeed(0);
  setDriverEnable(false);
  logEvent("Machine stopped");
  notifyBleLog("Heater forced OFF (STOP)");
}

void startAll() {
  machineRunning = true;
  setDriverEnable(true);
  stepper.setSpeed(currentStepperSpeed);
  setFan(true);
  logEvent("Machine started");
}

void handleFanControl() {
  if (!machineRunning) {
    setFan(false);
    return;
  }

  setFan(true);
}

void sensorFaultStop(const char* msg) {
  setHeaterPwm(0);
  digitalWrite(FAN_PIN, LOW);
  stepper.setSpeed(0);
  setDriverEnable(false);
  machineRunning = false;
  heaterState = false;
  fanState = false;

  Serial.print("EMERGENCY STOP: ");
  Serial.println(msg);

  if (!lcdReady) {
    while (1) delay(100);
  }

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
  setHeaterPwm(0);
  digitalWrite(FAN_PIN, HIGH);
  stepper.setSpeed(0);
  setDriverEnable(false);
  machineRunning = false;
  heaterState = false;
  fanState = true;

  Serial.print("OVER TEMP STOP: ");
  Serial.println(msg);

  if (!lcdReady) {
    while (1) delay(100);
  }

  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("OVER TEMP");
  lcd.setCursor(0, 1);
  lcd.print(msg);

  while (1) {
    delay(100);
  }
}