package com.anhba.launcher

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

class MainActivity : AppCompatActivity() {
    private lateinit var appsPager: ViewPager2
    private lateinit var pageIndicator: TextView
    private lateinit var dock: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var pagerAdapter: AppsPagerAdapter
    private lateinit var apps: List<LauncherApp>
    private var columns = 4
    private val rowsPerPage = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appsPager = findViewById(R.id.appsPager)
        pageIndicator = findViewById(R.id.pageIndicator)
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
        pagerAdapter = AppsPagerAdapter()
        appsPager.adapter = pagerAdapter
        appsPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updatePageIndicator(position)
        })
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                showApps(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        if (::appsPager.isInitialized) loadApps()
    }

    private fun loadApps() {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = packageManager.queryIntentActivities(launcherIntent, 0)
            .mapNotNull { info ->
                val activity = info.activityInfo ?: return@mapNotNull null
                try {
                    LauncherApp(
                        label = info.loadLabel(packageManager).toString(),
                        packageName = activity.packageName,
                        activityName = activity.name,
                        icon = info.loadIcon(packageManager)
                    )
                } catch (_: Exception) {
                    null
                }
            }
            .distinctBy { it.packageName to it.activityName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }

        renderDock()
        showApps(searchInput.text?.toString().orEmpty())
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

    private fun showApps(query: String) {
        if (!::pagerAdapter.isInitialized || !::apps.isInitialized) return
        val filtered = apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
        pagerAdapter.submitApps(filtered)
        appsPager.setCurrentItem(0, false)
        updatePageIndicator(0)
    }

    private fun updatePageIndicator(position: Int) {
        if (!::pageIndicator.isInitialized || !::pagerAdapter.isInitialized) return
        val pageCount = pagerAdapter.itemCount
        pageIndicator.text = getString(R.string.page_indicator, position + 1, pageCount)
        pageIndicator.contentDescription = getString(R.string.page_indicator_accessibility, position + 1, pageCount)
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
        val icon = ImageView(this).apply {
            setImageDrawable(app.icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = app.label
        }
        cell.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))
        if (!compact) {
            val label = TextView(this).apply {
                text = app.label
                setTextColor(Color.rgb(38, 40, 43))
                textSize = 11f
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(3), dp(4), dp(3), 0)
            }
            cell.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(24)))
        }
        return cell
    }

    private fun launchApp(app: LauncherApp) {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).apply {
                component = ComponentName(app.packageName, app.activityName)
            })
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

    private data class LauncherApp(
        val label: String,
        val packageName: String,
        val activityName: String,
        val icon: Drawable
    )

    private inner class AppsPagerAdapter : RecyclerView.Adapter<AppsPagerAdapter.PageHolder>() {
        private var pageApps: List<LauncherApp> = emptyList()
        private val appsPerPage get() = columns * rowsPerPage

        override fun getItemCount(): Int = maxOf(1, ceil(pageApps.size.toDouble() / appsPerPage).toInt())

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val grid = GridLayout(this@MainActivity).apply {
                columnCount = columns
                useDefaultMargins = false
                alignmentMode = GridLayout.ALIGN_BOUNDS
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            return PageHolder(grid)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            val grid = holder.grid
            grid.removeAllViews()
            val page = pageApps.drop(position * appsPerPage).take(appsPerPage)
            page.forEachIndexed { index, app ->
                val params = GridLayout.LayoutParams(
                    GridLayout.spec(index / columns),
                    GridLayout.spec(index % columns, 1f)
                ).apply {
                    width = 0
                    height = dp(88)
                    setMargins(dp(1), dp(2), dp(1), dp(2))
                }
                grid.addView(createAppCell(app, compact = false), params)
            }
            if (page.isEmpty()) {
                val emptyMessage = TextView(this@MainActivity).apply {
                    text = getString(R.string.no_apps_found)
                    gravity = Gravity.CENTER
                    setTextColor(Color.rgb(85, 88, 91))
                    textSize = 14f
                }
                grid.addView(emptyMessage, GridLayout.LayoutParams().apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = dp(88)
                    columnSpec = GridLayout.spec(0, 1f)
                })
            }
        }

        fun submitApps(appList: List<LauncherApp>) {
            pageApps = appList
            notifyDataSetChanged()
        }

        inner class PageHolder(val grid: GridLayout) : RecyclerView.ViewHolder(grid)
    }
}
