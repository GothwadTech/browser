package com.gothwad.browser.activity.main.dialogs

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import com.gothwad.browser.Config
import com.gothwad.browser.R
import com.gothwad.browser.activity.main.MainActivity
import com.gothwad.browser.activity.main.applyWebPageZoom
import com.gothwad.browser.activity.main.openInNewTab
import com.gothwad.browser.activity.main.view.home.HomeCardAdapter
import com.gothwad.browser.activity.main.view.home.HomeData
import com.gothwad.browser.activity.main.view.home.HomeShortcutItem
import com.gothwad.browser.activity.main.view.home.NativeHomeView
import com.gothwad.browser.activity.main.zoomWebIn
import com.gothwad.browser.activity.main.zoomWebOut

class WebsiteMenuPopup(private val activity: MainActivity) {

    private val popupWindow: PopupWindow
    private val contentView: View
    private val popupWidth: Int

    init {
        contentView = LayoutInflater.from(activity).inflate(R.layout.popup_website_menu, null)
        popupWidth = (220 * activity.resources.displayMetrics.density).toInt()
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
        }

        setupViews()
    }

    private fun bindItem(view: View, action: () -> Unit) {
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

        // Top 3 Quick Action Icons: Page Info, Bookmark, Refresh
        val btnPageInfo: ImageButton = contentView.findViewById(R.id.btnWebMenuPageInfo)
        val btnBookmark: ImageButton = contentView.findViewById(R.id.btnWebMenuBookmark)
        val btnRefresh: ImageButton = contentView.findViewById(R.id.btnWebMenuRefresh)

        bindItem(btnPageInfo) {
            val url = currentTab?.url ?: ""
            val isSecure = url.startsWith("https://")
            AlertDialog.Builder(activity)
                .setTitle(if (isSecure) "🔒 Secure Connection" else "ℹ️ Page Info")
                .setMessage("URL: ${if (url.isEmpty()) "Home Page" else url}\n\nSecurity: ${if (isSecure) "Encrypted Connection (HTTPS / SSL Active)" else "Unencrypted Connection (HTTP)"}\n\nCookies: Active\nJavaScript: Enabled")
                .setPositiveButton("OK", null)
                .show()
        }

        bindItem(btnBookmark) {
            val url = currentTab?.url ?: ""
            val title = currentTab?.title ?: "Bookmarked Site"
            if (url.isNotEmpty() && url != Config.HOME_PAGE_URL && url != Config.HOME_URL_ALIAS) {
                val currentList = NativeHomeView.loadUserBookmarks(activity).toMutableList()
                val exists = currentList.any { it.url == url }
                if (!exists) {
                    currentList.add(
                        HomeShortcutItem(
                            title = title,
                            url = url,
                            iconDrawableRes = HomeData.getIconForUrlOrTitle(url, title),
                            isUserBookmark = true
                        )
                    )
                    NativeHomeView.saveUserBookmarks(activity, currentList)
                    Toast.makeText(activity, "Added '$title' to Shortcuts", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(activity, "Already in Shortcuts", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(activity, "Cannot bookmark empty page", Toast.LENGTH_SHORT).show()
            }
        }

        bindItem(btnRefresh) {
            currentTab?.webEngine?.reload()
        }

        // Desktop Site Checkbox setup
        val btnDesktop: View = contentView.findViewById(R.id.btnWebMenuDesktop)
        val cbDesktop: CheckBox = contentView.findViewById(R.id.cbWebMenuDesktop)
        val isDesktop = config.desktopMode.value || config.userAgentString.value?.contains("Windows") == true
        cbDesktop.isChecked = isDesktop

        // 🔎 Website Zoom Controls
        val btnZoomOut: ImageButton = contentView.findViewById(R.id.btnWebZoomOut)
        val btnZoomIn: ImageButton = contentView.findViewById(R.id.btnWebZoomIn)
        val btnZoomReset: Button = contentView.findViewById(R.id.btnWebZoomReset)
        val tvZoomPercent: TextView = contentView.findViewById(R.id.tvWebZoomPercent)

        fun updateZoomDisplay() {
            val isDesk = cbDesktop.isChecked
            val zoom = config.getEffectiveZoom(isDesk)
            val modeLabel = if (isDesk) "Desktop" else "Mobile"
            tvZoomPercent.text = "$zoom% ($modeLabel)"
        }

        updateZoomDisplay()

        btnZoomOut.setOnClickListener {
            activity.zoomWebOut()
            updateZoomDisplay()
        }

        btnZoomIn.setOnClickListener {
            activity.zoomWebIn()
            updateZoomDisplay()
        }

        btnZoomReset.setOnClickListener {
            val isDesk = cbDesktop.isChecked
            activity.applyWebPageZoom(100, isDesk)
            updateZoomDisplay()
            Toast.makeText(activity, "Zoom reset to 100%", Toast.LENGTH_SHORT).show()
        }

        // 1. New Tab
        bindItem(contentView.findViewById(R.id.btnWebMenuNewTab)) {
            activity.openInNewTab(Config.HOME_PAGE_URL, needToHideMenuOverlay = true)
        }

        // 2. Desktop Site Checkbox
        bindItem(btnDesktop) {
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
            updateZoomDisplay()
            Toast.makeText(activity, if (willBeDesktop) "Desktop mode enabled (${newZoom}% zoom)" else "Mobile mode enabled (${newZoom}% zoom)", Toast.LENGTH_SHORT).show()
        }

        // 3. Find In Page
        bindItem(contentView.findViewById(R.id.btnWebMenuFindInPage)) {
            showFindInPageDialog()
        }

        // 4. Share...
        bindItem(contentView.findViewById(R.id.btnWebMenuShare)) {
            val url = currentTab?.url
            if (!url.isNullOrEmpty() && url != Config.HOME_PAGE_URL) {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, url)
                    type = "text/plain"
                }
                activity.startActivity(Intent.createChooser(sendIntent, "Share URL"))
            } else {
                Toast.makeText(activity, "No active page to share", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showFindInPageDialog() {
        val currentTab = activity.tabsModel.currentTab.value ?: return
        val webView = currentTab.webEngine.getView() as? WebView ?: return

        val input = EditText(activity).apply {
            hint = "Search text on webpage..."
            setSingleLine(true)
            setPadding(30, 20, 30, 20)
        }

        AlertDialog.Builder(activity)
            .setTitle("🔍 Find in page")
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
            contentView.findViewById<View>(R.id.btnWebZoomIn)?.requestFocus()
        }
    }

    fun dismiss() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }
}
