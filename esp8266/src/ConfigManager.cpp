#include "ConfigManager.h"
#include "config.h"

ConfigManager::ConfigManager() {
}

bool ConfigManager::begin() {
    if (!LittleFS.begin()) {
        Serial.println("An Error has occurred while mounting LittleFS");
        return false;
    }
    return loadConfig();
}

void ConfigManager::setDefaults() {
    // Default values if config file doesn't exist or is invalid
    config.wifi_ssid = DEFAULT_WIFI_SSID;
    config.wifi_pass = DEFAULT_WIFI_PASS;
    config.android_ip = DEFAULT_ANDROID_IP;
    config.android_port = DEFAULT_ANDROID_PORT;
    config.swap_sensors = false;
}

bool ConfigManager::loadConfig() {
    File configFile = LittleFS.open(configFilePath, "r");
    if (!configFile) {
        Serial.println("Failed to open config file for reading. Using defaults.");
        setDefaults();
        return false;
    }

    JsonDocument doc;
    DeserializationError error = deserializeJson(doc, configFile);
    configFile.close();

    if (error) {
        Serial.println("Failed to parse config file. Using defaults.");
        setDefaults();
        return false;
    }

    config.wifi_ssid = doc["wifi_ssid"] | "";
    config.wifi_pass = doc["wifi_pass"] | "";
    config.android_ip = doc["android_ip"] | "192.168.2.106";
    config.android_port = doc["android_port"] | 8888;
    config.swap_sensors = doc["swap_sensors"] | false;

    Serial.println("Config loaded successfully.");
    return true;
}

bool ConfigManager::saveConfig() {
    JsonDocument doc;
    doc["wifi_ssid"] = config.wifi_ssid;
    doc["wifi_pass"] = config.wifi_pass;
    doc["android_ip"] = config.android_ip;
    doc["android_port"] = config.android_port;
    doc["swap_sensors"] = config.swap_sensors;

    File configFile = LittleFS.open(configFilePath, "w");
    if (!configFile) {
        Serial.println("Failed to open config file for writing");
        return false;
    }

    if (serializeJson(doc, configFile) == 0) {
        Serial.println("Failed to write to file");
        configFile.close();
        return false;
    }

    configFile.close();
    Serial.println("Config saved successfully.");
    return true;
}

ConfigManager cfgManager;
