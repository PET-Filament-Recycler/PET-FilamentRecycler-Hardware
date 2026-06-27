bool isRunSwitchOn() {
  bool state = digitalRead(BTN_START_END);
  return SWITCH_ON_IS_LOW ? (state == LOW) : (state == HIGH);
}

void applyAppRunCommand(bool wantRunning) {
  appControlActive = true;
  appWantsRunning = wantRunning;

  if (wantRunning && !machineRunning) {
    startAll();
  } else if (!wantRunning && machineRunning) {
    stopAll();
  }
}

void releaseAppRunControl() {
  appControlActive = false;
  bool swOn = isRunSwitchOn();
  lastRunSwitchOn = swOn;

  if (swOn && !machineRunning) {
    startAll();
  } else if (!swOn && machineRunning) {
    stopAll();
  }
}

void handleRunSwitch() {
  unsigned long now = millis();
  if (now - lastSwitchPollMs < SWITCH_POLL_MS) return;
  lastSwitchPollMs = now;

  bool swOn = isRunSwitchOn();

  if (swOn != lastRunSwitchOn) {
    appControlActive = false;
    lastRunSwitchOn = swOn;
  }

  if (appControlActive) {
    if (appWantsRunning && !machineRunning) {
      startAll();
    } else if (!appWantsRunning && machineRunning) {
      stopAll();
    }
    return;
  }

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