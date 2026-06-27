#include <Wire.h>
#include <AccelStepper.h>
#include <hd44780.h>
#include <hd44780ioClass/hd44780_I2Cexp.h>

// ============================================================
// ESP32-C6 <-> TMC2208 Pin Mapping
// ============================================================
constexpr uint8_t STEP_PIN = 16;
constexpr uint8_t DIR_PIN  = 17;
constexpr uint8_t EN_PIN   = 18;

// ============================================================
// ESP32-C6 <-> LCD I2C Pin Mapping
// ============================================================
constexpr uint8_t I2C_SDA_PIN = 21;
constexpr uint8_t I2C_SCL_PIN = 22;

// TMC2208: EN = LOW enable, HIGH disable
constexpr uint8_t DRIVER_ENABLE_LEVEL  = LOW;
constexpr uint8_t DRIVER_DISABLE_LEVEL = HIGH;

// ============================================================
// Test Parameters
// ============================================================
constexpr float TEST_SPEED = 50.0;
constexpr uint32_t RUN_TIME_MS   = 5000;
constexpr uint32_t PAUSE_TIME_MS = 1500;

AccelStepper stepper(AccelStepper::DRIVER, STEP_PIN, DIR_PIN);
hd44780_I2Cexp lcd;

enum TestState {
  RUN_LEFT = 0,
  PAUSE_AFTER_LEFT,
  RUN_RIGHT,
  PAUSE_AFTER_RIGHT
};

TestState testState = RUN_LEFT;
unsigned long stateStartMs = 0;

void setDriverEnable(bool en) {
  digitalWrite(EN_PIN, en ? DRIVER_ENABLE_LEVEL : DRIVER_DISABLE_LEVEL);
}

void showStatus(const char* line1, float speedValue, uint32_t elapsedSec) {
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print(line1);

  lcd.setCursor(0, 1);
  lcd.print("S:");
  lcd.print(speedValue, 0);
  lcd.print(" T:");
  lcd.print(elapsedSec);
  lcd.print("s");
}

void startRunLeft() {
  stepper.setSpeed(-TEST_SPEED);
  stateStartMs = millis();
  showStatus("RUN LEFT", TEST_SPEED, 0);
}

void startRunRight() {
  stepper.setSpeed(TEST_SPEED);
  stateStartMs = millis();
  showStatus("RUN RIGHT", TEST_SPEED, 0);
}

void stopMotor(const char* pauseLabel) {
  stepper.setSpeed(0);
  stateStartMs = millis();
  showStatus(pauseLabel, 0, 0);
}

void setup() {
  pinMode(EN_PIN, OUTPUT);
  pinMode(DIR_PIN, OUTPUT);
  pinMode(STEP_PIN, OUTPUT);

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
  lcd.print("TMC2208 TEST");
  lcd.setCursor(0, 1);
  lcd.print("Init...");

  setDriverEnable(true);

  stepper.setMaxSpeed(300);
  stepper.setSpeed(0);

  delay(1000);
  startRunLeft();
}

void loop() {
  unsigned long now = millis();

  if (testState == RUN_LEFT || testState == RUN_RIGHT) {
    stepper.runSpeed();

    uint32_t elapsedSec = (now - stateStartMs) / 1000;
    static uint32_t lastShownSec = 999;

    if (elapsedSec != lastShownSec) {
      lastShownSec = elapsedSec;

      lcd.setCursor(0, 1);
      lcd.print("S:");
      lcd.print(TEST_SPEED, 0);
      lcd.print(" T:");
      lcd.print(elapsedSec);
      lcd.print("s   ");
    }
  }

  switch (testState) {
    case RUN_LEFT:
      if (now - stateStartMs >= RUN_TIME_MS) {
        stopMotor("PAUSE");
        testState = PAUSE_AFTER_LEFT;
      }
      break;

    case PAUSE_AFTER_LEFT:
      if (now - stateStartMs >= PAUSE_TIME_MS) {
        testState = RUN_RIGHT;
        startRunRight();
      }
      break;

    case RUN_RIGHT:
      if (now - stateStartMs >= RUN_TIME_MS) {
        stopMotor("PAUSE");
        testState = PAUSE_AFTER_RIGHT;
      }
      break;

    case PAUSE_AFTER_RIGHT:
      if (now - stateStartMs >= PAUSE_TIME_MS) {
        testState = RUN_LEFT;
        startRunLeft();
      }
      break;
  }
}