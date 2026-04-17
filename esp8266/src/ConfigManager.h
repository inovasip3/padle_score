#ifndef CONFIG_MANAGER_H
#define CONFIG_MANAGER_H

#include <Arduino.h>
#include <ArduinoJson.h>
#include <LittleFS.h>

struct Config {
    String wifi_ssid;
    String wifi_pass;
    String android_ip;
    int android_port;
    bool swap_sensors;
};

class ConfigManager {
public:
    ConfigManager();
    bool begin();
    bool loadConfig();
    bool saveConfig();
    void setDefaults();

    Config config;

private:
    const char* configFilePath = "/config.json";
};

extern ConfigManager cfgManager;

#endif
