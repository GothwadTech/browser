package com.gothwad.browser.settings

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.gothwad.browser.R

class SettingsDialog(private val context: Context, val model: SettingsModel) :
    VersionSettingsView.Callback {

    private val activity: Activity? = context as? Activity
    private val popupWindow: PopupWindow
    private val rootContainer: FrameLayout
    private val contentView: View

    private var mainView: MainSettingsView? = null
    private var versionView: VersionSettingsView? = null
    private var shortcutsView: ShortcutsSettingsView? = null

    private lateinit var llSettingsPageCategories: LinearLayout
    private lateinit var llSettingsPageDetail: LinearLayout
    private lateinit var flTabsContent: FrameLayout
    private lateinit var tvSettingsDetailTitle: TextView
    private lateinit var tvSettingsDetailBadge: TextView
    private lateinit var btnSettingsBackToCategories: ImageButton
    private lateinit var ibCloseSettings: ImageButton

    // Page 1 Category Items (17 Granular Sections)
    private lateinit var itemCatDisplayScale: View
    private lateinit var itemCatWebZoom: View
    private lateinit var itemCatThemes: View
    private lateinit var itemCatMediaPlayback: View
    private lateinit var itemCatSearchEngine: View
    private lateinit var itemCatHomePage: View
    private lateinit var itemCatUserAgent: View
    private lateinit var itemCatWebEngine: View
    private lateinit var itemCatAdBlock: View
    private lateinit var itemCatAppLock: View
    private lateinit var itemCatCacheStorage: View
    private lateinit var itemCatQuickTools: View
    private lateinit var itemCatRemoteNav: View
    private lateinit var itemCatCursorPhysics: View
    private lateinit var itemCatKeyboardMouse: View
    private lateinit var itemCatShortcuts: View
    private lateinit var itemCatAbout: View

    private var lastFocusedCategoryItem: View? = null

    init {
        rootContainer = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_BACK,
                        KeyEvent.KEYCODE_ESCAPE,
                        KeyEvent.KEYCODE_BUTTON_B -> {
                            if (::llSettingsPageDetail.isInitialized && llSettingsPageDetail.visibility == View.VISIBLE) {
                                showCategoriesPage()
                                return true
                            } else {
                                dismiss()
                                return true
                            }
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

        contentView = LayoutInflater.from(context).inflate(R.layout.dialog_settings, rootContainer, true)

        popupWindow = PopupWindow(
            rootContainer,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            true
        ).apply {
            isOutsideTouchable = false
            isFocusable = true
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#131314")))
            elevation = 0f
            animationStyle = android.R.style.Animation_Dialog
            setOnDismissListener {
                mainView?.save()
            }
        }

        bindViews()
        initChildViews()
        setupListeners()
    }

    private fun bindViews() {
        llSettingsPageCategories = contentView.findViewById(R.id.llSettingsPageCategories)
        llSettingsPageDetail = contentView.findViewById(R.id.llSettingsPageDetail)
        flTabsContent = contentView.findViewById(R.id.flTabsContent)
        tvSettingsDetailTitle = contentView.findViewById(R.id.tvSettingsDetailTitle)
        tvSettingsDetailBadge = contentView.findViewById(R.id.tvSettingsDetailBadge)
        btnSettingsBackToCategories = contentView.findViewById(R.id.btnSettingsBackToCategories)
        ibCloseSettings = contentView.findViewById(R.id.ibCloseSettings)

        itemCatDisplayScale = contentView.findViewById(R.id.itemCatDisplayScale)
        itemCatWebZoom = contentView.findViewById(R.id.itemCatWebZoom)
        itemCatThemes = contentView.findViewById(R.id.itemCatThemes)
        itemCatMediaPlayback = contentView.findViewById(R.id.itemCatMediaPlayback)
        itemCatSearchEngine = contentView.findViewById(R.id.itemCatSearchEngine)
        itemCatHomePage = contentView.findViewById(R.id.itemCatHomePage)
        itemCatUserAgent = contentView.findViewById(R.id.itemCatUserAgent)
        itemCatWebEngine = contentView.findViewById(R.id.itemCatWebEngine)
        itemCatAdBlock = contentView.findViewById(R.id.itemCatAdBlock)
        itemCatAppLock = contentView.findViewById(R.id.itemCatAppLock)
        itemCatCacheStorage = contentView.findViewById(R.id.itemCatCacheStorage)
        itemCatQuickTools = contentView.findViewById(R.id.itemCatQuickTools)
        itemCatRemoteNav = contentView.findViewById(R.id.itemCatRemoteNav)
        itemCatCursorPhysics = contentView.findViewById(R.id.itemCatCursorPhysics)
        itemCatKeyboardMouse = contentView.findViewById(R.id.itemCatKeyboardMouse)
        itemCatShortcuts = contentView.findViewById(R.id.itemCatShortcuts)
        itemCatAbout = contentView.findViewById(R.id.itemCatAbout)
    }

    private fun initChildViews() {
        mainView = MainSettingsView(context).apply {
            onDismissDialog = { dismiss() }
        }

        versionView = VersionSettingsView(context).apply {
            callback = this@SettingsDialog
        }

        shortcutsView = ShortcutsSettingsView(context)
    }

    private fun setupListeners() {
        ibCloseSettings.setOnClickListener { dismiss() }
        btnSettingsBackToCategories.setOnClickListener { showCategoriesPage() }

        bindCategoryItem(itemCatDisplayScale) {
            openMainCategory(itemCatDisplayScale, "Display & UI Scale", "Display & Layout", SettingsCategory.DISPLAY_SCALE)
        }
        bindCategoryItem(itemCatWebZoom) {
            openMainCategory(itemCatWebZoom, "Web Page Zoom", "Zoom Controls", SettingsCategory.WEB_ZOOM)
        }
        bindCategoryItem(itemCatThemes) {
            openMainCategory(itemCatThemes, "Themes & Appearance", "Appearance & Dark Mode", SettingsCategory.THEMES)
        }
        bindCategoryItem(itemCatMediaPlayback) {
            openMainCategory(itemCatMediaPlayback, "Media & Playback", "Video & Audio", SettingsCategory.MEDIA_PLAYBACK)
        }
        bindCategoryItem(itemCatSearchEngine) {
            openMainCategory(itemCatSearchEngine, "Search Engine", "Default Engine", SettingsCategory.SEARCH_ENGINE)
        }
        bindCategoryItem(itemCatHomePage) {
            openMainCategory(itemCatHomePage, "Home Page & Startup", "Startup & Bookmarks", SettingsCategory.HOME_PAGE)
        }
        bindCategoryItem(itemCatUserAgent) {
            openMainCategory(itemCatUserAgent, "User Agent & Identity", "Desktop / Mobile Mode", SettingsCategory.USER_AGENT)
        }
        bindCategoryItem(itemCatWebEngine) {
            openMainCategory(itemCatWebEngine, "Web Engine & Rendering", "Browser Core", SettingsCategory.WEB_ENGINE)
        }
        bindCategoryItem(itemCatAdBlock) {
            openMainCategory(itemCatAdBlock, "Ad & Tracker Blocker", "Filters & Rules", SettingsCategory.AD_BLOCKER)
        }
        bindCategoryItem(itemCatAppLock) {
            openMainCategory(itemCatAppLock, "App Lock & PIN Security", "Master Passcode", SettingsCategory.APP_LOCK)
        }
        bindCategoryItem(itemCatCacheStorage) {
            openMainCategory(itemCatCacheStorage, "Cache & Storage", "Data & History", SettingsCategory.CACHE_STORAGE)
        }
        bindCategoryItem(itemCatQuickTools) {
            openMainCategory(itemCatQuickTools, "Quick Tools & Utilities", "Toolbox & Actions", SettingsCategory.QUICK_TOOLS)
        }
        bindCategoryItem(itemCatRemoteNav) {
            openMainCategory(itemCatRemoteNav, "Remote & Joystick", "Navigation Controls", SettingsCategory.REMOTE_NAV)
        }
        bindCategoryItem(itemCatCursorPhysics) {
            openMainCategory(itemCatCursorPhysics, "Virtual Cursor & Physics", "Pointer Controls", SettingsCategory.CURSOR_PHYSICS)
        }
        bindCategoryItem(itemCatKeyboardMouse) {
            openMainCategory(itemCatKeyboardMouse, "Keyboard & Mouse", "Hardware Devices", SettingsCategory.KEYBOARD_MOUSE)
        }
        bindCategoryItem(itemCatShortcuts) {
            openShortcuts(itemCatShortcuts)
        }
        bindCategoryItem(itemCatAbout) {
            openAbout(itemCatAbout)
        }
    }

    private fun bindCategoryItem(view: View, action: () -> Unit) {
        view.setOnClickListener { action() }
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_BUTTON_A -> {
                        action()
                        true
                    }
                    else -> false
                }
            } else false
        }
    }

    private fun openMainCategory(
        clickedItem: View,
        title: String,
        badge: String,
        category: SettingsCategory
    ) {
        lastFocusedCategoryItem = clickedItem
        tvSettingsDetailTitle.text = title
        tvSettingsDetailBadge.text = badge

        val mv = mainView ?: return
        flTabsContent.removeAllViews()
        flTabsContent.addView(mv)
        mv.showCategory(category)

        showDetailPage()
    }

    private fun openShortcuts(clickedItem: View) {
        lastFocusedCategoryItem = clickedItem
        tvSettingsDetailTitle.text = context.getString(R.string.tab_shortcuts)
        tvSettingsDetailBadge.text = "Navigation & Keys"

        val sv = shortcutsView ?: return
        flTabsContent.removeAllViews()
        flTabsContent.addView(sv)

        showDetailPage()
    }

    private fun openAbout(clickedItem: View) {
        lastFocusedCategoryItem = clickedItem
        tvSettingsDetailTitle.text = "About & Version"
        tvSettingsDetailBadge.text = "Build Information"

        val vv = versionView ?: return
        flTabsContent.removeAllViews()
        flTabsContent.addView(vv)

        showDetailPage()
    }

    private fun showDetailPage() {
        llSettingsPageCategories.visibility = View.GONE
        llSettingsPageDetail.visibility = View.VISIBLE
        btnSettingsBackToCategories.post {
            btnSettingsBackToCategories.requestFocus()
        }
    }

    fun showCategoriesPage() {
        mainView?.save()
        llSettingsPageDetail.visibility = View.GONE
        llSettingsPageCategories.visibility = View.VISIBLE
        val focusTarget = lastFocusedCategoryItem ?: itemCatSearchEngine
        focusTarget.post {
            focusTarget.requestFocus()
        }
    }

    fun show(anchorView: View? = null) {
        val decorView = activity?.window?.decorView ?: return

        popupWindow.width = ViewGroup.LayoutParams.MATCH_PARENT
        popupWindow.height = ViewGroup.LayoutParams.MATCH_PARENT
        popupWindow.isClippingEnabled = true

        popupWindow.showAtLocation(decorView, android.view.Gravity.FILL, 0, 0)

        contentView.post {
            val focusTarget = lastFocusedCategoryItem ?: itemCatSearchEngine
            focusTarget.requestFocus()
        }
    }

    fun dismiss() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }

    override fun onNeedToCloseSettings() {
        dismiss()
    }
}
