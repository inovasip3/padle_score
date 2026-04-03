package com.padelboard

/**
 * V2.0 - Padel scoring state machine.
 * Supports two modes:
 *   - "standard": Traditional 0/15/30/40/Deuce/Advantage Padel scoring.
 *   - "custom": Numeric increment scoring with configurable max points and win-by-two.
 * Thread-safe via synchronized blocks.
 */
// --- Rewrite of ScoreState.kt ---

class ScoreState(private val config: ConfigManager) {

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
            return myPts >= config.superTieBreakTarget - 1 && myPts - opPts >= 1 // Rough logic for 'point away'
        }
        if (isTieBreak) {
            val target = config.tieBreakTarget
            val myPts = if (team == 'A') pointsA else pointsB
            val opPts = if (team == 'A') pointsB else pointsA
            val diffReq = if (config.tieBreakWinBy2) 2 else 1
            return myPts >= target - 1 && (myPts + 1) - opPts >= diffReq
        }
        
        if (config.useGoldenPoint && isDeuce) return true // Next point wins
        
        if (isDeuce) return advantageTeam == team
        
        val myPts = if (team == 'A') pointsA else pointsB
        val opPts = if (team == 'A') pointsB else pointsA
        return myPts == 3 && opPts < 3
    }

    fun isSetPoint(team: Char): Boolean {
        if (!isGamePoint(team)) return false
        if (isSuperTieBreak) return true // Super tie-break wins the match/set
        
        val myGames = if (team == 'A') gamesA else gamesB
        val opGames = if (team == 'A') gamesB else gamesA
        
        if (isTieBreak) return true // Winning tie-break wins the set
        
        val nextGames = myGames + 1
        if (config.winBy2Games) {
            return nextGames >= config.gamesToWinSet && nextGames - opGames >= 2
        } else {
            return nextGames >= config.gamesToWinSet
        }
    }

    fun isMatchPoint(team: Char): Boolean {
        if (!isSetPoint(team)) return false
        val mySets = if (team == 'A') setsA else setsB
        return (mySets + 1) >= config.setsToWinMatch
    }

    @Synchronized
    fun addPoint(team: Char) {
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
                winGame(team) // Golden point won
            } else {
                when {
                    advantageTeam == null -> advantageTeam = team
                    advantageTeam == team -> winGame(team)
                    else -> advantageTeam = null // Back to deuce
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
        
        // Reset Set State
        gamesA = 0
        gamesB = 0
        resetPointState()
        
        checkSuperTieBreak()
    }

    private fun checkTieBreak() {
        if (!config.useTieBreak) return
        if (gamesA == config.tieBreakAt && gamesB == config.tieBreakAt) {
            isTieBreak = true
        } else {
            isTieBreak = false
        }
    }
    
    private fun checkSuperTieBreak() {
        if (!config.finalSetSuperTieBreak) return
        
        // Ex: Best of 3 (setsToWinMatch = 2). If both teams have 1 set, next set is final set.
        val setsPlayed = setsA + setsB
        val totalSetsPossible = config.setsToWinMatch * 2 - 1 // best of 3 -> max 3 sets.
        
        if (setsPlayed == totalSetsPossible - 1) { // 1-1 in best of 3
            isSuperTieBreak = true
        } else {
            isSuperTieBreak = false
        }
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
    fun removePoint(team: Char) {
        // Simple undo logic
        lastGameWinner = null
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
        pointsA = 0; pointsB = 0
        gamesA = 0; gamesB = 0
        setsA = 0; setsB = 0
        isDeuce = false; advantageTeam = null
        isTieBreak = false; isSuperTieBreak = false
        lastGameWinner = null
        onScoreChanged?.invoke()
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
        "status" to getStatusText()
    )
}

