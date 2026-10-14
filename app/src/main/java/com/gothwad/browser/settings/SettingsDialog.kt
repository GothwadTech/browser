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
import com.gothwad.browser.activity.main.MainActivity
import com.gothwad.browser.activity.main.bindInputModeSegments

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

    companion object {
        /** title resource -> subtitle resource, so the search box also matches the subtitle text. */
        private val categorySubtitleRes = mapOf(
            R.string.engine to R.string.settings_google_duckduckgo_bing_custom,
            R.string.settings_display_and_ui_scale to R.string.settings_interface_scale_orientation_and,
            R.string.settings_web_page_zoom to R.string.settings_default_page_magnification_and,
            R.string.settings_themes_and_appearance to R.string.settings_dark_mode_amoled_black,
            R.string.settings_media_playback_and_video to R.string.settings_autoplay_media_background_audio,
            R.string.settings_home_page_and_startup to R.string.settings_new_tab_grid_custom,
            R.string.settings_user_agent_and_identity to R.string.settings_desktop_mode_mobile_browser,
            R.string.settings_web_engine_and_rendering to R.string.settings_system_webview_gpu_acceleration,
            R.string.settings_ad_and_tracker_blocker to R.string.settings_easylist_filter_rules_auto,
            R.string.settings_app_lock_and_pin to R.string.settings_passcode_protection_incognito_lock,
            R.string.settings_cache_and_storage to R.string.settings_clear_browsing_history_cookies,
            R.string.settings_quick_tools_and_utilities to R.string.settings_keep_screen_on_downloads,
            R.string.settings_remote_and_joystick to R.string.settings_d_pad_navigation_analog,
            R.string.settings_virtual_cursor_and_physics to R.string.settings_speed_acceleration_cursor_size,
            R.string.settings_keyboard_and_mouse to R.string.settings_virtual_keyboard_pointer_customisation,
            R.string.settings_keyboard_shortcuts to R.string.settings_configurable_hotkeys_for_tabs,
            R.string.settings_about to R.string.settings_about_and_version
        )
    }

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

        val bg = androidx.core.content.ContextCompat.getColor(context, R.color.google_bg)
        rootContainer.setBackgroundColor(bg)

        popupWindow = PopupWindow(
            rootContainer,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            true
        ).apply {
            isOutsideTouchable = false
            isFocusable = true
            setBackgroundDrawable(ColorDrawable(bg))
            elevation = 0f
            animationStyle = android.R.style.Animation_Dialog
            setOnDismissListener {
                mainView?.save()
            }
        }

        bindViews()
        initChildViews()
        setupListeners()
        setupInputModeRow()
    }

    /**
     * The "how do you browse" segmented switch that sits above the search field: one place that
     * turns TV-remote / keyboard+mouse / touch into the concrete pointer + page-layout switches.
     */
    private fun setupInputModeRow() {
        val main = activity as? MainActivity ?: run {
            contentView.findViewById<View>(R.id.llInputModeRow)?.visibility = View.GONE
            return
        }
        main.bindInputModeSegments(
            auto = contentView.findViewById(R.id.btnInputModeAuto),
            tvRemote = contentView.findViewById(R.id.btnInputModeTvRemote),
            keyboardMouse = contentView.findViewById(R.id.btnInputModeKeyboardMouse),
            touch = contentView.findViewById(R.id.btnInputModeTouch)
        )
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

        contentView.findViewById<View>(R.id.ibSettingsHelp)?.setOnClickListener {
            openAbout(itemCatAbout)
        }

        val etSearch = contentView.findViewById<android.widget.EditText>(R.id.etSearchSettings)
        // Search over the same resources the rows display: one source of truth, and it becomes
        // translatable for free (it used to match against hard-coded English keyword blobs).
        val allCategoryItems = listOf(
            itemCatSearchEngine to R.string.engine,
            itemCatDisplayScale to R.string.settings_display_and_ui_scale,
            itemCatWebZoom to R.string.settings_web_page_zoom,
            itemCatThemes to R.string.settings_themes_and_appearance,
            itemCatMediaPlayback to R.string.settings_media_playback_and_video,
            itemCatHomePage to R.string.settings_home_page_and_startup,
            itemCatUserAgent to R.string.settings_user_agent_and_identity,
            itemCatWebEngine to R.string.settings_web_engine_and_rendering,
            itemCatAdBlock to R.string.settings_ad_and_tracker_blocker,
            itemCatAppLock to R.string.settings_app_lock_and_pin,
            itemCatCacheStorage to R.string.settings_cache_and_storage,
            itemCatQuickTools to R.string.settings_quick_tools_and_utilities,
            itemCatRemoteNav to R.string.settings_remote_and_joystick,
            itemCatCursorPhysics to R.string.settings_virtual_cursor_and_physics,
            itemCatKeyboardMouse to R.string.settings_keyboard_and_mouse,
            itemCatShortcuts to R.string.settings_keyboard_shortcuts,
            itemCatAbout to R.string.settings_about
        )
        val categoryKeywords = allCategoryItems.map { (view, titleRes) ->
            val subRes = categorySubtitleRes[titleRes] ?: 0
            val text = buildString {
                append(context.getString(titleRes))
                if (subRes != 0) append(' ').append(context.getString(subRes))
            }
            view to text.lowercase(java.util.Locale.getDefault())
        }

        etSearch?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim() ?: ""
                for ((view, text) in categoryKeywords) {
                    view.visibility = if (q.isBlank() || text.contains(q.lowercase(java.util.Locale.getDefault()))) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        bindCategoryItem(itemCatDisplayScale) {
            openMainCategory(itemCatDisplayScale, R.string.settings_display_and_ui_scale, R.string.settings_interface_scale_orientation_and, SettingsCategory.DISPLAY_SCALE)
        }
        bindCategoryItem(itemCatWebZoom) {
            openMainCategory(itemCatWebZoom, R.string.settings_web_page_zoom, R.string.settings_default_page_magnification_and, SettingsCategory.WEB_ZOOM)
        }
        bindCategoryItem(itemCatThemes) {
            openMainCategory(itemCatThemes, R.string.settings_themes_and_appearance, R.string.settings_dark_mode_amoled_black, SettingsCategory.THEMES)
        }
        bindCategoryItem(itemCatMediaPlayback) {
            openMainCategory(itemCatMediaPlayback, R.string.settings_media_playback_and_video, R.string.settings_autoplay_media_background_audio, SettingsCategory.MEDIA_PLAYBACK)
        }
        bindCategoryItem(itemCatSearchEngine) {
            openMainCategory(itemCatSearchEngine, R.string.engine, R.string.settings_google_duckduckgo_bing_custom, SettingsCategory.SEARCH_ENGINE)
        }
        bindCategoryItem(itemCatHomePage) {
            openMainCategory(itemCatHomePage, R.string.settings_home_page_and_startup, R.string.settings_new_tab_grid_custom, SettingsCategory.HOME_PAGE)
        }
        bindCategoryItem(itemCatUserAgent) {
            openMainCategory(itemCatUserAgent, R.string.settings_user_agent_and_identity, R.string.settings_desktop_mode_mobile_browser, SettingsCategory.USER_AGENT)
        }
        bindCategoryItem(itemCatWebEngine) {
            openMainCategory(itemCatWebEngine, R.string.settings_web_engine_and_rendering, R.string.settings_system_webview_gpu_acceleration, SettingsCategory.WEB_ENGINE)
        }
        bindCategoryItem(itemCatAdBlock) {
            openMainCategory(itemCatAdBlock, R.string.settings_ad_and_tracker_blocker, R.string.settings_easylist_filter_rules_auto, SettingsCategory.AD_BLOCKER)
        }
        bindCategoryItem(itemCatAppLock) {
            openMainCategory(itemCatAppLock, R.string.settings_app_lock_and_pin, R.string.settings_passcode_protection_incognito_lock, SettingsCategory.APP_LOCK)
        }
        bindCategoryItem(itemCatCacheStorage) {
            openMainCategory(itemCatCacheStorage, R.string.settings_cache_and_storage, R.string.settings_clear_browsing_history_cookies, SettingsCategory.CACHE_STORAGE)
        }
        bindCategoryItem(itemCatQuickTools) {
            openMainCategory(itemCatQuickTools, R.string.settings_quick_tools_and_utilities, R.string.settings_keep_screen_on_downloads, SettingsCategory.QUICK_TOOLS)
        }
        bindCategoryItem(itemCatRemoteNav) {
            openMainCategory(itemCatRemoteNav, R.string.settings_remote_and_joystick, R.string.settings_d_pad_navigation_analog, SettingsCategory.REMOTE_NAV)
        }
        bindCategoryItem(itemCatCursorPhysics) {
            openMainCategory(itemCatCursorPhysics, R.string.settings_virtual_cursor_and_physics, R.string.settings_speed_acceleration_cursor_size, SettingsCategory.CURSOR_PHYSICS)
        }
        bindCategoryItem(itemCatKeyboardMouse) {
            openMainCategory(itemCatKeyboardMouse, R.string.settings_keyboard_and_mouse, R.string.settings_virtual_keyboard_pointer_customisation, SettingsCategory.KEYBOARD_MOUSE)
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
        @androidx.annotation.StringRes titleRes: Int,
        @androidx.annotation.StringRes badgeRes: Int,
        category: SettingsCategory
    ) {
        lastFocusedCategoryItem = clickedItem
        tvSettingsDetailTitle.setText(titleRes)
        tvSettingsDetailBadge.setText(badgeRes)

        val mv = mainView ?: return
        flTabsContent.removeAllViews()
        flTabsContent.addView(mv)
        mv.showCategory(category)

        showDetailPage()
    }

    private fun openShortcuts(clickedItem: View) {
        lastFocusedCategoryItem = clickedItem
        tvSettingsDetailTitle.text = context.getString(R.string.tab_shortcuts)
        tvSettingsDetailBadge.setText(R.string.settings_navigation_and_keys)

        val sv = shortcutsView ?: return
        flTabsContent.removeAllViews()
        flTabsContent.addView(sv)

        showDetailPage()
    }

    private fun openAbout(clickedItem: View) {
        lastFocusedCategoryItem = clickedItem
        tvSettingsDetailTitle.setText(R.string.settings_about_and_version)
        tvSettingsDetailBadge.setText(R.string.settings_gothwad_browser_build_details)

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
