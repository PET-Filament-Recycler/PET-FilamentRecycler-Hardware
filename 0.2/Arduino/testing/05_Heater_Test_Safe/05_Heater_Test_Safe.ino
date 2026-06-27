#include <Wire.h>
#include <hd44780.h>
#include <hd44780ioClass/hd44780_I2Cexp.h>
#include <math.h>

// ============================================================
// Pin Mapping
// ============================================================
constexpr uint8_t HEATER_PIN     = 6;   // MOS
constexpr uint8_t THERMISTOR_PIN = 2;
constexpr uint8_t I2C_SDA_PIN    = 21;
constexpr uint8_t I2C_SCL_PIN    = 22;

// ============================================================
// Thermistor Parameters
// 依你目前測到係 10k thermistor，先用 Beta 3950 + 4.7k
// ============================================================
constexpr double BETA     = 3950.0;
constexpr double R0       = 10000.0;
constexpr double SERIES_R = 4700.0;
constexpr double T0       = 298.15;
constexpr int ADC_MAX     = 4095;

// ============================================================
// Heater Test Timing
// ============================================================
constexpr uint32_t HEATER_ON_MS  = 2000;
constexpr uint32_t HEATER_OFF_MS = 4000;
constexpr float MAX_SAFE_TEMP_C  = 80.0;

// ============================================================
// ADC smoothing
// ============================================================
constexpr int AVG_SAMPLES = 8;

// ============================================================
// LCD
// ============================================================
hd44780_I2Cexp lcd;

// ============================================================
// State
// ============================================================
bool heaterOn = false;
unsigned long stateStartMs = 0;
unsigned long lastLcdMs = 0;
constexpr uint32_t LCD_UPDATE_MS = 250;

// ============================================================
// Helpers
// ============================================================
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
  double tempK = 1.0 / (1.0 / T0 + lnR / BETA);
  return tempK - 273.15;
}

void setHeater(bool on) {
  heaterOn = on;
  digitalWrite(HEATER_PIN, on ? HIGH : LOW);
  stateStartMs = millis();
}

void showStatus(int raw, double tempC) {
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print(heaterOn ? "HEATER ON " : "HEATER OFF");

  lcd.setCursor(0, 1);
  lcd.print("T:");
  if (isnan(tempC)) {
    lcd.print("ERR");
  } else {
    lcd.print(tempC, 1);
    lcd.print((char)223);
    lcd.print("C");
  }
}

void emergencyStop(const char* msg) {
  digitalWrite(HEATER_PIN, LOW);
  heaterOn = false;

  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("EMERGENCY STOP");
  lcd.setCursor(0, 1);
  lcd.print(msg);

  while (1) {
    delay(100);
  }
}

void setup() {
  pinMode(HEATER_PIN, OUTPUT);
  digitalWrite(HEATER_PIN, LOW);

  analogReadResolution(12);

  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);

  int status = lcd.begin(16, 2);
  if (status) {
    while (1) delay(100);
  }

  lcd.backlight();
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("HEATER TEST");
  lcd.setCursor(0, 1);
  lcd.print("Init...");
  delay(1000);

  setHeater(false);
}

void loop() {
  int raw = readAveragedADC();
  double tempC = adcToTempC(raw);

  if (isnan(tempC)) {
    emergencyStop("Sensor Error");
  }

  if (tempC >= MAX_SAFE_TEMP_C) {
    emergencyStop("Over Temp");
  }

  unsigned long now = millis();

  if (heaterOn) {
    if (now - stateStartMs >= HEATER_ON_MS) {
      setHeater(false);
    }
  } else {
    if (now - stateStartMs >= HEATER_OFF_MS) {
      setHeater(true);
    }
  }

  if (now - lastLcdMs >= LCD_UPDATE_MS) {
    lastLcdMs = now;
    showStatus(raw, tempC);
  }
}