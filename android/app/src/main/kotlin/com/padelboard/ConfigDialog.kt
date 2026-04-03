package com.padelboard

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONObject

/**
 * V2.0 Master Settings Dialog
 * Sections: Scoring Preset | Visuals | Audio | Remote Control | Advanced (JSON Theme)
 */
class ConfigDialog(
    private val context: Context,
    private val config: ConfigManager,
    private val onConfigChanged: () -> Unit
) {

    fun showPinDialog() {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Enter PIN"
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF888888.toInt())
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(40, 30, 40, 30)
            setBackgroundColor(0xFF222222.toInt())
        }

        AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("🔒 Admin Access")
            .setMessage("Enter PIN to access settings")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                if (input.text.toString() == config.pin) showMasterSettingsDialog()
                else Toast.makeText(context, "❌ Wrong PIN", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // -------------------------------------------------------------------------
    //  Master Settings Dialog (Tabbed via LinearLayout Sections)
    // -------------------------------------------------------------------------

    private fun showMasterSettingsDialog() {
        val scroll = ScrollView(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 40)
            setBackgroundColor(0xFF111111.toInt())
        }
        scroll.addView(root)

        // ── Section: Team ───────────────────────────────────────────────────
        addSectionHeader("👥 Team", root)
        val teamAInput = createInput("Team A Name", config.teamAName, root)
        val teamBInput = createInput("Team B Name", config.teamBName, root)

        // ── Section: Scoring Preset ─────────────────────────────────────────
        addSectionHeader("🏓 Scoring Preset", root)
        val presets = listOf("Standard Padel", "Golden Point", "Fast / Short", "Custom 1", "Custom 2", "Custom 3")
        val presetKeys = listOf("standard", "golden_point", "fast_short", "custom_1", "custom_2", "custom_3")
        val presetSpinner = createSpinner(presets, presetKeys.indexOf(config.scoringPreset).coerceAtLeast(0), root)
        
        // ── Section: Point Rule ──────────────────────────────────────────────
        addSectionHeader("🎾 POINT RULE", root)
        val goldenPointSwitch = createSwitch("Use Golden Point", config.useGoldenPoint, root)

        // ── Section: Game Rule ───────────────────────────────────────────────
        addSectionHeader("🎾 GAME RULE", root)
        val gamesToWinSetInput = createInput("Games to Win Set (4-6)", config.gamesToWinSet.toString(), root, InputType.TYPE_CLASS_NUMBER)
        val winBy2GamesSwitch = createSwitch("Must Win By 2 Games", config.winBy2Games, root)

        // ── Section: Tie Break ───────────────────────────────────────────────
        addSectionHeader("🎾 TIE BREAK", root)
        val useTieBreakSwitch = createSwitch("Use Tie Break", config.useTieBreak, root)
        val tieBreakAtInput = createInput("Tie Break At (e.g. 6)", config.tieBreakAt.toString(), root, InputType.TYPE_CLASS_NUMBER)
        val tieBreakTargetInput = createInput("Target Point (e.g. 7)", config.tieBreakTarget.toString(), root, InputType.TYPE_CLASS_NUMBER)
        val tieBreakWinBy2Switch = createSwitch("Win By 2 Points", config.tieBreakWinBy2, root)

        // ── Section: Set Rule ────────────────────────────────────────────────
        addSectionHeader("🎾 SET RULE", root)
        val bestOfOptions = listOf("Best of 1 (1 Set to Win)", "Best of 3 (2 Sets to Win)", "Best of 5 (3 Sets to Win)")
        val bestOfSetsOptions = listOf(1, 2, 3)
        val bestOfSpinner = createSpinner(bestOfOptions, bestOfSetsOptions.indexOf(config.setsToWinMatch).coerceAtLeast(0), root)
        val finalSetSuperTbSwitch = createSwitch("Final Set Super Tie Break", config.finalSetSuperTieBreak, root)
        val superTbTargetInput = createInput("Super Tie Break Target (e.g. 10)", config.superTieBreakTarget.toString(), root, InputType.TYPE_CLASS_NUMBER)

        val saveToCustomBtn = Button(context).apply {
            text = "Save Current to Custom Slot"
            setBackgroundColor(0xFF333333.toInt())
            setTextColor(Color.WHITE)
            setPadding(10, 20, 10, 20)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 30, 0, 10) }
            
            setOnClickListener {
                val slots = arrayOf("Custom 1", "Custom 2", "Custom 3")
                AlertDialog.Builder(context)
                    .setTitle("Save to Custom Slot")
                    .setItems(slots) { _, which ->
                        val slotNum = which + 1
                        val js = org.json.JSONObject().apply {
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
                        config.saveCustomSlotJson(slotNum, js)
                        Toast.makeText(context, "Saved to Custom $slotNum!", Toast.LENGTH_SHORT).show()
                        presetSpinner.setSelection(3 + which) // set to chosen custom slot
                    }
                    .show()
            }
        }
        root.addView(saveToCustomBtn)

        // Only explicitly set field values when a preset is actively clicked by the user
        var isInitialSetup = true
        presetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (isInitialSetup) {
                    isInitialSetup = false
                    return
                }
                val key = presetKeys[pos]
                when (key) {
                    "standard" -> {
                        goldenPointSwitch.isChecked = false
                        gamesToWinSetInput.setText("6")
                        winBy2GamesSwitch.isChecked = true
                        useTieBreakSwitch.isChecked = true
                        tieBreakAtInput.setText("6")
                        tieBreakTargetInput.setText("7")
                        tieBreakWinBy2Switch.isChecked = true
                        bestOfSpinner.setSelection(1) // 2 sets
                        finalSetSuperTbSwitch.isChecked = false
                    }
                    "golden_point" -> {
                        goldenPointSwitch.isChecked = true
                        gamesToWinSetInput.setText("6")
                        winBy2GamesSwitch.isChecked = true
                        useTieBreakSwitch.isChecked = true
                        tieBreakAtInput.setText("6")
                        tieBreakTargetInput.setText("7")
                        tieBreakWinBy2Switch.isChecked = true
                        bestOfSpinner.setSelection(1) // 2 sets
                        finalSetSuperTbSwitch.isChecked = false
                    }
                    "fast_short" -> {
                        goldenPointSwitch.isChecked = true
                        gamesToWinSetInput.setText("4")
                        winBy2GamesSwitch.isChecked = false
                        useTieBreakSwitch.isChecked = true
                        tieBreakAtInput.setText("4")
                        tieBreakTargetInput.setText("5")
                        tieBreakWinBy2Switch.isChecked = true
                        bestOfSpinner.setSelection(0) // 1 set
                        finalSetSuperTbSwitch.isChecked = false
                    }
                    "custom_1" -> loadCustomToForm(1)
                    "custom_2" -> loadCustomToForm(2)
                    "custom_3" -> loadCustomToForm(3)
                }
            }
            private fun loadCustomToForm(slotNum: Int) {
                val jsonStr = config.getCustomSlotJson(slotNum)
                if (jsonStr.isNotEmpty()) {
                    try {
                        val json = org.json.JSONObject(jsonStr)
                        goldenPointSwitch.isChecked = json.optBoolean("useGoldenPoint", false)
                        gamesToWinSetInput.setText(json.optInt("gamesToWinSet", 6).toString())
                        winBy2GamesSwitch.isChecked = json.optBoolean("winBy2Games", true)
                        useTieBreakSwitch.isChecked = json.optBoolean("useTieBreak", true)
                        tieBreakAtInput.setText(json.optInt("tieBreakAt", 6).toString())
                        tieBreakTargetInput.setText(json.optInt("tieBreakTarget", 7).toString())
                        tieBreakWinBy2Switch.isChecked = json.optBoolean("tieBreakWinBy2", true)
                        bestOfSpinner.setSelection(bestOfSetsOptions.indexOf(json.optInt("setsToWinMatch", 2)).coerceAtLeast(0))
                        finalSetSuperTbSwitch.isChecked = json.optBoolean("finalSetSuperTieBreak", false)
                        superTbTargetInput.setText(json.optInt("superTieBreakTarget", 10).toString())
                    } catch (e: Exception) {}
                }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // ── Section: Visuals ────────────────────────────────────────────────
        addSectionHeader("🎨 Visuals", root)
        createLabel("Team A Color", root) // using createLabel without storing var avoids 'never used' warning
        val colorASpinner = createColorSpinner(config.colorA, root)
        createLabel("Team B Color", root)
        val colorBSpinner = createColorSpinner(config.colorB, root)
        createLabel("Font Size", root)
        val fontScaleSpinner = createFontScaleSpinner(config.fontScale, root)
        createLabel("Font Style", root)
        val fontTypeSpinner = createFontTypeSpinner(config.fontTypeface, root)
        val winEffectSwitch = createSwitch("Enable Win Celebration Effect", config.enableWinEffect, root)

        // ── Section: Player Photos ──────────────────────────────────────────
        addSectionHeader("📸 Player Photos", root)
        val enablePhotosSwitch = createSwitch("Display Team Photos (Upload via Web Remote)", config.enablePhotos, root)
        val photoSizeInput = createInput("Photo Size (% of screen height, e.g. 25)", config.photoSize.toString(), root, InputType.TYPE_CLASS_NUMBER)
        val photoYPosInput = createInput("Photo Vertical Position (% from top, e.g. 35)", config.photoYPos.toString(), root, InputType.TYPE_CLASS_NUMBER)

        // ── Section: Audio ──────────────────────────────────────────────────
        addSectionHeader("🔊 Audio", root)
        val soundSwitch = createSwitch("Sound Effects Enabled", config.soundEnabled, root)
        val voiceRefSwitch = createSwitch("Enable Professional Voice Umpire (TTS)", config.enableVoiceRef, root)
        val useLoveSwitch = createSwitch("Say 'Love' instead of 'Zero'", config.useLoveForZero, root)

        // ── Section: Remote Control ─────────────────────────────────────────
        addSectionHeader("📡 Remote Control", root)
        val httpSwitch = createSwitch("Enable Web Remote (HTTP / WiFi)", config.enableHttpServer, root)
        val bleSwitch = createSwitch("Enable BLE HID Remote (ESP32-C3)", config.enableBleHid, root)
        createLabel("BLE Key Bindings (single letter each):", root)
        val kbAPlusInput  = createKeyInput("Team A  +  (Short left)",  config.keyTeamAPlus,  root)
        val kbAMinusInput = createKeyInput("Team A  −  (Long left)",   config.keyTeamAMinus, root)
        val kbBPlusInput  = createKeyInput("Team B  +  (Short right)", config.keyTeamBPlus,  root)
        val kbBMinusInput = createKeyInput("Team B  −  (Long right)",  config.keyTeamBMinus, root)
        val kbResetInput  = createKeyInput("Reset (Both long)",        config.keyReset,       root)

        // ── Section: Server / Security ──────────────────────────────────────
        addSectionHeader("🔐 Server & Security", root)
        val portInput = createInput("Server Port", config.serverPort.toString(), root, InputType.TYPE_CLASS_NUMBER)
        val pinInput  = createInput("PIN Code", config.pin, root, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)

        // ── Section: Advanced (JSON Theme) ──────────────────────────────────
        addSectionHeader("💻 Advanced – JSON Custom Theme", root)
        createLabel("Paste a JSON object to override theme. Supported keys: bgColor, colorA, colorB, fontScale. Leave blank to use settings above.", root)
        val jsonThemeInput = createInputMultiline("{ }", config.customThemeJson, root)

        // ── Save ────────────────────────────────────────────────────────────
        AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("⚙️ Padel Score v2.0 Settings")
            .setView(scroll)
            .setPositiveButton("Save") { _, _ ->
                config.teamAName = teamAInput.text.toString().ifEmpty { ConfigManager.DEFAULT_TEAM_A }
                config.teamBName = teamBInput.text.toString().ifEmpty { ConfigManager.DEFAULT_TEAM_B }
                config.serverPort = portInput.text.toString().toIntOrNull() ?: ConfigManager.DEFAULT_PORT
                config.pin = pinInput.text.toString().ifEmpty { ConfigManager.DEFAULT_PIN }

                // Scoring & Rules
                config.scoringPreset = presetKeys[presetSpinner.selectedItemPosition]
                config.useGoldenPoint = goldenPointSwitch.isChecked
                config.gamesToWinSet = gamesToWinSetInput.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 6
                config.winBy2Games = winBy2GamesSwitch.isChecked
                config.useTieBreak = useTieBreakSwitch.isChecked
                config.tieBreakAt = tieBreakAtInput.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 6
                config.tieBreakTarget = tieBreakTargetInput.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 7
                config.tieBreakWinBy2 = tieBreakWinBy2Switch.isChecked
                config.setsToWinMatch = bestOfSetsOptions[bestOfSpinner.selectedItemPosition]
                config.finalSetSuperTieBreak = finalSetSuperTbSwitch.isChecked
                config.superTieBreakTarget = superTbTargetInput.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 10

                // Visuals
                config.colorA       = getColorFromSpinner(colorASpinner)
                config.colorB       = getColorFromSpinner(colorBSpinner)
                config.fontScale    = getFontScaleFromSpinner(fontScaleSpinner)
                config.fontTypeface = getFontTypeFromSpinner(fontTypeSpinner)
                config.enableWinEffect = winEffectSwitch.isChecked

                // Photos
                config.enablePhotos = enablePhotosSwitch.isChecked
                config.photoSize    = photoSizeInput.text.toString().toIntOrNull()?.coerceIn(10, 80) ?: 25
                config.photoYPos    = photoYPosInput.text.toString().toIntOrNull()?.coerceIn(0, 100) ?: 35

                // Audio
                config.soundEnabled = soundSwitch.isChecked
                config.enableVoiceRef = voiceRefSwitch.isChecked
                config.useLoveForZero = useLoveSwitch.isChecked

                // Remote
                config.enableHttpServer = httpSwitch.isChecked
                config.enableBleHid     = bleSwitch.isChecked
                config.keyTeamAPlus  = kbAPlusInput.text.toString().ifEmpty { ConfigManager.DEFAULT_KB_A_PLUS }
                config.keyTeamAMinus = kbAMinusInput.text.toString().ifEmpty { ConfigManager.DEFAULT_KB_A_MINUS }
                config.keyTeamBPlus  = kbBPlusInput.text.toString().ifEmpty { ConfigManager.DEFAULT_KB_B_PLUS }
                config.keyTeamBMinus = kbBMinusInput.text.toString().ifEmpty { ConfigManager.DEFAULT_KB_B_MINUS }
                config.keyReset      = kbResetInput.text.toString().ifEmpty { ConfigManager.DEFAULT_KB_RESET }

                // Advanced JSON Theme
                val jsonRaw = jsonThemeInput.text.toString().trim()
                config.customThemeJson = if (isValidJson(jsonRaw)) jsonRaw else ""

                onConfigChanged()
                Toast.makeText(context, "✅ Settings saved!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // -------------------------------------------------------------------------
    //  Helpers
    // -------------------------------------------------------------------------

    private fun addSectionHeader(title: String, parent: LinearLayout) {
        TextView(context).apply {
            text = title
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 14f
            setPadding(0, 36, 0, 8)
            setBackgroundColor(0xFF111111.toInt())
            parent.addView(this)
        }
        View(context).apply {
            setBackgroundColor(0xFF333333.toInt())
            parent.addView(this, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
        }
    }

    private fun createLabel(label: String, parent: LinearLayout): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(0xFF888888.toInt())
            textSize = 12f
            setPadding(0, 18, 0, 4)
            parent.addView(this)
        }
    }

    private fun createInput(label: String, defaultValue: String, parent: LinearLayout, type: Int = InputType.TYPE_CLASS_TEXT): EditText {
        createLabel(label, parent)
        return EditText(context).apply {
            setText(defaultValue)
            inputType = type
            setTextColor(Color.WHITE)
            textSize = 18f
            setBackgroundColor(0xFF222222.toInt())
            setPadding(20, 16, 20, 16)
            parent.addView(this)
        }
    }

    private fun createInputMultiline(hint: String, defaultValue: String, parent: LinearLayout): EditText {
        return EditText(context).apply {
            setText(defaultValue)
            setHint(hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            gravity = Gravity.TOP
            setTextColor(Color.WHITE)
            textSize = 13f
            setBackgroundColor(0xFF1A1A2E.toInt())
            setPadding(20, 16, 20, 16)
            parent.addView(this)
        }
    }

    private fun createKeyInput(label: String, defaultValue: String, parent: LinearLayout): EditText {
        createLabel(label, parent)
        return EditText(context).apply {
            setText(defaultValue.take(1))
            inputType = InputType.TYPE_CLASS_TEXT
            filters = arrayOf(InputFilter.LengthFilter(1))
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF222222.toInt())
            setPadding(20, 12, 20, 12)
            parent.addView(this, LinearLayout.LayoutParams(200, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun createSwitch(label: String, checked: Boolean, parent: LinearLayout): Switch {
        return Switch(context).apply {
            text = label
            isChecked = checked
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 14f
            setPadding(0, 24, 0, 8)
            parent.addView(this)
        }
    }

    private fun createSpinner(items: List<String>, selectedIndex: Int, parent: LinearLayout): Spinner {
        return Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, items)
            setSelection(selectedIndex.coerceAtLeast(0))
            parent.addView(this)
        }
    }

    // Color options
    private val colorOptions = linkedMapOf(
        "Green (#00FF66)"     to 0xFF00FF66.toInt(),
        "Amber (#FFA500)"     to 0xFFFFA500.toInt(),
        "Red (#FF4444)"       to 0xFFFF4444.toInt(),
        "Blue (#4488FF)"      to 0xFF4488FF.toInt(),
        "Cyan (#00FFFF)"      to 0xFF00FFFF.toInt(),
        "Yellow (#FFFF00)"    to 0xFFFFFF00.toInt(),
        "White (#FFFFFF)"     to 0xFFFFFFFF.toInt(),
        "Magenta (#FF44FF)"   to 0xFFFF44FF.toInt(),
        "Hot Pink (#FF007F)"  to 0xFFFF007F.toInt(),
        "Electric Blue (#00FF)" to 0xFF00FFFF.toInt()
    )

    private fun createColorSpinner(currentColor: Int, parent: LinearLayout): Spinner {
        val spinner = Spinner(context)
        spinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, colorOptions.keys.toList())
        val idx = colorOptions.values.indexOf(currentColor)
        if (idx >= 0) spinner.setSelection(idx)
        parent.addView(spinner)
        return spinner
    }

    private fun getColorFromSpinner(spinner: Spinner): Int {
        return colorOptions[spinner.selectedItem as? String] ?: ConfigManager.DEFAULT_COLOR_A
    }

    // Font scale options
    private val fontScaleOptions = linkedMapOf("Small" to 0.8f, "Normal" to 1.0f, "Large" to 1.2f, "Extra Large" to 1.5f)

    private fun createFontScaleSpinner(currentScale: Float, parent: LinearLayout): Spinner {
        val spinner = Spinner(context)
        spinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, fontScaleOptions.keys.toList())
        val idx = fontScaleOptions.values.indexOfFirst { Math.abs(it - currentScale) < 0.01f }
        spinner.setSelection(if (idx >= 0) idx else 1)
        parent.addView(spinner)
        return spinner
    }

    private fun getFontScaleFromSpinner(spinner: Spinner): Float {
        return fontScaleOptions[spinner.selectedItem as? String] ?: 1.0f
    }

    // Font type options
    private val fontTypeOptions = linkedMapOf(
        "Monospace (LED-style)" to "monospace",
        "Sans-Serif"            to "sans-serif",
        "Serif"                 to "serif",
        "Sans-Serif Bold"       to "sans-serif-medium"
    )

    private fun createFontTypeSpinner(current: String, parent: LinearLayout): Spinner {
        val spinner = Spinner(context)
        spinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, fontTypeOptions.keys.toList())
        val idx = fontTypeOptions.values.indexOf(current)
        spinner.setSelection(if (idx >= 0) idx else 0)
        parent.addView(spinner)
        return spinner
    }

    private fun getFontTypeFromSpinner(spinner: Spinner): String {
        return fontTypeOptions[spinner.selectedItem as? String] ?: "monospace"
    }

    private fun isValidJson(raw: String): Boolean {
        if (raw.isBlank()) return false
        return try { JSONObject(raw); true } catch (e: Exception) { false }
    }
}
