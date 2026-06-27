#include <Wire.h>
#include <hd44780.h>
#include <hd44780ioClass/hd44780_I2Cexp.h>

// ============================================================
// Pin Mapping
// ============================================================
constexpr uint8_t POT_PIN     = 3;
constexpr uint8_t I2C_SDA_PIN = 21;
constexpr uint8_t I2C_SCL_PIN = 22;

// ============================================================
// LCD
// ============================================================
hd44780_I2Cexp lcd;

// ============================================================
// Timing
// ============================================================
unsigned long lastUpdateMs = 0;
constexpr uint32_t UPDATE_INTERVAL_MS = 200;

void setup() {
  Serial.begin(115200);
  delay(300);

  analogReadResolution(12);   // ESP32-C6 ADC: 0 ~ 4095

  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);

  int status = lcd.begin(16, 2);
  if (status) {
    Serial.print("LCD init failed, status=");
    Serial.println(status);
    while (1) {
      delay(100);
    }
  }

  lcd.backlight();
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("POT TEST");
  lcd.setCursor(0, 1);
  lcd.print("Init...");

  Serial.println("=================================");
  Serial.println("ESP32-C6 Potentiometer Test");
  Serial.println("POT PIN = GPIO3");
  Serial.println("ADC Resolution = 12-bit (0~4095)");
  Serial.println("=================================");
}

void loop() {
  unsigned long now = millis();
  if (now - lastUpdateMs < UPDATE_INTERVAL_MS) {
    return;
  }
  lastUpdateMs = now;

  int raw = analogRead(POT_PIN);

  float percent = (raw / 4095.0f) * 100.0f;

  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("RAW:");
  lcd.print(raw);

  lcd.setCursor(0, 1);
  lcd.print("P:");
  lcd.print(percent, 1);
  lcd.print("%   ");

  Serial.print("RAW = ");
  Serial.print(raw);
  Serial.print(" , Percent = ");
  Serial.print(percent, 1);
  Serial.println("%");
}