#include <Wire.h>
#include <hd44780.h>
#include <hd44780ioClass/hd44780_I2Cexp.h>
#include <AccelStepper.h>
#include <math.h>

// ============================================================
// Pin Mapping
// ============================================================
constexpr uint8_t BTN_START_END    = 0;   // latching start/end switch
constexpr uint8_t BTN_TEMP_PLUS    = 1;   // momentary Temp+ button
constexpr uint8_t BTN_TEMP_MINUS   = 23;  // momentary Temp- button
constexpr uint8_t HEATER_PIN       = 6;
constexpr uint8_t FAN_PIN          = 7;
constexpr uint8_t THERMISTOR_PIN   = 2;
constexpr uint8_t STEP_PIN         = 16;
constexpr uint8_t DIR_PIN          = 17;
constexpr uint8_t EN_PIN           = 18;
constexpr uint8_t I2C_SDA_PIN      = 21;
constexpr uint8_t I2C_SCL_PIN      = 22;

// ============================================================
// TMC2208
// EN = LOW enable, HIGH disable
// ============================================================
constexpr uint8_t DRIVER_ENABLE_LEVEL  = LOW;
constexpr uint8_t DRIVER_DISABLE_LEVEL = HIGH;

// ============================================================
// Thermistor Parameters
// 10k NTC + Beta3950 + 4.7k
// ============================================================
constexpr double BETA     = 3950.0;
constexpr double R0       = 10000.0;
constexpr double SERIES_R = 4700.0;
constexpr double T0       = 298.15;
constexpr int ADC_MAX     = 4095;

// ============================================================
// Config
// ============================================================
constexpr float STEPPER_SPEED            = 120.0;
constexpr float MAX_SAFE_TEMP_C          = 80.0;
constexpr uint32_t SWITCH_POLL_MS        = 20;
constexpr uint32_t BUTTON_DEBOUNCE_MS    = 50;
constexpr int AVG_SAMPLES                = 8;

constexpr bool SWITCH_ON_IS_LOW = false;   // 突出 = OFF，鎖住 = ON

constexpr float TARGET_TEMP_DEFAULT = 30.0;
constexpr float TARGET_TEMP_MIN     = 20.0;
constexpr float TARGET_TEMP_MAX     = 80.0;
constexpr float TARGET_TEMP_STEP    = 1.0;
constexpr float TEMP_HYSTERESIS_C   = 1.0;

// ============================================================
// Objects
// ============================================================
AccelStepper stepper(AccelStepper::DRIVER, STEP_PIN, DIR_PIN);
hd44780_I2Cexp lcd;

// ============================================================
// State
// ============================================================
bool machineRunning = false;
bool heaterState = false;
bool fanState = false;

unsigned long lastLcdMs = 0;
unsigned long lastSwitchPollMs = 0;
unsigned long lastTempPlusMs = 0;
unsigned long lastTempMinusMs = 0;

bool lastTempPlusState = HIGH;
bool lastTempMinusState = HIGH;

float targetTempC = TARGET_TEMP_DEFAULT;

// ============================================================
// Helpers
// ============================================================
void setDriverEnable(bool en) {
  digitalWrite(EN_PIN, en ? DRIVER_ENABLE_LEVEL : DRIVER_DISABLE_LEVEL);
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

void setHeater(bool on) {
  heaterState = on;
  digitalWrite(HEATER_PIN, on ? HIGH : LOW);
}

void setFan(bool on) {
  fanState = on;
  digitalWrite(FAN_PIN, on ? HIGH : LOW);
}

void stopAll() {
  machineRunning = false;
  setHeater(false);
  setFan(false);
  stepper.setSpeed(0);
  setDriverEnable(false);
}

void startAll() {
  machineRunning = true;
  setDriverEnable(true);
  stepper.setSpeed(STEPPER_SPEED);
  setFan(true);
}

void emergencyStop(const char* msg) {
  digitalWrite(HEATER_PIN, LOW);
  digitalWrite(FAN_PIN, LOW);
  stepper.setSpeed(0);
  setDriverEnable(false);
  machineRunning = false;
  heaterState = false;
  fanState = false;

  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("EMERGENCY STOP");
  lcd.setCursor(0, 1);
  lcd.print(msg);

  while (1) {
    delay(100);
  }
}

bool isRunSwitchOn() {
  bool state = digitalRead(BTN_START_END);
  return SWITCH_ON_IS_LOW ? (state == LOW) : (state == HIGH);
}

void handleRunSwitch() {
  unsigned long now = millis();
  if (now - lastSwitchPollMs < SWITCH_POLL_MS) return;
  lastSwitchPollMs = now;

  bool swOn = isRunSwitchOn();

  if (swOn && !machineRunning) {
    startAll();
  } else if (!swOn && machineRunning) {
    stopAll();
  }
}

void handleTempPlusButton() {
  bool currentState = digitalRead(BTN_TEMP_PLUS);
  unsigned long now = millis();

  if (lastTempPlusState == HIGH && currentState == LOW) {
    if (now - lastTempPlusMs > BUTTON_DEBOUNCE_MS) {
      lastTempPlusMs = now;
      targetTempC += TARGET_TEMP_STEP;
      if (targetTempC > TARGET_TEMP_MAX) {
        targetTempC = TARGET_TEMP_MAX;
      }
    }
  }

  lastTempPlusState = currentState;
}

void handleTempMinusButton() {
  bool currentState = digitalRead(BTN_TEMP_MINUS);
  unsigned long now = millis();

  if (lastTempMinusState == HIGH && currentState == LOW) {
    if (now - lastTempMinusMs > BUTTON_DEBOUNCE_MS) {
      lastTempMinusMs = now;
      targetTempC -= TARGET_TEMP_STEP;
      if (targetTempC < TARGET_TEMP_MIN) {
        targetTempC = TARGET_TEMP_MIN;
      }
    }
  }

  lastTempMinusState = currentState;
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

void handleFanControl() {
  if (!machineRunning) {
    setFan(false);
    return;
  }

  setFan(true);
}

void clearRow(uint8_t row) {
  lcd.setCursor(0, row);
  lcd.print("                ");
}

void updateLCD(double tempC) {
  unsigned long now = millis();
  if (now - lastLcdMs < 250) return;
  lastLcdMs = now;

  clearRow(0);
  clearRow(1);

  lcd.setCursor(0, 0);
  lcd.print(machineRunning ? "RUN " : "STOP");
  lcd.print("S:");
  lcd.print(targetTempC, 0);
  lcd.print("C ");
  lcd.print(fanState ? "F:ON" : "F:OFF");

  lcd.setCursor(0, 1);
  lcd.print("T:");
  if (isnan(tempC)) {
    lcd.print("ERR");
  } else {
    lcd.print(tempC, 1);
    lcd.print((char)223);
    lcd.print("C ");
  }

  lcd.print(heaterState ? "H:ON" : "H:OFF");
}

void setup() {
  pinMode(BTN_START_END, INPUT_PULLUP);
  pinMode(BTN_TEMP_PLUS, INPUT_PULLUP);
  pinMode(BTN_TEMP_MINUS, INPUT_PULLUP);
  pinMode(HEATER_PIN, OUTPUT);
  pinMode(FAN_PIN, OUTPUT);
  pinMode(EN_PIN, OUTPUT);
  pinMode(DIR_PIN, OUTPUT);
  pinMode(STEP_PIN, OUTPUT);

  digitalWrite(HEATER_PIN, LOW);
  digitalWrite(FAN_PIN, LOW);
  setDriverEnable(false);

  analogReadResolution(12);
  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);

  int status = lcd.begin(16, 2);
  if (status) {
    while (1) delay(100);
  }

  lcd.backlight();
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("READY");
  lcd.setCursor(0, 1);
  lcd.print("SET:");
  lcd.print(targetTempC, 0);
  lcd.print("C");
  delay(1200);

  stepper.setMaxSpeed(300);
  stepper.setSpeed(0);

  if (isRunSwitchOn()) {
    startAll();
  } else {
    stopAll();
  }

  lcd.clear();
}

void loop() {
  handleRunSwitch();
  handleTempPlusButton();
  handleTempMinusButton();

  int raw = readAveragedADC();
  double tempC = adcToTempC(raw);

  if (isnan(tempC)) {
    emergencyStop("Sensor Error");
  }

  if (tempC >= MAX_SAFE_TEMP_C) {
    emergencyStop("Over Temp");
  }

  handleHeaterControl(tempC);
  handleFanControl();

  if (machineRunning) {
    stepper.runSpeed();
  }

  updateLCD(tempC);
}