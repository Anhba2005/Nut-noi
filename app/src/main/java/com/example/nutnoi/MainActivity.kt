package com.anhba.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var toggleButton: Button
    private lateinit var sizeLabel: TextView
    private lateinit var sizeSeekBar: SeekBar
    private lateinit var blockCommandsInput: TextInputEditText
    private lateinit var unblockCommandsInput: TextInputEditText
    private val rootCheckExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toggleButton = findViewById(R.id.btnToggleOverlay)
        sizeLabel = findViewById(R.id.overlaySizeLabel)
        blockCommandsInput = findViewById(R.id.blockCommandsInput)
        unblockCommandsInput = findViewById(R.id.unblockCommandsInput)

        loadFirewallCommands()
        findViewById<Button>(R.id.btnSaveCommands).setOnClickListener { saveFirewallCommands() }
        toggleButton.setOnClickListener { toggleOverlayService() }
        setupSizeControl()
        updateToggleButton()
        checkRootStatus()
    }

    private fun loadFirewallCommands() {
        val prefs = getSharedPreferences(FloatingOverlayService.PREFERENCES, MODE_PRIVATE)
        blockCommandsInput.setText(
            prefs.getString(FloatingOverlayService.PREF_BLOCK_COMMANDS, Config.DEFAULT_BLOCK_COMMANDS)
                ?: Config.DEFAULT_BLOCK_COMMANDS
        )
        unblockCommandsInput.setText(
            prefs.getString(FloatingOverlayService.PREF_UNBLOCK_COMMANDS, Config.DEFAULT_UNBLOCK_COMMANDS)
                ?: Config.DEFAULT_UNBLOCK_COMMANDS
        )
    }

    /** Save the text exactly as entered; command validation/execution is not performed here. */
    private fun saveFirewallCommands() {
        getSharedPreferences(FloatingOverlayService.PREFERENCES, MODE_PRIVATE).edit()
            .putString(FloatingOverlayService.PREF_BLOCK_COMMANDS, blockCommandsInput.text?.toString().orEmpty())
            .putString(FloatingOverlayService.PREF_UNBLOCK_COMMANDS, unblockCommandsInput.text?.toString().orEmpty())
            .apply()
        Toast.makeText(this, R.string.commands_saved, Toast.LENGTH_SHORT).show()
    }

    private fun setupSizeControl() {
        val prefs = getSharedPreferences(FloatingOverlayService.PREFERENCES, MODE_PRIVATE)
        val initialSize = prefs.getInt(FloatingOverlayService.PREF_SIZE_DP, FloatingOverlayService.DEFAULT_SIZE_DP)
            .coerceIn(FloatingOverlayService.MIN_SIZE_DP, FloatingOverlayService.MAX_SIZE_DP)
        sizeSeekBar = findViewById(R.id.overlaySizeSeekBar)
        sizeSeekBar.max = FloatingOverlayService.MAX_SIZE_DP - FloatingOverlayService.MIN_SIZE_DP
        sizeSeekBar.progress = initialSize - FloatingOverlayService.MIN_SIZE_DP
        updateSizeLabel(initialSize)
        sizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val sizeDp = FloatingOverlayService.MIN_SIZE_DP + progress
                updateSizeLabel(sizeDp)
                if (fromUser) {
                    prefs.edit().putInt(FloatingOverlayService.PREF_SIZE_DP, sizeDp).apply()
                    if (FloatingOverlayService.isRunning) startService(
                        Intent(this@MainActivity, FloatingOverlayService::class.java)
                            .setAction(FloatingOverlayService.ACTION_UPDATE_SIZE)
                            .putExtra(FloatingOverlayService.EXTRA_SIZE_DP, sizeDp)
                    )
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun toggleOverlayService() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val intent = Intent(this, FloatingOverlayService::class.java)
        if (FloatingOverlayService.isRunning) stopService(intent)
        else ContextCompat.startForegroundService(this, intent)
        updateToggleButton()
    }

    private fun checkRootStatus() {
        val rootStatus = findViewById<TextView>(R.id.rootStatus)
        rootStatus.setText(R.string.root_status_checking)
        rootCheckExecutor.execute {
            val hasRoot = try {
                val process = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
                if (!process.waitFor(ROOT_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    false
                } else {
                    val output = process.inputStream.bufferedReader().use { it.readText() }
                    process.exitValue() == 0 && output.contains("uid=0")
                }
            } catch (_: Exception) {
                false
            }
            runOnUiThread {
                if (!isFinishing) rootStatus.setText(
                    if (hasRoot) R.string.root_status_available else R.string.root_status_unavailable
                )
            }
        }
    }

    private fun updateToggleButton() {
        if (::toggleButton.isInitialized) toggleButton.setText(
            if (FloatingOverlayService.isRunning) R.string.disable_button else R.string.enable_button
        )
    }

    private fun updateSizeLabel(sizeDp: Int) {
        sizeLabel.text = getString(R.string.overlay_size_value, sizeDp)
    }

    override fun onResume() {
        super.onResume()
        updateToggleButton()
    }

    override fun onDestroy() {
        rootCheckExecutor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val ROOT_CHECK_TIMEOUT_SECONDS = 5L
    }
}
