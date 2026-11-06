package com.example.nutnoi

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var permissionStatus: TextView
    private lateinit var requestPermissionButton: Button
    private lateinit var toggleOverlayButton: Button

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
                androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)
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
