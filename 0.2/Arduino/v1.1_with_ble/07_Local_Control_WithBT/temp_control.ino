void setupHeaterPwm() {
#if ESP_ARDUINO_VERSION >= ESP_ARDUINO_VERSION_VAL(3, 0, 0)
  ledcAttach(HEATER_PIN, HEATER_PWM_FREQ, HEATER_PWM_BITS);
#else
  ledcSetup(HEATER_PWM_CHANNEL, HEATER_PWM_FREQ, HEATER_PWM_BITS);
  ledcAttachPin(HEATER_PIN, HEATER_PWM_CHANNEL);
#endif
  setHeaterPwm(0);
}

void writeHeaterPwm(uint8_t duty) {
#if ESP_ARDUINO_VERSION >= ESP_ARDUINO_VERSION_VAL(3, 0, 0)
  ledcWrite(HEATER_PIN, duty);
#else
  ledcWrite(HEATER_PWM_CHANNEL, duty);
#endif
}

void setHeaterPwm(uint8_t duty) {
  if (duty > PID_MAX_OUTPUT) {
    duty = PID_MAX_OUTPUT;
  }
  heaterPwmDuty = duty;
  heaterState = (duty > 0);
  writeHeaterPwm(duty);
}

int rampHeaterPwm(int targetDuty) {
  if (targetDuty < 0) targetDuty = 0;
  if (targetDuty > PID_MAX_OUTPUT) targetDuty = PID_MAX_OUTPUT;

  int delta = targetDuty - (int)heaterPwmDuty;
  if (delta > PID_PWM_MAX_STEP) {
    targetDuty = heaterPwmDuty + PID_PWM_MAX_STEP;
  } else if (delta < -PID_PWM_MAX_STEP) {
    targetDuty = heaterPwmDuty - PID_PWM_MAX_STEP;
  }
  return targetDuty;
}

void resetPidState() {
  pidError = 0.0f;
  pidPreviousError = 0.0f;
  pidIntegral = 0.0f;
  pidOutput = 0.0f;
  setHeaterPwm(0);
}

void setHeater(bool on) {
  if (!on) {
    resetPidState();
  }
}

int readAveragedADC() {
  long sum = 0;
  for (int i = 0; i < AVG_SAMPLES; i++) {
    sum += analogRead(THERMISTOR_PIN);
    delay(2);
  }
  return sum / AVG_SAMPLES;
}

double adcToTempC(int adc) {
  if (adc <= 0 || adc >= ADC_MAX) return NAN;

  double R = SERIES_R / ((double)ADC_MAX / (double)adc - 1.0);
  double lnR = log(R / R0);
  double tempK = 1.0 / (1.0 / T0 + lnR / BETA);
  return tempK - 273.15;
}

void updatePidHeater(double tempC) {
  if (!machineRunning) {
    setHeaterPwm(0);
    return;
  }

  unsigned long now = millis();
  if (now - lastPidMs < PID_UPDATE_MS) return;

  float dt = (now - lastPidMs) / 1000.0f;
  lastPidMs = now;
  if (dt <= 0.0f) {
    dt = PID_UPDATE_MS / 1000.0f;
  }

  if (isnan(tempC)) {
    setHeaterPwm(0);
    return;
  }

  if (tempC > MAX_SAFE_TEMP_C) {
    setHeaterPwm(0);
    return;
  }

  float controlTarget = targetTempC + PID_CALIBRATION_OFFSET_C;
  pidError = controlTarget - (float)tempC;

  float pidP = PID_KP * pidError;
  if (pidError > 0.0f) {
    pidIntegral += PID_KI * pidError * dt;
  } else {
    pidIntegral += PID_KI * pidError * dt * 0.5f;
  }
  pidIntegral = constrain(pidIntegral, 0.0f, (float)PID_MAX_OUTPUT);
  float pidD = PID_KD * (pidError - pidPreviousError) / dt;

  int maxDuty = PID_MAX_OUTPUT;
  if (tempC >= controlTarget) {
    maxDuty = 0;
    pidIntegral = 0.0f;
  } else if (tempC >= controlTarget - PID_APPROACH_BAND_C) {
    float ratio = (controlTarget - (float)tempC) / PID_APPROACH_BAND_C;
    int approachCap = (int)(PID_APPROACH_MAX_PWM * ratio);
    if (approachCap < maxDuty) {
      maxDuty = approachCap;
    }
  }

  pidOutput = constrain(pidP + pidIntegral + pidD, 0.0f, (float)maxDuty);
  setHeaterPwm((uint8_t)rampHeaterPwm((int)pidOutput));

  pidPreviousError = pidError;
}