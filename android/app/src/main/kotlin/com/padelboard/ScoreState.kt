package com.padelboard

/**
 * V2.1 - Padel scoring state machine.
 * Supports two modes:
 *   - "standard": Traditional 0/15/30/40/Deuce/Advantage Padel scoring.
 *   - Tie-break / Super Tie-break modes.
 *
 * Key improvements in V2.1:
 *   - Full undo history stack (up to 15 moves). undo() restores exact state
 *     including across game/set boundaries — not just point-level estimation.
 *   - Thread-safe: all public mutation methods are @Synchronized.
 */
class ScoreState(private val config: ConfigManager) {

    // ── State Snapshot for Undo History ────────────────────────────────────
    private data class StateSnapshot(
        val pointsA: Int,
        val pointsB: Int,
        val gamesA: Int,
        val gamesB: Int,
        val setsA: Int,
        val setsB: Int,
        val isDeuce: Boolean,
        val advantageTeam: Char?,
        val isTieBreak: Boolean,
        val isSuperTieBreak: Boolean,
        val lastGameWinner: Char?
    )

    private val history = ArrayDeque<StateSnapshot>()
    private val MAX_HISTORY = 15

    private val standardLabels = arrayOf("0", "15", "30", "40")

    var pointsA: Int = 0
        private set
    var pointsB: Int = 0
        private set

    var gamesA: Int = 0
        private set
    var gamesB: Int = 0
        private set

    var setsA: Int = 0
        private set
    var setsB: Int = 0
        private set

    private var isDeuce: Boolean = false
    private var advantageTeam: Char? = null

    var isTieBreak: Boolean = false
        private set
    var isSuperTieBreak: Boolean = false
        private set

    var lastGameWinner: Char? = null
        private set

    var onScoreChanged: (() -> Unit)? = null

    // ── History Helpers ────────────────────────────────────────────────────

    private fun pushHistory() {
        history.addLast(
            StateSnapshot(
                pointsA, pointsB, gamesA, gamesB, setsA, setsB,
                isDeuce, advantageTeam, isTieBreak, isSuperTieBreak, lastGameWinner
            )
        )
        if (history.size > MAX_HISTORY) history.removeFirst()
    }

    private fun restoreSnapshot(snap: StateSnapshot) {
        pointsA = snap.pointsA
        pointsB = snap.pointsB
        gamesA = snap.gamesA
        gamesB = snap.gamesB
        setsA = snap.setsA
        setsB = snap.setsB
        isDeuce = snap.isDeuce
        advantageTeam = snap.advantageTeam
        isTieBreak = snap.isTieBreak
        isSuperTieBreak = snap.isSuperTieBreak
        lastGameWinner = snap.lastGameWinner
    }

    // ── Display ────────────────────────────────────────────────────────────

    @Synchronized
    fun getScoreDisplayA(): String {
        return if (isTieBreak || isSuperTieBreak) {
            pointsA.toString()
        } else {
            if (isDeuce) {
                if (advantageTeam == 'A') "AD" else if (advantageTeam == 'B') "" else "40"
            } else {
                standardLabels.getOrElse(pointsA) { "0" }
            }
        }
    }

    @Synchronized
    fun getScoreDisplayB(): String {
        return if (isTieBreak || isSuperTieBreak) {
            pointsB.toString()
        } else {
            if (isDeuce) {
                if (advantageTeam == 'B') "AD" else if (advantageTeam == 'A') "" else "40"
            } else {
                standardLabels.getOrElse(pointsB) { "0" }
            }
        }
    }

    @Synchronized
    fun getStatusText(): String {
        if (isSuperTieBreak) return "SUPER TIE-BREAK"
        if (isTieBreak) return "TIE-BREAK"
        return when {
            isDeuce && advantageTeam == null -> "DEUCE"
            isDeuce && advantageTeam == 'A' -> "ADVANTAGE"
            isDeuce && advantageTeam == 'B' -> "ADVANTAGE"
            else -> ""
        }
    }

    fun isGamePoint(team: Char): Boolean {
        if (isSuperTieBreak) {
            val myPts = if (team == 'A') pointsA else pointsB
            val opPts = if (team == 'A') pointsB else pointsA
            return myPts >= config.superTieBreakTarget - 1 && myPts - opPts >= 1
        }
        if (isTieBreak) {
            val target = config.tieBreakTarget
            val myPts = if (team == 'A') pointsA else pointsB
            val opPts = if (team == 'A') pointsB else pointsA
            val diffReq = if (config.tieBreakWinBy2) 2 else 1
            return myPts >= target - 1 && (myPts + 1) - opPts >= diffReq
        }
        if (config.useGoldenPoint && isDeuce) return true
        if (isDeuce) return advantageTeam == team
        val myPts = if (team == 'A') pointsA else pointsB
        val opPts = if (team == 'A') pointsB else pointsA
        return myPts == 3 && opPts < 3
    }

    fun isSetPoint(team: Char): Boolean {
        if (!isGamePoint(team)) return false
        if (isSuperTieBreak) return true
        val myGames = if (team == 'A') gamesA else gamesB
        val opGames = if (team == 'A') gamesB else gamesA
        if (isTieBreak) return true
        val nextGames = myGames + 1
        return if (config.winBy2Games) {
            nextGames >= config.gamesToWinSet && nextGames - opGames >= 2
        } else {
            nextGames >= config.gamesToWinSet
        }
    }

    fun isMatchPoint(team: Char): Boolean {
        if (!isSetPoint(team)) return false
        val mySets = if (team == 'A') setsA else setsB
        return (mySets + 1) >= config.setsToWinMatch
    }

    // ── Mutations ──────────────────────────────────────────────────────────

    @Synchronized
    fun addPoint(team: Char) {
        pushHistory() // Save state before any mutation
        lastGameWinner = null
        if (isSuperTieBreak) {
            handleTieBreakPoint(team, config.superTieBreakTarget)
        } else if (isTieBreak) {
            handleTieBreakPoint(team, config.tieBreakTarget)
        } else {
            handleStandardPoint(team)
        }
        onScoreChanged?.invoke()
    }

    /**
     * Undo the last scored point, restoring the full game state exactly.
     * Works across game and set boundaries.
     * @return true if there was a state to undo, false if history is empty
     */
    @Synchronized
    fun undo(): Boolean {
        if (history.isEmpty()) return false
        restoreSnapshot(history.removeLast())
        onScoreChanged?.invoke()
        return true
    }

    /**
     * Direct point removal (for BLE remote fine-tuning).
     * Only adjusts within current game — does NOT cross game/set boundaries.
     * For full accurate undo use undo().
     */
    @Synchronized
    fun removePoint(team: Char) {
        // V2.3: If we are at the start of a game (0-0) and the user wants to "minus" a score,
        // it means they likely want to undo the point that ended the last game.
        if (pointsA == 0 && pointsB == 0 && hasHistory()) {
            undo()
            return
        }

        if (isTieBreak || isSuperTieBreak) {
            if (team == 'A' && pointsA > 0) pointsA--
            if (team == 'B' && pointsB > 0) pointsB--
        } else {
            if (isDeuce) {
                if (advantageTeam != null) {
                    advantageTeam = null
                } else {
                    isDeuce = false
                    if (team == 'A') { pointsA = 2; pointsB = 3 }
                    else { pointsA = 3; pointsB = 2 }
                }
            } else {
                if (team == 'A' && pointsA > 0) pointsA--
                if (team == 'B' && pointsB > 0) pointsB--
            }
        }
        onScoreChanged?.invoke()
    }

    @Synchronized
    fun reset() {
        history.clear()
        pointsA = 0; pointsB = 0
        gamesA = 0; gamesB = 0
        setsA = 0; setsB = 0
        isDeuce = false; advantageTeam = null
        isTieBreak = false; isSuperTieBreak = false
        lastGameWinner = null
        onScoreChanged?.invoke()
    }

    fun hasHistory(): Boolean = history.isNotEmpty()

    // ── Internal helpers ───────────────────────────────────────────────────

    private fun handleTieBreakPoint(team: Char, targetPoints: Int) {
        if (team == 'A') pointsA++ else pointsB++
        val myPts = if (team == 'A') pointsA else pointsB
        val opPts = if (team == 'A') pointsB else pointsA
        val diffReq = if (config.tieBreakWinBy2) 2 else 1
        if (myPts >= targetPoints && (myPts - opPts) >= diffReq) {
            winGame(team)
        }
    }

    private fun handleStandardPoint(team: Char) {
        if (isDeuce) {
            if (config.useGoldenPoint) {
                winGame(team)
            } else {
                when {
                    advantageTeam == null -> advantageTeam = team
                    advantageTeam == team -> winGame(team)
                    else -> advantageTeam = null
                }
            }
            return
        }
        if (team == 'A') {
            if (pointsA < 3) {
                pointsA++
                if (pointsA == 3 && pointsB == 3) isDeuce = true
            } else {
                winGame('A')
            }
        } else {
            if (pointsB < 3) {
                pointsB++
                if (pointsA == 3 && pointsB == 3) isDeuce = true
            } else {
                winGame('B')
            }
        }
    }

    private fun winGame(team: Char) {
        lastGameWinner = team
        if (team == 'A') gamesA++ else gamesB++
        val myGames = if (team == 'A') gamesA else gamesB
        val opGames = if (team == 'A') gamesB else gamesA
        val setWon = if (config.winBy2Games) {
            myGames >= config.gamesToWinSet && (myGames - opGames) >= 2
        } else {
            myGames >= config.gamesToWinSet
        }
        if (setWon) {
            winSet(team)
        } else {
            resetPointState()
            checkTieBreak()
        }
    }

    private fun winSet(team: Char) {
        if (team == 'A') setsA++ else setsB++
        gamesA = 0
        gamesB = 0
        resetPointState()
        checkSuperTieBreak()
    }

    private fun checkTieBreak() {
        isTieBreak = config.useTieBreak &&
                gamesA == config.tieBreakAt && gamesB == config.tieBreakAt
    }

    private fun checkSuperTieBreak() {
        if (!config.finalSetSuperTieBreak) {
            isSuperTieBreak = false
            return
        }
        val setsPlayed = setsA + setsB
        val totalSetsPossible = config.setsToWinMatch * 2 - 1
        isSuperTieBreak = setsPlayed == totalSetsPossible - 1
    }

    private fun resetPointState() {
        pointsA = 0
        pointsB = 0
        isDeuce = false
        advantageTeam = null
        isTieBreak = false
        isSuperTieBreak = false
    }

    @Synchronized
    fun toMap(): Map<String, Any?> = mapOf(
        "scoreA" to getScoreDisplayA(),
        "scoreB" to getScoreDisplayB(),
        "gamesA" to gamesA,
        "gamesB" to gamesB,
        "setsA" to setsA,
        "setsB" to setsB,
        "isDeuce" to isDeuce,
        "advantage" to advantageTeam?.toString(),
        "status" to getStatusText(),
        "canUndo" to hasHistory()
    )
}
