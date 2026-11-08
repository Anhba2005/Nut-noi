package com.example.nutnoi

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etShell1 = findViewById<EditText>(R.id.editTextShell1)
        val etShell2 = findViewById<EditText>(R.id.editTextShell2)
        val cbRoot = findViewById<CheckBox>(R.id.checkBoxNeedRoot)
        val btnToggle = findViewById<Button>(R.id.btnToggleOverlay)

        btnToggle.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Config.cmd1 = etShell1.text.toString()
                Config.cmd2 = etShell2.text.toString()
                Config.isSuRequired = cbRoot.isChecked

                val serviceIntent = Intent(this, FloatingOverlayService::class.java)
                if (FloatingOverlayService.isRunning) {
                    stopService(serviceIntent)
                    btnToggle.text = "Bật nút nổi"
                } else {
                    startService(serviceIntent)
                    btnToggle.text = "Tắt nút nổi"
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val btnToggle = findViewById<Button>(R.id.btnToggleOverlay)
        if (FloatingOverlayService.isRunning) {
            btnToggle.text = "Tắt nút nổi"
        } else {
            btnToggle.text = "Bật nút nổi"
        }
    }
}
