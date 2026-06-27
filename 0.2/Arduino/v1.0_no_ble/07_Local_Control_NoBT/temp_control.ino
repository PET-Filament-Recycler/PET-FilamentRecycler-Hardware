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
  double tempC = tempK - 273.15;

  return tempC + TEMP_OFFSET_C;
}

void setHeater(bool on) {
  heaterState = on;
  digitalWrite(HEATER_PIN, on ? HIGH : LOW);
}

void handleHeaterControl(double tempC) {
  if (!machineRunning) {
    setHeater(false);
    return;
  }

  if (isnan(tempC)) {
    setHeater(false);
    return;
  }

  if (tempC <= targetTempC - TEMP_HYSTERESIS_C) {
    setHeater(true);
  } else if (tempC >= targetTempC + TEMP_HYSTERESIS_C) {
    setHeater(false);
  }
}