// Simple bang-bang heater control (no PWM):
//   ON  when temp <= target - hysteresis
//   OFF when temp >= target
//   hold previous state in between
//
// If the app/LCD shows HEAT:0% / H:0 but temperature still rises,
// the MOSFET/SSR is not following GPIO6 (hardware fault).

void setupHeaterPwm() {
  pinMode(HEATER_PIN, OUTPUT);
  forceHeaterOff();
}

// Drive the heater MOSFET/SSR gate. HEATER_ACTIVE_HIGH=true means HIGH=ON.
void writeHeaterGate(bool on) {
  bool levelHigh = HEATER_ACTIVE_HIGH ? on : !on;
  pinMode(HEATER_PIN, OUTPUT);
  digitalWrite(HEATER_PIN, levelHigh ? HIGH : LOW);
}

void forceHeaterOff() {
  heaterPwmDuty = 0;
  heaterState = false;
  heaterHoldOff = true;
  writeHeaterGate(false);
  logHeatPercent(0);
}

void forceHeaterOn() {
  heaterPwmDuty = PID_MAX_OUTPUT;
  heaterState = true;
  heaterHoldOff = false;
  writeHeaterGate(true);
  logHeatPercent(PID_MAX_OUTPUT);
}

// Kept for call sites that still pass a PWM duty. Any non-zero value = full ON.
void setHeaterPwm(uint8_t duty) {
  if (duty == 0) {
    forceHeaterOff();
  } else {
    forceHeaterOn();
  }
}

void resetPidState() {
  pidError = 0.0f;
  pidPreviousError = 0.0f;
  pidIntegral = 0.0f;
  pidOutput = 0.0f;
  forceHeaterOff();
}

void setHeater(bool on) {
  if (on) {
    forceHeaterOn();
  } else {
    resetPidState();
  }
}

int readAveragedADC() {
  long sum = 0;
  for (int i = 0; i < AVG_SAMPLES; i++) {
    sum += analogRead(THERMISTOR_PIN);
  }
  return sum / AVG_SAMPLES;
}

double adcToTempC(int adc) {
  if (adc <= 0 || adc >= ADC_MAX) return NAN;

  double R = SERIES_R / ((double)ADC_MAX / (double)adc - 1.0);
  double lnR = log(R / R0);
  double tempK = 1.0 / (1.0 / THERMISTOR_T0_K + lnR / BETA);
  return tempK - 273.15;
}

void updatePidHeater(double tempC) {
  // Always off when machine is stopped (app Stop or hardware run switch).
  if (!machineRunning) {
    forceHeaterOff();
    return;
  }

  unsigned long now = millis();
  if (now - lastPidMs < PID_UPDATE_MS) return;
  lastPidMs = now;

  if (isnan(tempC) || tempC > MAX_SAFE_TEMP_C) {
    forceHeaterOff();
    return;
  }

  // Bang-bang with hysteresis around the app/hardware setpoint.
  if (tempC >= targetTempC) {
    // Hit / above set temperature -> heater OFF, wait to cool.
    if (heaterState) {
      notifyBleLog("Heater OFF at setpoint");
    }
    forceHeaterOff();
    return;
  }

  if (tempC <= (targetTempC - HEATER_HYSTERESIS_C)) {
    // Cooled below setpoint - hysteresis -> heater ON again.
    if (!heaterState) {
      notifyBleLog("Heater ON below setpoint");
    }
    forceHeaterOn();
    return;
  }

  // Between (target - hysteresis) and target: keep current on/off state.
}
