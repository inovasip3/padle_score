package com.padelboard

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages match results history.
 * Stores match results in a JSON file in internal storage.
 */
class HistoryManager(private val context: Context) {

    private val historyFile = File(context.filesDir, "match_history.json")
    private val MAX_HISTORY = 50

    fun saveMatch(
        teamA: String, teamB: String,
        setsA: Int, setsB: Int,
        gamesA: Int, gamesB: Int,
        scoreA: String, scoreB: String
    ) {
        try {
            val history = getHistoryInternal()
            
            val match = JSONObject().apply {
                put("date", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()))
                put("teamA", teamA)
                put("teamB", teamB)
                put("setsA", setsA)
                put("setsB", setsB)
                put("gamesA", gamesA)
                put("gamesB", gamesB)
                put("scoreA", scoreA)
                put("scoreB", scoreB)
            }

            // Add to front
            val newList = JSONArray()
            newList.put(match)
            for (i in 0 until Math.min(history.length(), MAX_HISTORY - 1)) {
                newList.put(history.get(i))
            }

            historyFile.writeText(newList.toString())
        } catch (e: Exception) {
            Log.e("HistoryManager", "Failed to save match history", e)
        }
    }

    fun getHistory(): String {
        return getHistoryInternal().toString()
    }

    private fun getHistoryInternal(): JSONArray {
        return if (historyFile.exists()) {
            try {
                JSONArray(historyFile.readText())
            } catch (e: Exception) {
                JSONArray()
            }
        } else {
            JSONArray()
        }
    }

    fun clearHistory() {
        if (historyFile.exists()) historyFile.delete()
    }
}
