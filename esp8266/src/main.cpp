#include <Arduino.h>
#include <ESP8266WiFi.h>
#include <ESP8266HTTPClient.h>
#include <WiFiClient.h>
#include <DNSServer.h>
#include <ESP8266WebServer.h>

#include "config.h"
#include "ConfigManager.h"
#include "config_portal.h"

// Structured Logging Macro (Matching d1_mini pattern)
#define LOG(msg) Serial.println(F("[INFO] " msg))
#define LOG_VAL(label, val) { Serial.print(F("[INFO] ")); Serial.print(F(label)); Serial.print(F(": ")); Serial.println(val); }
#define LOG_ERR(msg) Serial.println(F("[ERR]  " msg))

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
unsigned long dualHoldStart = 0;
bool isBlinking = false;

// WiFi & App Connection
unsigned long lastWifiCheck = 0;
unsigned long lastAppCheck = 0;
bool appReady = false;
WiFiClient wifiClient;

// Server & DNS
DNSServer dnsServer;
ESP8266WebServer server(80);
bool setupMode = false;

// LED & Buzzer Feedback States
struct FeedbackState {
    bool active = false;
    unsigned long startTime = 0;
    int type = 0; // 0: Success, 1: Error
    bool ledA = false;
    bool ledB = false;
    bool readyLed = false;
    int step = 0;
};
FeedbackState feedback;

struct BuzzerState {
    bool active = false;
    unsigned long startTime = 0;
    int count = 0;
    int currentCount = 0;
    int duration = 0;
    bool isOn = false;
};
BuzzerState bzState;

// WiFi Connection State
bool isWifiConnecting = false;
unsigned long lastInternalBlink = 0;
bool internalLedState = HIGH;

// LED State Logic
enum ReadyLEDState { LED_FAST, LED_1_8 };
ReadyLEDState currentLEDState = LED_FAST;

// ============================================================
//  FUNCTION PROTOTYPES
// ============================================================
void connectWiFi();
void maintainWiFi();
void readSensors();
void processButtons();
void sendCommand(const char* cmd);
void checkAppConnection();
void startFeedback(int type, bool a, bool b, bool ready);
void updateFeedback();
void triggerBuzzerAsync(int durationMs, int count = 1);
void updateBuzzer();
void playReadyMelody();
void updateReadyLED();
void startSetupMode();
void handleRoot();
void handleSave();
void handleNotFound();

// ============================================================
//  SETUP
// ============================================================

void setup() {
    Serial.begin(115200);
    delay(500);
    LOG("Padel Scoreboard Booting...");
    LOG_VAL("Version", VERSION);
    
    // Configure pins
    pinMode(PIN_SENSOR_LEFT, INPUT);
    pinMode(PIN_SENSOR_RIGHT, INPUT);
    pinMode(PIN_LED_INTERNAL, OUTPUT);
    pinMode(PIN_LED_A, OUTPUT);
    pinMode(PIN_LED_B, OUTPUT);
    pinMode(PIN_LED_READY, OUTPUT);
    pinMode(PIN_BUZZER, OUTPUT);
    
    digitalWrite(PIN_LED_INTERNAL, HIGH); // Off
    digitalWrite(PIN_LED_A, LOW);
    digitalWrite(PIN_LED_B, LOW);
    digitalWrite(PIN_LED_READY, LOW);
    digitalWrite(PIN_BUZZER, LOW);

    // Initialize Config
    cfgManager.begin();

    // CHECK FOR SETUP MODE (Wait a bit for sensors to stabilize)
    delay(100);
    if (digitalRead(PIN_SENSOR_LEFT) == LOW && digitalRead(PIN_SENSOR_RIGHT) == LOW) {
        LOG("!!! SETUP MODE TRIGGERED !!!");
        setupMode = true;
        startSetupMode();
        return; 
    }
    
    // Normal mode
    connectWiFi();
}

// ============================================================
//  MAIN LOOP
// ============================================================

void loop() {
    if (setupMode) {
        dnsServer.processNextRequest();
        server.handleClient();
        
        digitalWrite(PIN_LED_READY, HIGH); // Steady ON for AP Mode
        
        static unsigned long lastSetupBlink = 0;
        if (millis() - lastSetupBlink > 200) {
            lastSetupBlink = millis();
            bool s = digitalRead(PIN_LED_A);
            digitalWrite(PIN_LED_A, !s);
            digitalWrite(PIN_LED_B, s);
        }
        return;
    }

    maintainWiFi();
    
    unsigned long now = millis();
    if (now - lastAppCheck >= APP_CHECK_INTERVAL) {
        lastAppCheck = now;
        checkAppConnection();
    }
    
    updateReadyLED();
    updateFeedback();
    updateBuzzer();
    readSensors();
    processButtons();
}

// ============================================================
//  WIFI MANAGEMENT
// ============================================================

void connectWiFi() {
    if (cfgManager.config.wifi_ssid == "") {
        LOG_ERR("No WiFi SSID configured.");
        return;
    }

    LOG_VAL("Connecting to", cfgManager.config.wifi_ssid);
    WiFi.mode(WIFI_STA);
    WiFi.begin(cfgManager.config.wifi_ssid.c_str(), cfgManager.config.wifi_pass.c_str());
    isWifiConnecting = true;
}

void maintainWiFi() {
    static bool lastConnected = false;
    bool connected = (WiFi.status() == WL_CONNECTED);

    if (connected) {
        if (!lastConnected) {
            LOG_VAL("Connected! IP", WiFi.localIP().toString());
            digitalWrite(PIN_LED_INTERNAL, LOW); // LED on = connected
            isWifiConnecting = false;
            lastConnected = true;
            checkAppConnection(); // Verify app immediately on connection
        }
    } else {
        if (lastConnected) {
            LOG_ERR("WiFi lost!");
            digitalWrite(PIN_LED_INTERNAL, HIGH);
            appReady = false;
            lastConnected = false;
        }

        unsigned long now = millis();
        // Internal LED Blinking while connecting
        if (now - lastInternalBlink >= 250) {
            lastInternalBlink = now;
            internalLedState = !internalLedState;
            digitalWrite(PIN_LED_INTERNAL, internalLedState);
        }

        if (now - lastWifiCheck >= WIFI_RETRY_MS) {
            lastWifiCheck = now;
            LOG("Attempting WiFi reconnection...");
            WiFi.begin(cfgManager.config.wifi_ssid.c_str(), cfgManager.config.wifi_pass.c_str());
            isWifiConnecting = true;
        }
    }
}

// ============================================================
//  APP CONNECTION CHECK
// ============================================================

void checkAppConnection() {
    if (WiFi.status() != WL_CONNECTED) {
        appReady = false;
        digitalWrite(PIN_LED_READY, LOW);
        return;
    }

    WiFiClient client;
    client.setTimeout(1000);
    
    if (client.connect(cfgManager.config.android_ip.c_str(), cfgManager.config.android_port)) {
        if (!appReady) {
            LOG(">>> Padle Score App is READY!");
            playReadyMelody();
        }
        appReady = true;
        client.stop();
    } else {
        if (appReady) {
            LOG_ERR(">>> Padle Score App DISCONNECTED!");
            triggerBuzzerAsync(500); 
        }
        appReady = false;
    }
}

// ============================================================
//  SENSORS & BUTTONS
// ============================================================

void triggerBuzzerAsync(int durationMs, int count) {
    bzState.active = true;
    bzState.duration = durationMs;
    bzState.count = count;
    bzState.currentCount = 0;
    bzState.startTime = 0; // Trigger immediate start in update
    bzState.isOn = false;
}

void updateBuzzer() {
    if (!bzState.active) return;

    unsigned long now = millis();
    if (!bzState.isOn) {
        if (bzState.currentCount < bzState.count) {
            if (bzState.startTime == 0 || now - bzState.startTime >= 50) { // 50ms pause between beeps
                digitalWrite(PIN_BUZZER, HIGH);
                bzState.isOn = true;
                bzState.startTime = now;
            }
        } else {
            bzState.active = false;
        }
    } else {
        if (now - bzState.startTime >= (unsigned long)bzState.duration) {
            digitalWrite(PIN_BUZZER, LOW);
            bzState.isOn = false;
            bzState.startTime = now;
            bzState.currentCount++;
        }
    }
}

void readSensors() {
    unsigned long now = millis();
    
    bool leftRaw = digitalRead(PIN_SENSOR_LEFT);
    if (leftRaw != leftLastState) leftDebounceTime = now;
    if ((now - leftDebounceTime) > DEBOUNCE_MS) {
        bool newState = (leftRaw == LOW);
        if (newState != leftTriggered) {
            leftTriggered = newState;
            if (leftTriggered) {
                leftTriggerStart = now;
                leftCommandSent = false;
                LOG("LEFT sensor: TRIGGERED");
                triggerBuzzerAsync(80);
            }
        }
    }
    leftLastState = leftRaw;
    
    bool rightRaw = digitalRead(PIN_SENSOR_RIGHT);
    if (rightRaw != rightLastState) rightDebounceTime = now;
    if ((now - rightDebounceTime) > DEBOUNCE_MS) {
        bool newState = (rightRaw == LOW);
        if (newState != rightTriggered) {
            rightTriggered = newState;
            if (rightTriggered) {
                rightTriggerStart = now;
                rightCommandSent = false;
                LOG("RIGHT sensor: TRIGGERED");
                triggerBuzzerAsync(80);
            }
        }
    }
    rightLastState = rightRaw;

    if (!isBlinking) {
        digitalWrite(PIN_LED_A, leftTriggered ? HIGH : LOW);
        digitalWrite(PIN_LED_B, rightTriggered ? HIGH : LOW);
    }
}

void processButtons() {
    unsigned long now = millis();
    if (now - lastSendTime < SEND_COOLDOWN_MS) return;
    
    if (leftTriggered && rightTriggered) {
        if (dualHoldStart == 0) dualHoldStart = now;
        if (!dualCommandSent && (now - dualHoldStart >= LONG_PRESS_MIN)) {
            LOG(">>> DUAL LONG PRESS -> RESET");
            sendCommand("RESET");
            dualCommandSent = true;
            leftCommandSent = true;
            rightCommandSent = true;
        }
        return;
    } else {
        dualHoldStart = 0;
    }
    
    if (!leftTriggered && !rightTriggered) dualCommandSent = false;
    
    if (!leftTriggered && leftTriggerStart > 0 && !leftCommandSent) {
        unsigned long duration = now - leftTriggerStart;
        if (!rightTriggered) {
            if (duration < SHORT_PRESS_MAX) sendCommand("A_PLUS");
        }
        leftCommandSent = true;
        leftTriggerStart = 0;
    }
    
    if (leftTriggered && !leftCommandSent && !rightTriggered) {
        if (now - leftTriggerStart >= LONG_PRESS_MIN) {
            sendCommand("A_MINUS");
            leftCommandSent = true;
        }
    }
    
    if (!rightTriggered && rightTriggerStart > 0 && !rightCommandSent) {
        unsigned long duration = now - rightTriggerStart;
        if (!leftTriggered) {
            if (duration < SHORT_PRESS_MAX) sendCommand("B_PLUS");
        }
        rightCommandSent = true;
        rightTriggerStart = 0;
    }
    
    if (rightTriggered && !rightCommandSent && !leftTriggered) {
        if (now - rightTriggerStart >= LONG_PRESS_MIN) {
            sendCommand("B_MINUS");
            rightCommandSent = true;
        }
    }
}

void sendCommand(const char* cmd) {
    bool feedA = strstr(cmd, "A_") || strstr(cmd, "RESET");
    bool feedB = strstr(cmd, "B_") || strstr(cmd, "RESET");
    
    String finalCmd = cmd;
    if (cfgManager.config.swap_sensors) {
        if (strstr(cmd, "A_")) finalCmd.replace("A_", "B_");
        else if (strstr(cmd, "B_")) finalCmd.replace("B_", "A_");
    }

    if (WiFi.status() != WL_CONNECTED || !appReady) {
        LOG_ERR("System NOT READY (WiFi or App disconnected)");
        startFeedback(2, true, true, false); // Berkedip pada LED A dan B bersamaan (Type 2 = Long Error)
        triggerBuzzerAsync(400, 1);
        return;
    }

    if (feedA) digitalWrite(PIN_LED_A, HIGH);
    if (feedB) digitalWrite(PIN_LED_B, HIGH);
    
    String url = "http://" + cfgManager.config.android_ip + ":" + String(cfgManager.config.android_port) + "/cmd?c=" + finalCmd;
    LOG_VAL("Sending", url);
    
    bool success = false;
    for (int attempt = 1; attempt <= HTTP_RETRIES; attempt++) {
        HTTPClient http;
        http.begin(wifiClient, url);
        http.setTimeout(HTTP_TIMEOUT_MS);
        int httpCode = http.GET();
        if (httpCode == 200) {
            success = true;
            http.end();
            break;
        }
        http.end();
        if (attempt < HTTP_RETRIES) delay(100);
    }
    
    if (success) startFeedback(0, feedA, feedB, true);
    else {
        LOG_ERR("Command failed!");
        startFeedback(1, feedA, feedB, true);
        triggerBuzzerAsync(500);
    }
    lastSendTime = millis();
}

// ============================================================
//  FEEDBACK
// ============================================================

void startFeedback(int type, bool a, bool b, bool ready) {
    feedback.active = true;
    feedback.type = type;
    feedback.ledA = a;
    feedback.ledB = b;
    feedback.readyLed = ready;
    feedback.step = 0;
    feedback.startTime = millis();
    isBlinking = true;
    if (type == 0) triggerBuzzerAsync(40, 3);
}

void updateFeedback() {
    if (!feedback.active) return;

    unsigned long now = millis();
    int interval = (feedback.type == 0) ? 60 : 200;
    int maxSteps = (feedback.type == 2) ? 12 : 6; // Type 2 (Long Error) blinks twice as long

    if (now - feedback.startTime >= (unsigned long)interval) {
        feedback.startTime = now;
        feedback.step++;

        if (feedback.step >= maxSteps) {
            feedback.active = false;
            isBlinking = false;
            // Ensure LEDs are state-correct after blink
            if (feedback.ledA) digitalWrite(PIN_LED_A, LOW);
            if (feedback.ledB) digitalWrite(PIN_LED_B, LOW);
            return;
        }

        bool ledOn = (feedback.step % 2 == 0);
        if (feedback.ledA) digitalWrite(PIN_LED_A, ledOn);
        if (feedback.ledB) digitalWrite(PIN_LED_B, ledOn);
        if (feedback.readyLed) digitalWrite(PIN_LED_READY, ledOn);
    }
}

void playReadyMelody() {
    // Keeping this simple for now, but async melody would need a sequence
    triggerBuzzerAsync(100, 2); 
}

void updateReadyLED() {
    if (WiFi.status() != WL_CONNECTED || !appReady) currentLEDState = LED_FAST;
    else currentLEDState = LED_1_8;

    if (isBlinking) return; // Let feedback handle Ready LED if active

    unsigned long now = millis();
    static unsigned long lastToggle = 0;
    switch (currentLEDState) {
        case LED_1_8:
            {
                bool currentState = digitalRead(PIN_LED_READY);
                unsigned long interval = currentState ? BLINK_1_8_ON_MS : BLINK_1_8_OFF_MS;
                if (now - lastToggle >= interval) {
                    lastToggle = now;
                    digitalWrite(PIN_LED_READY, !currentState);
                }
            }
            break;
        case LED_FAST:
            if (now - lastToggle >= BLINK_FAST_MS) {
                lastToggle = now;
                digitalWrite(PIN_LED_READY, !digitalRead(PIN_LED_READY));
            }
            break;
    }
}

// ============================================================
//  SETUP MODE
// ============================================================

void startSetupMode() {
    WiFi.mode(WIFI_AP);
    WiFi.softAP("Padle-Score-Setup");
    LOG_VAL("AP IP", WiFi.softAPIP().toString());
    dnsServer.start(53, "*", WiFi.softAPIP());
    server.on("/", HTTP_GET, handleRoot);
    server.on("/save", HTTP_POST, handleSave);
    server.onNotFound(handleNotFound);
    server.begin();
    LOG("Captive Portal Started.");
}

void handleRoot() {
    String html = FPSTR(CONFIG_HTML);
    // Replace placeholders with current values
    html.replace("id=\"ssd\"", "id=\"ssd\" value=\"" + cfgManager.config.wifi_ssid + "\"");
    html.replace("id=\"pw\"", "id=\"pw\" value=\"" + cfgManager.config.wifi_pass + "\"");
    html.replace("id=\"ip\"", "id=\"ip\" value=\"" + cfgManager.config.android_ip + "\"");
    html.replace("id=\"pt\" value=\"8888\"", "id=\"pt\" value=\"" + String(cfgManager.config.android_port) + "\"");
    
    String swapSel = cfgManager.config.swap_sensors ? "selected" : "";
    html.replace("<option value=\"1\">", "<option value=\"1\" " + swapSel + ">");
    
    server.send(200, "text/html", html);
}

void handleSave() {
    if (server.hasArg("ssd")) cfgManager.config.wifi_ssid = server.arg("ssd");
    if (server.hasArg("pw")) cfgManager.config.wifi_pass = server.arg("pw");
    if (server.hasArg("ip")) cfgManager.config.android_ip = server.arg("ip");
    if (server.hasArg("pt")) cfgManager.config.android_port = server.arg("pt").toInt();
    if (server.hasArg("swp")) cfgManager.config.swap_sensors = (server.arg("swp") == "1");
    cfgManager.saveConfig();
    server.send(200, "text/html", FPSTR(SAVE_HTML));
    delay(2000);
    ESP.restart();
}

void handleNotFound() {
    server.sendHeader("Location", "/", true);
    server.send(302, "text/plain", "");
}
