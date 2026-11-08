package com.example.nutnoi

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var permissionStatus: TextView
    private lateinit var requestPermissionButton: Button
    private lateinit var toggleOverlayButton: Button
    private lateinit var sizeLabel: TextView
    private lateinit var sizeSeekBar: SeekBar

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        permissionStatus = findViewById(R.id.permissionStatus)
        requestPermissionButton = findViewById(R.id.requestPermissionButton)
        toggleOverlayButton = findViewById(R.id.toggleOverlayButton)
        sizeLabel = findViewById(R.id.sizeLabel)
        sizeSeekBar = findViewById(R.id.sizeSeekBar)

        val savedSize = getOverlayPreferences().getInt(
            FloatingOverlayService.PREF_SIZE_DP,
            FloatingOverlayService.DEFAULT_SIZE_DP
        )
        sizeSeekBar.progress = savedSize - FloatingOverlayService.MIN_SIZE_DP
        updateSizeLabel(savedSize)

        sizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val sizeDp = FloatingOverlayService.MIN_SIZE_DP + progress
                updateSizeLabel(sizeDp)
                if (fromUser) {
                    getOverlayPreferences().edit()
                        .putInt(FloatingOverlayService.PREF_SIZE_DP, sizeDp)
                        .apply()
                    updateRunningOverlaySize(sizeDp)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        requestPermissionButton.setOnClickListener {
            if (hasOverlayPermission()) {
                Toast.makeText(this, getString(R.string.granted), Toast.LENGTH_SHORT).show()
            } else {
                requestOverlayPermission()
            }
        }

        toggleOverlayButton.setOnClickListener {
            if (FloatingOverlayService.isRunning) {
                stopService(Intent(this, FloatingOverlayService::class.java))
                updateUi()
            } else if (!hasOverlayPermission()) {
                Toast.makeText(this, getString(R.string.overlay_permission_missing), Toast.LENGTH_LONG).show()
                requestOverlayPermission()
            } else {
                val serviceIntent = Intent(this, FloatingOverlayService::class.java)
                ContextCompat.startForegroundService(this, serviceIntent)
                updateUi()
            }
        }

        updateUi()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun updateRunningOverlaySize(sizeDp: Int) {
        if (!FloatingOverlayService.isRunning) return
        val intent = Intent(this, FloatingOverlayService::class.java)
            .setAction(FloatingOverlayService.ACTION_UPDATE_SIZE)
            .putExtra(FloatingOverlayService.EXTRA_SIZE_DP, sizeDp)
        startService(intent)
    }

    private fun updateSizeLabel(sizeDp: Int) {
        sizeLabel.text = getString(R.string.overlay_size_value, sizeDp)
    }

    private fun getOverlayPreferences() =
        getSharedPreferences(FloatingOverlayService.PREFERENCES, MODE_PRIVATE)

    private fun updateUi() {
        val permitted = hasOverlayPermission()
        permissionStatus.text = getString(
            if (permitted) R.string.granted else R.string.not_granted
        )
        requestPermissionButton.isEnabled = !permitted
        requestPermissionButton.text = getString(
            if (permitted) R.string.permission_granted_button else R.string.request_permission
        )
        toggleOverlayButton.isEnabled = permitted || FloatingOverlayService.isRunning
        toggleOverlayButton.text = getString(
            if (FloatingOverlayService.isRunning) R.string.disable_button else R.string.enable_button
        )
    }
}
