package com.gothwad.browser.activity.main.dialogs

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import com.gothwad.browser.BuildConfig
import com.gothwad.browser.Config
import com.gothwad.browser.R
import com.gothwad.browser.activity.lock.AppLockActivity
import com.gothwad.browser.activity.lock.TvPinDialog
import com.gothwad.browser.activity.main.MainActivity
import com.gothwad.browser.activity.main.showClipboardActivity
import com.gothwad.browser.activity.main.showDownloads
import com.gothwad.browser.activity.main.showFavoritesDialog
import com.gothwad.browser.activity.main.showFileManager
import com.gothwad.browser.activity.main.showHistoryActivity
import com.gothwad.browser.activity.main.showNotes
import com.gothwad.browser.activity.main.showSettingsDialog
import com.gothwad.browser.activity.main.toggleIncognitoMode
import com.gothwad.browser.singleton.AppLockManager

class BrowserSidebarPopup(private val activity: MainActivity) {

    private val popupWindow: PopupWindow
    private val rootContainer: FrameLayout
    private val contentView: View

    init {
        rootContainer = object : FrameLayout(activity) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_BACK,
                        KeyEvent.KEYCODE_ESCAPE,
                        KeyEvent.KEYCODE_BUTTON_B -> {
                            dismiss()
                            return true
                        }
                    }
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        contentView = LayoutInflater.from(activity).inflate(R.layout.dialog_browser_side_menu, rootContainer, true)

        val popupWidth = SidebarHelper.calculateLeftSidebarWidth(activity)

        popupWindow = PopupWindow(
            rootContainer,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 24f
            animationStyle = R.style.SideDrawerLeftAnimation
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
        val config = activity.config
        val currentTab = activity.tabsModel.currentTab.value

        // Circular Back Button
        val btnBack: ImageButton? = contentView.findViewById(R.id.btnSideMenuBack)
        btnBack?.setOnClickListener {
            dismiss()
        }
        btnBack?.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_BUTTON_A,
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_ESCAPE -> {
                        dismiss()
                        true
                    }
                    else -> false
                }
            } else false
        }

        // 1. Privacy Protection / App Lock
        contentView.findViewById<View>(R.id.btnSidePrivacy)?.let { btn ->
            bindItem(btn) {
                if (AppLockManager.isLockEnabled(activity)) {
                    AppLockManager.setSessionUnlocked(false)
                    activity.startActivity(Intent(activity, AppLockActivity::class.java))
                } else {
                    val dlg = TvPinDialog(
                        context = activity,
                        mode = TvPinDialog.Mode.CREATE,
                        onSuccess = {
                            AppLockManager.setSessionUnlocked(false)
                            activity.startActivity(Intent(activity, AppLockActivity::class.java))
                        }
                    )
                    dlg.show()
                }
            }
        }

        // 2. Incognito Mode
        val tvIncognito = contentView.findViewById<TextView>(R.id.tvSideIncognitoTitle)
        val tvIncognitoSub = contentView.findViewById<TextView>(R.id.tvSideIncognitoSub)
        if (config.incognitoMode) {
            tvIncognito?.text = "Exit incognito"
            tvIncognitoSub?.text = "Switch to regular browsing mode"
        } else {
            tvIncognito?.text = "Start incognito"
            tvIncognitoSub?.text = "Private browsing without history"
        }
        contentView.findViewById<View>(R.id.btnSideIncognito)?.let { btn ->
            bindItem(btn) {
                activity.toggleIncognitoMode(andSwitchProcess = true)
            }
        }

        // 3. Share
        contentView.findViewById<View>(R.id.btnSideShare)?.let { btn ->
            bindItem(btn) {
                val url = currentTab?.url
                if (!url.isNullOrEmpty() && url != Config.HOME_PAGE_URL) {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, url)
                        putExtra(Intent.EXTRA_SUBJECT, currentTab.title ?: "Link")
                        type = "text/plain"
                    }
                    activity.startActivity(Intent.createChooser(sendIntent, "Share URL"))
                } else {
                    Toast.makeText(activity, "No active page to share", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 4. Bookmarks (Left Sidebar)
        contentView.findViewById<View>(R.id.btnSideBookmarks)?.let { btn ->
            bindItem(btn) {
                activity.showFavoritesDialog(btn)
            }
        }

        // 5. History (Left Sidebar)
        contentView.findViewById<View>(R.id.btnSideHistory)?.let { btn ->
            bindItem(btn) {
                activity.showHistoryActivity(btn)
            }
        }

        // 6. Downloads (Right Sidebar)
        contentView.findViewById<View>(R.id.btnSideDownloads)?.let { btn ->
            bindItem(btn) {
                activity.showDownloads()
            }
        }

        // 7. File Manager (Right Sidebar)
        contentView.findViewById<View>(R.id.btnSideFileManager)?.let { btn ->
            bindItem(btn) {
                activity.showFileManager()
            }
        }

        // 8. Notes (Right Sidebar)
        contentView.findViewById<View>(R.id.btnSideNotes)?.let { btn ->
            bindItem(btn) {
                activity.showNotes()
            }
        }

        // 9. Native Clipboard (Right Sidebar)
        contentView.findViewById<View>(R.id.btnSideClipboard)?.let { btn ->
            bindItem(btn) {
                activity.showClipboardActivity()
            }
        }

        // 10. Desktop View Mode
        val ivDesktopCheck = contentView.findViewById<ImageView>(R.id.ivSideDesktopCheck)
        val isDesktop = config.isDesktopMode()
        ivDesktopCheck?.setImageResource(
            if (isDesktop) R.drawable.ic_check_box_checked else R.drawable.ic_check_box_outline
        )
        contentView.findViewById<View>(R.id.btnSideDesktopMode)?.let { btn ->
            bindItem(btn) {
                val willBeDesktop = !isDesktop
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
        }

        // 11. Full Settings
        contentView.findViewById<View>(R.id.btnSideSettings)?.let { btn ->
            bindItem(btn) {
                activity.showSettingsDialog()
            }
        }
    }

    fun show(anchorView: View? = null) {
        val decorView = activity.window.decorView
        val header = activity.findViewById<View>(R.id.rlActionBar) ?: anchorView ?: decorView

        val loc = IntArray(2)
        header.getLocationInWindow(loc)
        if (loc[1] == 0) {
            header.getLocationOnScreen(loc)
        }
        val headerBottom = loc[1] + header.height

        val screenWidth = if (decorView.width > 0) decorView.width else activity.resources.displayMetrics.widthPixels
        val screenHeight = if (decorView.height > 0) decorView.height else activity.resources.displayMetrics.heightPixels

        val popupWidth = SidebarHelper.calculateLeftSidebarWidth(activity)
        val popupHeight = (screenHeight - headerBottom).coerceAtLeast(100)

        popupWindow.width = popupWidth
        popupWindow.height = popupHeight
        popupWindow.isClippingEnabled = false

        popupWindow.showAtLocation(decorView, android.view.Gravity.TOP or android.view.Gravity.START, 0, headerBottom)

        contentView.post {
            contentView.findViewById<View>(R.id.btnSidePrivacy)?.requestFocus()
        }
    }

    fun dismiss() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }
}
