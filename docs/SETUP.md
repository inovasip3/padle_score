# 🏓 Padel Scoreboard - Setup Guide

## 📦 System Components

| Component | Purpose |
|-----------|---------|
| **Android TV Box** | Display device (HDMI output to TV/monitor) |
| **Android APK** | Scoreboard app (kiosk launcher + HTTP server) |
| **ESP8266 + IR Sensors** | Touchless button controller via WiFi |

---

## 🔧 Part 1: Build & Install the Android APK

### Prerequisites
- **JDK 17+** installed on your build machine
- Or use **Android Studio** to open the `android/` folder and build from there

### Build from Command Line

```bash
cd android
# Windows:
gradlew.bat assembleDebug

# Linux/Mac:
./gradlew assembleDebug
```

The APK will be at: `android/app/build/outputs/apk/debug/app-debug.apk`

### Install on Android TV Box

1. Copy the APK to a USB drive or SD card
2. Insert into the Android TV Box
3. Use a file manager to install the APK
4. When prompted, **set "Padel Scoreboard" as the default HOME launcher**
5. **Reboot** → The scoreboard appears automatically!

### First Boot Behavior
- Black screen with **green** (Team A) and **amber** (Team B) scores
- IP address shown briefly at the bottom (e.g., `📡 192.168.1.100:8888`)
- Note this IP — you'll need it for the ESP8266

---

## 🔌 Part 2: ESP8266 IR Sensor Controller

### Hardware Required
| Part | Quantity | Notes |
|------|----------|-------|
| ESP8266 NodeMCU | 1 | Any ESP8266 board works |
| IR Line Detection Module | 2 | TCRT5000 or HW-201 recommended |
| Jumper wires | 6+ | Female-to-female |
| USB cable | 1 | For power + programming |

### Wiring Diagram

```
ESP8266 NodeMCU          IR Sensor LEFT (Team A)
─────────────────        ─────────────────────
  3.3V  ──────────────── VCC
  GND   ──────────────── GND
  D1    ──────────────── OUT (Digital)

ESP8266 NodeMCU          IR Sensor RIGHT (Team B)
─────────────────        ─────────────────────
  3.3V  ──────────────── VCC
  GND   ──────────────── GND
  D2    ──────────────── OUT (Digital)
```

> **TIP**: Adjust the potentiometer on each IR sensor module to set the detection distance (typically 2-10cm).

### Flash the Firmware (PlatformIO + VS Code)

1. Install **VS Code** + **PlatformIO extension**
2. Open folder: `esp8266/`
3. Edit `platformio.ini` or `src/main.cpp` to set:
   - `WIFI_SSID` — your WiFi network name
   - `WIFI_PASS` — your WiFi password
   - `ANDROID_IP` — the IP shown on the scoreboard at boot
   - `ANDROID_PORT` — default `8888`
4. Connect ESP8266 via USB
5. Click **Upload** (→) in PlatformIO toolbar
6. Open **Serial Monitor** to verify connection

### Button Behavior (Touchless)

| Action | Trigger | Command |
|--------|---------|---------|
| Wave LEFT sensor (<0.5s) | Short trigger | Team A +1 point |
| Wave RIGHT sensor (<0.5s) | Short trigger | Team B +1 point |
| Hold hand at LEFT (≥1.5s) | Long trigger | Team A −1 point (undo) |
| Hold hand at RIGHT (≥1.5s) | Long trigger | Team B −1 point (undo) |
| Hold BOTH sensors (≥1.5s) | Dual long trigger | RESET all scores |

---

## ⚙️ Part 3: Hidden Configuration

### Access Settings
1. **Tap the TV screen 5 times rapidly** (within 2 seconds)
2. Enter PIN: `1234` (default)
3. Configure:
   - Team names
   - Server port
   - Score colors
   - PIN code

---

## 🌐 Part 4: HTTP API (for testing/integration)

Test from any browser on the same WiFi:

```
http://<IP>:8888/cmd?c=A_PLUS    → Team A score up
http://<IP>:8888/cmd?c=B_PLUS    → Team B score up
http://<IP>:8888/cmd?c=A_MINUS   → Team A score down
http://<IP>:8888/cmd?c=B_MINUS   → Team B score down
http://<IP>:8888/cmd?c=RESET     → Reset all scores
http://<IP>:8888/status           → Get current score (JSON)
http://<IP>:8888/ping             → Health check
```

---

## 🛠️ Troubleshooting

| Issue | Solution |
|-------|----------|
| No IP shown on screen | Check WiFi connection on TV Box |
| ESP8266 won't connect | Verify SSID/password in firmware |
| Scores don't update | Check ANDROID_IP matches TV Box IP |
| App exits kiosk mode | Re-set as default HOME launcher |
| IR sensor too sensitive | Adjust potentiometer on sensor module |
| IR sensor not detecting | Bring hand closer / clean sensor lens |
