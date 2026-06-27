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

constexpr float MAX_SAFE_TEMP_C = 270.0;
constexpr uint32_t SWITCH_POLL_MS = 20;
constexpr uint32_t BUTTON_DEBOUNCE_MS = 50;
constexpr int AVG_SAMPLES = 8;

constexpr bool SWITCH_ON_IS_LOW = false;

constexpr float TARGET_TEMP_DEFAULT = 255.0;
constexpr float TARGET_TEMP_MIN = 0.0;
constexpr float TARGET_TEMP_MAX = 270.0;
constexpr float TARGET_TEMP_STEP = 1.0;

// PID + PWM (tuned for ~255C normal use, max 270C; based on 0.1 Firmware_1.1)
constexpr float PID_CALIBRATION_OFFSET_C = 6.0;
constexpr float PID_APPROACH_BAND_C = 12.0;
constexpr int PID_KP = 90;
constexpr int PID_KI = 30;
constexpr int PID_KD = 80;
constexpr int PID_MAX_OUTPUT = 255;
constexpr int PID_APPROACH_MAX_PWM = 180;
constexpr int PID_PWM_MAX_STEP = 30;
constexpr uint32_t PID_UPDATE_MS = 250;

constexpr uint8_t HEATER_PWM_CHANNEL = 0;
constexpr uint32_t HEATER_PWM_FREQ = 7812;
constexpr uint8_t HEATER_PWM_BITS = 8;

AccelStepper stepper(AccelStepper::DRIVER, STEP_PIN, DIR_PIN);
hd44780_I2Cexp lcd;

bool machineRunning = false;
bool heaterState = false;
bool fanState = false;
uint8_t heaterPwmDuty = 0;

float pidError = 0.0f;
float pidPreviousError = 0.0f;
float pidIntegral = 0.0f;
float pidOutput = 0.0f;
unsigned long lastPidMs = 0;

unsigned long lastLcdMs = 0;
unsigned long lastSwitchPollMs = 0;
unsigned long lastTempPlusMs = 0;
unsigned long lastTempMinusMs = 0;
unsigned long lastPotMs = 0;

bool lastTempPlusState = HIGH;
bool lastTempMinusState = HIGH;

float targetTempC = TARGET_TEMP_DEFAULT;
float currentStepperSpeed = 0.0;
float lastMeasuredTempC = NAN;

bool bleSpeedOverride = false;
bool lcdReady = false;
extern bool bleClientConnected;

bool appControlActive = false;
bool appWantsRunning = false;
bool lastRunSwitchOn = false;