package com.padelboard

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * V2.3 Professional Settings Dialog
 * Features:
 * - Tab-based interface (User vs Admin)
 * - Admin sub-tabs for deep configuration
 * - Professional dark theme
 * - Integrated Match History
 */
class ConfigDialog(
    private val context: Context,
    private val config: ConfigManager,
    private val onConfigChanged: () -> Unit
) {

    private val historyManager = HistoryManager(context)
    private lateinit var userPresetSpinner: Spinner
    private lateinit var userSoundSwitch: Switch
    private lateinit var userPhotosSwitch: Switch
    
    // Admin Fields
    private lateinit var teamAInput: EditText
    private lateinit var teamBInput: EditText
    private lateinit var goldenPointSwitch: Switch
    private lateinit var gamesToWinSetInput: EditText
    private lateinit var winBy2GamesSwitch: Switch
    private lateinit var useTieBreakSwitch: Switch
    private lateinit var tieBreakAtInput: EditText
    private lateinit var tieBreakTargetInput: EditText
    private lateinit var tieBreakWinBy2Switch: Switch
    private lateinit var bestOfSpinner: Spinner
    private lateinit var finalSetSuperTbSwitch: Switch
    private lateinit var superTbTargetInput: EditText
    private lateinit var colorASpinner: Spinner
    private lateinit var colorBSpinner: Spinner
    private lateinit var fontScaleSpinner: Spinner
    private lateinit var fontTypeSpinner: Spinner
    private lateinit var winEffectSwitch: Switch
    private lateinit var enablePhotosSwitch: Switch
    private lateinit var photoSizeInput: EditText
    private lateinit var photoYPosInput: EditText
    private lateinit var soundSwitch: Switch
    private lateinit var voiceRefSwitch: Switch
    private lateinit var useLoveSwitch: Switch
    private lateinit var httpSwitch: Switch
    private lateinit var bleSwitch: Switch
    private lateinit var shutterSwitch: Switch
    private lateinit var kbAPlusInput: EditText
    private lateinit var kbAMinusInput: EditText
    private lateinit var kbBPlusInput: EditText
    private lateinit var kbBMinusInput: EditText
    private lateinit var kbResetInput: EditText
    private lateinit var allowUserUploadPhotoSwitch: Switch
    private lateinit var portInput: EditText
    private lateinit var settingsPinInput: EditText
    private lateinit var jsonThemeInput: EditText
    private lateinit var showDebugMsgSwitch: Switch
    private lateinit var presetSpinner: Spinner

    // Bluetooth Remote Mode Fields (V2.3)
    private lateinit var remoteModeSpinner: Spinner
    private lateinit var remoteSwapSwitch: Switch
    private lateinit var remoteResetEnabledSwitch: Switch
    private lateinit var remoteLongPressMsInput: EditText
    private lateinit var remoteDualPressMsInput: EditText
    private lateinit var remoteADescText: TextView
    private lateinit var remoteBDescText: TextView

    private val bestOfSetsOptions = listOf(1, 2, 3)
    private val adminPresetKeys = listOf("standard", "golden_point", "fast_short", "custom_1", "custom_2", "custom_3")

    fun showConfigDialog() {
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF111111.toInt())
        }

        val topTabsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(10, 20, 10, 20)
            setBackgroundColor(0xFF1A1A1A.toInt())
            gravity = Gravity.CENTER
        }
        rootLayout.addView(topTabsLayout)

        val contentScroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
        }
        val contentRoot = FrameLayout(context).apply {
            setPadding(50, 30, 50, 40)
        }
        contentScroll.addView(contentRoot)
        rootLayout.addView(contentScroll)

        val userContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val adminLoginContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(30, 50, 30, 50)
        }
        val adminPanelContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        contentRoot.addView(userContainer)
        contentRoot.addView(adminLoginContainer)
        contentRoot.addView(adminPanelContainer)

        val btnUserTab = createTabButton("👤 PENGGUNA", true)
        val btnAdminTab = createTabButton("🔒 ADMIN", false)
        topTabsLayout.addView(btnUserTab)
        topTabsLayout.addView(btnAdminTab)

        var isAdminUnlocked = false
        btnUserTab.setOnClickListener {
            updateTabStyles(btnUserTab, btnAdminTab)
            userContainer.visibility = View.VISIBLE
            adminLoginContainer.visibility = View.GONE
            adminPanelContainer.visibility = View.GONE
        }

        btnAdminTab.setOnClickListener {
            updateTabStyles(btnAdminTab, btnUserTab)
            userContainer.visibility = View.GONE
            if (isAdminUnlocked) {
                adminLoginContainer.visibility = View.GONE
                adminPanelContainer.visibility = View.VISIBLE
            } else {
                adminLoginContainer.visibility = View.VISIBLE
                adminPanelContainer.visibility = View.GONE
            }
        }

        setupAdminLogin(adminLoginContainer) {
            isAdminUnlocked = true
            adminLoginContainer.visibility = View.GONE
            adminPanelContainer.visibility = View.VISIBLE
        }

        setupUserUI(userContainer)
        setupAdminUI(adminPanelContainer)

        AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("⚙️ Padle Score v2.3 Settings")
            .setView(rootLayout)
            .setPositiveButton("Save") { _, _ ->
                saveAllSettings(isAdminUnlocked)
                onConfigChanged()
                Toast.makeText(context, "✅ Settings saved!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createTabButton(text: String, active: Boolean) = TextView(context).apply {
        this.text = text
        textSize = 16f
        setPadding(40, 20, 40, 20)
        setTextColor(if (active) Color.WHITE else 0xFF888888.toInt())
        setBackgroundColor(if (active) 0xFF444444.toInt() else Color.TRANSPARENT)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        gravity = Gravity.CENTER
    }

    private fun updateTabStyles(active: TextView, inactive: TextView) {
        active.setTextColor(Color.WHITE)
        active.setBackgroundColor(0xFF444444.toInt())
        inactive.setTextColor(0xFF888888.toInt())
        inactive.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun setupAdminLogin(container: LinearLayout, onUnlock: () -> Unit) {
        val pinInput = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Enter PIN"
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF888888.toInt())
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(40, 30, 40, 30)
            setBackgroundColor(0xFF222222.toInt())
        }
        val btnUnlock = Button(context).apply {
            text = "UNLOCK ADMIN"
            setBackgroundColor(0xFF00FF66.toInt())
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 30, 0, 0)
            }
            setOnClickListener {
                if (pinInput.text.toString() == config.pin) onUnlock()
                else Toast.makeText(context, "❌ Wrong PIN", Toast.LENGTH_SHORT).show()
            }
        }
        container.addView(pinInput)
        container.addView(btnUnlock)
    }

    private fun setupUserUI(container: LinearLayout) {
        addSectionHeader("👥 PENGGUNA", container)
        createLabel("Memilih Mode Skoring Preset", container)
        val presets = listOf("Standard Padel", "Golden Point", "Fast / Short", "Custom 1", "Custom 2", "Custom 3")
        userPresetSpinner = createSpinner(presets, adminPresetKeys.indexOf(config.scoringPreset).coerceAtLeast(0), container)
        
        userSoundSwitch = createSwitch("Enable/Mute Suara (Sound Effects)", config.soundEnabled, container)
        userPhotosSwitch = createSwitch("Enable/Hide Foto", config.enablePhotos, container)
        createLabel("Tip: Gunakan Web Remote untuk upload foto tim.", container)
    }

    private fun setupAdminUI(container: LinearLayout) {
        val adminTabsLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val hScrollView = HorizontalScrollView(context).apply { isFillViewport = true; addView(adminTabsLayout) }
        container.addView(hScrollView)

        val tabNames = listOf("Teams", "Rules", "Visuals", "Photos", "Audio", "Remote", "System", "History")
        val tabViews = mutableListOf<LinearLayout>()
        val tabButtons = mutableListOf<TextView>()

        tabNames.forEachIndexed { index, name ->
            val sectionView = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (index == 0) View.VISIBLE else View.GONE
            }
            tabViews.add(sectionView)
            container.addView(sectionView)
            
            val tabBtn = TextView(context).apply {
                text = name.uppercase()
                textSize = 12f
                setPadding(25, 15, 25, 15)
                setTextColor(if (index == 0) Color.WHITE else 0xFF888888.toInt())
                setBackgroundColor(if (index == 0) 0xFF555555.toInt() else 0xFF222222.toInt())
                setOnClickListener {
                    tabButtons.forEachIndexed { i, btn ->
                        btn.setTextColor(if (i == index) Color.WHITE else 0xFF888888.toInt())
                        btn.setBackgroundColor(if (i == index) 0xFF555555.toInt() else 0xFF222222.toInt())
                        tabViews[i].visibility = if (i == index) View.VISIBLE else View.GONE
                    }
                }
            }
            adminTabsLayout.addView(tabBtn)
            tabButtons.add(tabBtn)
        }

        setupTeamsTab(tabViews[0])
        setupRulesTab(tabViews[1])
        setupVisualsTab(tabViews[2])
        setupPhotosTab(tabViews[3])
        setupAudioTab(tabViews[4])
        setupRemoteTab(tabViews[5])
        setupSystemTab(tabViews[6])
        setupHistoryTab(tabViews[7])
    }

    private fun setupTeamsTab(root: LinearLayout) {
        addSectionHeader("👥 Team", root)
        teamAInput = createInput("Team A Name", config.teamAName, root)
        teamBInput = createInput("Team B Name", config.teamBName, root)
    }

    private fun setupRulesTab(root: LinearLayout) {
        addSectionHeader("🏓 Scoring Preset", root)
        val presets = listOf("Standard Padel", "Golden Point", "Fast / Short", "Custom 1", "Custom 2", "Custom 3")
        presetSpinner = createSpinner(presets, adminPresetKeys.indexOf(config.scoringPreset).coerceAtLeast(0), root)
        
        addSectionHeader("🎾 POINT RULE", root)
        goldenPointSwitch = createSwitch("Use Golden Point", config.useGoldenPoint, root)

        addSectionHeader("🎾 GAME RULE", root)
        gamesToWinSetInput = createInput("Games to Win Set (4-6)", config.gamesToWinSet.toString(), root, InputType.TYPE_CLASS_NUMBER)
        winBy2GamesSwitch = createSwitch("Must Win By 2 Games", config.winBy2Games, root)

        addSectionHeader("🎾 TIE BREAK", root)
        useTieBreakSwitch = createSwitch("Use Tie Break", config.useTieBreak, root)
        tieBreakAtInput = createInput("Tie Break At", config.tieBreakAt.toString(), root, InputType.TYPE_CLASS_NUMBER)
        tieBreakTargetInput = createInput("Target Point", config.tieBreakTarget.toString(), root, InputType.TYPE_CLASS_NUMBER)
        tieBreakWinBy2Switch = createSwitch("Win By 2 Points", config.tieBreakWinBy2, root)

        addSectionHeader("🎾 SET RULE", root)
        val bestOfOptions = listOf("Best of 1", "Best of 3", "Best of 5")
        bestOfSpinner = createSpinner(bestOfOptions, bestOfSetsOptions.indexOf(config.setsToWinMatch).coerceAtLeast(0), root)
        finalSetSuperTbSwitch = createSwitch("Final Set Super Tie Break", config.finalSetSuperTieBreak, root)
        superTbTargetInput = createInput("Super TB Target", config.superTieBreakTarget.toString(), root, InputType.TYPE_CLASS_NUMBER)

        val btnSaveCustom = Button(context).apply {
            text = "Save as Custom Slot"
            setOnClickListener { showSaveCustomDialog() }
        }
        root.addView(btnSaveCustom)
        
        // Setup preset logic
        presetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                applyPresetToForm(adminPresetKeys[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun applyPresetToForm(key: String) {
        when (key) {
            "standard" -> setFormRules(false, 6, true, true, 6, 7, true, 1, false)
            "golden_point" -> setFormRules(true, 6, true, true, 6, 7, true, 1, false)
            "fast_short" -> setFormRules(true, 4, false, true, 4, 5, true, 0, false)
            "custom_1" -> loadCustom(1)
            "custom_2" -> loadCustom(2)
            "custom_3" -> loadCustom(3)
        }
    }

    private fun setFormRules(gp: Boolean, games: Int, wb2g: Boolean, utb: Boolean, tbAt: Int, tbTarget: Int, tbwb2: Boolean, bestOfIdx: Int, superTb: Boolean) {
        goldenPointSwitch.isChecked = gp
        gamesToWinSetInput.setText(games.toString())
        winBy2GamesSwitch.isChecked = wb2g
        useTieBreakSwitch.isChecked = utb
        tieBreakAtInput.setText(tbAt.toString())
        tieBreakTargetInput.setText(tbTarget.toString())
        tieBreakWinBy2Switch.isChecked = tbwb2
        bestOfSpinner.setSelection(bestOfIdx)
        finalSetSuperTbSwitch.isChecked = superTb
    }

    private fun loadCustom(slot: Int) {
        try {
            val json = JSONObject(config.getCustomSlotJson(slot))
            setFormRules(
                json.optBoolean("useGoldenPoint", false),
                json.optInt("gamesToWinSet", 6),
                json.optBoolean("winBy2Games", true),
                json.optBoolean("useTieBreak", true),
                json.optInt("tieBreakAt", 6),
                json.optInt("tieBreakTarget", 7),
                json.optBoolean("tieBreakWinBy2", true),
                bestOfSetsOptions.indexOf(json.optInt("setsToWinMatch", 2)).coerceAtLeast(0),
                json.optBoolean("finalSetSuperTieBreak", false)
            )
            superTbTargetInput.setText(json.optInt("superTieBreakTarget", 10).toString())
        } catch (e: Exception) {}
    }

    private fun showSaveCustomDialog() {
        val slots = arrayOf("Custom 1", "Custom 2", "Custom 3")
        AlertDialog.Builder(context).setTitle("Save to Slot").setItems(slots) { _, which ->
            val js = JSONObject().apply {
                put("useGoldenPoint", goldenPointSwitch.isChecked)
                put("gamesToWinSet", gamesToWinSetInput.text.toString().toIntOrNull() ?: 6)
                put("winBy2Games", winBy2GamesSwitch.isChecked)
                put("useTieBreak", useTieBreakSwitch.isChecked)
                put("tieBreakAt", tieBreakAtInput.text.toString().toIntOrNull() ?: 6)
                put("tieBreakTarget", tieBreakTargetInput.text.toString().toIntOrNull() ?: 7)
                put("tieBreakWinBy2", tieBreakWinBy2Switch.isChecked)
                put("setsToWinMatch", bestOfSetsOptions[bestOfSpinner.selectedItemPosition])
                put("finalSetSuperTieBreak", finalSetSuperTbSwitch.isChecked)
                put("superTieBreakTarget", superTbTargetInput.text.toString().toIntOrNull() ?: 10)
            }.toString()
            config.saveCustomSlotJson(which + 1, js)
            Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show()
        }.show()
    }

    private fun setupVisualsTab(root: LinearLayout) {
        addSectionHeader("🎨 Visuals", root)
        createLabel("Team A Color", root)
        colorASpinner = createColorSpinner(config.colorA, root)
        createLabel("Team B Color", root)
        colorBSpinner = createColorSpinner(config.colorB, root)
        createLabel("Font Scale", root)
        fontScaleSpinner = createFontScaleSpinner(config.fontScale, root)
        createLabel("Font Style", root)
        fontTypeSpinner = createFontTypeSpinner(config.fontTypeface, root)
        winEffectSwitch = createSwitch("Enable Celebration Effects", config.enableWinEffect, root)
    }

    private fun setupPhotosTab(root: LinearLayout) {
        addSectionHeader("📸 Player Photos", root)
        enablePhotosSwitch = createSwitch("Display Team Photos", config.enablePhotos, root)
        allowUserUploadPhotoSwitch = createSwitch("Allow User (Remote) Upload Photos", config.allowUserUploadPhoto, root)
        photoSizeInput = createInput("Photo Size %", config.photoSize.toString(), root, InputType.TYPE_CLASS_NUMBER)
        photoYPosInput = createInput("Photo Y Pos %", config.photoYPos.toString(), root, InputType.TYPE_CLASS_NUMBER)
    }

    private fun setupAudioTab(root: LinearLayout) {
        addSectionHeader("🔊 Audio", root)
        soundSwitch = createSwitch("Sound Effects", config.soundEnabled, root)
        voiceRefSwitch = createSwitch("Voice Umpire (TTS)", config.enableVoiceRef, root)
        useLoveSwitch = createSwitch("Use 'Love' for Zero", config.useLoveForZero, root)
    }

    private fun setupRemoteTab(root: LinearLayout) {
        addSectionHeader("📡 Bluetooth Remote (V2.3)", root)
        bleSwitch = createSwitch("Master Enable BLE HID", config.enableBleHid, root)
        shutterSwitch = createSwitch("AB Shutter 3 Optimization", config.enableShutterRemote, root)
        
        createLabel("Remote Mode", root)
        val modes = listOf("Mode 1: Single Remote (1-click=A, 2-click=B)", "Mode 2: Dual Remotes (Remote A & Remote B)")
        remoteModeSpinner = createSpinner(modes, (config.remoteMode - 1).coerceAtLeast(0), root)
        
        val mode2Container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (config.remoteMode == 2) View.VISIBLE else View.GONE
        }
        root.addView(mode2Container)

        remoteModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                mode2Container.visibility = if (pos == 1) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        remoteSwapSwitch = createSwitch("Swap Tim AB di Mode 2", config.remoteSwapMod2, mode2Container)
        remoteResetEnabledSwitch = createSwitch("Enable Dual-Button Reset (A+B)", config.remoteResetEnabled, mode2Container)
        
        remoteLongPressMsInput = createInput("Durasi Undo (ms)", config.remoteLongPressMs.toString(), root, InputType.TYPE_CLASS_NUMBER)
        remoteDualPressMsInput = createInput("Durasi Reset (ms)", config.remoteDualPressMs.toString(), mode2Container, InputType.TYPE_CLASS_NUMBER)

        addSectionHeader("🎮 Mode 2: Assign Remotes", mode2Container)
        
        val layoutA = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        remoteADescText = TextView(context).apply { text = if (config.remoteADesc.isEmpty()) "Not Assigned" else "Assigned: ${config.remoteADesc.take(8)}..."; setTextColor(Color.WHITE); textSize = 12f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val btnAssignA = Button(context).apply { text = "ASSIGN A"; setOnClickListener { startLearningRemote(true) } }
        layoutA.addView(remoteADescText); layoutA.addView(btnAssignA)
        mode2Container.addView(layoutA)

        val layoutB = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        remoteBDescText = TextView(context).apply { text = if (config.remoteBDesc.isEmpty()) "Not Assigned" else "Assigned: ${config.remoteBDesc.take(8)}..."; setTextColor(Color.WHITE); textSize = 12f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val btnAssignB = Button(context).apply { text = "ASSIGN B"; setOnClickListener { startLearningRemote(false) } }
        layoutB.addView(remoteBDescText); layoutB.addView(btnAssignB)
        mode2Container.addView(layoutB)

        addSectionHeader("⌨️ Legacy Key Bindings", root)
        kbAPlusInput = createKeyInput("A+", config.keyTeamAPlus, root)
        kbAMinusInput = createKeyInput("A-", config.keyTeamAMinus, root)
        kbBPlusInput = createKeyInput("B+", config.keyTeamBPlus, root)
        kbBMinusInput = createKeyInput("B-", config.keyTeamBMinus, root)
        kbResetInput = createKeyInput("Reset", config.keyReset, root)
    }

    private fun startLearningRemote(isTeamA: Boolean) {
        val dialog = AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Learning Remote ${if (isTeamA) "A" else "B"}")
            .setMessage("Silakan tekan tombol APA PUN di remote ${if (isTeamA) "A" else "B"} sekarang...")
            .setNegativeButton("Cancel", null)
            .create()

        // Play a specific sound if available (beep) - SoundManager is used in MainActivity but we can use simple ToneGenerator
        val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
        tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP)

        dialog.setOnKeyListener { _, _, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                val descriptor = event.device?.descriptor ?: ""
                if (descriptor.isNotEmpty()) {
                    if (isTeamA) {
                        config.remoteADesc = descriptor
                        remoteADescText.text = "Assigned: ${descriptor.take(8)}..."
                    } else {
                        config.remoteBDesc = descriptor
                        remoteBDescText.text = "Assigned: ${descriptor.take(8)}..."
                    }
                    tg.startTone(android.media.ToneGenerator.TONE_PROP_ACK)
                    Toast.makeText(context, "Remote ${if (isTeamA) "A" else "B"} Linked!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    return@setOnKeyListener true
                }
            }
            false
        }
        dialog.show()
    }

    private fun setupSystemTab(root: LinearLayout) {
        addSectionHeader("🔐 System", root)
        portInput = createInput("Server Port", config.serverPort.toString(), root, InputType.TYPE_CLASS_NUMBER)
        settingsPinInput = createInput("Admin PIN", config.pin, root, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        showDebugMsgSwitch = createSwitch("Show Remote Debug Message", config.showDebugMsg, root)
        addSectionHeader("💻 Advanced JSON", root)
        jsonThemeInput = createInputMultiline("JSON Theme Override", config.customThemeJson, root)
    }

    private fun setupHistoryTab(root: LinearLayout) {
        addSectionHeader("📜 Match History (Last 50)", root)
        val historyContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        root.addView(historyContainer)

        val btnClear = Button(context).apply {
            text = "CLEAR HISTORY"
            setOnClickListener {
                historyManager.clearHistory()
                refreshHistory(historyContainer)
            }
        }
        root.addView(btnClear)
        refreshHistory(historyContainer)
    }

    private fun refreshHistory(container: LinearLayout) {
        container.removeAllViews()
        try {
            val historyArr = JSONArray(historyManager.getHistory())
            if (historyArr.length() == 0) {
                createLabel("No history available.", container)
                return
            }
            for (i in 0 until historyArr.length()) {
                val item = historyArr.getJSONObject(i)
                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(20, 20, 20, 20)
                    setBackgroundColor(0xFF222222.toInt())
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.setMargins(0, 10, 0, 10)
                    layoutParams = lp
                }
                TextView(context).apply { text = item.getString("date"); textSize = 10f; setTextColor(0xFF888888.toInt()); card.addView(this) }
                TextView(context).apply { 
                    text = "${item.getString("teamA")} vs ${item.getString("teamB")}"
                    textSize = 14f; setTextColor(Color.WHITE); card.addView(this) 
                }
                TextView(context).apply { 
                    text = "Sets: ${item.getInt("setsA")} - ${item.getInt("setsB")} (Games: ${item.getInt("gamesA")}-${item.getInt("gamesB")})"
                    textSize = 12f; setTextColor(0xFF00FF66.toInt()); card.addView(this) 
                }
                container.addView(card)
            }
        } catch (e: Exception) {
            createLabel("Error loading history", container)
        }
    }

    private fun saveAllSettings(isAdminUnlocked: Boolean) {
        config.saveBatch {
            // User section
            putString(ConfigManager.KEY_SCORING_PRESET, adminPresetKeys[userPresetSpinner.selectedItemPosition])
            putBoolean(ConfigManager.KEY_SOUND_ENABLED, userSoundSwitch.isChecked)
            putBoolean(ConfigManager.KEY_ENABLE_PHOTOS, userPhotosSwitch.isChecked)

            if (isAdminUnlocked) {
                putString(ConfigManager.KEY_TEAM_A_NAME, teamAInput.text.toString())
                putString(ConfigManager.KEY_TEAM_B_NAME, teamBInput.text.toString())
                putBoolean(ConfigManager.KEY_USE_GOLDEN_POINT, goldenPointSwitch.isChecked)
                putInt(ConfigManager.KEY_GAMES_TO_WIN_SET, gamesToWinSetInput.text.toString().toIntOrNull() ?: 6)
                putBoolean(ConfigManager.KEY_WIN_BY_2_GAMES, winBy2GamesSwitch.isChecked)
                putBoolean(ConfigManager.KEY_USE_TIE_BREAK, useTieBreakSwitch.isChecked)
                putInt(ConfigManager.KEY_TIE_BREAK_AT, tieBreakAtInput.text.toString().toIntOrNull() ?: 6)
                putInt(ConfigManager.KEY_TIE_BREAK_TARGET, tieBreakTargetInput.text.toString().toIntOrNull() ?: 7)
                putBoolean(ConfigManager.KEY_TIE_BREAK_WIN_BY_2, tieBreakWinBy2Switch.isChecked)
                putInt(ConfigManager.KEY_SETS_TO_WIN_MATCH, bestOfSetsOptions[bestOfSpinner.selectedItemPosition])
                putBoolean(ConfigManager.KEY_FINAL_SET_SUPER_TB, finalSetSuperTbSwitch.isChecked)
                putInt(ConfigManager.KEY_SUPER_TB_TARGET, superTbTargetInput.text.toString().toIntOrNull() ?: 10)
                
                putInt(ConfigManager.KEY_COLOR_A, getColor(colorASpinner))
                putInt(ConfigManager.KEY_COLOR_B, getColor(colorBSpinner))
                putFloat(ConfigManager.KEY_FONT_SCALE, getFontScale(fontScaleSpinner))
                putString(ConfigManager.KEY_FONT_TYPEFACE, getFontType(fontTypeSpinner))
                putBoolean(ConfigManager.KEY_ENABLE_WIN_EFFECT, winEffectSwitch.isChecked)
                
                putBoolean(ConfigManager.KEY_ENABLE_PHOTOS, enablePhotosSwitch.isChecked)
                putBoolean(ConfigManager.KEY_ALLOW_USER_UPLOAD_PHOTO, allowUserUploadPhotoSwitch.isChecked)
                putInt(ConfigManager.KEY_PHOTO_SIZE, photoSizeInput.text.toString().toIntOrNull() ?: 25)
                putInt(ConfigManager.KEY_PHOTO_Y_POS, photoYPosInput.text.toString().toIntOrNull() ?: 35)
                
                putBoolean(ConfigManager.KEY_SOUND_ENABLED, soundSwitch.isChecked)
                putBoolean(ConfigManager.KEY_ENABLE_VOICE_REF, voiceRefSwitch.isChecked)
                putBoolean(ConfigManager.KEY_USE_LOVE_FOR_ZERO, useLoveSwitch.isChecked)
                
                putBoolean(ConfigManager.KEY_ENABLE_HTTP, httpSwitch.isChecked)
                putBoolean(ConfigManager.KEY_ENABLE_BLE_HID, bleSwitch.isChecked)
                putBoolean(ConfigManager.KEY_ENABLE_SHUTTER_REMOTE, shutterSwitch.isChecked)
                
                putInt(ConfigManager.KEY_REMOTE_MODE, remoteModeSpinner.selectedItemPosition + 1)
                putBoolean(ConfigManager.KEY_REMOTE_SWP_MOD2, remoteSwapSwitch.isChecked)
                putBoolean(ConfigManager.KEY_REMOTE_RESET_ENABLED, remoteResetEnabledSwitch.isChecked)
                putInt(ConfigManager.KEY_REMOTE_LONG_PRESS_MS, remoteLongPressMsInput.text.toString().toIntOrNull() ?: 600)
                putInt(ConfigManager.KEY_REMOTE_DUAL_PRESS_MS, remoteDualPressMsInput.text.toString().toIntOrNull() ?: 2000)

                putString(ConfigManager.KEY_KB_TEAM_A_PLUS, kbAPlusInput.text.toString())
                putString(ConfigManager.KEY_KB_TEAM_A_MINUS, kbAMinusInput.text.toString())
                putString(ConfigManager.KEY_KB_TEAM_B_PLUS, kbBPlusInput.text.toString())
                putString(ConfigManager.KEY_KB_TEAM_B_MINUS, kbBMinusInput.text.toString())
                putString(ConfigManager.KEY_KB_RESET, kbResetInput.text.toString())
                
                putInt(ConfigManager.KEY_SERVER_PORT, portInput.text.toString().toIntOrNull() ?: 8888)
                putString(ConfigManager.KEY_PIN, settingsPinInput.text.toString())
                putBoolean(ConfigManager.KEY_SHOW_DEBUG_MSG, showDebugMsgSwitch.isChecked)
                putString(ConfigManager.KEY_CUSTOM_THEME_JSON, jsonThemeInput.text.toString())
            }
        }
    }

    // Support Helpers
    private fun addSectionHeader(title: String, parent: LinearLayout) {
        TextView(context).apply {
            text = title; setTextColor(0xFFAAAAAA.toInt()); textSize = 14f
            setPadding(0, 36, 0, 8); parent.addView(this)
        }
        View(context).apply { setBackgroundColor(0xFF333333.toInt()); parent.addView(this, LinearLayout.LayoutParams(-1, 2)) }
    }
    private fun createLabel(t: String, p: LinearLayout) = TextView(context).apply { text = t; setTextColor(0xFF888888.toInt()); textSize = 11f; setPadding(0, 16, 0, 4); p.addView(this) }
    private fun createInput(l: String, v: String, p: LinearLayout, t: Int = InputType.TYPE_CLASS_TEXT) = EditText(context).apply {
        createLabel(l, p); setText(v); inputType = t; setTextColor(Color.WHITE); textSize = 16f; setBackgroundColor(0xFF222222.toInt()); setPadding(20, 15, 20, 15); p.addView(this)
    }
    private fun createInputMultiline(h: String, v: String, p: LinearLayout) = EditText(context).apply {
        setText(v); hint = h; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        minLines = 3; setTextColor(Color.WHITE); textSize = 12f; setBackgroundColor(0xFF1A1A2E.toInt()); setPadding(20, 15, 20, 15); p.addView(this)
    }
    private fun createKeyInput(l: String, v: String, p: LinearLayout) = EditText(context).apply {
        createLabel(l, p); setText(v.take(1)); filters = arrayOf(InputFilter.LengthFilter(1)); setTextColor(Color.WHITE); gravity = Gravity.CENTER; setBackgroundColor(0xFF222222.toInt()); p.addView(this, LinearLayout.LayoutParams(150, -2))
    }
    private fun createSwitch(l: String, c: Boolean, p: LinearLayout) = Switch(context).apply { text = l; isChecked = c; setTextColor(0xFFCCCCCC.toInt()); textSize = 13f; setPadding(0, 20, 0, 8); p.addView(this) }
    private fun createSpinner(i: List<String>, s: Int, p: LinearLayout) = Spinner(context).apply { adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, i); setSelection(s); p.addView(this) }

    private val colorOptions = linkedMapOf("Green" to 0xFF00FF66.toInt(), "Amber" to 0xFFFFA500.toInt(), "Red" to 0xFFFF4444.toInt(), "Blue" to 0xFF4488FF.toInt(), "Cyan" to 0xFF00FFFF.toInt(), "White" to 0xFFFFFFFF.toInt())
    private fun createColorSpinner(c: Int, p: LinearLayout) = Spinner(context).apply { adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, colorOptions.keys.toList()); setSelection(colorOptions.values.indexOf(c).coerceAtLeast(0)); p.addView(this) }
    private fun getColor(s: Spinner) = colorOptions[s.selectedItem as? String] ?: 0xFF00FF66.toInt()

    private val fontScaleOptions = linkedMapOf("Small" to 0.8f, "Normal" to 1.0f, "Large" to 1.2f, "Extra" to 1.5f)
    private fun createFontScaleSpinner(c: Float, p: LinearLayout) = Spinner(context).apply { adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, fontScaleOptions.keys.toList()); setSelection(fontScaleOptions.values.indexOfFirst { Math.abs(it-c)<0.01f }.coerceAtLeast(1)); p.addView(this) }
    private fun getFontScale(s: Spinner) = fontScaleOptions[s.selectedItem as? String] ?: 1.0f

    private val fontTypeOptions = linkedMapOf("Monospace" to "monospace", "Sans" to "sans-serif", "Serif" to "serif")
    private fun createFontTypeSpinner(c: String, p: LinearLayout) = Spinner(context).apply { adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, fontTypeOptions.keys.toList()); setSelection(fontTypeOptions.values.indexOf(c).coerceAtLeast(0)); p.addView(this) }
    private fun getFontType(s: Spinner) = fontTypeOptions[s.selectedItem as? String] ?: "monospace"
}
