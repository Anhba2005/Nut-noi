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

class MainActivity : AppCompatActivity() {

    private lateinit var toggleButton: Button
    private lateinit var sizeLabel: TextView
    private lateinit var sizeSeekBar: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toggleButton = findViewById(R.id.btnToggleOverlay)
        sizeLabel = findViewById(R.id.overlaySizeLabel)
        sizeSeekBar = findViewById(R.id.overlaySizeSeekBar)

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
}
