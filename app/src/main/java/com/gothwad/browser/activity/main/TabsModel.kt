package com.gothwad.browser.activity.main

import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.gothwad.browser.AppContext
import com.gothwad.browser.BrowserApp
import com.gothwad.browser.Config
import com.gothwad.browser.model.HostConfig
import com.gothwad.browser.model.WebTabState
import com.gothwad.browser.singleton.AppDatabase
import com.gothwad.browser.utils.Utils
import com.gothwad.browser.utils.activemodel.ActiveModel
import com.gothwad.browser.utils.observable.ObservableList
import com.gothwad.browser.utils.observable.ObservableValue
import com.gothwad.browser.webengine.WebEngineWindowProviderCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class TabsModel : ActiveModel() {
    companion object {
        var TAG: String = TabsModel::class.java.simpleName
    }

    var loaded = false
    val currentTab = ObservableValue<WebTabState?>(null)
    val tabsStates = ObservableList<WebTabState>()
    private val config = AppContext.provideConfig()
    private var incognitoMode = config.incognitoMode

    data class RecentlyClosedTab(
        val title: String,
        val url: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    val recentlyClosedTabs = mutableListOf<RecentlyClosedTab>()

    fun recordClosedTab(tab: WebTabState) {
        val url = tab.url
        if (url.isNotBlank() && url != Config.HOME_PAGE_URL && url != Config.HOME_URL_ALIAS && url != "about:blank") {
            val title = if (tab.title.isNotBlank()) tab.title else url
            recentlyClosedTabs.removeAll { it.url == url }
            recentlyClosedTabs.add(0, RecentlyClosedTab(title, url))
            if (recentlyClosedTabs.size > 25) {
                recentlyClosedTabs.removeAt(recentlyClosedTabs.size - 1)
            }
        }
    }

    init {
        tabsStates.subscribe({
            //auto-update positions on any list change
            var positionsChanged = false
            tabsStates.forEachIndexed { index, webTabState ->
                if (webTabState.position != index) {
                    webTabState.position = index
                    positionsChanged = true
                }
            }
            if (positionsChanged) {
                val tabsListClone = listOf(*tabsStates.toTypedArray())
                modelScope.launch(Dispatchers.IO) {
                    try {
                        val tabsDao = AppDatabase.db.tabsDao()
                        tabsDao.updatePositions(tabsListClone)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error updating tab positions in DB: $e")
                    }
                }
            }
        }, false)
    }

    fun loadState() = modelScope.launch(Dispatchers.Main) {
        if (loaded) {
            //check is incognito mode changed
            if (incognitoMode != config.incognitoMode) {
                incognitoMode = config.incognitoMode
                loaded = false
            } else {
                return@launch
            }
        }
        val tabsDao = AppDatabase.db.tabsDao()
        tabsStates.replaceAll(tabsDao.getAll(config.incognitoMode))
        loaded = true
    }

    /**
     * Persist only the lightweight per-tab flags (currently: desktop-mode override) without
     * touching the WebView state file, so a menu tap does not do any disk I/O on the UI thread.
     */
    fun persistDesktopMode(tab: WebTabState) {
        if (tab.id == 0L) {
            return
        }
        modelScope.launch(Dispatchers.IO) {
            try {
                AppDatabase.db.tabsDao().update(tab)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist desktop-mode flag: $e")
            }
        }
    }

    suspend fun saveTab(tab: WebTabState) = withContext(Dispatchers.IO) {
        try {
            val tabsDB = AppDatabase.db.tabsDao()
            if (tab.selected) {
                tabsDB.unselectAll(config.incognitoMode)
            }
            tab.saveWebViewStateToFile()
            if (tab.id != 0L) {
                tabsDB.update(tab)
            } else {
                tab.id = tabsDB.insert(tab)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save tab in DB: $e")
        }
    }

    fun onCloseTab(tab: WebTabState) {
        recordClosedTab(tab)
        tab.webEngine.onDetachFromWindow(completely = true, destroyTab = true)
        tabsStates.remove(tab)
        modelScope.launch(Dispatchers.IO) {
            try {
                val tabsDB = AppDatabase.db.tabsDao()
                tabsDB.delete(tab)
                tab.removeFiles()
            } catch (e: Exception) {
                Log.w(TAG, "Error deleting tab: $e")
            }
        }
    }

    fun onCloseAllTabs() = modelScope.launch(Dispatchers.IO) {
        try {
            val tabsClone = ArrayList(tabsStates)
            withContext(Dispatchers.Main) {
                tabsClone.forEach { recordClosedTab(it) }
                tabsStates.clear()
            }
            val tabsDB = AppDatabase.db.tabsDao()
            tabsDB.deleteAll(config.incognitoMode)
            tabsClone.forEach { it.removeFiles() }
        } catch (e: Exception) {
            Log.w(TAG, "Error closing all tabs: $e")
        }
    }

    fun onDetachActivity() {
        for (tab in tabsStates) {
            tab.webEngine.onDetachFromWindow(completely = true, destroyTab = false)
        }
    }

    fun changeTab(
        newTab: WebTabState,
        webViewProvider: (tab: WebTabState) -> View?,
        webViewParent: ViewGroup,
        webEngineWindowProviderCallback: WebEngineWindowProviderCallback
    ) {
        val oldTab = currentTab.value
        if (oldTab == newTab && newTab.webEngine.getView() != null && newTab.webEngine.getView()?.parent == webViewParent) {
            return
        }

        if (oldTab != null && oldTab != newTab) {
            oldTab.selected = false
            oldTab.webEngine.onDetachFromWindow(completely = false, destroyTab = false)
            oldTab.onPause()
            modelScope.launch(Dispatchers.IO) {
                try {
                    saveTab(oldTab)
                } catch (e: Exception) {
                    Log.w(TAG, "Error saving old tab: $e")
                }
            }
            pruneBackgroundWebViews(newTab)
        }

        tabsStates.forEach {
            it.selected = (it == newTab)
        }
        newTab.selected = true
        newTab.lastActiveTimestamp = System.currentTimeMillis()

        var wv = newTab.webEngine.getView()
        var needReloadUrl = false
        if (wv == null) {
            wv = webViewProvider(newTab)
            if (wv == null) {
                return
            }
            needReloadUrl = !newTab.restoreWebView()
        }

        newTab.webEngine.onAttachToWindow(webEngineWindowProviderCallback, webViewParent)

        if (needReloadUrl && newTab.url.isNotEmpty() && newTab.url != "about:blank" && newTab.url != Config.HOME_PAGE_URL && newTab.url != Config.HOME_URL_ALIAS) {
            newTab.webEngine.loadUrl(newTab.url)
        }
        newTab.webEngine.setNetworkAvailable(Utils.isNetworkConnected(BrowserApp.instance))

        currentTab.value = newTab
    }

    private val hostConfigCache = java.util.concurrent.ConcurrentHashMap<String, HostConfig>()

    fun getCachedHostConfig(tab: WebTabState): HostConfig? {
        val currentHostName = try {
            java.net.URL(tab.url).host
        } catch (e: Exception) {
            null
        } ?: return null

        tab.cachedHostConfig?.let {
            if (it.hostName == currentHostName) return it
        }
        hostConfigCache[currentHostName]?.let {
            tab.cachedHostConfig = it
            return it
        }
        modelScope.launch(Dispatchers.IO) {
            findHostConfig(tab, false)
        }
        return null
    }

    suspend fun findHostConfig(tab: WebTabState, createIfNotFound: Boolean): HostConfig? = withContext(Dispatchers.IO) {
        Log.d(WebTabState.TAG, "findOrCreateHostConfig")
        val currentHostName = try {
            java.net.URL(tab.url).host
        } catch (e: Exception) {
            Log.w(WebTabState.TAG, "Can not parse current url host: $e")
            return@withContext null
        }
        var hostConfig = tab.cachedHostConfig
        if (hostConfig == null || hostConfig.hostName != currentHostName) {
            hostConfig = hostConfigCache[currentHostName]
            if (hostConfig == null) {
                val db = com.gothwad.browser.singleton.AppDatabase.db.hostsDao()
                hostConfig = db.findByHostName(currentHostName)
                if (hostConfig == null && createIfNotFound) {
                    hostConfig = HostConfig(currentHostName)
                    hostConfig.id = db.insert(hostConfig)
                }
            }
            if (hostConfig != null) {
                hostConfigCache[currentHostName] = hostConfig
            }
            tab.cachedHostConfig = hostConfig
        }
        return@withContext hostConfig
    }

    suspend fun changePopupBlockingLevel(newLevel: Int, tab: WebTabState) {
        val hostConfig = findHostConfig(tab,true) ?: return
        hostConfig.popupBlockLevel = newLevel
        hostConfigCache[hostConfig.hostName] = hostConfig
        withContext(Dispatchers.IO) {
            AppDatabase.db.hostsDao().update(hostConfig)
        }
    }

    /**
     * Memory Management Strategy:
     * 1. Process Persistence: BrowserKeepAliveService (foreground service) keeps the process priority elevated
     *    when backgrounded on Android TV (where no swipe-to-kill UI exists), preventing the OS from prematurely
     *    killing the browser process and dropping tabs/session state.
     * 2. Tab-Level Proactive LRU Policy: Keeps up to [Config.maxLiveTabs] (default 6) tab WebViews simultaneously
     *    live in memory. 6 is balanced for typical Android TV hardware (1.5GB - 3GB RAM), providing instantaneous
     *    switching between recent tabs without risking OutOfMemory errors.
     *    When live tabs exceed this cap, least-recently-used background tabs (excluding actively loading tabs)
     *    are proactively trimmed (saving their state bundle and destroying their detached WebView).
     * 3. Seamless Restoration: When a trimmed tab is re-selected, it transparently restores its full state and
     *    history via [WebTabState.restoreWebView] or reloads its URL.
     * 4. Hard Safety Net: ComponentCallbacks2 [onTrimMemory] and [onLowMemory] are retained as emergency fallbacks
     *    to trim all inactive tabs and clear image caches under severe OS memory pressure.
     */
    fun onTrimMemory() {
        val active = currentTab.value
        tabsStates.forEach { tab ->
            if (tab != active) {
                tab.trimMemory()
            }
        }
    }

    fun pruneBackgroundWebViews(activeTab: WebTabState) {
        if (tabsStates.size <= 1) return

        val maxLive = config.maxLiveTabs
        val liveTabs = tabsStates.filter { it.webEngine.getView() != null }
        if (liveTabs.size <= maxLive) return

        val countToTrim = liveTabs.size - maxLive
        val candidatesToTrim = liveTabs
            .filter { it != activeTab && !it.isPageLoading }
            .sortedBy { it.lastActiveTimestamp }
            .take(countToTrim)

        for (tab in candidatesToTrim) {
            if (tab != currentTab.value && !tab.isPageLoading) {
                tab.trimMemory()
            }
        }
    }
}