#include <Wire.h>
#include <AccelStepper.h>
#include <hd44780.h>
#include <hd44780ioClass/hd44780_I2Cexp.h>

// ============================================================
// Pin Mapping
// ============================================================
constexpr uint8_t POT_PIN     = 3;
constexpr uint8_t STEP_PIN    = 16;
constexpr uint8_t DIR_PIN     = 17;
constexpr uint8_t EN_PIN      = 18;
constexpr uint8_t I2C_SDA_PIN = 21;
constexpr uint8_t I2C_SCL_PIN = 22;

// ============================================================
// TMC2208
// EN = LOW enable, HIGH disable
// ============================================================
constexpr uint8_t DRIVER_ENABLE_LEVEL  = LOW;
constexpr uint8_t DRIVER_DISABLE_LEVEL = HIGH;

// ============================================================
// Pot Calibration
// 你目前量到：
// min 附近會 0~2
// max 大約 3370~3414
// ============================================================
constexpr int POT_RAW_MIN = 2;
constexpr int POT_RAW_MAX = 3414;

// 小於這個 speed 就當 0，避免極低速抖動
constexpr float MIN_RUN_SPEED = 30.0f;
constexpr float MAX_RUN_SPEED = 400.0f;

// ADC 去抖
constexpr int POT_HYSTERESIS = 4;
constexpr int POT_AVG_SAMPLES = 8;

// 更新周期
constexpr uint32_t LCD_UPDATE_MS = 200;
constexpr uint32_t SERIAL_UPDATE_MS = 300;

// ============================================================
// Objects
// ============================================================
AccelStepper stepper(AccelStepper::DRIVER, STEP_PIN, DIR_PIN);
hd44780_I2Cexp lcd;

// ============================================================
// State
// ============================================================
int stableRaw = 0;
float currentPercent = 0.0f;
float currentSpeed = 0.0f;

unsigned long lastLcdUpdateMs = 0;
unsigned long lastSerialUpdateMs = 0;

// ============================================================
// Helper
// ============================================================
void setDriverEnable(bool en) {
  digitalWrite(EN_PIN, en ? DRIVER_ENABLE_LEVEL : DRIVER_DISABLE_LEVEL);
}

int readAveragedPotRaw() {
  long sum = 0;
  for (int i = 0; i < POT_AVG_SAMPLES; i++) {
    sum += analogRead(POT_PIN);
  }
  return sum / POT_AVG_SAMPLES;
}

int readStablePotRaw() {
  int newRaw = readAveragedPotRaw();

  if (abs(newRaw - stableRaw) > POT_HYSTERESIS) {
    stableRaw = newRaw;
  }

  return stableRaw;
}

float mapRawToPercent(int raw) {
  int clamped = constrain(raw, POT_RAW_MIN, POT_RAW_MAX);
  return (clamped - POT_RAW_MIN) * 100.0f / (POT_RAW_MAX - POT_RAW_MIN);
}

float mapPercentToSpeed(float percent) {
  if (percent <= 0.5f) {
    return 0.0f;
  }

  float speed = MIN_RUN_SPEED + (percent / 100.0f) * (MAX_RUN_SPEED - MIN_RUN_SPEED);

  if (speed > MAX_RUN_SPEED) speed = MAX_RUN_SPEED;
  if (speed < MIN_RUN_SPEED) speed = MIN_RUN_SPEED;

  return speed;
}

void updateLCD() {
  unsigned long now = millis();
  if (now - lastLcdUpdateMs < LCD_UPDATE_MS) return;
  lastLcdUpdateMs = now;

  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("RAW:");
  lcd.print(stableRaw);

  lcd.setCursor(0, 1);
  lcd.print("P:");
  lcd.print(currentPercent, 0);
  lcd.print("% S:");
  lcd.print(currentSpeed, 0);
}

void updateSerial() {
  unsigned long now = millis();
  if (now - lastSerialUpdateMs < SERIAL_UPDATE_MS) return;
  lastSerialUpdateMs = now;

  Serial.print("RAW=");
  Serial.print(stableRaw);
  Serial.print(" , Percent=");
  Serial.print(currentPercent, 1);
  Serial.print("% , Speed=");
  Serial.println(currentSpeed, 1);
}

void setup() {
  Serial.begin(115200);
  delay(300);

  analogReadResolution(12);

  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);

  int status = lcd.begin(16, 2);
  if (status) {
    while (1) {
      delay(100);
    }
  }

  lcd.backlight();
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("POT+STEPPER");
  lcd.setCursor(0, 1);
  lcd.print("Init...");

  pinMode(EN_PIN, OUTPUT);
  pinMode(DIR_PIN, OUTPUT);
  pinMode(STEP_PIN, OUTPUT);

  setDriverEnable(true);

  stepper.setMaxSpeed(MAX_RUN_SPEED);
  stepper.setSpeed(0);

  stableRaw = readAveragedPotRaw();

  delay(1000);
}

void loop() {
  stableRaw = readStablePotRaw();
  currentPercent = mapRawToPercent(stableRaw);
  currentSpeed = mapPercentToSpeed(currentPercent);

  if (currentSpeed <= 0.0f) {
    stepper.setSpeed(0);
  } else {
    stepper.setSpeed(currentSpeed);   // 固定一個方向
    stepper.runSpeed();
  }

  updateLCD();
  updateSerial();
}