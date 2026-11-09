package com.example.nutnoi

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var toggleButton: Button
    private lateinit var sizeLabel: TextView
    private lateinit var sizeSeekBar: SeekBar
    private lateinit var rootStatus: TextView
    private val rootCheckExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toggleButton = findViewById(R.id.btnToggleOverlay)
        sizeLabel = findViewById(R.id.overlaySizeLabel)
        sizeSeekBar = findViewById(R.id.overlaySizeSeekBar)
        rootStatus = findViewById(R.id.rootStatus)

        toggleButton.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val permissionIntent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(permissionIntent)
                return@setOnClickListener
            }

            val serviceIntent = Intent(this, FloatingOverlayService::class.java)
            if (FloatingOverlayService.isRunning) {
                stopService(serviceIntent)
            } else {
                ContextCompat.startForegroundService(this, serviceIntent)
            }
            updateToggleButton()
        }

        val prefs = getSharedPreferences(FloatingOverlayService.PREFERENCES, MODE_PRIVATE)
        val initialSize = prefs.getInt(
            FloatingOverlayService.PREF_SIZE_DP,
            FloatingOverlayService.DEFAULT_SIZE_DP
        ).coerceIn(FloatingOverlayService.MIN_SIZE_DP, FloatingOverlayService.MAX_SIZE_DP)
        sizeSeekBar.max = FloatingOverlayService.MAX_SIZE_DP - FloatingOverlayService.MIN_SIZE_DP
        sizeSeekBar.progress = initialSize - FloatingOverlayService.MIN_SIZE_DP
        updateSizeLabel(initialSize)

        sizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val sizeDp = FloatingOverlayService.MIN_SIZE_DP + progress
                updateSizeLabel(sizeDp)
                if (fromUser) {
                    prefs.edit().putInt(FloatingOverlayService.PREF_SIZE_DP, sizeDp).apply()
                    if (FloatingOverlayService.isRunning) {
                        val resizeIntent = Intent(this@MainActivity, FloatingOverlayService::class.java)
                            .setAction(FloatingOverlayService.ACTION_UPDATE_SIZE)
                            .putExtra(FloatingOverlayService.EXTRA_SIZE_DP, sizeDp)
                        startService(resizeIntent)
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        updateToggleButton()
    }

    override fun onResume() {
        super.onResume()
        if (::toggleButton.isInitialized) updateToggleButton()
        if (::rootStatus.isInitialized) checkRootStatus()
    }

    private fun checkRootStatus() {
        rootStatus.setText(R.string.root_status_checking)
        rootCheckExecutor.execute {
            val hasRoot = try {
                val process = ProcessBuilder("su", "-c", "id")
                    .redirectErrorStream(true)
                    .start()
                val finished = process.waitFor(ROOT_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (!finished) {
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
                if (!isFinishing && ::rootStatus.isInitialized) {
                    rootStatus.setText(
                        if (hasRoot) R.string.root_status_available
                        else R.string.root_status_unavailable
                    )
                }
            }
        }
    }

    private fun updateToggleButton() {
        toggleButton.setText(
            if (FloatingOverlayService.isRunning) R.string.disable_button
            else R.string.enable_button
        )
    }

    private fun updateSizeLabel(sizeDp: Int) {
        sizeLabel.text = getString(R.string.overlay_size_value, sizeDp)
    }

    override fun onDestroy() {
        rootCheckExecutor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val ROOT_CHECK_TIMEOUT_SECONDS = 5L
    }
}
