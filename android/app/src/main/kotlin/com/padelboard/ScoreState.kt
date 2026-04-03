package com.padelboard

/**
 * V2.0 - Padel scoring state machine.
 * Supports two modes:
 *   - "standard": Traditional 0/15/30/40/Deuce/Advantage Padel scoring.
 *   - "custom": Numeric increment scoring with configurable max points and win-by-two.
 * Thread-safe via synchronized blocks.
 */
class ScoreState {

    // Standard Padel point labels
    private val standardLabels = arrayOf("0", "15", "30", "40")

    // Mode reference: set from outside after config load
    var scoringMode: String = "standard"     // "standard" | "custom"
    var customIncrement: Int = 1
    var maxPointsToWin: Int = 11
    var winByTwo: Boolean = true

    // --- Standard mode internals ---
    private var pointsA: Int = 0
    private var pointsB: Int = 0
    private var isDeuce: Boolean = false
    private var advantageTeam: Char? = null

    // --- Custom mode internals ---
    private var customPointsA: Int = 0
    private var customPointsB: Int = 0

    // --- Set/Match counters (shared by both modes) ---
    var setsA: Int = 0
        private set
    var setsB: Int = 0
        private set
    var matchSetsA: Int = 0
        private set
    var matchSetsB: Int = 0
        private set

    var lastGameWinner: Char? = null
        private set

    var onScoreChanged: (() -> Unit)? = null

    // -------------------------------------------------------------------------
    // Public Display API
    // -------------------------------------------------------------------------

    @Synchronized
    fun getScoreDisplayA(): String {
        return if (scoringMode == "standard") {
            if (isDeuce) if (advantageTeam == 'A') "AD" else if (advantageTeam == 'B') "" else "40"
            else standardLabels.getOrElse(pointsA) { "0" }
        } else {
            customPointsA.toString()
        }
    }

    @Synchronized
    fun getScoreDisplayB(): String {
        return if (scoringMode == "standard") {
            if (isDeuce) if (advantageTeam == 'B') "AD" else if (advantageTeam == 'A') "" else "40"
            else standardLabels.getOrElse(pointsB) { "0" }
        } else {
            customPointsB.toString()
        }
    }

    @Synchronized
    fun getStatusText(): String {
        if (scoringMode != "standard") return ""
        return when {
            isDeuce && advantageTeam == null -> "DEUCE"
            isDeuce && advantageTeam == 'A' -> "ADVANTAGE"
            isDeuce && advantageTeam == 'B' -> "ADVANTAGE"
            else -> ""
        }
    }

    fun isGamePoint(team: Char): Boolean {
        return if (scoringMode == "standard") {
            if (isDeuce) advantageTeam == team
            else if (team == 'A') pointsA == 3 && pointsB < 3 else pointsB == 3 && pointsA < 3
        } else {
            val myPts = if (team == 'A') customPointsA else customPointsB
            val opPts = if (team == 'A') customPointsB else customPointsA
            if (winByTwo) {
                myPts + customIncrement >= maxPointsToWin && myPts - opPts >= 0
            } else {
                myPts + customIncrement >= maxPointsToWin
            }
        }
    }

    fun isSetPoint(team: Char): Boolean {
        if (!isGamePoint(team)) return false
        return if (team == 'A') setsA == 5 else setsB == 5
    }

    fun isMatchPoint(team: Char): Boolean {
        if (!isSetPoint(team)) return false
        return if (team == 'A') matchSetsA == 1 else matchSetsB == 1
    }

    // -------------------------------------------------------------------------
    // Point mutation
    // -------------------------------------------------------------------------

    @Synchronized
    fun addPoint(team: Char) {
        lastGameWinner = null
        if (scoringMode == "standard") {
            if (isDeuce) handleDeucePoint(team) else handleStandardPoint(team)
        } else {
            handleCustomPoint(team)
        }
        onScoreChanged?.invoke()
    }

    private fun handleStandardPoint(team: Char) {
        if (team == 'A') {
            if (pointsA < 3) {
                pointsA++
                if (pointsA == 3 && pointsB == 3) isDeuce = true
            } else winGame('A')
        } else {
            if (pointsB < 3) {
                pointsB++
                if (pointsA == 3 && pointsB == 3) isDeuce = true
            } else winGame('B')
        }
    }

    private fun handleDeucePoint(team: Char) {
        when {
            advantageTeam == null -> advantageTeam = team
            advantageTeam == team -> winGame(team)
            else -> advantageTeam = null
        }
    }

    private fun handleCustomPoint(team: Char) {
        if (team == 'A') customPointsA += customIncrement
        else customPointsB += customIncrement

        val myPts = if (team == 'A') customPointsA else customPointsB
        val opPts = if (team == 'A') customPointsB else customPointsA

        val reachedTarget = myPts >= maxPointsToWin
        val clearByTwo = !winByTwo || (myPts - opPts >= 2)

        if (reachedTarget && clearByTwo) {
            winGame(team)
        }
    }

    private fun winGame(team: Char) {
        lastGameWinner = team
        if (team == 'A') setsA++ else setsB++
        if (setsA == 6 || setsB == 6) winSet(team) else resetGame()
    }

    private fun winSet(team: Char) {
        if (team == 'A') matchSetsA++ else matchSetsB++
        setsA = 0; setsB = 0
        resetGame()
    }

    private fun resetGame() {
        pointsA = 0; pointsB = 0
        customPointsA = 0; customPointsB = 0
        isDeuce = false; advantageTeam = null
    }

    @Synchronized
    fun removePoint(team: Char) {
        lastGameWinner = null
        if (scoringMode == "standard") {
            if (isDeuce) {
                if (advantageTeam != null) advantageTeam = null
                else {
                    isDeuce = false
                    if (team == 'A') { pointsA = 2; pointsB = 3 } else { pointsA = 3; pointsB = 2 }
                }
            } else {
                if (team == 'A') { if (pointsA > 0) pointsA-- } else { if (pointsB > 0) pointsB-- }
            }
        } else {
            if (team == 'A') { if (customPointsA > 0) customPointsA -= customIncrement }
            else { if (customPointsB > 0) customPointsB -= customIncrement }
        }
        onScoreChanged?.invoke()
    }

    @Synchronized
    fun reset() {
        pointsA = 0; pointsB = 0
        customPointsA = 0; customPointsB = 0
        setsA = 0; setsB = 0
        matchSetsA = 0; matchSetsB = 0
        isDeuce = false; advantageTeam = null
        lastGameWinner = null
        onScoreChanged?.invoke()
    }

    @Synchronized
    fun toMap(): Map<String, Any?> = mapOf(
        "scoreA" to getScoreDisplayA(),
        "scoreB" to getScoreDisplayB(),
        "setsA" to setsA,
        "setsB" to setsB,
        "matchSetsA" to matchSetsA,
        "matchSetsB" to matchSetsB,
        "isDeuce" to isDeuce,
        "advantage" to advantageTeam?.toString(),
        "status" to getStatusText(),
        "scoringMode" to scoringMode
    )
}
