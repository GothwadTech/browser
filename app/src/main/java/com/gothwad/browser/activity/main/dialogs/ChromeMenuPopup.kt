package com.gothwad.browser.activity.main.dialogs

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import com.gothwad.browser.BuildConfig
import com.gothwad.browser.Config
import com.gothwad.browser.R
import com.gothwad.browser.activity.lock.AppLockActivity
import com.gothwad.browser.activity.main.MainActivity
import com.gothwad.browser.activity.main.openInNewTab
import com.gothwad.browser.activity.main.showDownloads
import com.gothwad.browser.activity.main.showFavoritesDialog
import com.gothwad.browser.activity.main.showHistoryActivity
import com.gothwad.browser.activity.main.showSettingsDialog
import com.gothwad.browser.activity.main.showTabsRowDialog
import com.gothwad.browser.activity.main.toggleIncognitoMode
import com.gothwad.browser.model.FavoriteItem
import com.gothwad.browser.singleton.AppDatabase
import com.gothwad.browser.singleton.AppLockManager
import com.gothwad.browser.webengine.WebEngineFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class ChromeMenuPopup(private val activity: MainActivity) {

    private val popupWindow: PopupWindow
    private val contentView: View
    private val popupWidth: Int

    init {
        contentView = LayoutInflater.from(activity).inflate(R.layout.popup_chrome_menu, null)
        popupWidth = (250 * activity.resources.displayMetrics.density).toInt()
        popupWindow = PopupWindow(
            contentView,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 24f
            setOnDismissListener {
                // Return focus cleanly
            }
        }

        setupViews()
    }

    private fun bindMenuItem(view: View, action: () -> Unit) {
        view.setOnClickListener {
            dismiss()
            action()
        }
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_BUTTON_A -> {
                        view.performClick()
                        true
                    }
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_ESCAPE,
                    KeyEvent.KEYCODE_BUTTON_B -> {
                        dismiss()
                        true
                    }
                    else -> false
                }
            } else false
        }
    }

    private fun setupViews() {
        val currentTab = activity.tabsModel.currentTab.value
        val config = activity.config
        val currentUrl = currentTab?.url ?: ""
        val isWebPage = currentUrl.isNotEmpty() && currentUrl != Config.HOME_PAGE_URL && currentUrl != Config.HOME_URL_ALIAS

        // Top 5 Quick Action Icons: Forward, Star Bookmark, Download, Page Info, Refresh
        val btnForward: ImageButton = contentView.findViewById(R.id.btnMenuForward)
        val btnBookmarkPage: ImageButton = contentView.findViewById(R.id.btnMenuBookmarkPage)
        val btnDownloadPage: ImageButton = contentView.findViewById(R.id.btnMenuDownloadPage)
        val btnPageInfo: ImageButton = contentView.findViewById(R.id.btnMenuPageInfo)
        val btnRefresh: ImageButton = contentView.findViewById(R.id.btnMenuRefresh)

        // 1. Forward
        val canGoForward = currentTab?.webEngine?.canGoForward() == true || (!currentTab?.lastUrlBeforeHome.isNullOrEmpty())
        btnForward.isEnabled = canGoForward
        btnForward.alpha = if (canGoForward) 1.0f else 0.4f
        bindMenuItem(btnForward) {
            val tab = activity.tabsModel.currentTab.value ?: return@bindMenuItem
            if (!tab.lastUrlBeforeHome.isNullOrEmpty()) {
                val restoreUrl = tab.lastUrlBeforeHome!!
                tab.lastUrlBeforeHome = null
                activity.navigate(restoreUrl)
            } else if (tab.webEngine.canGoForward()) {
                tab.webEngine.goForward()
            }
        }

        // 2. Star / Bookmark Current Page
        bindMenuItem(btnBookmarkPage) {
            if (isWebPage) {
                val title = currentTab?.title?.ifEmpty { currentUrl } ?: currentUrl
                CoroutineScope(Dispatchers.IO).launch {
                    val favDao = AppDatabase.db.favoritesDao()
                    val existing = favDao.getAll(false).find { it.url == currentUrl }
                    if (existing == null) {
                        val newItem = FavoriteItem().apply {
                            this.title = title
                            this.url = currentUrl
                            this.homePageBookmark = false
                        }
                        favDao.insert(newItem)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(activity, "Bookmark added: $title", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(activity, "Already in bookmarks", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                Toast.makeText(activity, "No active webpage to bookmark", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. Download Page
        bindMenuItem(btnDownloadPage) {
            if (isWebPage) {
                try {
                    val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager
                    if (dm != null) {
                        val request = android.app.DownloadManager.Request(Uri.parse(currentUrl)).apply {
                            setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "page_${System.currentTimeMillis()}.html")
                        }
                        dm.enqueue(request)
                        Toast.makeText(activity, "Download started...", Toast.LENGTH_SHORT).show()
                    } else {
                        activity.showDownloads(activity.vb.ibMenu)
                    }
                } catch (e: Exception) {
                    activity.showDownloads(activity.vb.ibMenu)
                }
            } else {
                activity.showDownloads(activity.vb.ibMenu)
            }
        }

        // 4. Page Info & SSL Security Details
        bindMenuItem(btnPageInfo) {
            val isSecure = currentUrl.startsWith("https://")
            AlertDialog.Builder(activity)
                .setTitle(if (isSecure) "🔒 Secure Connection" else "ℹ️ Page Info")
                .setMessage("URL: ${if (currentUrl.isEmpty()) "Home Page" else currentUrl}\n\nSecurity: ${if (isSecure) "Encrypted Connection (HTTPS / SSL Active)" else "Unencrypted Connection (HTTP)"}\n\nCookies: Active\nJavaScript: Enabled\nDesktop Mode: ${if (config.isDesktopMode()) "ON" else "OFF"}")
                .setPositiveButton("OK", null)
                .show()
        }

        // 5. Refresh Page
        bindMenuItem(btnRefresh) {
            currentTab?.webEngine?.reload()
        }

        // List Actions
        // 1. New Tab
        bindMenuItem(contentView.findViewById(R.id.btnMenuNewTab)) {
            activity.openInNewTab(activity.settingsModel.homePage, needToHideMenuOverlay = false, navigateImmediately = true)
        }

        // 2. Incognito Tab (New Incognito Tab or Close Incognito Mode)
        val tvIncognito = contentView.findViewById<TextView>(R.id.tvMenuIncognito)
        val isIncognito = activity.config.incognitoMode
        if (isIncognito) {
            tvIncognito?.text = "Close Incognito mode"
        } else {
            tvIncognito?.text = "New Incognito tab"
        }
        bindMenuItem(contentView.findViewById(R.id.btnMenuIncognito)) {
            activity.toggleIncognitoMode(andSwitchProcess = true)
        }

        // 3. History
        bindMenuItem(contentView.findViewById(R.id.btnMenuHistory)) {
            activity.showHistoryActivity()
        }

        // 4. Delete Browsing Data
        bindMenuItem(contentView.findViewById(R.id.btnMenuDeleteBrowsingData)) {
            showClearBrowsingDataDialog()
        }

        // 5. Downloads
        bindMenuItem(contentView.findViewById(R.id.btnMenuDownloads)) {
            activity.showDownloads(activity.vb.ibMenu)
        }

        // 6. Bookmarks
        bindMenuItem(contentView.findViewById(R.id.btnMenuBookmarks)) {
            activity.showFavoritesDialog(activity.vb.ibMenu)
        }

        // 7. Recent Tabs
        bindMenuItem(contentView.findViewById(R.id.btnMenuRecentTabs)) {
            activity.showTabsRowDialog()
        }

        // 8. Share...
        bindMenuItem(contentView.findViewById(R.id.btnMenuShare)) {
            if (isWebPage) {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, currentUrl)
                    putExtra(Intent.EXTRA_SUBJECT, currentTab?.title ?: "Web Page")
                    type = "text/plain"
                }
                activity.startActivity(Intent.createChooser(sendIntent, "Share URL"))
            } else {
                Toast.makeText(activity, "No active webpage to share", Toast.LENGTH_SHORT).show()
            }
        }

        // 9. Find in page
        bindMenuItem(contentView.findViewById(R.id.btnMenuFindInPage)) {
            showFindInPageDialog()
        }

        // 10. Translate...
        bindMenuItem(contentView.findViewById(R.id.btnMenuTranslate)) {
            if (isWebPage) {
                try {
                    val encoded = URLEncoder.encode(currentUrl, "UTF-8")
                    val translateUrl = "https://translate.google.com/translate?sl=auto&tl=en&u=$encoded"
                    activity.navigate(translateUrl)
                } catch (e: Exception) {
                    Toast.makeText(activity, "Unable to translate page", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(activity, "No webpage to translate", Toast.LENGTH_SHORT).show()
            }
        }

        // 11. Show Reading Mode
        bindMenuItem(contentView.findViewById(R.id.btnMenuReadingMode)) {
            if (isWebPage) {
                val webView = currentTab?.webEngine?.getView() as? WebView
                if (webView != null) {
                    val js = """
                        (function() {
                            if (document.body.classList.contains('tv-reading-mode')) {
                                document.body.classList.remove('tv-reading-mode');
                                var el = document.getElementById('tv-reading-style');
                                if (el) el.remove();
                            } else {
                                document.body.classList.add('tv-reading-mode');
                                var s = document.createElement('style');
                                s.id = 'tv-reading-style';
                                s.innerHTML = 'body.tv-reading-mode { max-width: 800px !important; margin: 0 auto !important; padding: 24px !important; font-size: 20px !important; line-height: 1.6 !important; background: #1a1a1a !important; color: #e0e0e0 !important; }';
                                document.head.appendChild(s);
                            }
                        })();
                    """.trimIndent()
                    webView.evaluateJavascript(js, null)
                    Toast.makeText(activity, "Reading mode toggled", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(activity, "Reading mode not available on this page", Toast.LENGTH_SHORT).show()
            }
        }

        // 12. Install and create shortcut
        bindMenuItem(contentView.findViewById(R.id.btnMenuInstallShortcut)) {
            if (isWebPage) {
                val title = currentTab?.title?.ifEmpty { currentUrl } ?: currentUrl
                CoroutineScope(Dispatchers.IO).launch {
                    val favDao = AppDatabase.db.favoritesDao()
                    val newItem = FavoriteItem().apply {
                        this.title = title
                        this.url = currentUrl
                        this.homePageBookmark = true
                    }
                    favDao.insert(newItem)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(activity, "Shortcut created on Home & Bookmarks: $title", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(activity, "No webpage to create shortcut for", Toast.LENGTH_SHORT).show()
            }
        }

        // 13. Desktop Site Checkbox
        val btnDesktop: View = contentView.findViewById(R.id.btnMenuDesktop)
        val cbDesktop: CheckBox = contentView.findViewById(R.id.cbDesktopSite)
        val isDesktop = config.isDesktopMode()
        cbDesktop.isChecked = isDesktop

        bindMenuItem(btnDesktop) {
            val willBeDesktop = !cbDesktop.isChecked
            cbDesktop.isChecked = willBeDesktop
            config.desktopMode.value = willBeDesktop
            config.userAgentString.value = if (willBeDesktop) Config.DESKTOP_UA else null
            val newZoom = config.getEffectiveZoom(willBeDesktop)
            for (tab in activity.tabsModel.tabsStates) {
                tab.webEngine.userAgentString = if (willBeDesktop) Config.DESKTOP_UA else null
                tab.webEngine.setPageZoom(newZoom)
            }
            currentTab?.webEngine?.reload()
            Toast.makeText(activity, if (willBeDesktop) "Desktop site enabled" else "Mobile site enabled", Toast.LENGTH_SHORT).show()
        }

        // 14. Settings
        bindMenuItem(contentView.findViewById(R.id.btnMenuSettings)) {
            activity.showSettingsDialog(activity.vb.ibMenu)
        }

        // 15. Help & feedback
        bindMenuItem(contentView.findViewById(R.id.btnMenuHelp)) {
            AlertDialog.Builder(activity)
                .setTitle("Gothwad TV Browser")
                .setMessage("Android TV & Tablet Web Browser.\n\nVersion: ${BuildConfig.VERSION_NAME}\nDeveloper: gothwadtech@gmail.com\n\nFast, lightweight, ad-free browsing optimized for remote and touch.")
                .setPositiveButton("OK", null)
                .show()
        }

        // 16. App Lock / Security
        bindMenuItem(contentView.findViewById(R.id.btnMenuAppLock)) {
            if (AppLockManager.isLockEnabled(activity)) {
                AppLockManager.setSessionUnlocked(false)
                activity.startActivity(Intent(activity, AppLockActivity::class.java))
            } else {
                val dlg = com.gothwad.browser.activity.lock.TvPinDialog(
                    context = activity,
                    mode = com.gothwad.browser.activity.lock.TvPinDialog.Mode.CREATE,
                    onSuccess = {
                        AppLockManager.setSessionUnlocked(false)
                        activity.startActivity(Intent(activity, AppLockActivity::class.java))
                    }
                )
                dlg.show()
            }
        }
    }

    private fun showClearBrowsingDataDialog() {
        val options = arrayOf(
            "Clear all browsing history",
            "Clear cache and cookies",
            "Clear everything (History, Cache & Cookies)"
        )
        AlertDialog.Builder(activity)
            .setTitle("Delete Browsing Data")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        CoroutineScope(Dispatchers.IO).launch {
                            AppDatabase.db.historyDao().deleteAll()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(activity, "Browsing history cleared", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    1 -> {
                        CoroutineScope(Dispatchers.IO).launch {
                            WebEngineFactory.clearCache(activity)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(activity, "Cache & cookies cleared", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    2 -> {
                        CoroutineScope(Dispatchers.IO).launch {
                            AppDatabase.db.historyDao().deleteAll()
                            WebEngineFactory.clearCache(activity)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(activity, "All browsing data cleared", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showFindInPageDialog() {
        val currentTab = activity.tabsModel.currentTab.value ?: return
        val webView = currentTab.webEngine.getView() as? WebView ?: return

        val input = EditText(activity).apply {
            hint = "Search on webpage..."
            setSingleLine(true)
            setPadding(32, 24, 32, 24)
        }

        AlertDialog.Builder(activity)
            .setTitle("Find in page")
            .setView(input)
            .setPositiveButton("Find") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) {
                    webView.findAllAsync(query)
                    Toast.makeText(activity, "Searching for: $query", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun show(anchorView: View) {
        val density = activity.resources.displayMetrics.density
        val anchorWidth = if (anchorView.width > 0) anchorView.width else (38 * density).toInt()
        val xOffset = anchorWidth - popupWidth
        val yOffset = (2 * density).toInt()

        popupWindow.showAsDropDown(anchorView, xOffset, yOffset)

        contentView.post {
            contentView.findViewById<View>(R.id.btnMenuNewTab)?.requestFocus()
        }
    }

    fun dismiss() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }
}
