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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var isDragging = false
    private var holdTriggered = false
    private var isLocked = false
    // Start red so the first tap changes the button to blue.
    private var isBlue = false
    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }

    private val longPressRunnable = Runnable {
        holdTriggered = true
        isLocked = !isLocked
        getPreferences().edit().putBoolean(PREF_LOCKED, isLocked).apply()
        overlayView?.contentDescription = getString(
            if (isLocked) R.string.overlay_locked else R.string.overlay_unlocked
        )
        Toast.makeText(
            this,
            getString(if (isLocked) R.string.overlay_locked else R.string.overlay_unlocked),
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_UPDATE_SIZE) {
            val sizeDp = intent.getIntExtra(EXTRA_SIZE_DP, readSizeDp())
                .coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)
            getPreferences().edit().putInt(PREF_SIZE_DP, sizeDp).apply()
            updateOverlaySize(sizeDp)
            return START_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification())
        if (overlayView == null) showOverlay()
        isRunning = true
        return START_STICKY
    }

    private fun showOverlay() {
        windowManager = getSystemService(WindowManager::class.java)
        val view = LayoutInflater.from(this).inflate(R.layout.floating_button, null)
        val label = view.findViewById<TextView>(R.id.floatingLabel)
        label.setBackgroundResource(R.drawable.floating_button_red)
        isLocked = getPreferences().getBoolean(PREF_LOCKED, false)
        view.contentDescription = getString(
            if (isLocked) R.string.overlay_locked else R.string.overlay_unlocked
        )

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val sizePx = dpToPx(readSizeDp())
        val layoutParams = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
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
                        // A long press toggled the move lock; do not treat release as a tap.
                    } else if (isDragging && !isLocked) {
                        getPreferences().edit()
                            .putInt(PREF_X, layoutParams.x)
                            .putInt(PREF_Y, layoutParams.y)
                            .apply()
                    } else if (!isDragging) {
                        isBlue = !isBlue
                        label.setBackgroundResource(
                            if (isBlue) R.drawable.floating_button_blue
                            else R.drawable.floating_button_red
                        )
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

    private fun updateOverlaySize(sizeDp: Int) {
        val view = overlayView ?: return
        val params = overlayLayoutParams ?: return
        val sizePx = dpToPx(sizeDp)
        params.width = sizePx
        params.height = sizePx
        windowManager.updateViewLayout(view, params)
    }

    private fun readSizeDp(): Int = getPreferences()
        .getInt(PREF_SIZE_DP, DEFAULT_SIZE_DP)
        .coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)

    private fun dpToPx(sizeDp: Int): Int =
        (sizeDp * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private fun createNotification(): Notification {
        val channelId = "nutnoi_overlay_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, FloatingOverlayService::class.java).setAction(ACTION_STOP),
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
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
        overlayLayoutParams = null
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.example.nutnoi.action.STOP_OVERLAY"
        const val ACTION_UPDATE_SIZE = "com.example.nutnoi.action.UPDATE_OVERLAY_SIZE"
        const val EXTRA_SIZE_DP = "overlay_size_dp"
        const val PREFERENCES = "overlay_position"
        const val PREF_SIZE_DP = "size_dp"
        const val DEFAULT_SIZE_DP = 72
        const val MIN_SIZE_DP = 48
        const val MAX_SIZE_DP = 144
        private const val NOTIFICATION_ID = 101
        private const val PREF_X = "x"
        private const val PREF_Y = "y"
        private const val PREF_LOCKED = "locked"
        private const val LONG_PRESS_MS = 2_000L

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
