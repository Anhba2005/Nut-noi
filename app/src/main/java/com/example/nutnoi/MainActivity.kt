package com.example.nutnoi

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var appsGrid: GridLayout
    private lateinit var dock: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var apps: List<LauncherApp>
    private var columns = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appsGrid = findViewById(R.id.appsGrid)
        dock = findViewById(R.id.dock)
        searchInput = findViewById(R.id.searchInput)
        columns = (resources.displayMetrics.widthPixels / (88 * resources.displayMetrics.density)).toInt()
            .coerceIn(3, 6)

        findViewById<TextView>(R.id.dateLabel).text =
            SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())
        findViewById<TextView>(R.id.searchAction).setOnClickListener { searchWeb() }
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchWeb()
                true
            } else false
        }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderApps(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        if (::appsGrid.isInitialized) loadApps()
    }

    private fun loadApps() {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = packageManager.queryIntentActivities(launcherIntent, 0)
            .mapNotNull { info ->
                val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                try {
                    LauncherApp(
                        label = info.loadLabel(packageManager).toString(),
                        packageName = packageName,
                        icon = info.loadIcon(packageManager)
                    )
                } catch (_: Exception) {
                    null
                }
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }

        renderDock()
        renderApps(searchInput.text?.toString().orEmpty())
    }

    private fun renderDock() {
        dock.removeAllViews()
        if (apps.isEmpty()) return

        val preferredPackages = listOf(
            "com.google.android.dialer", "com.android.dialer",
            "com.google.android.apps.messaging", "com.android.messaging",
            "com.google.android.chrome", "com.android.chrome",
            "com.google.android.GoogleCamera", "com.android.camera2", "com.sec.android.app.camera"
        )
        val selected = mutableListOf<LauncherApp>()
        preferredPackages.forEach { packageName ->
            apps.firstOrNull { it.packageName == packageName }?.let { app ->
                if (selected.none { it.packageName == app.packageName } && selected.size < 5) selected.add(app)
            }
        }
        apps.forEach { app ->
            if (selected.size < 5 && selected.none { it.packageName == app.packageName }) selected.add(app)
        }
        selected.forEach { app ->
            dock.addView(createAppCell(app, compact = true), LinearLayout.LayoutParams(0, dp(80), 1f))
        }
    }

    private fun renderApps(query: String) {
        if (!::appsGrid.isInitialized || !::apps.isInitialized) return
        appsGrid.removeAllViews()
        appsGrid.columnCount = columns
        val filtered = apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
        filtered.forEachIndexed { index, app ->
            val cell = createAppCell(app, compact = false)
            val params = GridLayout.LayoutParams(
                GridLayout.spec(index / columns),
                GridLayout.spec(index % columns, 1f)
            ).apply {
                width = 0
                height = dp(104)
                setMargins(dp(2), dp(3), dp(2), dp(3))
            }
            appsGrid.addView(cell, params)
        }
    }

    private fun createAppCell(app: LauncherApp, compact: Boolean): LinearLayout {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = app.label
            setOnClickListener { launchApp(app) }
        }
        val iconSize = if (compact) 48 else 52
        val icon = ImageView(this).apply {
            setImageDrawable(app.icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = app.label
        }
        cell.addView(icon, LinearLayout.LayoutParams(dp(iconSize), dp(iconSize)))
        if (!compact) {
            val label = TextView(this).apply {
                text = app.label
                setTextColor(Color.rgb(38, 40, 43))
                textSize = 11f
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(3), dp(5), dp(3), 0)
            }
            cell.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(25)))
        }
        return cell
    }

    private fun launchApp(app: LauncherApp) {
        try {
            packageManager.getLaunchIntentForPackage(app.packageName)?.let(::startActivity)
                ?: Toast.makeText(this, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun searchWeb() {
        val query = searchInput.text?.toString()?.trim().orEmpty()
        if (query.isEmpty()) {
            searchInput.requestFocus()
            Toast.makeText(this, R.string.search_hint, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.browser_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class LauncherApp(val label: String, val packageName: String, val icon: Drawable)
}
