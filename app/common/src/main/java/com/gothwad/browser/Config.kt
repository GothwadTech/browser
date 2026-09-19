package com.gothwad.browser

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Build
import com.gothwad.browser.utils.Utils
import com.gothwad.browser.utils.observable.ObservableValue

class Config(val prefs: SharedPreferences) {
    companion object {
        const val SEARCH_ENGINE_URL_PREF_KEY = "search_engine_url"
        const val SEARCH_ENGINE_AS_HOME_PAGE_KEY = "search_engine_as_home_page"
        const val HOME_PAGE_KEY = "home_page"
        const val USER_AGENT_PREF_KEY = "user_agent"
        const val THEME_KEY = "theme"
        const val LAST_UPDATE_USER_NOTIFICATION_TIME_KEY = "last_update_notif"
        const val AUTO_CHECK_UPDATES_KEY = "auto_check_updates"
        const val UPDATE_CHANNEL_KEY = "update_channel"
        const val TV_BRO_UA_PREFIX = "Browser/1.0 "
        const val HOME_URL_ALIAS = "about:home"
        const val KEEP_SCREEN_ON_KEY = "keep_screen_on"
        const val SCREEN_ORIENTATION_KEY = "screen_orientation"
        const val ORIENTATION_LANDSCAPE = 0
        const val ORIENTATION_PORTRAIT = 1
        const val ORIENTATION_AUTO = 2
        const val DISABLE_VIRTUAL_KEYBOARD_KEY = "disable_virtual_keyboard"
        const val ENABLE_VIRTUAL_CURSOR_KEY = "enable_virtual_cursor"
        const val CURSOR_SIZE_PERCENT_KEY = "cursor_size_percent"
        const val CURSOR_STYLE_KEY = "cursor_style"
        const val BLOCKED_INPUT_DEVICES_KEY = "blocked_input_devices"

        const val ENABLE_HARDWARE_MOUSE_CUSTOMIZATION_KEY = "enable_hardware_mouse_customization"
        const val HARDWARE_MOUSE_SIZE_PERCENT_KEY = "hardware_mouse_size_percent"
        const val HARDWARE_MOUSE_STYLE_KEY = "hardware_mouse_style"
        const val HARDWARE_MOUSE_SIZE_PERCENT_MIN = 30
        const val HARDWARE_MOUSE_SIZE_PERCENT_MAX = 300
        const val HARDWARE_MOUSE_SIZE_PERCENT_DEFAULT = 100

        const val CURSOR_SIZE_PERCENT_MIN = 50
        const val CURSOR_SIZE_PERCENT_MAX = 200
        /** When true, analog stick / hat axes from generic motion events are not translated to DPAD keys. */
        const val DISABLE_MOTION_AXES_DPAD_NAVIGATION_KEY = "disable_motion_axes_dpad_navigation"
        /** Percent of built-in default (100 = default). Range [CURSOR_PHYSICS_PERCENT_MIN], [CURSOR_PHYSICS_PERCENT_MAX]. */
        const val CURSOR_MAX_SPEED_PERCENT_KEY = "cursor_max_speed_percent"
        const val CURSOR_ACCELERATION_PERCENT_KEY = "cursor_acceleration_percent"
        const val CURSOR_PHYSICS_PERCENT_MIN = 25
        const val CURSOR_PHYSICS_PERCENT_MAX = 200
        const val UI_SCALE_PERCENT_KEY = "ui_scale_percent"
        const val UI_SCALE_PERCENT_MIN = 1
        const val UI_SCALE_PERCENT_MAX = 100
        const val UI_SCALE_PERCENT_DEFAULT = 50
        val STANDARD_ZOOM_LEVELS = intArrayOf(25, 33, 50, 67, 75, 80, 90, 100, 110, 125, 150, 175, 200, 250, 300)
        const val WEB_PAGE_ZOOM_PERCENT_KEY = "web_page_zoom_percent"
        const val MOBILE_WEB_PAGE_ZOOM_PERCENT_KEY = "mobile_web_page_zoom_percent"
        const val DESKTOP_WEB_PAGE_ZOOM_PERCENT_KEY = "desktop_web_page_zoom_percent"
        const val WEB_PAGE_ZOOM_PERCENT_MIN = 25
        const val WEB_PAGE_ZOOM_PERCENT_MAX = 300
        const val WEB_PAGE_ZOOM_PERCENT_DEFAULT = 100
        const val INCOGNITO_MODE_KEY = "incognito_mode"
        const val INCOGNITO_MODE_HINT_SUPPRESS_KEY = "incognito_mode_hint_suppress"
        const val DIRECT_NAVIGATION_MODE_HINT_SUPPRESS_KEY = "direct_navigation_mode_hint_suppress"
        const val HOME_PAGE_MODE = "home_page_mode"
        const val HOME_PAGE_SUGGESTIONS_MODE = "home_page_suggestions_mode"
        const val WEB_ENGINE = "web_engine"
        const val ALLOW_AUTOPLAY_MEDIA = "allow_autoplay_media"
        const val WEB_ENGINE_DEBUG_KEY = "web_engine_debug"
        //const val HOME_PAGE_VERSION_EXTRACTED = "home_page_version_extracted"
        const val INITIAL_BOOKMARKS_SUGGESTIONS_LOADED = "initial_bookmarks_suggestions_loaded"
        const val ADBLOCK_ENABLED_PREF_KEY = "adblock_enabled"
        const val ADBLOCK_LAST_UPDATE_LIST_KEY = "adblock_last_update"
        const val ADBLOCK_LIST_URL_KEY = "adblock_list_url"
        const val APP_WEB_EXTENSION_VERSION_KEY = "app_web_extension_version"
        const val NOTIFICATION_ABOUT_ENGINE_CHANGE_SHOWN_KEY = "notification_about_engine_change_shown"
        const val APP_VERSION_CODE_MARK_KEY = "app_version_code_mark"
        const val SHOW_TOP_TAB_BAR_KEY = "show_top_tab_bar"
        const val KEEP_ALIVE_IN_BACKGROUND_KEY = "keep_alive_in_background"
        const val MAX_LIVE_TABS_KEY = "max_live_tabs"
        const val SAVE_HISTORY_KEY = "save_history"
        /**
         * Maximum number of tab WebViews kept fully live in memory simultaneously.
         * Default is 6: balanced for typical Android TV boxes (1.5GB - 3GB RAM) to allow
         * fast tab switching without exhausting system memory.
         */
        const val DEFAULT_MAX_LIVE_TABS = 6
        const val DESKTOP_MODE_KEY = "desktop_mode"
        const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        const val ENGINE_WEB_VIEW = "WebView"

        const val DEFAULT_ADBLOCK_LIST_URL = "https://easylist.to/easylist/easylist.txt"
        val SearchEnginesTitles = arrayOf("Google", "Bing", "DuckDuckGo", "Yahoo!", "Yandex", "Startpage", "Custom")
        val SearchEnginesNames = arrayOf("google", "bing", "ddg", "yahoo", "yandex", "startpage", "custom")
        val SearchEnginesURLs = listOf(
            "https://www.google.com/search?q=[query]",
            "https://www.bing.com/search?q=[query]",
            "https://duckduckgo.com/?q=[query]",
            "https://search.yahoo.com/search?p=[query]",
            "https://yandex.com/search/?text=[query]",
            "https://www.startpage.com/sp/search?query=[query]",
            ""
        )
        val SupportedWebEngines = arrayOf(ENGINE_WEB_VIEW)
        const val HOME_PAGE_URL = "https://tvbrowser.phlox.dev/appcontent/home/"

        fun canRecommendGeckoView(): Boolean {
            return false
        }
    }

    enum class Theme {
        SYSTEM,
        WHITE_PURE,
        WHITE_WARM,
        WHITE_COOL,
        BLACK_AMOLED,
        BLACK_CHARCOAL,
        BLACK_MIDNIGHT;
    }

    enum class HomePageMode {
        HOME_PAGE, SEARCH_ENGINE, CUSTOM, BLANK
    }

    enum class HomePageLinksMode {
        BOOKMARKS, LATEST_HISTORY, MOST_VISITED
    }

    var incognitoMode: Boolean
        get() = prefs.getBoolean(INCOGNITO_MODE_KEY, false)
        @SuppressLint("ApplySharedPref")
        set(value) {
            prefs.edit().putBoolean(INCOGNITO_MODE_KEY, value).commit()
        }

    var incognitoModeHintSuppress: Boolean
        get() = prefs.getBoolean(INCOGNITO_MODE_HINT_SUPPRESS_KEY, false)
        set(value) {
            prefs.edit().putBoolean(INCOGNITO_MODE_HINT_SUPPRESS_KEY, value).apply()
        }

    var directNavigationModeHintSuppress: Boolean
        get() = prefs.getBoolean(DIRECT_NAVIGATION_MODE_HINT_SUPPRESS_KEY, false)
        set(value) {
            prefs.edit().putBoolean(DIRECT_NAVIGATION_MODE_HINT_SUPPRESS_KEY, value).apply()
        }

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEEP_SCREEN_ON_KEY, false)
        set(value) {
            prefs.edit().putBoolean(KEEP_SCREEN_ON_KEY, value).apply()
        }

    var screenOrientation: Int
        get() = prefs.getInt(SCREEN_ORIENTATION_KEY, ORIENTATION_LANDSCAPE)
        set(value) {
            prefs.edit().putInt(SCREEN_ORIENTATION_KEY, value).apply()
        }

    var disableVirtualKeyboard: Boolean
        get() = prefs.getBoolean(DISABLE_VIRTUAL_KEYBOARD_KEY, false)
        set(value) {
            prefs.edit().putBoolean(DISABLE_VIRTUAL_KEYBOARD_KEY, value).apply()
        }

    var blockedInputDeviceIds: Set<String>
        get() = prefs.getStringSet(BLOCKED_INPUT_DEVICES_KEY, emptySet()) ?: emptySet()
        set(value) {
            prefs.edit().putStringSet(BLOCKED_INPUT_DEVICES_KEY, value).apply()
        }

    fun isInputDeviceBlocked(deviceIdentifier: String): Boolean {
        return blockedInputDeviceIds.contains(deviceIdentifier)
    }

    fun setInputDeviceBlocked(deviceIdentifier: String, blocked: Boolean) {
        val current = blockedInputDeviceIds.toMutableSet()
        if (blocked) {
            current.add(deviceIdentifier)
        } else {
            current.remove(deviceIdentifier)
        }
        blockedInputDeviceIds = current
    }

    var enableVirtualCursor: Boolean
        get() = prefs.getBoolean(ENABLE_VIRTUAL_CURSOR_KEY, true)
        set(value) {
            prefs.edit().putBoolean(ENABLE_VIRTUAL_CURSOR_KEY, value).apply()
        }

    var cursorSizePercent: Int
        get() = prefs.getInt(CURSOR_SIZE_PERCENT_KEY, 100)
            .coerceIn(CURSOR_SIZE_PERCENT_MIN, CURSOR_SIZE_PERCENT_MAX)
        set(value) {
            prefs.edit().putInt(
                CURSOR_SIZE_PERCENT_KEY,
                value.coerceIn(CURSOR_SIZE_PERCENT_MIN, CURSOR_SIZE_PERCENT_MAX)
            ).apply()
        }

    var cursorStyle: Int
        get() = prefs.getInt(CURSOR_STYLE_KEY, 0)
        set(value) {
            prefs.edit().putInt(CURSOR_STYLE_KEY, value).apply()
        }

    var enableHardwareMouseCustomization: Boolean
        get() = prefs.getBoolean(ENABLE_HARDWARE_MOUSE_CUSTOMIZATION_KEY, true)
        set(value) {
            prefs.edit().putBoolean(ENABLE_HARDWARE_MOUSE_CUSTOMIZATION_KEY, value).apply()
        }

    var hardwareMouseSizePercent: Int
        get() = prefs.getInt(HARDWARE_MOUSE_SIZE_PERCENT_KEY, HARDWARE_MOUSE_SIZE_PERCENT_DEFAULT)
            .coerceIn(HARDWARE_MOUSE_SIZE_PERCENT_MIN, HARDWARE_MOUSE_SIZE_PERCENT_MAX)
        set(value) {
            prefs.edit().putInt(
                HARDWARE_MOUSE_SIZE_PERCENT_KEY,
                value.coerceIn(HARDWARE_MOUSE_SIZE_PERCENT_MIN, HARDWARE_MOUSE_SIZE_PERCENT_MAX)
            ).apply()
        }

    var hardwareMouseStyle: Int
        get() = prefs.getInt(HARDWARE_MOUSE_STYLE_KEY, 0)
        set(value) {
            prefs.edit().putInt(HARDWARE_MOUSE_STYLE_KEY, value).apply()
        }

    var disableMotionAxesDpadNavigation: Boolean
        get() = prefs.getBoolean(DISABLE_MOTION_AXES_DPAD_NAVIGATION_KEY, false)
        set(value) {
            prefs.edit().putBoolean(DISABLE_MOTION_AXES_DPAD_NAVIGATION_KEY, value).apply()
        }

    /** Virtual cursor max speed as a percent of the default (screen-width–based) cap. */
    var cursorMaxSpeedPercent: Int
        get() = prefs.getInt(CURSOR_MAX_SPEED_PERCENT_KEY, 100)
            .coerceIn(CURSOR_PHYSICS_PERCENT_MIN, CURSOR_PHYSICS_PERCENT_MAX)
        set(value) {
            prefs.edit().putInt(
                CURSOR_MAX_SPEED_PERCENT_KEY,
                value.coerceIn(CURSOR_PHYSICS_PERCENT_MIN, CURSOR_PHYSICS_PERCENT_MAX)
            ).apply()
        }

    /** Virtual cursor acceleration as a percent of the default ramp-up per frame. */
    var cursorAccelerationPercent: Int
        get() = prefs.getInt(CURSOR_ACCELERATION_PERCENT_KEY, 100)
            .coerceIn(CURSOR_PHYSICS_PERCENT_MIN, CURSOR_PHYSICS_PERCENT_MAX)
        set(value) {
            prefs.edit().putInt(
                CURSOR_ACCELERATION_PERCENT_KEY,
                value.coerceIn(CURSOR_PHYSICS_PERCENT_MIN, CURSOR_PHYSICS_PERCENT_MAX)
            ).apply()
        }

    /** UI scaling percent for Android TV interface (50 = default/100% density, 1 = 25% density, 100 = 125% density). */
    var uiScalePercent: Int
        get() = prefs.getInt(UI_SCALE_PERCENT_KEY, UI_SCALE_PERCENT_DEFAULT)
            .coerceIn(UI_SCALE_PERCENT_MIN, UI_SCALE_PERCENT_MAX)
        set(value) {
            prefs.edit().putInt(
                UI_SCALE_PERCENT_KEY,
                value.coerceIn(UI_SCALE_PERCENT_MIN, UI_SCALE_PERCENT_MAX)
            ).apply()
        }

    fun getUiScaleDensityMultiplier(): Float {
        val p = uiScalePercent.coerceIn(UI_SCALE_PERCENT_MIN, UI_SCALE_PERCENT_MAX)
        return if (p <= 50) {
            // 1% -> 0.25f (25%), 50% -> 1.00f (100%)
            0.25f + (p - 1).toFloat() * (0.75f / 49f)
        } else {
            // 50% -> 1.00f (100%), 100% -> 1.25f (125%)
            1.00f + (p - 50).toFloat() * (0.25f / 50f)
        }
    }

    /** Web page zoom percent for mobile view (100 = default, 25 to 300). */
    var mobileWebPageZoomPercent: Int
        get() = prefs.getInt(MOBILE_WEB_PAGE_ZOOM_PERCENT_KEY, prefs.getInt(WEB_PAGE_ZOOM_PERCENT_KEY, WEB_PAGE_ZOOM_PERCENT_DEFAULT))
            .coerceIn(WEB_PAGE_ZOOM_PERCENT_MIN, WEB_PAGE_ZOOM_PERCENT_MAX)
        set(value) {
            val clamped = value.coerceIn(WEB_PAGE_ZOOM_PERCENT_MIN, WEB_PAGE_ZOOM_PERCENT_MAX)
            prefs.edit().putInt(MOBILE_WEB_PAGE_ZOOM_PERCENT_KEY, clamped).apply()
        }

    /** Web page zoom percent for desktop view (100 = default, 25 to 300). */
    var desktopWebPageZoomPercent: Int
        get() = prefs.getInt(DESKTOP_WEB_PAGE_ZOOM_PERCENT_KEY, prefs.getInt(WEB_PAGE_ZOOM_PERCENT_KEY, WEB_PAGE_ZOOM_PERCENT_DEFAULT))
            .coerceIn(WEB_PAGE_ZOOM_PERCENT_MIN, WEB_PAGE_ZOOM_PERCENT_MAX)
        set(value) {
            val clamped = value.coerceIn(WEB_PAGE_ZOOM_PERCENT_MIN, WEB_PAGE_ZOOM_PERCENT_MAX)
            prefs.edit().putInt(DESKTOP_WEB_PAGE_ZOOM_PERCENT_KEY, clamped).apply()
        }

    fun getEffectiveZoom(isDesktop: Boolean): Int {
        return if (isDesktop) desktopWebPageZoomPercent else mobileWebPageZoomPercent
    }

    fun setEffectiveZoom(isDesktop: Boolean, percent: Int) {
        val clamped = percent.coerceIn(WEB_PAGE_ZOOM_PERCENT_MIN, WEB_PAGE_ZOOM_PERCENT_MAX)
        if (isDesktop) {
            desktopWebPageZoomPercent = clamped
        } else {
            mobileWebPageZoomPercent = clamped
        }
    }

    fun isDesktopUa(ua: String?): Boolean {
        if (ua == null) return false
        return ua.contains("Windows") || ua.contains("X11; Linux x86_64") || ua.contains("Macintosh")
    }

    fun isDesktopMode(customUa: String? = userAgentString.value): Boolean {
        return desktopMode.value || isDesktopUa(customUa)
    }

    /** Current effective web page zoom percent based on active desktop/mobile mode (100 = default, 25 to 300). */
    var webPageZoomPercent: Int
        get() = getEffectiveZoom(isDesktopMode())
        set(value) {
            setEffectiveZoom(isDesktopMode(), value)
        }

    /** Whether to record visited URLs and searches into local history. */
    var saveHistory: Boolean
        get() = prefs.getBoolean(SAVE_HISTORY_KEY, true)
        set(value) {
            prefs.edit().putBoolean(SAVE_HISTORY_KEY, value).apply()
        }

    var theme = object : ObservableValue<Theme>(Theme.SYSTEM) {
        override var value: Theme =
            try {
                val index = prefs.getInt(THEME_KEY, 0)
                if (index in Theme.values().indices) Theme.values()[index] else Theme.SYSTEM
            } catch (e: Exception) {
                Theme.SYSTEM
            }
            set(value) {
                prefs.edit().putInt(THEME_KEY, value.ordinal).apply()
                field = value
                notifyObservers()
            }
    }

    var homePageMode: HomePageMode
        get() = prefs.getInt(HOME_PAGE_MODE, 0)
            .let {
                //ignore value if search engine as home page is set
                if (prefs.getBoolean(SEARCH_ENGINE_AS_HOME_PAGE_KEY, false)) {
                    prefs.edit().remove(SEARCH_ENGINE_AS_HOME_PAGE_KEY).apply()
                    HomePageMode.SEARCH_ENGINE.ordinal
                } else it
            }
            .let { if (it < 0 || it >= HomePageMode.values().size) 0 else it }
            .let { HomePageMode.values()[it] }
        set(value) {
            prefs.edit().putInt(HOME_PAGE_MODE, value.ordinal).apply()
        }

    var homePageLinksMode: HomePageLinksMode
        get() = prefs.getInt(HOME_PAGE_SUGGESTIONS_MODE, 0)
            .let { if (it < 0 || it >= HomePageLinksMode.values().size) 0 else it }
            .let { HomePageLinksMode.values()[it] }
        set(value) {
            prefs.edit().putInt(HOME_PAGE_SUGGESTIONS_MODE, value.ordinal).apply()
        }

    var homePage: String
        get() = prefs.getString(HOME_PAGE_KEY, HOME_URL_ALIAS)!!
        set(value) {
            prefs.edit().putString(HOME_PAGE_KEY, value).apply()
        }

    var searchEngineURL = ObservableStringPreference(SearchEnginesURLs[0], SEARCH_ENGINE_URL_PREF_KEY)

    var webEngine: String
        get() = ENGINE_WEB_VIEW
        set(value) {
            prefs.edit().putString(WEB_ENGINE, value).apply()
        }

    var allowAutoplayMedia: Boolean
        get() = prefs.getBoolean(ALLOW_AUTOPLAY_MEDIA, false)
        set(value) {
            prefs.edit().putBoolean(ALLOW_AUTOPLAY_MEDIA, value).apply()
        }

    var webEngineDebug: Boolean
        get() = prefs.getBoolean(WEB_ENGINE_DEBUG_KEY, false)
        set(value) {
            prefs.edit().putBoolean(WEB_ENGINE_DEBUG_KEY, value).apply()
        }

    /*var homePageVersionExtracted: Int
        get() = prefs.getInt(HOME_PAGE_VERSION_EXTRACTED, 0)
        set(value) {
            prefs.edit().putInt(HOME_PAGE_VERSION_EXTRACTED, value).apply()
        }*/

    var initialBookmarksSuggestionsLoaded: Boolean
        get() = prefs.getBoolean(INITIAL_BOOKMARKS_SUGGESTIONS_LOADED, false)
        set(value) {
            prefs.edit().putBoolean(INITIAL_BOOKMARKS_SUGGESTIONS_LOADED, value).apply()
        }

    var userAgentString = ObservableOptStringPreference(null, USER_AGENT_PREF_KEY)
    var desktopMode = ObservableBooleanPreference(false, DESKTOP_MODE_KEY)
    var showTopTabBar = ObservableBooleanPreference(false, SHOW_TOP_TAB_BAR_KEY)
    var keepAliveInBackground = ObservableBooleanPreference(true, KEEP_ALIVE_IN_BACKGROUND_KEY)

    var maxLiveTabs: Int
        get() = prefs.getInt(MAX_LIVE_TABS_KEY, DEFAULT_MAX_LIVE_TABS).coerceIn(2, 20)
        set(value) {
            prefs.edit().putInt(MAX_LIVE_TABS_KEY, value.coerceIn(2, 20)).apply()
        }

    var adBlockEnabled: Boolean = prefs.getBoolean(ADBLOCK_ENABLED_PREF_KEY, true)
        set(value) {
            field = value
            prefs.edit().putBoolean(ADBLOCK_ENABLED_PREF_KEY, field).apply()
        }

    var adBlockListURL = ObservableStringPreference(DEFAULT_ADBLOCK_LIST_URL, ADBLOCK_LIST_URL_KEY)

    var adBlockListLastUpdate: Long
        get() = prefs.getLong(ADBLOCK_LAST_UPDATE_LIST_KEY, 0)
        set(value) {
            prefs.edit().putLong(ADBLOCK_LAST_UPDATE_LIST_KEY, value).apply()
        }

    var appWebExtensionVersion: Int
        get() = prefs.getInt(APP_WEB_EXTENSION_VERSION_KEY, 0)
        set(value) {
            prefs.edit().putInt(APP_WEB_EXTENSION_VERSION_KEY, value).apply()
        }

    //0 - not shown, any other value - app version code where notification already shown (to show only once after update)
    var notificationAboutEngineChangeShown: Int
        get() = prefs.getInt(NOTIFICATION_ABOUT_ENGINE_CHANGE_SHOWN_KEY, 0)
        set(value) {
            prefs.edit().putInt(NOTIFICATION_ABOUT_ENGINE_CHANGE_SHOWN_KEY, value).apply()
        }

    var autoCheckUpdates: Boolean
        get() = prefs.getBoolean(AUTO_CHECK_UPDATES_KEY, Utils.isInstalledByAPK(AppContext.get()))
        set(value) {
            prefs.edit().putBoolean(AUTO_CHECK_UPDATES_KEY, value).apply()
        }

    var updateChannel: String
        get() = prefs.getString(UPDATE_CHANNEL_KEY, "release")!!
        set(value) {
            prefs.edit().putString(UPDATE_CHANNEL_KEY, value).apply()
        }

    var appVersionCodeMark: Int
        get() = prefs.getInt(APP_VERSION_CODE_MARK_KEY, 0)
        set(value) {
            prefs.edit().putInt(APP_VERSION_CODE_MARK_KEY, value).apply()
        }

    fun isWebEngineGecko(): Boolean {
        return false
    }

    fun isWebEngineNotSet(): Boolean {
        return !prefs.contains(WEB_ENGINE)
    }

    fun guessSearchEngineName(): String {
        val url = searchEngineURL.value
        val index = SearchEnginesURLs.indexOf(url)
        if (index != -1 && index < SearchEnginesNames.size) {
            return SearchEnginesNames[index]
        }
        val lower = url.lowercase()
        return when {
            lower.contains("google.") -> "google"
            lower.contains("bing.") -> "bing"
            lower.contains("duckduckgo.") -> "ddg"
            lower.contains("yahoo.") -> "yahoo"
            lower.contains("yandex.") -> "yandex"
            lower.contains("startpage.") -> "startpage"
            else -> SearchEnginesNames[SearchEnginesNames.size - 1]
        }
    }

    inner class ObservableStringPreference(default: String, private val prefsKey: String) : ObservableValue<String>(default) {
        override var value: String = prefs.getString(prefsKey, default)!!
            set(value) {
                prefs.edit().putString(prefsKey, value).apply()
                field = value
                notifyObservers()
            }
    }

    inner class ObservableOptStringPreference(default: String?, private val prefsKey: String) : ObservableValue<String?>(default) {
        override var value: String? = prefs.getString(prefsKey, default)
            set(value) {
                if (value == null) prefs.edit().remove(prefsKey).apply()
                else prefs.edit().putString(prefsKey, value).apply()
                field = value
                notifyObservers()
            }
    }

    inner class ObservableBooleanPreference(default: Boolean, private val prefsKey: String) : ObservableValue<Boolean>(default) {
        override var value: Boolean = prefs.getBoolean(prefsKey, default)
            set(value) {
                prefs.edit().putBoolean(prefsKey, value).apply()
                field = value
                notifyObservers()
            }
    }
}
