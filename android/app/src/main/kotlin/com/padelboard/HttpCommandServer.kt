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
    private val onCommand: (String) -> Unit
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val params = session.parms

        return when {
            uri == "/cmd" -> handleCommand(params)
            uri == "/status" -> handleStatus()
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
            "RESET" -> scoreState.reset()
        }

        // Notify UI thread
        onCommand(cmd)

        // Return current state
        val state = scoreState.toMap()
        val json = buildJsonResponse(state)
        return newFixedLengthResponse(
            Response.Status.OK, "application/json", json
        )
    }

    private fun handleStatus(): Response {
        val state = scoreState.toMap()
        val json = buildJsonResponse(state)
        return newFixedLengthResponse(
            Response.Status.OK, "application/json", json
        )
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
}
