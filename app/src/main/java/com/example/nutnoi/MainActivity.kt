package com.anhba.nutnoi

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var edtCmdGreen: EditText
    private lateinit var edtCmdRed: EditText
    private lateinit var tvRootStatus: TextView
    private lateinit var tvSizeLabel: TextView
    private lateinit var sbSize: SeekBar
    private lateinit var btnSave: Button
    private lateinit var btnToggleFloating: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        edtCmdGreen = findViewById(R.id.edtCmdGreen)
        edtCmdRed = findViewById(R.id.edtCmdRed)
        tvRootStatus = findViewById(R.id.tvRootStatus)
        tvSizeLabel = findViewById(R.id.tvSizeLabel)
        sbSize = findViewById(R.id.sbSize)
        btnSave = findViewById(R.id.btnSave)
        btnToggleFloating = findViewById(R.id.btnToggleFloating)

        // KHÓA CỨNG MÀU CHỮ BẰNG CODE (Trị dứt điểm lỗi chữ tàng hình trên Xiaomi)
        edtCmdGreen.setTextColor(Color.parseColor("#00FF66")) // Chữ xanh lá neon
        edtCmdGreen.setHintTextColor(Color.parseColor("#888888"))

        edtCmdRed.setTextColor(Color.parseColor("#FF5252"))   // Chữ đỏ neon
        edtCmdRed.setHintTextColor(Color.parseColor("#888888"))

        val prefs = getSharedPreferences("NutNoiPrefs", Context.MODE_PRIVATE)
        edtCmdGreen.setText(prefs.getString("CMD_GREEN", ""))
        edtCmdRed.setText(prefs.getString("CMD_RED", ""))
        val savedSize = prefs.getInt("BUTTON_SIZE", 72)
        sbSize.progress = savedSize
        tvSizeLabel.text = "Kích thước nút nổi: $savedSize dp"

        // Kiểm tra quyền Root
        Thread {
            val hasRoot = ShellUtils.isRootAvailable()
            runOnUiThread {
                tvRootStatus.text = if (hasRoot) "Trạng thái root: Đã cấp quyền." else "Trạng thái root: Chưa có quyền Root!"
            }
        }.start()

        sbSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, progress: Int, p2: Boolean) {
                val size = if (progress < 40) 40 else progress
                tvSizeLabel.text = "Kích thước nút nổi: $size dp"
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        btnSave.setOnClickListener {
            val size = if (sbSize.progress < 40) 40 else sbSize.progress
            prefs.edit()
                .putString("CMD_GREEN", edtCmdGreen.text.toString())
                .putString("CMD_RED", edtCmdRed.text.toString())
                .putInt("BUTTON_SIZE", size)
                .apply()
            Toast.makeText(this, "Đã lưu cấu hình thành công!", Toast.LENGTH_SHORT).show()
        }

        btnToggleFloating.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
                Toast.makeText(this, "Vui lòng cấp quyền hiển thị trên các ứng dụng khác", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val serviceIntent = Intent(this, FloatingButtonService::class.java)
            if (FloatingButtonService.isRunning) {
                stopService(serviceIntent)
                btnToggleFloating.text = "Bật nút nổi"
            } else {
                startService(serviceIntent)
                btnToggleFloating.text = "Tắt nút nổi"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        btnToggleFloating.text = if (FloatingButtonService.isRunning) "Tắt nút nổi" else "Bật nút nổi"
    }
}
