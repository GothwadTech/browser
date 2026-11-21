package com.bansify.internet.settings

import com.bansify.internet.AppContext
import com.bansify.internet.Config
import com.bansify.internet.utils.activemodel.ActiveModel
import com.bansify.internet.utils.observable.ObservableValue

enum class SettingsCategory {
    DISPLAY_SCALE,
    WEB_ZOOM,
    THEMES,
    MEDIA_PLAYBACK,
    SEARCH_ENGINE,
    HOME_PAGE,
    USER_AGENT,
    WEB_ENGINE,
    AD_BLOCKER,
    APP_LOCK,
    CACHE_STORAGE,
    QUICK_TOOLS,
    REMOTE_NAV,
    CURSOR_PHYSICS,
    KEYBOARD_MOUSE
}

class SettingsModel : ActiveModel() {
    companion object {
        val TAG = SettingsModel::class.java.simpleName
        const val TV_BRO_UA_PREFIX = "Browser/1.0 "
    }

    val config = AppContext.provideConfig()

    // Home page settings
    var homePage by config::homePage
    var homePageMode by config::homePageMode
    var homePageLinksMode by config::homePageLinksMode

    // User agent strings configuration
    val userAgentStringTitles = arrayOf(
        "Default (recommended)",
        "Chrome (Desktop)",
        "Chrome (Mobile)",
        "Firefox (Desktop)",
        "Firefox (Mobile)",
        "Edge (Desktop)",
        "Custom"
    )

    val uaStrings = listOf(
        "",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Linux; Android 16; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0",
        "Mozilla/5.0 (Android 16; Mobile; rv:147.0) Gecko/125.0 Firefox/147.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36 Edg/145.0.0.0",
        ""
    )

    var keepScreenOn = object : ObservableValue<Boolean>(config.keepScreenOn) {
        override var value: Boolean = config.keepScreenOn
            set(value) {
                config.keepScreenOn = value
                field = value
                notifyObservers()
            }
            get() = config.keepScreenOn
    }

    fun setSearchEngineURL(url: String) {
        config.searchEngineURL.value = url
        if (homePageMode == Config.HomePageMode.SEARCH_ENGINE) {
            updateHomeAsSearchEngine(url)
        }
    }

    private fun updateHomeAsSearchEngine(url: String) {
        val regexForUrl = """^https?://[^#?/]+""".toRegex()
        val homePageUrl = regexForUrl.find(url)?.value ?: Config.HOME_URL_ALIAS
        homePage = homePageUrl
    }

    fun setHomePageProperties(homePageMode: Config.HomePageMode, customHomePageUrl: String?, homePageLinksMode: Config.HomePageLinksMode) {
        this.homePageMode = homePageMode
        this.homePageLinksMode = homePageLinksMode
        when (homePageMode) {
            Config.HomePageMode.SEARCH_ENGINE -> {
                updateHomeAsSearchEngine(config.searchEngineURL.value)
            }
            Config.HomePageMode.HOME_PAGE, Config.HomePageMode.BLANK -> {
                homePage = Config.HOME_URL_ALIAS
            }
            Config.HomePageMode.CUSTOM -> {
                homePage = customHomePageUrl ?: Config.HOME_URL_ALIAS
            }
        }
    }
}
