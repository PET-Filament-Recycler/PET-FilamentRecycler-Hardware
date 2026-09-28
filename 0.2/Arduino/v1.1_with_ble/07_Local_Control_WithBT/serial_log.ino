unsigned long lastSerialStatusMs = 0;
int lastLoggedHeatPercent = -1;

void logEvent(const char* msg) {
  if (!serialLoggingAllowed()) return;
  Serial.print("EVENT -> ");
  Serial.println(msg);
}

void logSetTarget(float tempC) {
  if (!serialLoggingAllowed()) return;
  Serial.print("SET:");
  Serial.println((int)round(tempC));
}

void logHeatPercent(uint8_t duty) {
  int percent = (duty * 100) / PID_MAX_OUTPUT;
  if (lastLoggedHeatPercent < 0) {
    lastLoggedHeatPercent = percent;
    return;
  }
  if (percent == lastLoggedHeatPercent) return;
  if (percent != 0 && lastLoggedHeatPercent != 0 &&
      abs(percent - lastLoggedHeatPercent) < 5) {
    return;
  }

  lastLoggedHeatPercent = percent;
  if (!serialLoggingAllowed()) return;
  Serial.print("HEAT:");
  Serial.println(percent);
}

void logBtState(bool connected) {
  if (!serialLoggingAllowed()) return;
  Serial.print("BT:");
  Serial.println(connected ? "ON" : "OFF");
}

void logMachineStatus(float measuredTempC) {
  unsigned long now = millis();
  if (now - lastSerialStatusMs < SERIAL_STATUS_LOG_INTERVAL_MS) return;
  lastSerialStatusMs = now;
  if (!serialLoggingAllowed()) return;

  Serial.print("STATUS -> ");
  Serial.println(buildBleStatusString(measuredTempC));
}