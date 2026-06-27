#include <Wire.h>
#include <hd44780.h>
#include <hd44780ioClass/hd44780_I2Cexp.h>
#include <math.h>

// ============================================================
// Pin Mapping
// ============================================================
constexpr uint8_t THERMISTOR_PIN = 2;
constexpr uint8_t I2C_SDA_PIN    = 21;
constexpr uint8_t I2C_SCL_PIN    = 22;

// ============================================================
// Thermistor Parameters
// 改成 10k NTC
// ============================================================
constexpr double BETA     = 3950.0;
constexpr double R0       = 10000.0;    // 10k @ 25C
constexpr double SERIES_R = 4700.0;     // 先按你提供的 4.7k
constexpr double T0       = 298.15;     // 25C = 298.15K

constexpr int ADC_MAX = 4095;
constexpr int AVG_SAMPLES = 8;
constexpr uint32_t UPDATE_INTERVAL_MS = 300;

hd44780_I2Cexp lcd;
unsigned long lastUpdateMs = 0;

int readAveragedADC() {
  long sum = 0;
  for (int i = 0; i < AVG_SAMPLES; i++) {
    sum += analogRead(THERMISTOR_PIN);
  }
  return sum / AVG_SAMPLES;
}

double readTemperatureCFromADC(int adc) {
  if (adc <= 0 || adc >= ADC_MAX) {
    return NAN;
  }

  // 先用呢個方向
  double R = SERIES_R / ((double)ADC_MAX / (double)adc - 1.0);

  double lnR = log(R / R0);
  double tempK = 1.0 / (1.0 / T0 + lnR / BETA);
  return tempK - 273.15;
}

void setup() {
  Serial.begin(115200);
  delay(300);

  analogReadResolution(12);
  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);

  int status = lcd.begin(16, 2);
  if (status) {
    while (1) delay(100);
  }

  lcd.backlight();
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("THERM 10K TEST");
  lcd.setCursor(0, 1);
  lcd.print("Init...");
  delay(1000);
}

void loop() {
  unsigned long now = millis();
  if (now - lastUpdateMs < UPDATE_INTERVAL_MS) return;
  lastUpdateMs = now;

  int raw = readAveragedADC();
  double tempC = readTemperatureCFromADC(raw);

  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("RAW:");
  lcd.print(raw);

  lcd.setCursor(0, 1);
  lcd.print("T:");
  if (isnan(tempC)) {
    lcd.print("ERR");
  } else {
    lcd.print(tempC, 1);
    lcd.print((char)223);
    lcd.print("C");
  }

  Serial.print("RAW=");
  Serial.print(raw);
  Serial.print(" , TempC=");
  Serial.println(tempC, 2);
}