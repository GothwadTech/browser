package com.gothwad.browser.activity.main

import android.util.Patterns
import com.gothwad.browser.Config
import com.gothwad.browser.R
import com.gothwad.browser.utils.Utils
import java.io.UnsupportedEncodingException
import java.net.URLEncoder

fun MainActivity.handleSearch(aText: String) {
    var text = aText
    val trimmedLowercased = text.trim { it <= ' ' }.lowercase()
    if (Patterns.WEB_URL.matcher(text).matches() || trimmedLowercased.startsWith("http://") || trimmedLowercased.startsWith("https://")) {
        if (!text.lowercase().contains("://")) {
            text = "https://$text"
        }
        navigate(text)
    } else {
        var query: String? = null
        try {
            query = URLEncoder.encode(text, "utf-8")
        } catch (e1: UnsupportedEncodingException) {
            e1.printStackTrace()
            Utils.showToast(this, R.string.error)
            return
        }
        val searchUrl = config.searchEngineURL.value.replace("[query]", query)
        navigate(searchUrl)
    }
}

fun MainActivity.isCurrentTabInDesktopMode(): Boolean {
    val currentTab = tabsModel.currentTab.value
    val engineUa = currentTab?.webEngine?.userAgentString ?: config.userAgentString.value
    return config.isDesktopMode(engineUa)
}

fun MainActivity.applyWebPageZoom(percent: Int, targetIsDesktop: Boolean? = null) {
    val clamped = percent.coerceIn(Config.WEB_PAGE_ZOOM_PERCENT_MIN, Config.WEB_PAGE_ZOOM_PERCENT_MAX)
    val isDesktop = targetIsDesktop ?: isCurrentTabInDesktopMode()
    config.setEffectiveZoom(isDesktop, clamped)
    tabsModel.tabsStates.forEach { tab ->
        val tabIsDesktop = tab.webEngine.userAgentString?.let { ua ->
            config.isDesktopUa(ua)
        } ?: config.isDesktopMode()
        if (tabIsDesktop == isDesktop) {
            tab.webEngine.setPageZoom(clamped)
        }
    }
}

fun MainActivity.zoomWebIn() {
    val isDesktop = isCurrentTabInDesktopMode()
    val current = config.getEffectiveZoom(isDesktop)
    val next = Config.STANDARD_ZOOM_LEVELS.firstOrNull { it > current } ?: Config.WEB_PAGE_ZOOM_PERCENT_MAX
    applyWebPageZoom(next, isDesktop)
}

fun MainActivity.zoomWebOut() {
    val isDesktop = isCurrentTabInDesktopMode()
    val current = config.getEffectiveZoom(isDesktop)
    val prev = Config.STANDARD_ZOOM_LEVELS.lastOrNull { it < current } ?: Config.WEB_PAGE_ZOOM_PERCENT_MIN
    applyWebPageZoom(prev, isDesktop)
}
