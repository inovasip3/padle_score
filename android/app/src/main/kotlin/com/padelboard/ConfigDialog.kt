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
        val presets = listOf("Standard Padel", "American 11", "American 21", "Custom")
        val presetKeys = listOf("standard", "american11", "american21", "custom")
        val presetSpinner = createSpinner(presets, presetKeys.indexOf(config.scoringPreset), root)

        // Custom fields (shown/hidden on preset change)
        val customIncrLabel = createLabel("Point Increment per score", root)
        val customIncrInput = createInput("e.g. 1", config.customIncrement.toString(), root, InputType.TYPE_CLASS_NUMBER)
        val maxPointsLabel = createLabel("Max Points to win a game", root)
        val maxPointsInput = createInput("e.g. 11", config.maxPointsToWin.toString(), root, InputType.TYPE_CLASS_NUMBER)
        val winByTwoSwitch = createSwitch("Win by 2 (Deuce/Jus) required", config.winByTwo, root)

        fun updatePresetFields(position: Int) {
            val isCustom = presetKeys[position] == "custom"
            val editableFields = listOf<View>(customIncrLabel, customIncrInput, maxPointsLabel, maxPointsInput, winByTwoSwitch)
            editableFields.forEach { it.visibility = if (isCustom) View.VISIBLE else View.GONE }
            // Pre-fill if selecting a named preset
            when (presetKeys[position]) {
                "american11" -> { customIncrInput.setText("1"); maxPointsInput.setText("11"); winByTwoSwitch.isChecked = true }
                "american21" -> { customIncrInput.setText("1"); maxPointsInput.setText("21"); winByTwoSwitch.isChecked = true }
                "standard"   -> { customIncrInput.setText("15"); maxPointsInput.setText("40"); winByTwoSwitch.isChecked = false }
                else -> {}
            }
        }
        updatePresetFields(presetKeys.indexOf(config.scoringPreset).coerceAtLeast(0))
        presetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = updatePresetFields(pos)
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // ── Section: Visuals ────────────────────────────────────────────────
        addSectionHeader("🎨 Visuals", root)
        val colorALabel = createLabel("Team A Color", root)
        val colorASpinner = createColorSpinner(config.colorA, root)
        val colorBLabel = createLabel("Team B Color", root)
        val colorBSpinner = createColorSpinner(config.colorB, root)
        val fontScaleLabel = createLabel("Font Size", root)
        val fontScaleSpinner = createFontScaleSpinner(config.fontScale, root)
        val fontTypeLabel = createLabel("Font Style", root)
        val fontTypeSpinner = createFontTypeSpinner(config.fontTypeface, root)
        val winEffectSwitch = createSwitch("Enable Win Celebration Effect", config.enableWinEffect, root)

        // ── Section: Audio ──────────────────────────────────────────────────
        addSectionHeader("🔊 Audio", root)
        val soundSwitch = createSwitch("Sound Enabled", config.soundEnabled, root)

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

                // Scoring
                val selectedPreset = presetKeys[presetSpinner.selectedItemPosition]
                config.applyPreset(selectedPreset)
                if (selectedPreset == "custom") {
                    config.customIncrement = customIncrInput.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
                    config.maxPointsToWin  = maxPointsInput.text.toString().toIntOrNull()?.coerceAtLeast(2) ?: 11
                    config.winByTwo = winByTwoSwitch.isChecked
                }

                // Visuals
                config.colorA       = getColorFromSpinner(colorASpinner)
                config.colorB       = getColorFromSpinner(colorBSpinner)
                config.fontScale    = getFontScaleFromSpinner(fontScaleSpinner)
                config.fontTypeface = getFontTypeFromSpinner(fontTypeSpinner)
                config.enableWinEffect = winEffectSwitch.isChecked

                // Audio
                config.soundEnabled = soundSwitch.isChecked

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
