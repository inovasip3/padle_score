/*
 * ============================================================
 *  PADEL SCOREBOARD - ESP8266 IR SENSOR BUTTON CONTROLLER
 * ============================================================
 * 
 * Uses 2x IR line detection sensors as touchless switch buttons.
 * When a hand passes near the IR sensor, it triggers a score command.
 * 
 * WIRING:
 *   IR Sensor LEFT  (Team A) → D1 (GPIO5)  - Digital OUT
 *   IR Sensor RIGHT (Team B) → D2 (GPIO4)  - Digital OUT
 *   Status LED               → D4 (GPIO2)  - Built-in LED
 *   
 *   IR Sensors: VCC → 3.3V, GND → GND, OUT → Dx
 *   
 * IR LINE DETECTION MODULE (e.g. TCRT5000, HW-201):
 *   - Output LOW  when object/hand is detected (near)
 *   - Output HIGH when no object (clear)
 *   - Adjust sensitivity via onboard potentiometer
 *
 * BEHAVIOR:
 *   Short trigger (<500ms):   LEFT → A_PLUS,  RIGHT → B_PLUS
 *   Long trigger (≥1500ms):   LEFT → A_MINUS, RIGHT → B_MINUS  
 *   Both triggered (≥1500ms): RESET
 *
 * COMMUNICATION:
 *   HTTP GET to Android TV Box on local WiFi
 *   http://<ANDROID_IP>:<PORT>/cmd?c=<COMMAND>
 * 
 * ============================================================
 */

#include <Arduino.h>
#include <ESP8266WiFi.h>
#include <ESP8266HTTPClient.h>
#include <WiFiClient.h>

// ============================================================
//  CONFIGURATION - Edit these values for your setup
// ============================================================

// WiFi credentials (can also be set in platformio.ini build_flags)
#ifndef WIFI_SSID
  #define WIFI_SSID     "WIFI_SSD"
#endif
#ifndef WIFI_PASS
  #define WIFI_PASS     "password"
#endif

// Android TV Box IP and port
#ifndef ANDROID_IP
  #define ANDROID_IP    "192.168.2.106"
#endif
#ifndef ANDROID_PORT
  #define ANDROID_PORT  8888
#endif

// ============================================================
//  PIN DEFINITIONS
// ============================================================

#define PIN_SENSOR_LEFT   5     // GPIO5 (D1) - IR sensor for Team A
#define PIN_SENSOR_RIGHT  4     // GPIO4 (D2) - IR sensor for Team B
#define PIN_LED           2     // GPIO2 (D4) - Built-in LED (active LOW)
#define PIN_BUZZER        14    // GPIO14 (D5) - Active Buzzer (active HIGH)

// ============================================================
//  HELPER: BUZZER TONE
// ============================================================
void triggerBuzzer(int durationMs, int count = 1) {
    for (int i = 0; i < count; i++) {
        digitalWrite(PIN_BUZZER, HIGH);
        delay(durationMs);
        digitalWrite(PIN_BUZZER, LOW);
        if (count > 1 && i < count - 1) delay(50);
    }
}

// ============================================================
//  TIMING CONSTANTS
// ============================================================

#define SHORT_PRESS_MAX   500     // Max ms for short press
#define LONG_PRESS_MIN    1500    // Min ms for long press
#define DUAL_PRESS_WINDOW 300     // Max ms between two sensors triggering
#define DEBOUNCE_MS       50      // Debounce time in ms
#define SEND_COOLDOWN_MS  500     // Min time between consecutive sends
#define WIFI_RETRY_MS     5000    // WiFi reconnect interval
#define HTTP_TIMEOUT_MS   2000    // HTTP request timeout
#define HTTP_RETRIES      3       // Number of retry attempts

// ============================================================
//  STATE VARIABLES
// ============================================================

// Sensor states (LOW = triggered/detected, HIGH = clear)
bool leftTriggered = false;
bool rightTriggered = false;
bool leftLastState = HIGH;
bool rightLastState = HIGH;

// Timing
unsigned long leftTriggerStart = 0;
unsigned long rightTriggerStart = 0;
unsigned long leftDebounceTime = 0;
unsigned long rightDebounceTime = 0;
unsigned long lastSendTime = 0;

// Command state
bool leftCommandSent = false;
bool rightCommandSent = false;
bool dualCommandSent = false;

// WiFi
unsigned long lastWifiCheck = 0;
WiFiClient wifiClient;

// ============================================================
//  FUNCTION PROTOTYPES
// ============================================================
void connectWiFi();
void maintainWiFi();
void readSensors();
void processButtons();
void sendCommand(const char* cmd);
void blinkSuccess();
void blinkError();

// ============================================================
//  SETUP
// ============================================================

void setup() {
    Serial.begin(115200);
    Serial.println();
    Serial.println(F("================================"));
    Serial.println(F(" PADEL SCOREBOARD CONTROLLER"));
    Serial.println(F(" IR Sensor Touch-Free Buttons"));
    Serial.println(F("================================"));
    
    // Configure pins
    pinMode(PIN_SENSOR_LEFT, INPUT);
    pinMode(PIN_SENSOR_RIGHT, INPUT);
    pinMode(PIN_LED, OUTPUT);
    pinMode(PIN_BUZZER, OUTPUT);
    digitalWrite(PIN_LED, HIGH); // LED off (active LOW)
    digitalWrite(PIN_BUZZER, LOW);
    
    // Connect to WiFi
    connectWiFi();
}

// ============================================================
//  MAIN LOOP
// ============================================================

void loop() {
    // Ensure WiFi stays connected
    maintainWiFi();
    
    // Read sensors with debounce
    readSensors();
    
    // Process button logic
    processButtons();
    
    // Small delay to prevent CPU hogging
    delay(5);
}

// ============================================================
//  WIFI MANAGEMENT
// ============================================================

void connectWiFi() {
    Serial.print(F("Connecting to WiFi: "));
    Serial.println(WIFI_SSID);
    
    WiFi.mode(WIFI_STA);
    WiFi.setAutoReconnect(true);
    WiFi.persistent(true);
    WiFi.begin(WIFI_SSID, WIFI_PASS);
    
    // Blink LED while connecting
    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < 60) {
        digitalWrite(PIN_LED, !digitalRead(PIN_LED));
        delay(250);
        Serial.print(".");
        attempts++;
    }
    
    if (WiFi.status() == WL_CONNECTED) {
        digitalWrite(PIN_LED, LOW); // LED on = connected
        Serial.println();
        Serial.print(F("Connected! IP: "));
        Serial.println(WiFi.localIP());
        Serial.print(F("Target: "));
        Serial.print(ANDROID_IP);
        Serial.print(F(":"));
        Serial.println(ANDROID_PORT);
        
        // Quick blink to indicate success
        for (int i = 0; i < 6; i++) {
            digitalWrite(PIN_LED, !digitalRead(PIN_LED));
            delay(100);
        }
        digitalWrite(PIN_LED, LOW); // LED on
    } else {
        Serial.println(F("\nWiFi connection failed! Will retry..."));
        digitalWrite(PIN_LED, HIGH); // LED off
    }
}

void maintainWiFi() {
    if (WiFi.status() != WL_CONNECTED) {
        unsigned long now = millis();
        if (now - lastWifiCheck >= WIFI_RETRY_MS) {
            lastWifiCheck = now;
            Serial.println(F("WiFi lost, reconnecting..."));
            connectWiFi();
        }
    }
}

// ============================================================
//  SENSOR READING (with debounce)
// ============================================================

void readSensors() {
    unsigned long now = millis();
    
    // Read left sensor with debounce
    bool leftRaw = digitalRead(PIN_SENSOR_LEFT);
    if (leftRaw != leftLastState) {
        leftDebounceTime = now;
    }
    if ((now - leftDebounceTime) > DEBOUNCE_MS) {
        // IR sensor: LOW = object detected (triggered)
        bool newState = (leftRaw == LOW);
        if (newState != leftTriggered) {
            leftTriggered = newState;
            if (leftTriggered) {
                leftTriggerStart = now;
                leftCommandSent = false;
                Serial.println(F("LEFT sensor: TRIGGERED"));
                triggerBuzzer(80); // Quick feedback beep
            } else {
                Serial.println(F("LEFT sensor: RELEASED"));
            }
        }
    }
    leftLastState = leftRaw;
    
    // Read right sensor with debounce
    bool rightRaw = digitalRead(PIN_SENSOR_RIGHT);
    if (rightRaw != rightLastState) {
        rightDebounceTime = now;
    }
    if ((now - rightDebounceTime) > DEBOUNCE_MS) {
        bool newState = (rightRaw == LOW);
        if (newState != rightTriggered) {
            rightTriggered = newState;
            if (rightTriggered) {
                rightTriggerStart = now;
                rightCommandSent = false;
                Serial.println(F("RIGHT sensor: TRIGGERED"));
                triggerBuzzer(80); // Quick feedback beep
            } else {
                Serial.println(F("RIGHT sensor: RELEASED"));
            }
        }
    }
    rightLastState = rightRaw;
}

// ============================================================
//  BUTTON LOGIC PROCESSING
// ============================================================

void processButtons() {
    unsigned long now = millis();
    
    // Cooldown check
    if (now - lastSendTime < SEND_COOLDOWN_MS) return;
    
    // --- DUAL PRESS DETECTION ---
    if (leftTriggered && rightTriggered && !dualCommandSent) {
        // Check if both were triggered within the allowed window
        unsigned long timeDiff = 0;
        if (leftTriggerStart > rightTriggerStart) {
            timeDiff = leftTriggerStart - rightTriggerStart;
        } else {
            timeDiff = rightTriggerStart - leftTriggerStart;
        }
        
        if (timeDiff <= DUAL_PRESS_WINDOW) {
            // Both triggered nearly simultaneously
            unsigned long earlierStart = min(leftTriggerStart, rightTriggerStart);
            unsigned long holdDuration = now - earlierStart;
            
            if (holdDuration >= LONG_PRESS_MIN) {
                // DUAL LONG PRESS → RESET
                Serial.println(F(">>> DUAL LONG PRESS → RESET"));
                sendCommand("RESET");
                dualCommandSent = true;
                leftCommandSent = true;
                rightCommandSent = true;
            }
        }
        return; // Don't process individual buttons while both are triggered
    }
    
    // Reset dual flag when both released
    if (!leftTriggered && !rightTriggered) {
        dualCommandSent = false;
    }
    
    // --- LEFT SENSOR (Team A) ---
    if (!leftTriggered && leftTriggerStart > 0 && !leftCommandSent) {
        unsigned long duration = now - leftTriggerStart;
        // Released - check duration (but only if it was truly released, not dual)
        if (!rightTriggered || (rightTriggerStart == 0)) {
            if (duration < SHORT_PRESS_MAX) {
                Serial.println(F(">>> LEFT SHORT → A_PLUS"));
                sendCommand("A_PLUS");
            }
            // Long press on release not needed, handled below
        }
        leftCommandSent = true;
        leftTriggerStart = 0;
    }
    
    // Left long press (still held)
    if (leftTriggered && !leftCommandSent && !rightTriggered) {
        unsigned long duration = now - leftTriggerStart;
        if (duration >= LONG_PRESS_MIN) {
            Serial.println(F(">>> LEFT LONG → A_MINUS"));
            sendCommand("A_MINUS");
            leftCommandSent = true;
        }
    }
    
    // --- RIGHT SENSOR (Team B) ---
    if (!rightTriggered && rightTriggerStart > 0 && !rightCommandSent) {
        unsigned long duration = now - rightTriggerStart;
        if (!leftTriggered || (leftTriggerStart == 0)) {
            if (duration < SHORT_PRESS_MAX) {
                Serial.println(F(">>> RIGHT SHORT → B_PLUS"));
                sendCommand("B_PLUS");
            }
        }
        rightCommandSent = true;
        rightTriggerStart = 0;
    }
    
    // Right long press (still held)
    if (rightTriggered && !rightCommandSent && !leftTriggered) {
        unsigned long duration = now - rightTriggerStart;
        if (duration >= LONG_PRESS_MIN) {
            Serial.println(F(">>> RIGHT LONG → B_MINUS"));
            sendCommand("B_MINUS");
            rightCommandSent = true;
        }
    }
}

// ============================================================
//  HTTP COMMAND SENDER (with retry)
// ============================================================

void sendCommand(const char* cmd) {
    if (WiFi.status() != WL_CONNECTED) {
        Serial.println(F("WiFi not connected, cannot send command"));
        blinkError();
        return;
    }
    
    // Build URL
    String url = "http://";
    url += ANDROID_IP;
    url += ":";
    url += String(ANDROID_PORT);
    url += "/cmd?c=";
    url += cmd;
    
    Serial.print(F("Sending: "));
    Serial.println(url);
    
    // LED feedback
    digitalWrite(PIN_LED, HIGH); // LED off during send
    
    bool success = false;
    for (int attempt = 1; attempt <= HTTP_RETRIES; attempt++) {
        HTTPClient http;
        http.begin(wifiClient, url);
        http.setTimeout(HTTP_TIMEOUT_MS);
        
        int httpCode = http.GET();
        
        if (httpCode == 200) {
            String response = http.getString();
            Serial.print(F("OK ("));
            Serial.print(attempt);
            Serial.print(F("): "));
            Serial.println(response);
            success = true;
            http.end();
            break;
        } else {
            Serial.print(F("Attempt "));
            Serial.print(attempt);
            Serial.print(F(" failed: "));
            Serial.println(httpCode);
            http.end();
            
            if (attempt < HTTP_RETRIES) {
                delay(100); // Brief delay before retry
            }
        }
    }
    
    if (success) {
        // Quick blink + double beep = success
        blinkSuccess();
        triggerBuzzer(50, 2);
    } else {
        // Error blink + long beep
        Serial.println(F("All retries failed!"));
        blinkError();
        triggerBuzzer(500);
    }
    
    lastSendTime = millis();
}

// ============================================================
//  LED FEEDBACK
// ============================================================

void blinkSuccess() {
    // Single quick flash
    digitalWrite(PIN_LED, LOW);   // ON
    delay(50);
    digitalWrite(PIN_LED, HIGH);  // OFF
    delay(50);
    digitalWrite(PIN_LED, LOW);   // ON (stays on = connected)
}

void blinkError() {
    // Triple fast blink
    for (int i = 0; i < 3; i++) {
        digitalWrite(PIN_LED, LOW);
        delay(80);
        digitalWrite(PIN_LED, HIGH);
        delay(80);
    }
    // Return to connection state
    if (WiFi.status() == WL_CONNECTED) {
        digitalWrite(PIN_LED, LOW); // ON
    } else {
        digitalWrite(PIN_LED, HIGH); // OFF
    }
}
