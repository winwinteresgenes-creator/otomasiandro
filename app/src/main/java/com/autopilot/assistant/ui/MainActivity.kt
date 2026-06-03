package com.autopilot.assistant.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.autopilot.assistant.R
import com.autopilot.assistant.databinding.ActivityMainBinding
import com.autopilot.assistant.engine.MacroLoader
import com.autopilot.assistant.model.Macro
import com.autopilot.assistant.service.AutomationAccessibilityService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.enableButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.stopButton.setOnClickListener {
            AutomationAccessibilityService.instance?.stopMacro()
            Toast.makeText(this, R.string.stopped, Toast.LENGTH_SHORT).show()
        }
        binding.macroDirText.text =
            getString(R.string.macros_dir_hint, MacroLoader.userMacroDir(this).absolutePath)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        populateMacros(MacroLoader.loadAll(this))
    }

    private fun refreshStatus() {
        val connected = AutomationAccessibilityService.isConnected()
        binding.statusText.text = if (connected) {
            getString(R.string.status_connected)
        } else {
            getString(R.string.status_disconnected)
        }
    }

    private fun populateMacros(macros: List<Macro>) {
        val container = binding.macroContainer
        container.removeAllViews()
        if (macros.isEmpty()) {
            val empty = TextView(this).apply { setText(R.string.no_macros) }
            container.addView(empty)
            return
        }
        macros.forEach { macro -> container.addView(macroRow(macro)) }
    }

    private fun macroRow(macro: Macro): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val title = TextView(this).apply {
            text = macro.name
            textSize = 16f
        }
        val subtitle = TextView(this).apply {
            text = macro.description.ifBlank { macro.targetPackage ?: "" }
            textSize = 12f
        }
        val run = Button(this).apply {
            setText(R.string.run_macro)
            setOnClickListener { runMacro(macro) }
        }
        row.addView(title)
        row.addView(subtitle)
        row.addView(run)
        return row
    }

    private fun runMacro(macro: Macro) {
        val service = AutomationAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, R.string.enable_first, Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        Toast.makeText(this, getString(R.string.running_macro, macro.name), Toast.LENGTH_SHORT).show()
        service.runMacro(macro)
        // Step aside so the target app is in the foreground for the macro.
        moveTaskToBack(true)
    }
}
