#ifndef CONFIG_H
#define CONFIG_H

#include <Arduino.h>

// ============================================================
//  PIN DEFINITIONS
// ============================================================
#define PIN_SENSOR_LEFT   5     // GPIO5 (D1) - IR sensor for Team A
#define PIN_SENSOR_RIGHT  4     // GPIO4 (D2) - IR sensor for Team B
#define PIN_LED_INTERNAL  2     // GPIO2 (D4) - Built-in LED (active LOW)
#define PIN_LED_A         12    // GPIO12 (D6) - Team A Success LED
#define PIN_LED_B         13    // GPIO13 (D7) - Team B Success LED
#define PIN_LED_READY     15    // GPIO15 (D8) - App Connection LED
#define PIN_BUZZER        14    // GPIO14 (D5) - Active Buzzer (active HIGH)

// ============================================================
//  TIMING CONSTANTS
// ============================================================
#define SHORT_PRESS_MAX   500     
#define LONG_PRESS_MIN    1500    
#define DUAL_PRESS_WINDOW 300     
#define DEBOUNCE_MS       50      
#define SEND_COOLDOWN_MS  500     
#define WIFI_RETRY_MS     10000   
#define HTTP_TIMEOUT_MS   2000    
#define HTTP_RETRIES      2       

// LED Blinking Intervals
#define BLINK_FAST_MS     150
#define BLINK_1_8_ON_MS   30      // Short flash
#define BLINK_1_8_OFF_MS  5000    // 5 second delay

#define APP_CHECK_INTERVAL 5000

// ============================================================
//  VERSION
// ============================================================
#define VERSION "v2.2.0"

// ============================================================
//  DEFAULTS
// ============================================================
#define DEFAULT_WIFI_SSID    "tjiptohome"
#define DEFAULT_WIFI_PASS    "chepotz72"
#define DEFAULT_ANDROID_IP   "192.168.2.106"
#define DEFAULT_ANDROID_PORT 8888

#endif
