#ifndef CONFIG_PORTAL_H
#define CONFIG_PORTAL_H

#include <ESP8266WebServer.h>

const char CONFIG_HTML[] PROGMEM = R"=====(
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Padle Score Setup</title>
    <style>
        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #1a1a1a; color: #f0f2f5; margin: 0; padding: 20px; display: flex; justify-content: center; align-items: center; min-height: 100vh; }
        .card { background: #262626; padding: 30px; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.5); width: 100%; max-width: 400px; border: 1px solid #333; }
        h2 { margin-top: 0; color: #007bff; text-align: center; }
        .form-group { margin-bottom: 15px; }
        label { display: block; margin-bottom: 5px; font-weight: 600; font-size: 14px; color: #aaa; }
        input, select { width: 100%; padding: 10px; border: 1px solid #444; border-radius: 6px; box-sizing: border-box; font-size: 16px; background: #333; color: white; }
        input:focus { border-color: #007bff; outline: none; box-shadow: 0 0 0 2px rgba(0,123,255,0.2); }
        button { width: 100%; padding: 12px; background: #007bff; color: white; border: none; border-radius: 6px; font-size: 16px; font-weight: 600; cursor: pointer; transition: background 0.2s; margin-top: 10px; }
        button:hover { background: #0056b3; }
        .footer { text-align: center; margin-top: 20px; font-size: 12px; color: #666; }
    </style>
</head>
<body>
    <div class="card">
        <h2>Padle Score Setup</h2>
        <form action="/save" method="POST">
            <div class="form-group">
                <label>1. WiFi SSID</label>
                <input type="text" name="ssd" id="ssd" required>
            </div>
            <div class="form-group">
                <label>2. WiFi Password</label>
                <input type="password" name="pw" id="pw">
            </div>
            <div class="form-group">
                <label>3. TV Box IP Address</label>
                <input type="text" name="ip" id="ip" required>
            </div>
            <div class="form-group">
                <label>4. TV Box Port</label>
                <input type="number" name="pt" id="pt" value="8888" required>
            </div>
            <div class="form-group">
                <label>5. Swap Team A & B</label>
                <select name="swp" id="swp">
                    <option value="0">No (Normal)</option>
                    <option value="1">Yes (Swapped)</option>
                </select>
            </div>
            <button type="submit">SAVE SETTINGS</button>
        </form>
        <div class="footer">Device will restart after saving.</div>
    </div>
    <script>
        // Load existing values (will be injected by server)
        // document.getElementById('ssd').value = '...';
    </script>
</body>
</html>
)=====";

const char SAVE_HTML[] PROGMEM = R"=====(
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Saving...</title>
    <style>
        body { font-family: sans-serif; text-align: center; padding: 50px; background: #1a1a1a; color: white; }
        .card { background: #262626; padding: 30px; border-radius: 12px; display: inline-block; box-shadow: 0 4px 12px rgba(0,0,0,0.5); border: 1px solid #333; }
        h2 { color: #28a745; }
    </style>
</head>
<body>
    <div class="card">
        <h2>Settings Saved!</h2>
        <p>Device is restarting...</p>
        <p>Please reconnect to the target WiFi.</p>
    </div>
</body>
</html>
)=====";

#endif
