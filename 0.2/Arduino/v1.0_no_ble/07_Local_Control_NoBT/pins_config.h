constexpr uint8_t BTN_START_END = 0;
constexpr uint8_t BTN_TEMP_PLUS = 1;
constexpr uint8_t BTN_TEMP_MINUS = 23;
constexpr uint8_t POT_PIN = 3;

constexpr uint8_t HEATER_PIN = 6;
constexpr uint8_t FAN_PIN = 7;
constexpr uint8_t THERMISTOR_PIN = 2;
constexpr uint8_t STEP_PIN = 16;
constexpr uint8_t DIR_PIN = 17;
constexpr uint8_t EN_PIN = 18;
constexpr uint8_t I2C_SDA_PIN = 21;
constexpr uint8_t I2C_SCL_PIN = 22;

constexpr uint8_t DRIVER_ENABLE_LEVEL = LOW;
constexpr uint8_t DRIVER_DISABLE_LEVEL = HIGH;

constexpr double BETA = 3950.0;
constexpr double R0 = 10000.0;
constexpr double SERIES_R = 4700.0;
constexpr double T0 = 298.15;
constexpr int ADC_MAX = 4095;

constexpr float STEPPER_SPEED_MIN = 0.0;
constexpr float STEPPER_SPEED_MAX = 1000.0;
constexpr uint32_t POT_UPDATE_MS = 50;

constexpr float MAX_SAFE_TEMP_C = 80.0;
constexpr uint32_t SWITCH_POLL_MS = 20;
constexpr uint32_t BUTTON_DEBOUNCE_MS = 50;
constexpr int AVG_SAMPLES = 8;

constexpr bool SWITCH_ON_IS_LOW = false;

constexpr float TARGET_TEMP_DEFAULT = 30.0;
constexpr float TARGET_TEMP_MIN = 20.0;
constexpr float TARGET_TEMP_MAX = 80.0;
constexpr float TARGET_TEMP_STEP = 1.0;
constexpr float TEMP_HYSTERESIS_C = 1.0;
constexpr float TEMP_OFFSET_C = 10.0;

AccelStepper stepper(AccelStepper::DRIVER, STEP_PIN, DIR_PIN);
hd44780_I2Cexp lcd;

bool machineRunning = false;
bool heaterState = false;
bool fanState = false;

unsigned long lastLcdMs = 0;
unsigned long lastSwitchPollMs = 0;
unsigned long lastTempPlusMs = 0;
unsigned long lastTempMinusMs = 0;
unsigned long lastPotMs = 0;

bool lastTempPlusState = HIGH;
bool lastTempMinusState = HIGH;

float targetTempC = TARGET_TEMP_DEFAULT;
float currentStepperSpeed = 0.0;