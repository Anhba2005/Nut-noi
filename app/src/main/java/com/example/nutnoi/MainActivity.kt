package com.example.nutnoi

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.net.NetworkInterface
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var toggleButton: Button
    private lateinit var applyButton: Button
    private lateinit var sizeLabel: TextView
    private lateinit var sizeSeekBar: SeekBar
    private lateinit var deviceInfo: TextView
    private lateinit var rootStatus: TextView
    private lateinit var interfaceChoices: LinearLayout
    private val interfaceCheckBoxes = mutableMapOf<String, CheckBox>()
    private val rootCheckExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        toggleButton = findViewById(R.id.btnToggleOverlay)
        applyButton = findViewById(R.id.btnApplyFirewall)
        sizeLabel = findViewById(R.id.overlaySizeLabel)
        sizeSeekBar = findViewById(R.id.overlaySizeSeekBar)
        deviceInfo = findViewById(R.id.deviceInfo)
        rootStatus = findViewById(R.id.rootStatus)
        interfaceChoices = findViewById(R.id.interfaceChoices)

        detectDeviceAndInterfaces()
        toggleButton.setOnClickListener { toggleOverlayService() }
        applyButton.setOnClickListener { confirmAndApplyFirewall() }
        setupSizeControl()
        updateToggleButton()
        checkRootStatus()
    }

    private fun setupSizeControl() {
        val prefs = getSharedPreferences(FloatingOverlayService.PREFERENCES, MODE_PRIVATE)
        val initialSize = prefs.getInt(FloatingOverlayService.PREF_SIZE_DP, FloatingOverlayService.DEFAULT_SIZE_DP)
            .coerceIn(FloatingOverlayService.MIN_SIZE_DP, FloatingOverlayService.MAX_SIZE_DP)
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

    private fun detectDeviceAndInterfaces() {
        val soc = detectSoc()
        val allInterfaces = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().map { it.name }.distinct().sorted()
        } catch (_: Exception) {
            emptyList()
        }
        val candidates = allInterfaces.filter(::isLikelyNetworkInterface)
        val prefs = getSharedPreferences(FloatingOverlayService.PREFERENCES, MODE_PRIVATE)
        val selected = prefs.getStringSet(FloatingOverlayService.PREF_SELECTED_INTERFACES, emptySet()).orEmpty()
        interfaceChoices.removeAllViews()
        interfaceCheckBoxes.clear()

        if (candidates.isEmpty()) {
            deviceInfo.text = getString(R.string.device_info_no_interfaces, soc, allInterfaces.joinToString(", ").ifBlank { getString(R.string.interfaces_none) })
            applyButton.isEnabled = false
            return
        }
        deviceInfo.text = getString(
            R.string.device_info,
            soc,
            allInterfaces.joinToString(", ").ifBlank { getString(R.string.interfaces_none) }
        )
        candidates.forEach { iface ->
            val box = CheckBox(this).apply {
                text = getString(interfaceLabel(iface), iface)
                isChecked = if (selected.isEmpty()) isCommonDefaultInterface(iface) else iface in selected
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            interfaceCheckBoxes[iface] = box
            interfaceChoices.addView(box)
        }
        applyButton.isEnabled = true
    }

    private fun isLikelyNetworkInterface(name: String): Boolean {
        val value = name.lowercase(Locale.ROOT)
        return listOf("wlan", "wifi", "rmnet", "ccmni", "ccemni", "wwan", "pdp", "eth", "en").any(value::startsWith)
    }

    private fun isCommonDefaultInterface(name: String): Boolean {
        val value = name.lowercase(Locale.ROOT)
        return value.startsWith("wlan") || value.startsWith("wifi") || value.startsWith("rmnet") ||
            value.startsWith("ccmni") || value.startsWith("ccemni") || value.startsWith("wwan") || value.startsWith("pdp")
    }

    private fun interfaceLabel(name: String): Int {
        val value = name.lowercase(Locale.ROOT)
        return if (value.startsWith("wlan") || value.startsWith("wifi")) R.string.interface_wifi_label
        else if (value.startsWith("rmnet") || value.startsWith("ccmni") || value.startsWith("ccemni") ||
            value.startsWith("wwan") || value.startsWith("pdp")) R.string.interface_mobile_label
        else R.string.interface_other_label
    }

    private fun detectSoc(): String {
        val identifiers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Build.SOC_MODEL.orEmpty())
            add(Build.HARDWARE.orEmpty())
            add(Build.BOARD.orEmpty())
            add(Build.DEVICE.orEmpty())
        }.joinToString(" ").lowercase(Locale.ROOT)
        return when {
            "mediatek" in identifiers || Regex("(^|[^a-z])mt[0-9]{3,5}([^0-9]|$)").containsMatchIn(identifiers) -> getString(R.string.soc_mediatek)
            "qcom" in identifiers || "qualcomm" in identifiers || "snapdragon" in identifiers -> getString(R.string.soc_snapdragon)
            else -> getString(R.string.soc_unknown)
        }
    }

    private fun confirmAndApplyFirewall() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val selected = interfaceCheckBoxes.filterValues { it.isChecked }.keys
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.select_interface_first, Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.firewall_warning_title)
            .setMessage(R.string.firewall_global_warning)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.apply) { _, _ ->
                getSharedPreferences(FloatingOverlayService.PREFERENCES, MODE_PRIVATE).edit()
                    .putStringSet(FloatingOverlayService.PREF_SELECTED_INTERFACES, selected.toSet())
                    .apply()
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, FloatingOverlayService::class.java)
                        .setAction(FloatingOverlayService.ACTION_APPLY_FIREWALL)
                )
            }
            .show()
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
        rootStatus.setText(R.string.root_status_checking)
        rootCheckExecutor.execute {
            val hasRoot = try {
                val process = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
                if (!process.waitFor(ROOT_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly(); false
                } else {
                    val output = process.inputStream.bufferedReader().use { it.readText() }
                    process.exitValue() == 0 && output.contains("uid=0")
                }
            } catch (_: Exception) { false }
            runOnUiThread {
                if (!isFinishing && ::rootStatus.isInitialized) {
                    rootStatus.setText(if (hasRoot) R.string.root_status_available else R.string.root_status_unavailable)
                }
            }
        }
    }

    private fun updateToggleButton() {
        if (::toggleButton.isInitialized) toggleButton.setText(
            if (FloatingOverlayService.isRunning) R.string.disable_button else R.string.enable_button
        )
    }

    private fun updateSizeLabel(sizeDp: Int) { sizeLabel.text = getString(R.string.overlay_size_value, sizeDp) }

    override fun onResume() { super.onResume(); updateToggleButton() }
    override fun onDestroy() { rootCheckExecutor.shutdownNow(); super.onDestroy() }

    companion object { private const val ROOT_CHECK_TIMEOUT_SECONDS = 5L }
}
