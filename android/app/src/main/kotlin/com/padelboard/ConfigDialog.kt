package com.padelboard

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.widget.*

/**
 * Hidden configuration dialog, accessible via 5 rapid taps.
 * Protected by PIN (default: 1234).
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
                if (input.text.toString() == config.pin) {
                    showConfigDialog()
                } else {
                    Toast.makeText(context, "❌ Wrong PIN", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showConfigDialog() {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 30)
            setBackgroundColor(0xFF111111.toInt())
        }

        val teamAInput = createInput("Team A Name", config.teamAName, layout)
        val teamBInput = createInput("Team B Name", config.teamBName, layout)
        val portInput = createInput("Server Port", config.serverPort.toString(), layout,
            InputType.TYPE_CLASS_NUMBER)
        val pinInput = createInput("PIN Code", config.pin, layout,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)

        // Color selection for Team A
        val colorALabel = createLabel("Team A Color", layout)
        val colorASpinner = createColorSpinner(config.colorA, layout)

        // Color selection for Team B
        val colorBLabel = createLabel("Team B Color", layout)
        val colorBSpinner = createColorSpinner(config.colorB, layout)

        val scrollView = ScrollView(context).apply {
            addView(layout)
        }

        AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("⚙️ Scoreboard Settings")
            .setView(scrollView)
            .setPositiveButton("Save") { _, _ ->
                config.teamAName = teamAInput.text.toString().ifEmpty { ConfigManager.DEFAULT_TEAM_A }
                config.teamBName = teamBInput.text.toString().ifEmpty { ConfigManager.DEFAULT_TEAM_B }

                val port = portInput.text.toString().toIntOrNull() ?: ConfigManager.DEFAULT_PORT
                config.serverPort = port

                val pin = pinInput.text.toString().ifEmpty { ConfigManager.DEFAULT_PIN }
                config.pin = pin

                config.colorA = getColorFromSpinner(colorASpinner)
                config.colorB = getColorFromSpinner(colorBSpinner)

                onConfigChanged()
                Toast.makeText(context, "✅ Settings saved!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Reset Scores") { _, _ ->
                // Handled externally via callback if needed
                onConfigChanged()
            }
            .show()
    }

    private fun createLabel(label: String, parent: LinearLayout): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(0xFF888888.toInt())
            textSize = 12f
            setPadding(0, 20, 0, 4)
            parent.addView(this)
        }
    }

    private fun createInput(
        label: String,
        defaultValue: String,
        parent: LinearLayout,
        type: Int = InputType.TYPE_CLASS_TEXT
    ): EditText {
        val labelView = TextView(context).apply {
            text = label
            setTextColor(0xFF888888.toInt())
            textSize = 12f
            setPadding(0, 20, 0, 4)
        }
        parent.addView(labelView)

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

    private val colorOptions = mapOf(
        "Green (#00FF66)" to 0xFF00FF66.toInt(),
        "Amber (#FFA500)" to 0xFFFFA500.toInt(),
        "Red (#FF4444)" to 0xFFFF4444.toInt(),
        "Blue (#4488FF)" to 0xFF4488FF.toInt(),
        "Cyan (#00FFFF)" to 0xFF00FFFF.toInt(),
        "Yellow (#FFFF00)" to 0xFFFFFF00.toInt(),
        "White (#FFFFFF)" to 0xFFFFFFFF.toInt(),
        "Magenta (#FF44FF)" to 0xFFFF44FF.toInt()
    )

    private fun createColorSpinner(currentColor: Int, parent: LinearLayout): Spinner {
        val spinner = Spinner(context)
        val adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            colorOptions.keys.toList()
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        // Select current color
        val currentIndex = colorOptions.values.indexOf(currentColor)
        if (currentIndex >= 0) spinner.setSelection(currentIndex)

        parent.addView(spinner)
        return spinner
    }

    private fun getColorFromSpinner(spinner: Spinner): Int {
        val selectedName = spinner.selectedItem as? String ?: return ConfigManager.DEFAULT_COLOR_A
        return colorOptions[selectedName] ?: ConfigManager.DEFAULT_COLOR_A
    }
}
