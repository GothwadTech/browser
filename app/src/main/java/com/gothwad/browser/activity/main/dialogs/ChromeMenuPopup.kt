package com.gothwad.browser.activity.main.dialogs

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import com.gothwad.browser.BuildConfig
import com.gothwad.browser.Config
import com.gothwad.browser.R
import com.gothwad.browser.activity.downloads.DownloadsActivity
import com.gothwad.browser.activity.lock.AppLockActivity
import com.gothwad.browser.activity.main.MainActivity
import com.gothwad.browser.activity.main.openInNewTab
import com.gothwad.browser.activity.main.showClipboardActivity
import com.gothwad.browser.activity.main.showHistoryActivity
import com.gothwad.browser.activity.main.showSettingsDialog
import com.gothwad.browser.activity.main.toggleIncognitoMode
import com.gothwad.browser.notes.clipboard.ClipboardActivity
import com.gothwad.browser.singleton.AppLockManager

class ChromeMenuPopup(private val activity: MainActivity) {

    private val popupWindow: PopupWindow
    private val contentView: View
    private val popupWidth: Int

    init {
        contentView = LayoutInflater.from(activity).inflate(R.layout.popup_chrome_menu, null)
        popupWidth = (185 * activity.resources.displayMetrics.density).toInt()
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
                // Focus back to anchor or webview
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

        // Top 3 Quick Action Icons: Lock, Page Info, Refresh
        val btnAppLock: ImageButton = contentView.findViewById(R.id.btnMenuAppLock)
        val btnPageInfo: ImageButton = contentView.findViewById(R.id.btnMenuPageInfo)
        val btnRefresh: ImageButton = contentView.findViewById(R.id.btnMenuRefresh)

        bindMenuItem(btnAppLock) {
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

        bindMenuItem(btnPageInfo) {
            val url = currentTab?.url ?: ""
            val isSecure = url.startsWith("https://")
            AlertDialog.Builder(activity)
                .setTitle(if (isSecure) "🔒 Secure Connection" else "ℹ️ Page Info")
                .setMessage("URL: ${if (url.isEmpty()) "Home Page" else url}\n\nSecurity: ${if (isSecure) "Encrypted Connection (HTTPS / SSL Active)" else "Unencrypted Connection (HTTP)"}\n\nCookies: Active\nJavaScript: Enabled")
                .setPositiveButton("OK", null)
                .show()
        }

        bindMenuItem(btnRefresh) {
            currentTab?.webEngine?.reload()
        }

        // List Actions
        // 1. New Tab
        bindMenuItem(contentView.findViewById(R.id.btnMenuNewTab)) {
            activity.openInNewTab(Config.HOME_PAGE_URL, needToHideMenuOverlay = true)
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

        // 7. Share...
        bindMenuItem(contentView.findViewById(R.id.btnMenuShare)) {
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

        // 8. Desktop Site Checkbox
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
            Toast.makeText(activity, if (willBeDesktop) "Desktop mode enabled (${newZoom}% zoom)" else "Mobile mode enabled (${newZoom}% zoom)", Toast.LENGTH_SHORT).show()
        }

        // 9. Help & feedback
        bindMenuItem(contentView.findViewById(R.id.btnMenuHelp)) {
            AlertDialog.Builder(activity)
                .setTitle("Gothwad Browser")
                .setMessage("Modern Fast TV Web Browser with Native Clipboard, File Manager & Notes.\n\nVersion: ${BuildConfig.VERSION_NAME}\nDeveloper: gothwadtech@gmail.com")
                .setPositiveButton("OK", null)
                .show()
        }
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
