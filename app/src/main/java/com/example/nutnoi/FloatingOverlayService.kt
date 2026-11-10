package com.example.nutnoi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class FloatingOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val commandExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var overlayView: View? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var isDragging = false
    private var holdTriggered = false
    private var isLocked = false
    private var isBlocked = false
    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }

    private val longPressRunnable = Runnable {
        holdTriggered = true
        isLocked = !isLocked
        getPreferences().edit().putBoolean(PREF_LOCKED, isLocked).apply()
        val message = getString(if (isLocked) R.string.overlay_locked else R.string.overlay_unlocked)
        overlayView?.let { view ->
            view.contentDescription = message
            view.announceForAccessibility(message)
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, createNotification())
        if (overlayView == null) showOverlay()
        isRunning = true

        when (intent?.action) {
            ACTION_UPDATE_SIZE -> {
                val sizeDp = intent.getIntExtra(EXTRA_SIZE_DP, readSizeDp())
                    .coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)
                getPreferences().edit().putInt(PREF_SIZE_DP, sizeDp).apply()
                updateOverlaySize(sizeDp)
            }
            ACTION_APPLY_FIREWALL -> applySelectedFirewall()
        }
        return START_STICKY
    }

    private fun showOverlay() {
        windowManager = getSystemService(WindowManager::class.java)
        val view = LayoutInflater.from(this).inflate(R.layout.floating_button, null)
        isLocked = getPreferences().getBoolean(PREF_LOCKED, false)
        isBlocked = getPreferences().getBoolean(PREF_BLOCKED, false)
        updateOverlayAppearance(view)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val sizePx = dpToPx(readSizeDp())
        val layoutParams = WindowManager.LayoutParams(
            sizePx, sizePx, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = getPreferences().getInt(PREF_X, 24)
            y = getPreferences().getInt(PREF_Y, 180)
        }
        overlayLayoutParams = layoutParams
        overlayView = view
        windowManager.addView(view, layoutParams)

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = layoutParams.x
                    startY = layoutParams.y
                    isDragging = false
                    holdTriggered = false
                    mainHandler.removeCallbacks(longPressRunnable)
                    mainHandler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (!isLocked && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        mainHandler.removeCallbacks(longPressRunnable)
                        isDragging = true
                    }
                    if (isDragging && !isLocked) {
                        layoutParams.x = (startX + dx).coerceAtLeast(0)
                        layoutParams.y = (startY + dy).coerceAtLeast(0)
                        windowManager.updateViewLayout(view, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    if (holdTriggered) {
                        // Long press toggled move lock.
                    } else if (isDragging && !isLocked) {
                        getPreferences().edit().putInt(PREF_X, layoutParams.x)
                            .putInt(PREF_Y, layoutParams.y).apply()
                    } else if (!isDragging) {
                        toggleFirewall()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    true
                }
                else -> false
            }
        }
    }

    private fun selectedInterfaces(): List<String> =
        getPreferences().getStringSet(PREF_SELECTED_INTERFACES, emptySet())?.toList().orEmpty()

    private fun applySelectedFirewall() {
        val selected = selectedInterfaces()
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.select_interface_first, Toast.LENGTH_LONG).show()
            return
        }
        val prefs = getPreferences()
        val previouslyApplied = prefs.getStringSet(PREF_APPLIED_INTERFACES, emptySet())?.toList().orEmpty()
        val commands = buildList {
            if (prefs.getBoolean(PREF_BLOCKED, false) && previouslyApplied.isNotEmpty()) {
                addAll(Config.unblockCommands(previouslyApplied))
            }
            addAll(Config.blockCommands(selected))
        }
        runFirewallCommands(commands) { success ->
            if (success) {
                prefs.edit().putBoolean(PREF_BLOCKED, true)
                    .putStringSet(PREF_APPLIED_INTERFACES, selected.toSet()).apply()
                isBlocked = true
                updateOverlayAppearance()
            }
        }
    }

    private fun toggleFirewall() {
        val prefs = getPreferences()
        if (!isBlocked) {
            applySelectedFirewall()
            return
        }
        val applied = prefs.getStringSet(PREF_APPLIED_INTERFACES, emptySet())?.toList().orEmpty()
        if (applied.isEmpty()) {
            isBlocked = false
            prefs.edit().putBoolean(PREF_BLOCKED, false).apply()
            updateOverlayAppearance()
            return
        }
        runFirewallCommands(Config.unblockCommands(applied)) { success ->
            if (success) {
                prefs.edit().putBoolean(PREF_BLOCKED, false).remove(PREF_APPLIED_INTERFACES).apply()
                isBlocked = false
                updateOverlayAppearance()
            }
        }
    }

    private fun updateOverlayAppearance(view: View? = overlayView) {
        view?.findViewById<TextView>(R.id.floatingLabel)?.setBackgroundResource(
            if (isBlocked) R.drawable.floating_button_blue else R.drawable.floating_button_red
        )
        view?.contentDescription = getString(if (isLocked) R.string.overlay_locked else R.string.overlay_unlocked)
    }

    private fun runFirewallCommands(commands: List<String>, onComplete: (Boolean) -> Unit) {
        commandExecutor.execute {
            var process: Process? = null
            val success = try {
                process = ProcessBuilder("su").redirectErrorStream(true).start()
                BufferedWriter(OutputStreamWriter(process.outputStream)).use { writer ->
                    writer.write("set -e"); writer.newLine()
                    commands.forEach { writer.write(it); writer.newLine() }
                    writer.write("exit"); writer.newLine(); writer.flush()
                }
                if (!process.waitFor(ROOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    false
                } else {
                    val output = process.inputStream.bufferedReader().use { it.readText() }
                    val ok = process.exitValue() == 0
                    if (!ok) Log.e(TAG, "Global firewall command failed: $output")
                    ok
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not apply global firewall rules", e)
                process?.destroyForcibly()
                false
            }
            mainHandler.post {
                if (isRunning) Toast.makeText(
                    this,
                    getString(if (success) R.string.firewall_updated else R.string.firewall_root_denied),
                    Toast.LENGTH_LONG
                ).show()
                onComplete(success)
            }
        }
    }

    private fun updateOverlaySize(sizeDp: Int) {
        val view = overlayView ?: return
        val params = overlayLayoutParams ?: return
        val px = dpToPx(sizeDp)
        params.width = px
        params.height = px
        windowManager.updateViewLayout(view, params)
    }

    private fun readSizeDp() = getPreferences().getInt(PREF_SIZE_DP, DEFAULT_SIZE_DP)
        .coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)

    private fun dpToPx(sizeDp: Int) = (sizeDp * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private fun createNotification(): Notification {
        val channelId = "nutnoi_overlay_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val stopIntent = PendingIntent.getService(
            this, 0, Intent(this, FloatingOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .addAction(0, getString(R.string.disable_button), stopIntent)
            .build()
    }

    private fun getPreferences() = getSharedPreferences(PREFERENCES, MODE_PRIVATE)

    override fun onDestroy() {
        mainHandler.removeCallbacks(longPressRunnable)
        val prefs = getPreferences()
        val applied = prefs.getStringSet(PREF_APPLIED_INTERFACES, emptySet())?.toList().orEmpty()
        if (prefs.getBoolean(PREF_BLOCKED, false) && applied.isNotEmpty()) {
            commandExecutor.execute { runFirewallCommandsForCleanup(Config.unblockCommands(applied)) }
            prefs.edit().putBoolean(PREF_BLOCKED, false).remove(PREF_APPLIED_INTERFACES).apply()
        }
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        overlayLayoutParams = null
        isRunning = false
        commandExecutor.shutdown()
        super.onDestroy()
    }

    private fun runFirewallCommandsForCleanup(commands: List<String>) {
        var process: Process? = null
        try {
            process = ProcessBuilder("su").redirectErrorStream(true).start()
            BufferedWriter(OutputStreamWriter(process.outputStream)).use { writer ->
                commands.forEach { writer.write(it); writer.newLine() }
                writer.write("exit"); writer.newLine(); writer.flush()
            }
            if (!process.waitFor(ROOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) process.destroyForcibly()
        } catch (e: Exception) {
            Log.e(TAG, "Could not remove global firewall rules during shutdown", e)
            process?.destroyForcibly()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.example.nutnoi.action.STOP_OVERLAY"
        const val ACTION_UPDATE_SIZE = "com.example.nutnoi.action.UPDATE_OVERLAY_SIZE"
        const val ACTION_APPLY_FIREWALL = "com.example.nutnoi.action.APPLY_FIREWALL"
        const val EXTRA_SIZE_DP = "overlay_size_dp"
        const val PREFERENCES = "overlay_position"
        const val PREF_SIZE_DP = "size_dp"
        const val PREF_SELECTED_INTERFACES = "selected_interfaces"
        const val PREF_APPLIED_INTERFACES = "applied_interfaces"
        const val DEFAULT_SIZE_DP = 72
        const val MIN_SIZE_DP = 48
        const val MAX_SIZE_DP = 144
        private const val PREF_BLOCKED = "app_blocked"
        private const val NOTIFICATION_ID = 101
        private const val PREF_X = "x"
        private const val PREF_Y = "y"
        private const val PREF_LOCKED = "locked"
        private const val LONG_PRESS_MS = 2_000L
        private const val ROOT_TIMEOUT_SECONDS = 15L
        private const val TAG = "FloatingOverlayService"

        @Volatile var isRunning: Boolean = false
            private set
    }
}
