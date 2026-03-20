package com.padelboard

import fi.iki.elonen.NanoHTTPD

/**
 * Lightweight HTTP server embedded in the app.
 * Listens on the local WiFi IP and handles scoring commands.
 * Designed for <50ms response time.
 */
class HttpCommandServer(
    port: Int,
    private val scoreState: ScoreState,
    private val config: ConfigManager, // Added for config management
    private val onCommand: (String) -> Unit
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val params = session.parms

        return when {
            uri == "/" || uri == "/index.html" -> serveWebUI()
            uri == "/cmd" -> handleCommand(params)
            uri == "/status" -> handleStatus()
            uri == "/config" -> handleConfig(params)
            uri == "/ping" -> newFixedLengthResponse(
                Response.Status.OK, "application/json",
                """{"ok":true,"msg":"pong"}"""
            )
            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND, "application/json",
                """{"ok":false,"error":"not found"}"""
            )
        }.also { response ->
            // CORS headers for flexibility
            response.addHeader("Access-Control-Allow-Origin", "*")
            response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            response.addHeader("Cache-Control", "no-cache")
        }
    }

    private fun handleCommand(params: Map<String, String>): Response {
        val cmd = params["c"] ?: return newFixedLengthResponse(
            Response.Status.BAD_REQUEST, "application/json",
            """{"ok":false,"error":"missing parameter 'c'"}"""
        )

        val validCommands = setOf("A_PLUS", "B_PLUS", "A_MINUS", "B_MINUS", "RESET")
        if (cmd !in validCommands) {
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, "application/json",
                """{"ok":false,"error":"unknown command: $cmd"}"""
            )
        }

        // Execute command
        when (cmd) {
            "A_PLUS" -> scoreState.addPoint('A')
            "B_PLUS" -> scoreState.addPoint('B')
            "A_MINUS" -> scoreState.removePoint('A')
            "B_MINUS" -> scoreState.removePoint('B')
            "RESET" -> {
                scoreState.reset()
                config.teamAName = ConfigManager.DEFAULT_TEAM_A
                config.teamBName = ConfigManager.DEFAULT_TEAM_B
                onCommand("CONFIG_UPDATE")
            }
        }

        // Notify UI thread
        onCommand(cmd)

        // Return current state
        val json = buildJsonResponse(getFullState())
        return newFixedLengthResponse(
            Response.Status.OK, "application/json", json
        )
    }

    private fun handleStatus(): Response {
        val json = buildJsonResponse(getFullState())
        return newFixedLengthResponse(
            Response.Status.OK, "application/json", json
        )
    }
    
    private fun handleConfig(params: Map<String, String>): Response {
        val teamA = params["teamA"]
        if (teamA != null) config.teamAName = teamA
        
        val teamB = params["teamB"]
        if (teamB != null) config.teamBName = teamB
        
        if (teamA != null || teamB != null) {
            onCommand("CONFIG_UPDATE")
        }
        
        val json = buildJsonResponse(getFullState())
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }

    private fun getFullState(): Map<String, Any?> {
        val state = scoreState.toMap().toMutableMap()
        state["teamA"] = config.teamAName
        state["teamB"] = config.teamBName
        return state
    }

    private fun buildJsonResponse(state: Map<String, Any?>): String {
        val sb = StringBuilder()
        sb.append("""{"ok":true,"state":{""")
        val entries = state.entries.toList()
        entries.forEachIndexed { index, (key, value) ->
            sb.append("\"$key\":")
            when (value) {
                is String -> sb.append("\"$value\"")
                is Int -> sb.append(value)
                is Boolean -> sb.append(value)
                null -> sb.append("null")
                else -> sb.append("\"$value\"")
            }
            if (index < entries.size - 1) sb.append(",")
        }
        sb.append("}}")
        return sb.toString()
    }
    
    private fun serveWebUI(): Response {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, touch-action=none">
                <title>Remote Score Editor</title>
                <style>
                    body {
                        background-color: #000;
                        color: #FFF;
                        font-family: monospace;
                        margin: 0;
                        padding: 0;
                        display: flex;
                        flex-direction: column;
                        height: 100vh;
                        user-select: none;
                        -webkit-user-select: none;
                        -webkit-touch-callout: none;
                    }
                    .header {
                        display: flex;
                        justify-content: space-around;
                        padding: 10px;
                        font-size: 24px;
                        font-weight: bold;
                    }
                    .team-name {
                        cursor: pointer;
                        padding: 10px;
                    }
                    .team-a { color: #00FF66; }
                    .team-b { color: #FFA500; }
                    .scores {
                        display: flex;
                        flex: 1;
                        justify-content: space-around;
                        align-items: center;
                    }
                    .score-panel {
                        flex: 1;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        height: 100%;
                    }
                    .score {
                        font-size: 30vw;
                        font-weight: bold;
                    }
                    .sets {
                        font-size: 8vw;
                        color: #888;
                    }
                    .status {
                        position: absolute;
                        bottom: 10%;
                        width: 100%;
                        text-align: center;
                        font-size: 24px;
                    }
                </style>
            </head>
            <body>
                <div class="header">
                    <div id="nameA" class="team-name team-a">TEAM A</div>
                    <div id="nameB" class="team-name team-b">TEAM B</div>
                </div>
                <div class="scores" id="scoresArea">
                    <div class="score-panel" id="panelA">
                        <div class="score team-a" id="scoreA">0</div>
                        <div class="sets">SETS: <span id="setsA">0</span></div>
                    </div>
                    <div class="score-panel" id="panelB">
                        <div class="score team-b" id="scoreB">0</div>
                        <div class="sets">SETS: <span id="setsB">0</span></div>
                    </div>
                </div>
                <div class="status" id="status"></div>
            
                <script>
                    let pointers = 0;
                    let lastDownTime = 0;
                    let touchTimer = null;
                    let isLongPress = false;
                    const LONG_PRESS_MS = 600;
                    let lastTapTime = 0;
                    const TAP_DEBOUNCE_MS = 300;
            
                    // Fetch state
                    async function fetchStatus() {
                        try {
                            const res = await fetch('/status');
                            const data = await res.json();
                            if (data.ok) updateUI(data.state);
                        } catch (e) {}
                    }
                    
                    async function sendCommand(cmd) {
                        try {
                            const res = await fetch('/cmd?c=' + cmd);
                            const data = await res.json();
                            if (data.ok) updateUI(data.state);
                        } catch (e) {}
                    }
                    
                    async function updateConfig(team, newName) {
                        try {
                            const res = await fetch('/config?' + team + '=' + encodeURIComponent(newName));
                            const data = await res.json();
                            if (data.ok) updateUI(data.state);
                        } catch (e) {}
                    }
            
                    function updateUI(state) {
                        document.getElementById('scoreA').innerText = state.scoreA;
                        document.getElementById('scoreB').innerText = state.scoreB;
                        document.getElementById('setsA').innerText = state.setsA;
                        document.getElementById('setsB').innerText = state.setsB;
                        document.getElementById('status').innerText = state.status || "";
                        document.getElementById('nameA').innerText = state.teamA;
                        document.getElementById('nameB').innerText = state.teamB;
                    }
            
                    setInterval(fetchStatus, 1000);
                    fetchStatus();
                    
                    document.getElementById('nameA').onclick = () => {
                        let name = prompt("Enter Name for Team A", document.getElementById('nameA').innerText);
                        if (name) updateConfig('teamA', name);
                    };
                    document.getElementById('nameB').onclick = () => {
                        let name = prompt("Enter Name for Team B", document.getElementById('nameB').innerText);
                        if (name) updateConfig('teamB', name);
                    };
                    
                    const handleTouch = (targetIds, cmdPlus, cmdMinus) => {
                        targetIds.forEach(id => {
                            const el = document.getElementById(id);
                            el.addEventListener('touchstart', (e) => {
                                pointers = e.touches.length;
                                if (pointers >= 2) {
                                    clearTimeout(touchTimer);
                                    touchTimer = setTimeout(() => { sendCommand('RESET'); isLongPress = true; }, 1000);
                                    return;
                                }
                                e.preventDefault();
                                isLongPress = false;
                                clearTimeout(touchTimer);
                                touchTimer = setTimeout(() => {
                                    isLongPress = true;
                                    sendCommand(cmdMinus);
                                }, LONG_PRESS_MS);
                            }, {passive: false});
                            
                            el.addEventListener('touchend', (e) => {
                                e.preventDefault();
                                clearTimeout(touchTimer);
                                if (!isLongPress && pointers < 2) {
                                    let now = Date.now();
                                    if (now - lastTapTime > TAP_DEBOUNCE_MS) {
                                        lastTapTime = now;
                                        sendCommand(cmdPlus);
                                    }
                                }
                            }, {passive: false});
                        });
                    };
                    
                    handleTouch(['panelA', 'scoreA'], 'A_PLUS', 'A_MINUS');
                    handleTouch(['panelB', 'scoreB'], 'B_PLUS', 'B_MINUS');
                </script>
            </body>
            </html>
        """.trimIndent()
        
        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }
}
