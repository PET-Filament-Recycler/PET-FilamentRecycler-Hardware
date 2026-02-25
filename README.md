# PET Filament Recycler

A DIY project to recycle PET bottles into 3D printing filament, integrated with an Android app for Bluetooth-based control and monitoring.

---

## Overview

This project builds a machine that cuts PET plastic bottles into strips, then heats and extrudes them into usable 1.75 mm 3D printer filament. An Arduino-based controller manages the heating element and stepper motor via PID control, while an Android app communicates with the machine over Bluetooth to set parameters, start/stop operation, and view command logs.

---

## Features

- **PID temperature control** for the heating block (target: ~200 °C)
- **Stepper motor control** for filament extrusion speed
- **Bluetooth connectivity** (HC-05 / Serial1) for wireless control
- **Android app** with:
  - Bluetooth device discovery and connection
  - Real-time temperature and speed display
  - Start / Stop / Save settings commands
  - Bluetooth command log viewer (SQLite)
- **EEPROM persistence** – temperature and speed settings survive power cycles
- **Custom PCB** design included (GERBER files provided)
- **3D-printable parts** for the mechanical assembly (15 STL files)

---

## Repository Structure

```
PET-Filament-Recycler/
├── 0.1/
│   ├── arduino/
│   │   ├── Firmware_1/          # Original Arduino Nano firmware
│   │   ├── Firmware_1.1/        # Updated Arduino UNO R4 firmware (Bluetooth, EEPROM)
│   │   └── tests/               # Individual component test sketches
│   │       ├── Bluetooth_Test/
│   │       ├── Button_Test/
│   │       ├── LCD_Test/
│   │       ├── PID_Test/
│   │       ├── PWM_Test/
│   │       ├── Stepper_Test/
│   │       └── Thermistor_Test/
│   └── docs/
│       ├── 3D Files STL/        # 3D-printable part files
│       ├── AppLayout/           # UI design mockups (PNG)
│       ├── PCB GERBERs/         # PCB manufacturing files
│       ├── TestResult/          # Component test result photos/videos
│       ├── FlowChart.png        # App ↔ Arduino interaction flow chart
│       └── partList.md          # Bill of materials
└── Android App/                 # Android Studio project (Java)
    └── app/src/main/java/com/petfilament/recycler/
        ├── MainActivity.java
        ├── ControlActivity.java
        ├── BluetoothManager.java
        ├── LogActivity.java
        ├── LogsAdapter.java
        └── DatabaseHelper.java
```

---

## Bill of Materials

| Qty | Component |
|-----|-----------|
| 1 | Arduino NANO (Firmware 1) / Arduino UNO R4 (Firmware 1.1) |
| 6 | 608ZZ Bearing |
| 1 | NEMA 17 Stepper Motor |
| 1 | I²C LCD Screen (16×2) |
| 1 | TMC2208 Stepper Driver |
| 1 | 3D Printer Aluminum Heating Block |
| 2 | IRF3205 MOSFETs |
| 1 | HC-05 Bluetooth Module |
| 1 | 10 kΩ Potentiometer |
| 1 | Push Button |
| 2 | 3 mm Red LEDs |
| 3 | 10 kΩ Resistors |
| 2 | 10 Ω Resistors |
| 2 | 1.8 kΩ Resistors |
| 1 | 10 µF Capacitor |
| 2 | 100 µF Capacitors |
| — | Female pin headers, screw terminals, M3 screws |

> Full details: [`0.1/docs/partList.md`](0.1/docs/partList.md)

---

## 3D Printed Parts

All STL files are located in [`0.1/docs/3D Files STL/`](0.1/docs/3D%20Files%20STL/).

| File | Description |
|------|-------------|
| `Board_leg.stl` | PCB mounting leg |
| `Cutter_support.stl` / `Cutter_support_V2.stl` | Bottle-strip cutter support |
| `Filament_aligner.stl` | Guides filament into the heater |
| `Gear_spool_A.stl` / `Gear_spool_B.stl` | Drive gear pair |
| `PET_Spool_A.stl` / `PET_Spool_B.stl` | PET bottle spool halves |
| `PET_Spool_support_A.stl` / `PET_Spool_support_B.stl` | Spool support brackets |
| `Pully.stl` | Motor pulley |
| `Small_gear.stl` | Secondary drive gear |
| `Spool_holder_side_A.stl` / `Spool_holder_side_B.stl` | Spool holder sides |
| `Stepper_support.stl` | NEMA 17 motor mount |

---

## PCB

Gerber files for the custom PCB are in [`0.1/docs/PCB GERBERs/`](0.1/docs/PCB%20GERBERs/). Reference photos of the assembled board are also in [`0.1/docs/`](0.1/docs/).

The circuit is based on the [Electronoobs PET bottle recycler](http://electronoobs.com/eng_arduino_tut174.php) design, with additions for the Bluetooth module.

---

## Arduino Firmware

### Firmware 1 (`0.1/arduino/Firmware_1/`)
- Target board: **Arduino Nano**
- Controls heating element (PID), stepper motor speed (potentiometer), and LCD display.
- Required libraries: `LiquidCrystal_I2C`, `thermistor` ([Electronoobs](https://electronoobs.com/eng_arduino_tut_thermistor.php)), `AccelStepper`

### Firmware 1.1 (`0.1/arduino/Firmware_1.1/`)
- Target board: **Arduino UNO R4 WiFi/Minima**
- Adds Bluetooth serial (`Serial1`) communication and EEPROM settings persistence.
- Replaces the thermistor library with a manual NTC 100 k / β=3950 calculation.
- Required libraries: `hd44780`, `AccelStepper`

#### Supported Bluetooth Commands

| Command | Description |
|---------|-------------|
| `SET_TEMP:<value>` | Set target temperature (0–300 °C) |
| `SET_SPEED:<value>` | Set motor speed (0–1000 steps/s) |
| `START` | Enable stepper motor |
| `STOP` | Disable stepper motor |
| `GET_STATUS` | Returns `TEMP:x,SPEED:x,STATUS:ON/OFF,CONNECTED:yes` |
| `SAVE` | Persist current temperature and speed to EEPROM |

---

## Android App

Located in [`Android App/`](Android%20App/). Open with **Android Studio**.

### Screens

| Activity | Description |
|----------|-------------|
| `MainActivity` | Home screen; navigate to Control screen |
| `ControlActivity` | Bluetooth discovery, connection, machine control, live status |
| `LogActivity` | View history of all sent/received Bluetooth commands |

### Permissions Required

- `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` (Android 12+)
- `ACCESS_FINE_LOCATION` (required for Bluetooth scanning on all versions)

### Build

1. Open the `Android App/` folder in Android Studio.
2. Sync Gradle and build the project.
3. Install on a device running Android 8.0 (API 26) or later.

---

## Setup & Usage

1. **Print** all 3D parts and assemble the mechanical frame.
2. **Solder** the PCB using the GERBER files and the parts list above.
3. **Flash** the Arduino firmware using the Arduino IDE.
4. **Pair** your Android phone with the HC-05 Bluetooth module (default PIN: `1234`).
5. **Install** the Android app on your phone.
6. Open the app, tap **Navigate → Control**, then select the HC-05 device and tap **Connect**.
7. Set the desired temperature and speed, tap **Save**, then tap **Start** to begin extruding.
8. Monitor live temperature and speed on the Control screen, or tap **View Logs** to review command history.

---

## References

- Electronoobs PET Recycler Tutorial: <http://electronoobs.com/eng_arduino_tut174.php>
- HC-05 Bluetooth Module Guide: <https://www.electronicshub.org/arduino-hc-05-bluetooth-module-tutorial/>
