#include <Wire.h>
#include <hd44780.h>
#include <hd44780ioClass/hd44780_I2Cexp.h>

constexpr uint8_t I2C_SDA_PIN = 21;
constexpr uint8_t I2C_SCL_PIN = 22;

hd44780_I2Cexp lcd;

void scanI2C() {
  Serial.println("I2C scan start...");
  byte count = 0;

  for (byte addr = 1; addr < 127; addr++) {
    Wire.beginTransmission(addr);
    if (Wire.endTransmission() == 0) {
      Serial.print("Found I2C device at 0x");
      if (addr < 16) Serial.print("0");
      Serial.println(addr, HEX);
      count++;
    }
  }

  if (count == 0) {
    Serial.println("No I2C devices found.");
  } else {
    Serial.println("I2C scan done.");
  }
}

void setup() {
  Serial.begin(115200);
  delay(300);

  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
  scanI2C();

  int status = lcd.begin(16, 2);
  if (status) {
    Serial.print("LCD init failed, status=");
    Serial.println(status);
    return;
  }

  lcd.backlight();
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("ESP32-C6 LCD OK");
  lcd.setCursor(0, 1);
  lcd.print("SDA21 SCL22");

  Serial.println("LCD test done.");
}

void loop() {
  static unsigned long lastMs = 0;
  static uint32_t sec = 0;

  if (millis() - lastMs >= 1000) {
    lastMs = millis();
    sec++;

    lcd.setCursor(0, 1);
    lcd.print("Count: ");
    lcd.print(sec);
    lcd.print("    ");

    Serial.print("LCD running, sec=");
    Serial.println(sec);
  }
}