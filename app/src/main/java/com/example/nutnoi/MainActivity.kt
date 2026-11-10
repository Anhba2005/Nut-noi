package com.example.nutnoi

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var toggleButton: Button
    private lateinit var chooseAppButton: Button
    private lateinit var selectedAppLabel: TextView
    private lateinit var sizeLabel: TextView
    private lateinit var sizeSeekBar: SeekBar
    private lateinit var wifiCheckBox: CheckBox
    private lateinit var mobileDataCheckBox: CheckBox
    private lateinit var rootStatus: TextView
    private val rootCheckExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toggleButton = findViewById(R.id.btnToggleOverlay)
        chooseAppButton = findViewById(R.id.btnChooseApp)
        selectedAppLabel = findViewById(R.id.selectedAppLabel)
        sizeLabel = findViewById(R.id.overlaySizeLabel)
        sizeSeekBar = findViewById(R.id.overlaySizeSeekBar)
        wifiCheckBox = findViewById(R.id.blockWifiCheckBox)
        mobileDataCheckBox = findViewById(R.id.blockMobileDataCheckBox)
        rootStatus = findViewById(R.id.rootStatus)

        val prefs = getSharedPreferences(FloatingOverlayService.PREFERENCES, MODE_PRIVATE)
        wifiCheckBox.isChecked = prefs.getBoolean(FloatingOverlayService.PREF_BLOCK_WIFI, true)
        mobileDataCheckBox.isChecked = prefs.getBoolean(FloatingOverlayService.PREF_BLOCK_MOBILE, true)
        wifiCheckBox.setOnCheckedChangeListener { _, _ -> updateNetworkOptions(prefs) }
        mobileDataCheckBox.setOnCheckedChangeListener { _, _ -> updateNetworkOptions(prefs) }

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

        chooseAppButton.setOnClickListener { showAppPicker() }

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

        updateSelectedAppLabel()
        updateToggleButton()
    }

    private fun updateNetworkOptions(prefs: android.content.SharedPreferences) {
        val oldBlockWifi = prefs.getBoolean(FloatingOverlayService.PREF_BLOCK_WIFI, true)
        val oldBlockMobile = prefs.getBoolean(FloatingOverlayService.PREF_BLOCK_MOBILE, true)
        val newBlockWifi = wifiCheckBox.isChecked
        val newBlockMobile = mobileDataCheckBox.isChecked
        if (oldBlockWifi == newBlockWifi && oldBlockMobile == newBlockMobile) return

        prefs.edit()
            .putBoolean(FloatingOverlayService.PREF_BLOCK_WIFI, newBlockWifi)
            .putBoolean(FloatingOverlayService.PREF_BLOCK_MOBILE, newBlockMobile)
            .apply()

        if (FloatingOverlayService.isRunning) {
            val intent = Intent(this, FloatingOverlayService::class.java)
                .setAction(FloatingOverlayService.ACTION_UPDATE_NETWORKS)
                .putExtra(FloatingOverlayService.EXTRA_OLD_BLOCK_WIFI, oldBlockWifi)
                .putExtra(FloatingOverlayService.EXTRA_OLD_BLOCK_MOBILE, oldBlockMobile)
            startService(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::toggleButton.isInitialized) updateToggleButton()
        if (::rootStatus.isInitialized) checkRootStatus()
    }

    private fun showAppPicker() {
        val apps = getLaunchableApps()
        if (apps.isEmpty()) {
            AlertDialog.Builder(this)
                .setMessage(R.string.no_apps_found)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val names = apps.map { "${it.label} (${it.packageName})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_app_title)
            .setItems(names) { _, which ->
                val selected = apps[which]
                getSharedPreferences(FloatingOverlayService.PREFERENCES, MODE_PRIVATE)
                    .edit()
                    .putString(FloatingOverlayService.PREF_SELECTED_PACKAGE, selected.packageName)
                    .apply()
                updateSelectedAppLabel()
                if (FloatingOverlayService.isRunning) {
                    startService(
                        Intent(this, FloatingOverlayService::class.java)
                            .setAction(FloatingOverlayService.ACTION_SELECT_APP)
                    )
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun getLaunchableApps(): List<AppChoice> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        }
        return resolved.mapNotNull { item ->
            val info = item.activityInfo?.applicationInfo ?: return@mapNotNull null
            if (info.packageName == packageName || info.uid < 0) return@mapNotNull null
            AppChoice(
                label = packageManager.getApplicationLabel(info).toString(),
                packageName = info.packageName
            )
        }.distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun updateSelectedAppLabel() {
        val selectedPackage = getSharedPreferences(FloatingOverlayService.PREFERENCES, MODE_PRIVATE)
            .getString(FloatingOverlayService.PREF_SELECTED_PACKAGE, null)
        val appLabel = selectedPackage?.let { packageName ->
            try {
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getApplicationInfo(packageName, 0)
                }
                packageManager.getApplicationLabel(info).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
        selectedAppLabel.text = getString(R.string.selected_app_value, appLabel ?: getString(R.string.no_app_selected))
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

    private data class AppChoice(val label: String, val packageName: String)

    companion object {
        private const val ROOT_CHECK_TIMEOUT_SECONDS = 5L
    }
}
