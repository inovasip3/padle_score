/**
 * ============================================================
 *  PADEL SCOREBOARD - ESP32-C3 BLE HID CONTROLLER (V2.1)
 * ============================================================
 *
 * Uses 2x GY-530 VL53L0X Laser TOF sensors as touchless 
 * "tap" buttons — optimized for Padel RACKET gestures.
 *
 * KEY IMPROVEMENTS in V2.1:
 *   - Hysteresis band (separate trigger-in / trigger-out distance)
 *     to prevent jitter at the detection boundary.
 *   - Multi-sample confirmation to ignore single noise spikes
 *     or vibration from a fast racket swing.
 *   - Minimum dwell time: shorter than typical hand-wave setting
 *     to accommodate fast racket tap movements.
 *   - Mandatory cooldown after each action to prevent double-fire
 *     from a racket "bouncing" through the zone twice.
 *   - VL53L0X set to SHORT RANGE mode for faster, more stable
 *     readings at close distances (<200mm).
 *   - All critical timing/threshold parameters are labeled with
 *     [TUNABLE] markers and explanation for easy adjustment.
 *
 * WIRING:
 *   I2C SDA        → GPIO 8
 *   I2C SCL        → GPIO 9
 *   XSHUT LEFT     → GPIO 4
 *   XSHUT RIGHT    → GPIO 5
 *   LED            → GPIO 6
 *   Buzzer         → GPIO 7
 *
 * SERIAL CONSOLE COMMANDS (USB-C, 115200 baud):
 *   INFO                 — Show current config
 *   SET_DIST_IN  <mm>    — Set trigger-in distance
 *   SET_DIST_OUT <mm>    — Set trigger-out (hysteresis) distance
 *   SET_DELAY    <ms>    — Long hold duration for minus/reset
 *   SET_DWELL    <ms>    — Min time in zone to count as valid
 *   SET_CONFIRM  <n>     — Number of consecutive samples to confirm
 *   SET_COOLDOWN <ms>    — Cooldown after each action
 *   SET_KEY_LA   <char>  — Key: Left short (Team A +)
 *   SET_KEY_LB   <char>  — Key: Left long  (Team A -)
 *   SET_KEY_RA   <char>  — Key: Right short (Team B +)
 *   SET_KEY_RB   <char>  — Key: Right long  (Team B -)
 *   SET_KEY_RES  <char>  — Key: Both long   (Reset)
 *
 * ============================================================
 */

#include <Arduino.h>
#include <Wire.h>
#include <BleKeyboard.h>
#include <Adafruit_VL53L0X.h>
#include <Preferences.h>

// ============================================================
//  PIN DEFINITIONS
//  Adjust these if your ESP32-C3 board uses different GPIO layout
// ============================================================
#define I2C_SDA       8
#define I2C_SCL       9
#define XSHUT_LEFT    4
#define XSHUT_RIGHT   5
#define PIN_LED       6
#define PIN_BUZZER    7

// VL53L0X I2C addresses after address reassignment at boot
#define ADDR_LEFT     0x30
#define ADDR_RIGHT    0x31

// ============================================================
//  [TUNABLE] SENSING PARAMETERS
//  These are the most important values to adjust on the court.
//  All can also be changed live via USB Serial commands.
// ============================================================

// [TUNABLE] DIST_IN: Distance (mm) to START a trigger.
//   Raket (good reflector): try 80-120mm
//   Hand: try 130-180mm
//   ↑ Lower = closer tap needed. ↓ Higher = more forgiving.
uint16_t distIn = 100;

// [TUNABLE] DIST_OUT: Distance (mm) to END a trigger (hysteresis).
//   Must be > distIn. The gap prevents jitter at the boundary.
//   Recommended: distIn + 60 to 80mm
//   Example: distIn=100, distOut=180 → 80mm hysteresis band.
uint16_t distOut = 180;

// [TUNABLE] MIN_DWELL_MS: Minimum time (ms) the racket must stay
//   in the zone before the trigger is considered VALID.
//   Prevents false triggers from the racket just flying past.
//   Raket: 40-80ms.  Hand: 100-200ms.
uint16_t minDwellMs = 50;

// [TUNABLE] CONFIRM_SAMPLES: Number of consecutive sensor readings
//   below distIn needed before a trigger is registered.
//   Prevents single noise/vibration spikes from firing.
//   At 15ms/loop → 2 samples = 30ms confirm time.
//   Raket: 2-3.  Hand/slower: 3-5.
uint8_t confirmSamples = 2;

// [TUNABLE] COOLDOWN_MS: Mandatory wait (ms) after any action fires
//   before the same sensor can trigger again.
//   Prevents a single racket swing from scoring twice if it
//   passes through the zone and bounces back quickly.
//   Recommended: 600-1000ms.
uint32_t cooldownMs = 700;

// [TUNABLE] DELAY_LONG_MS: How long the sensor must be blocked
//   continuously to fire a LONG (minus/undo) action.
//   Raket use case: hold racket steady near sensor for Undo.
//   Recommended: 1500-2000ms.
uint32_t delayLong = 1500;

// ============================================================
//  KEY BINDINGS (configurable via Serial)
// ============================================================
char keyLeftShort  = 'a';
char keyLeftLong   = 's';
char keyRightShort = 'b';
char keyRightLong  = 'd';
char keyReset      = 'r';

// ============================================================
//  INTERNAL STATE
// ============================================================
BleKeyboard       bleKeyboard("Padel Remote V2", "PadelBoard", 100);
Adafruit_VL53L0X  sensorL;
Adafruit_VL53L0X  sensorR;
Preferences       prefs;

struct SensorState {
    bool      triggered;
    uint8_t   confirmCount;  // consecutive samples inside distIn
    uint32_t  dwellStart;    // when first confirmed entry happened
    uint32_t  lastActionTime;
    bool      actionSent;
    bool      longActionSent;
};

SensorState stateL = {false, 0, 0, 0, false, false};
SensorState stateR = {false, 0, 0, 0, false, false};

// Dual-sensor reset state
bool   dualActive     = false;
uint32_t dualStart    = 0;
bool   resetSent      = false;

// ============================================================
//  FEEDBACK
// ============================================================
void triggerBuzzer(int durationMs, int count = 1) {
    for (int i = 0; i < count; i++) {
        digitalWrite(PIN_BUZZER, HIGH); delay(durationMs);
        digitalWrite(PIN_BUZZER, LOW);
        if (count > 1 && i < count - 1) delay(50);
    }
}

void blinkLED(int durationMs, int count = 1) {
    for (int i = 0; i < count; i++) {
        digitalWrite(PIN_LED, HIGH); delay(durationMs);
        digitalWrite(PIN_LED, LOW);
        if (count > 1 && i < count - 1) delay(50);
    }
}

// Feedback types: 1=short-press, 2=long-press, 3=reset, 0=error
void feedback(int type) {
    if (type == 1) { triggerBuzzer(60);    blinkLED(60); }
    else if (type == 2) { triggerBuzzer(250);   blinkLED(250); }
    else if (type == 3) { triggerBuzzer(80, 3); blinkLED(80, 3); }
    else               { triggerBuzzer(40, 2); } // BLE disconnected
}

// ============================================================
//  SEND KEY
// ============================================================
void sendKey(char k, int type) {
    if (bleKeyboard.isConnected()) {
        bleKeyboard.print(k);
        Serial.printf(">> Sent: '%c' (type=%d)\n", k, type);
        feedback(type);
    } else {
        Serial.printf(">> BLE not connected — ignoring '%c'\n", k);
        feedback(0);
    }
}

// ============================================================
//  CONFIG: LOAD / SAVE / PRINT
// ============================================================
void printConfig() {
    Serial.println("\n======= Config =======");
    Serial.printf("  distIn      : %u mm    [TUNABLE: SET_DIST_IN]\n",  distIn);
    Serial.printf("  distOut     : %u mm    [TUNABLE: SET_DIST_OUT]\n", distOut);
    Serial.printf("  minDwell    : %u ms    [TUNABLE: SET_DWELL]\n",    minDwellMs);
    Serial.printf("  confirm     : %u smpls [TUNABLE: SET_CONFIRM]\n",  confirmSamples);
    Serial.printf("  cooldown    : %u ms    [TUNABLE: SET_COOLDOWN]\n", cooldownMs);
    Serial.printf("  delayLong   : %u ms    [TUNABLE: SET_DELAY]\n",    delayLong);
    Serial.printf("  Keys L(+/-)  : %c / %c\n", keyLeftShort,  keyLeftLong);
    Serial.printf("  Keys R(+/-)  : %c / %c\n", keyRightShort, keyRightLong);
    Serial.printf("  Key Reset    : %c\n",      keyReset);
    Serial.println("======================\n");
}

void loadConfig() {
    prefs.begin("padel", false);
    distIn         = prefs.getUShort("dist_in",      100);
    distOut        = prefs.getUShort("dist_out",     180);
    minDwellMs     = prefs.getUShort("min_dwell",     50);
    confirmSamples = prefs.getUChar ("confirm",         2);
    cooldownMs     = prefs.getUInt  ("cooldown",      700);
    delayLong      = prefs.getUInt  ("delay_long",   1500);
    keyLeftShort   = prefs.getChar  ("key_la",        'a');
    keyLeftLong    = prefs.getChar  ("key_lb",        's');
    keyRightShort  = prefs.getChar  ("key_ra",        'b');
    keyRightLong   = prefs.getChar  ("key_rb",        'd');
    keyReset       = prefs.getChar  ("key_res",       'r');
    printConfig();
}

// ============================================================
//  SERIAL COMMAND PROCESSOR
// ============================================================
void processSerialCommand() {
    if (!Serial.available()) return;
    String cmd = Serial.readStringUntil('\n');
    cmd.trim();
    if (cmd.isEmpty()) return;

    int spaceIdx = cmd.indexOf(' ');
    if (spaceIdx <= 0) {
        if (cmd == "INFO") printConfig();
        else Serial.println("Unknown cmd. Try: INFO, SET_DIST_IN, SET_DIST_OUT, SET_DWELL, SET_CONFIRM, SET_COOLDOWN, SET_DELAY, SET_KEY_LA/LB/RA/RB/RES");
        return;
    }

    String action = cmd.substring(0, spaceIdx);
    String value  = cmd.substring(spaceIdx + 1);
    value.trim();
    bool changed = false;

    if      (action == "SET_DIST_IN")   { distIn         = value.toInt(); prefs.putUShort("dist_in",    distIn);         changed = true; }
    else if (action == "SET_DIST_OUT")  { distOut        = value.toInt(); prefs.putUShort("dist_out",   distOut);        changed = true; }
    else if (action == "SET_DWELL")     { minDwellMs     = value.toInt(); prefs.putUShort("min_dwell",  minDwellMs);     changed = true; }
    else if (action == "SET_CONFIRM")   { confirmSamples = value.toInt(); prefs.putUChar ("confirm",    confirmSamples); changed = true; }
    else if (action == "SET_COOLDOWN")  { cooldownMs     = value.toInt(); prefs.putUInt  ("cooldown",   cooldownMs);     changed = true; }
    else if (action == "SET_DELAY")     { delayLong      = value.toInt(); prefs.putUInt  ("delay_long", delayLong);      changed = true; }
    else if (action == "SET_KEY_LA" && value.length() > 0) { keyLeftShort  = value[0]; prefs.putChar("key_la",  keyLeftShort);  changed = true; }
    else if (action == "SET_KEY_LB" && value.length() > 0) { keyLeftLong   = value[0]; prefs.putChar("key_lb",  keyLeftLong);   changed = true; }
    else if (action == "SET_KEY_RA" && value.length() > 0) { keyRightShort = value[0]; prefs.putChar("key_ra",  keyRightShort); changed = true; }
    else if (action == "SET_KEY_RB" && value.length() > 0) { keyRightLong  = value[0]; prefs.putChar("key_rb",  keyRightLong);  changed = true; }
    else if (action == "SET_KEY_RES" && value.length() > 0){ keyReset      = value[0]; prefs.putChar("key_res", keyReset);      changed = true; }
    else Serial.printf("Unknown action: %s\n", action.c_str());

    if (changed) {
        Serial.printf("  Applied: %s = %s\n", action.c_str(), value.c_str());
        feedback(1);
    }
}

// ============================================================
//  SENSOR INIT (dual I2C address via XSHUT)
// ============================================================
void initSensors() {
    // Pull both sensors offline
    digitalWrite(XSHUT_LEFT,  LOW);
    digitalWrite(XSHUT_RIGHT, LOW);
    delay(20);

    // Boot LEFT, assign address
    digitalWrite(XSHUT_LEFT, HIGH); delay(20);
    if (!sensorL.begin(ADDR_LEFT, false, &Wire)) {
        Serial.println("[ERR] LEFT VL53L0X not found!");
    } else {
        // [TUNABLE] Timing budget: lower = faster but noisier.
        //   20000 µs (20ms) = SHORT RANGE, best for <200mm racket use.
        //   33000 µs (33ms) = balanced
        //   200000 µs       = highest accuracy (too slow for tap detection)
        sensorL.setMeasurementTimingBudgetMicroSeconds(20000);
        Serial.println("[OK] LEFT VL53L0X @ 0x30, 20ms timing");
    }

    // Boot RIGHT, assign address
    digitalWrite(XSHUT_RIGHT, HIGH); delay(20);
    if (!sensorR.begin(ADDR_RIGHT, false, &Wire)) {
        Serial.println("[ERR] RIGHT VL53L0X not found!");
    } else {
        sensorR.setMeasurementTimingBudgetMicroSeconds(20000);
        Serial.println("[OK] RIGHT VL53L0X @ 0x31, 20ms timing");
    }
}

// ============================================================
//  SENSOR LOGIC: process one sensor with full hysteresis,
//  confirmation, dwell, cooldown, and long-press detection.
// ============================================================
void processSensor(
    SensorState &state,
    uint16_t     rawDist,
    bool         rangeOk,
    bool         blockedByDual,
    char         keyShort,
    char         keyLong
) {
    uint32_t now = millis();

    // Ignore sensor entirely during cooldown
    if (now - state.lastActionTime < cooldownMs) {
        return;
    }

    // Determine if this reading is "in zone" using hysteresis:
    //   Enter condition: below distIn (tight threshold)
    //   Exit condition : above distOut (wider threshold)
    bool inZone = rangeOk && (rawDist < distIn);
    bool outZone = !rangeOk || (rawDist > distOut);

    // --- NOT YET TRIGGERED ---
    if (!state.triggered) {
        if (inZone) {
            state.confirmCount++;
            // [TUNABLE] Must see `confirmSamples` consecutive readings to confirm
            if (state.confirmCount >= confirmSamples) {
                state.triggered     = true;
                state.dwellStart    = now;
                state.actionSent    = false;
                state.longActionSent = false;
                state.confirmCount  = 0;
                Serial.println("  [ENTER] Sensor triggered");
            }
        } else {
            state.confirmCount = 0; // Reset counter on any reading out of zone
        }
        return;
    }

    // --- TRIGGERED: check for long press or exit ---
    if (blockedByDual) return; // Dual-press logic takes priority

    // Long press: still in zone and held long enough
    if (!outZone && !state.longActionSent &&
        (now - state.dwellStart >= delayLong)) {
        Serial.printf("  [LONG] held %lu ms\n", now - state.dwellStart);
        sendKey(keyLong, 2);
        state.longActionSent  = true;
        state.actionSent      = true;
        state.lastActionTime  = now;
    }

    // Exit zone: fire short press if not already fired a long press
    if (outZone) {
        uint32_t dwellTime = now - state.dwellStart;
        Serial.printf("  [EXIT] dwell=%lu ms\n", dwellTime);

        // Only fire short if within valid dwell window and nothing sent yet
        if (!state.actionSent && dwellTime >= minDwellMs && dwellTime < delayLong) {
            sendKey(keyShort, 1);
            state.lastActionTime = now;
        }

        // Reset state for next gesture
        state.triggered      = false;
        state.confirmCount   = 0;
        state.actionSent     = false;
        state.longActionSent = false;
    }
}

// ============================================================
//  SETUP
// ============================================================
void setup() {
    Serial.begin(115200);
    delay(2000); // Allow USB serial to enumerate on C3

    pinMode(XSHUT_LEFT,  OUTPUT);
    pinMode(XSHUT_RIGHT, OUTPUT);
    pinMode(PIN_LED,     OUTPUT);
    pinMode(PIN_BUZZER,  OUTPUT);
    digitalWrite(PIN_LED,    LOW);
    digitalWrite(PIN_BUZZER, LOW);

    Serial.println("\n============================");
    Serial.println("  Padel Remote V2.1 Boot");
    Serial.println("============================");

    loadConfig();

    Wire.begin(I2C_SDA, I2C_SCL);
    initSensors();

    bleKeyboard.begin();

    // Boot jingle: 2 beeps = ready
    triggerBuzzer(100, 2);
    blinkLED(100, 2);
    Serial.println(">> System Ready. Type INFO for config.\n");
}

// ============================================================
//  MAIN LOOP
// ============================================================
void loop() {
    processSerialCommand();

    // Read both sensors
    VL53L0X_RangingMeasurementData_t mL, mR;
    sensorL.rangingTest(&mL, false);
    sensorR.rangingTest(&mR, false);

    uint16_t dL = mL.RangeMilliMeter;
    uint16_t dR = mR.RangeMilliMeter;
    bool     okL = (mL.RangeStatus != 4);
    bool     okR = (mR.RangeStatus != 4);

    bool lInZone = okL && (dL < distIn);
    bool rInZone = okR && (dR < distIn);

    uint32_t now = millis();

    // ── DUAL SENSOR (Reset logic) ────────────────────────────
    if (lInZone && rInZone) {
        if (!dualActive) {
            dualActive = true;
            dualStart  = now;
            resetSent  = false;
            // Block individual sensor actions while dual is active
            stateL.actionSent = true;
            stateR.actionSent = true;
            Serial.println("  [DUAL] Both sensors active");
        } else if (!resetSent && (now - dualStart >= delayLong)) {
            sendKey(keyReset, 3);
            resetSent = true;
        }
        // [TUNABLE] Loop delay during dual detection
        // Keep short so reset fires promptly.
        delay(15);
        return;
    }

    // Exit dual mode
    if (dualActive && !(lInZone && rInZone)) {
        dualActive = false;
        resetSent  = false;
        stateL.triggered = false;
        stateR.triggered = false;
        stateL.confirmCount = 0;
        stateR.confirmCount = 0;
    }

    // ── INDIVIDUAL SENSORS ───────────────────────────────────
    processSensor(stateL, dL, okL, dualActive, keyLeftShort,  keyLeftLong);
    processSensor(stateR, dR, okR, dualActive, keyRightShort, keyRightLong);

    // [TUNABLE] Main loop delay (ms).
    //   Lower = more responsive but higher I2C load.
    //   At 20ms sensor timing budget, going below 10ms is pointless.
    //   Recommended: 10-20ms for racket use.
    delay(15);
}
