package com.gothwad.browser.singleton.shortcuts

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.widget.Toast
import androidx.annotation.UiThread
import com.gothwad.browser.BrowserApp
import com.gothwad.browser.Config
import com.gothwad.browser.R
import com.gothwad.browser.activity.main.MainActivity
import com.gothwad.browser.activity.main.applyWebPageZoom
import com.gothwad.browser.activity.main.closeTab
import com.gothwad.browser.activity.main.openInNewTab
import com.gothwad.browser.activity.main.showMenuOverlay
import com.gothwad.browser.activity.main.showTabsRowDialog
import com.gothwad.browser.activity.main.switchToTab
import com.gothwad.browser.activity.main.view.home.HomeData
import com.gothwad.browser.activity.main.view.home.HomeShortcutItem
import com.gothwad.browser.activity.main.view.home.NativeHomeView
import com.gothwad.browser.activity.main.zoomWebIn
import com.gothwad.browser.activity.main.zoomWebOut
import com.gothwad.browser.model.WebTabState
import com.gothwad.browser.webengine.WebEngine

/**
 * Created by PDT on 06.08.2017.
 */

class ShortcutMgr private constructor() {
    private val shortcuts = ArrayList<Shortcut>()
    private var trackingShortcuts: List<Shortcut>? = null
    private val prefs: SharedPreferences =
        BrowserApp.instance.getSharedPreferences(PREFS_SHORTCUTS, Context.MODE_PRIVATE)
    private val uiHandler = Handler(Looper.getMainLooper())

    init {
        var migratedCorruptCount = 0
        for (shortcut in Shortcut.entries) {
            val savedKey = prefs.getInt(shortcut.prefsKey, shortcut.defaultKeyCode)
            val savedMod = prefs.getInt(shortcut.prefsKey + "_mod", shortcut.defaultModifiers)
            val savedLp = prefs.getBoolean(shortcut.prefsKey + "_lp", shortcut.defaultLongPress)

            if (Shortcut.isPureModifierKey(savedKey)) {
                android.util.Log.w(TAG, "Migration: Clearing corrupt pure modifier shortcut for ${shortcut.name} (keyCode=$savedKey)")
                prefs.edit()
                    .remove(shortcut.prefsKey)
                    .remove(shortcut.prefsKey + "_mod")
                    .remove(shortcut.prefsKey + "_lp")
                    .apply()
                shortcut.keyCode = shortcut.defaultKeyCode
                shortcut.modifiers = shortcut.defaultModifiers
                shortcut.longPressFlag = shortcut.defaultLongPress
                migratedCorruptCount++
            } else {
                shortcut.keyCode = savedKey
                shortcut.modifiers = savedMod
                shortcut.longPressFlag = savedLp
            }
            if (shortcut.keyCode != 0 && !Shortcut.isPureModifierKey(shortcut.keyCode)) {
                shortcuts.add(shortcut)
            }
        }
        if (migratedCorruptCount > 0) {
            android.util.Log.i(TAG, "Shortcut migration completed: cleared $migratedCorruptCount corrupted pure-modifier shortcut(s).")
        }
    }

    fun save(shortcut: Shortcut) {
        if (shortcut.keyCode == 0 || Shortcut.isPureModifierKey(shortcut.keyCode)) {
            prefs.edit()
                    .remove(shortcut.prefsKey)
                    .remove(shortcut.prefsKey + "_mod")
                    .remove(shortcut.prefsKey + "_lp")
                    .apply()
            shortcuts.remove(shortcut)
            return
        }
        if (!shortcuts.contains(shortcut)) {
            shortcuts.add(shortcut)
        }
        prefs.edit()
                .putInt(shortcut.prefsKey, shortcut.keyCode)
                .putInt(shortcut.prefsKey + "_mod", shortcut.modifiers)
                .putBoolean(shortcut.prefsKey + "_lp", shortcut.longPressFlag)
                .apply()
    }

    fun resetAllToDefaults() {
        prefs.edit().clear().apply()
        shortcuts.clear()
        Shortcut.resetToDefaults()
        for (shortcut in Shortcut.entries) {
            if (shortcut.keyCode != 0) {
                shortcuts.add(shortcut)
            }
        }
    }

    fun findForId(id: Int): Shortcut {
        val shortcut = Shortcut.entries[id]
        for (s in shortcuts) {
            if (s == shortcut) {
                return s
            }
        }
        return shortcut
    }

    @UiThread
    fun process(shortcut: Shortcut, mainActivity: MainActivity, webEngine: WebEngine?) {
        when (shortcut) {
            Shortcut.NAVIGATE_BACK -> {
                mainActivity.navigateBack()
            }
            Shortcut.NAVIGATE_FORWARD -> {
                if (webEngine?.canGoForward() == true) {
                    webEngine.goForward()
                }
            }
            Shortcut.NAVIGATE_HOME -> {
                mainActivity.navigate(Config.HOME_URL_ALIAS)
            }
            Shortcut.REFRESH_PAGE -> {
                mainActivity.refresh()
            }
            Shortcut.HARD_RELOAD -> {
                webEngine?.reload()
            }
            Shortcut.NEW_TAB -> {
                mainActivity.openInNewTab(mainActivity.settingsModel.homePage, mainActivity.tabsModel.tabsStates.size, needToHideMenuOverlay = false, navigateImmediately = true)
            }
            Shortcut.CLOSE_TAB -> {
                mainActivity.closeTab(mainActivity.tabsModel.currentTab.value)
            }
            Shortcut.NEXT_TAB -> {
                val tabs = mainActivity.tabsModel.tabsStates
                if (tabs.size > 1) {
                    val currentIndex = tabs.indexOf(mainActivity.tabsModel.currentTab.value)
                    val nextIndex = (currentIndex + 1) % tabs.size
                    mainActivity.switchToTab(tabs[nextIndex])
                }
            }
            Shortcut.PREV_TAB -> {
                val tabs = mainActivity.tabsModel.tabsStates
                if (tabs.size > 1) {
                    val currentIndex = tabs.indexOf(mainActivity.tabsModel.currentTab.value)
                    val prevIndex = if (currentIndex - 1 < 0) tabs.size - 1 else currentIndex - 1
                    mainActivity.switchToTab(tabs[prevIndex])
                }
            }
            Shortcut.TABS_OVERVIEW -> {
                mainActivity.showTabsRowDialog()
            }
            Shortcut.FOCUS_ADDRESS_BAR -> {
                mainActivity.showMenuOverlay()
                mainActivity.vb.vActionBar.catchFocus()
            }
            Shortcut.VOICE_SEARCH -> {
                mainActivity.initiateVoiceSearch()
            }
            Shortcut.HISTORY -> {
                mainActivity.showHistory()
            }
            Shortcut.DOWNLOADS -> {
                mainActivity.showDownloads()
            }
            Shortcut.BOOKMARKS -> {
                mainActivity.showFavorites()
            }
            Shortcut.ADD_BOOKMARK -> {
                val currentTab = mainActivity.tabsModel.currentTab.value
                val url = currentTab?.url
                if (!url.isNullOrEmpty() && url != Config.HOME_URL_ALIAS && url != Config.HOME_PAGE_URL) {
                    val title = currentTab.title.ifEmpty { url }
                    val item = HomeShortcutItem(
                        title = title,
                        url = url,
                        iconDrawableRes = HomeData.getIconForUrlOrTitle(url, title),
                        isUserBookmark = true
                    )
                    val list = NativeHomeView.loadUserBookmarks(mainActivity).toMutableList()
                    if (list.none { it.url.equals(url, ignoreCase = true) }) {
                        list.add(item)
                        val sorted = HomeData.sortShortcutsWithGoogleFirst(list)
                        NativeHomeView.saveUserBookmarks(mainActivity, sorted)
                        Toast.makeText(mainActivity, "⭐ Added to Bookmarks: $title", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(mainActivity, "Already in Bookmarks", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            Shortcut.ZOOM_IN -> {
                mainActivity.zoomWebIn()
            }
            Shortcut.ZOOM_OUT -> {
                mainActivity.zoomWebOut()
            }
            Shortcut.ZOOM_RESET -> {
                mainActivity.applyWebPageZoom(100)
                Toast.makeText(mainActivity, R.string.quick_zoom_reset, Toast.LENGTH_SHORT).show()
            }
            Shortcut.FULLSCREEN -> {
                mainActivity.toggleHeader()
            }
            Shortcut.SETTINGS -> {
                mainActivity.showSettings()
            }
            Shortcut.TOGGLE_INCOGNITO -> {
                mainActivity.toggleIncognitoMode()
            }
            Shortcut.SCROLL_TOP -> {
                webEngine?.evaluateJavascript("window.scrollTo({top: 0, behavior: 'smooth'});")
            }
            Shortcut.SCROLL_BOTTOM -> {
                webEngine?.evaluateJavascript("window.scrollTo({top: document.body.scrollHeight, behavior: 'smooth'});")
            }
            Shortcut.PAGE_DOWN -> {
                webEngine?.evaluateJavascript("window.scrollBy({top: window.innerHeight * 0.85, behavior: 'smooth'});")
            }
            Shortcut.PAGE_UP -> {
                webEngine?.evaluateJavascript("window.scrollBy({top: -window.innerHeight * 0.85, behavior: 'smooth'});")
            }
            Shortcut.PLAY_PAUSE -> {
                webEngine?.togglePlayback()
            }
            Shortcut.MEDIA_STOP -> {
                webEngine?.stopPlayback()
            }
            Shortcut.MEDIA_REWIND -> {
                webEngine?.rewind()
            }
            Shortcut.MEDIA_FAST_FORWARD -> {
                webEngine?.fastForward()
            }
        }
    }

    private fun shortCutsForEvent(keyCode: Int, modifiers: Int): List<Shortcut> {
        if (Shortcut.isPureModifierKey(keyCode)) return emptyList()
        val findings = ArrayList<Shortcut>()
        for (shortcut in shortcuts) {
            if (shortcut.keyCode == keyCode) {
                if (shortcut.modifiers == modifiers) {
                    findings.add(shortcut)
                }
            }
        }
        return findings
    }

    private fun onKeyDown(event: KeyEvent, mainActivity: MainActivity, tab: WebTabState?): Boolean {
        if (Shortcut.isPureModifierKey(event.keyCode)) return false
        val shortcuts = shortCutsForEvent(event.keyCode, event.modifiers)
        if (shortcuts.isEmpty()) return false
        trackingShortcuts = shortcuts
        if (event.repeatCount == 0) {
            event.startTracking()
        }
        if (event.isLongPress) {
            return onKeyLongPress(event, mainActivity, tab)
        }
        return true
    }

    private fun onKeyUp(event: KeyEvent, mainActivity: MainActivity, tab: WebTabState?): Boolean {
        if (Shortcut.isPureModifierKey(event.keyCode)) return false
        val trackingShortcuts = trackingShortcuts ?: return false
        for (shortcut in trackingShortcuts) {
            if (Shortcut.isPureModifierKey(shortcut.keyCode) || shortcut.longPressFlag || event.modifiers != shortcut.modifiers) {
                continue
            }
            uiHandler.post { process(shortcut, mainActivity, tab?.webEngine) }
            this.trackingShortcuts = null
            return true
        }
        return false
    }

    private fun onKeyLongPress(event: KeyEvent, mainActivity: MainActivity, tab: WebTabState?): Boolean {
        if (Shortcut.isPureModifierKey(event.keyCode)) return false
        val trackingShortcuts = trackingShortcuts ?: return false
        for (shortcut in trackingShortcuts) {
            if (Shortcut.isPureModifierKey(shortcut.keyCode) || !shortcut.longPressFlag || event.modifiers != shortcut.modifiers) {
                continue
            }
            uiHandler.post { process(shortcut, mainActivity, tab?.webEngine) }
            this.trackingShortcuts = null
            return true
        }
        return false
    }

    fun handle(event: KeyEvent, mainActivity: MainActivity, value: WebTabState?): Boolean {
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> onKeyDown(event, mainActivity, value)
            KeyEvent.ACTION_UP -> onKeyUp(event, mainActivity, value)
            else -> false
        }
    }

    fun tryHandleEmulatedSimpleKeyPress(keyCode: Int, mainActivity: MainActivity, tab: WebTabState?): Boolean {
        if (Shortcut.isPureModifierKey(keyCode)) return false
        val shortcuts = shortCutsForEvent(keyCode, 0)
        if (shortcuts.isEmpty()) return false
        uiHandler.post { process(shortcuts.first(), mainActivity, tab?.webEngine) }
        return true
    }

    companion object {
        const val TAG = "ShortcutMgr"
        const val PREFS_SHORTCUTS = "shortcuts"

        private var instance: ShortcutMgr? = null

        @Synchronized fun getInstance(): ShortcutMgr {
            if (instance == null) {
                instance = ShortcutMgr()
            }
            return instance!!
        }
    }
}
