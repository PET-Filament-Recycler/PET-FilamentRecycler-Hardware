void clearRow(uint8_t row) {
  lcd.setCursor(0, row);
  lcd.print("                ");
}

void updateLCD(double tempC) {
  if (!lcdReady) return;

  unsigned long now = millis();
  if (now - lastLcdMs < 500) return;
  lastLcdMs = now;

  clearRow(0);
  clearRow(1);

  lcd.setCursor(0, 0);
  lcd.print(machineRunning ? "RUN " : "STOP");
  lcd.print(bleClientConnected ? "BT " : "   ");
  lcd.print("S:");
  lcd.print(targetTempC, 0);
  lcd.print(" ");

  lcd.setCursor(0, 1);
  lcd.print("T:");
  if (isnan(tempC)) {
    lcd.print("ERR");
  } else {
    lcd.print(tempC, 1);
    lcd.print((char)223);
    lcd.print("C ");
  }

  lcd.print("H:");
  lcd.print((heaterPwmDuty * 100) / PID_MAX_OUTPUT);
  lcd.print("%");
}