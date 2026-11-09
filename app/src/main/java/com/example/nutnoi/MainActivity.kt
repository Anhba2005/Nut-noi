package com.example.nutnoi

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toggleButton = findViewById<Button>(R.id.btnToggleOverlay)
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
            updateToggleButton(toggleButton)
        }
    }

    override fun onResume() {
        super.onResume()
        findViewById<Button>(R.id.btnToggleOverlay)?.let(::updateToggleButton)
    }

    private fun updateToggleButton(button: Button) {
        button.setText(
            if (FloatingOverlayService.isRunning) R.string.disable_button
            else R.string.enable_button
        )
    }
}
