package com.gothwad.browser.activity.main

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.gothwad.browser.Config
import com.gothwad.browser.R
import com.gothwad.browser.activity.IncognitoModeMainActivity
import com.gothwad.browser.model.HostConfig
import com.gothwad.browser.model.WebTabState
import com.gothwad.browser.utils.sameDay
import com.gothwad.browser.webengine.WebEngine
import com.gothwad.browser.webengine.WebEngineFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.Calendar
import kotlin.system.exitProcess

internal fun MainActivity.tabByTitleIndex(index: Int) =
    if (index >= 0 && index < tabsModel.tabsStates.size) tabsModel.tabsStates[index] else null

internal fun MainActivity.loadState() = lifecycleScope.launch(Dispatchers.Main) {
    Log.d("MainActivity", "loadState")
    WebEngineFactory.initialize(this@loadState, vb.flWebViewContainer)

    vb.progressBarGeneric.visibility = View.VISIBLE
    vb.progressBarGeneric.requestFocus()
    viewModel.loadState().join()
    viewModel.loadState().join()
    tabsModel.loadState().join()

    if (!isActive) {
        return@launch
    }

    vb.progressBarGeneric.visibility = View.GONE

    if (intent.data == null) {
        if (tabsModel.tabsStates.isEmpty()) {
            openInNewTab(settingsModel.homePage, 0,
                needToHideMenuOverlay = false,
                navigateImmediately = true
            )
        } else {
            var foundSelectedTab = false
            for (i in tabsModel.tabsStates.indices) {
                val tab = tabsModel.tabsStates[i]
                if (tab.selected) {
                    changeTab(tab)
                    foundSelectedTab = true
                    break
                }
            }
            if (!foundSelectedTab) {
                changeTab(tabsModel.tabsStates[0])
            }
        }
    } else {
        handleIntent(intent)
    }

    val currentTab = tabsModel.currentTab.value
    if (currentTab == null || currentTab.url == settingsModel.homePage) {
        showMenuOverlay()
        vb.ibMenu.requestFocus()
    }
    if (autoUpdateModel.needAutoCheckUpdates &&
        autoUpdateModel.updateChecker.versionCheckResult == null &&
        !autoUpdateModel.lastUpdateNotificationTime.sameDay(Calendar.getInstance())) {
        autoUpdateModel.checkUpdate(false) {
            if (autoUpdateModel.updateChecker.hasUpdate()) {
                autoUpdateModel.showUpdateDialogIfNeeded(this@loadState)
            }
        }
    }
}

internal fun MainActivity.handleIntent(intent: Intent) {
    Log.d("MainActivity", "handleIntent: " + intent.data)
    if (intent.getBooleanExtra("com.gothwad.browser.EXTRA_OPEN_IN_SAME_TAB", false) &&
        tabsModel.tabsStates.isNotEmpty()) {
        if (tabsModel.currentTab.value == null) {
            changeTab(tabsModel.tabsStates[0])
        }
        navigate(intent.data.toString())
        return
    }

    openInNewTab(
        intent.data.toString(), tabsModel.tabsStates.size, needToHideMenuOverlay = false,
        navigateImmediately = true
    )
}

internal fun MainActivity.updateBackForwardButtons(canGoBack: Boolean, canGoForward: Boolean) {
    vb.ibBack.isEnabled = true
    vb.ibBack.isFocusable = true
    vb.ibBack.alpha = if (canGoBack) 1.0f else 0.4f

    vb.ibForward.isEnabled = true
    vb.ibForward.isFocusable = true
    vb.ibForward.alpha = if (canGoForward) 1.0f else 0.4f
}

internal fun MainActivity.showHomeScreen() {
    val currentTab = tabsModel.currentTab.value
    if (currentTab != null) {
        val currentUrl = currentTab.url
        val isAlreadyHome = currentUrl.isEmpty() ||
                currentUrl == settingsModel.homePage ||
                currentUrl == Config.HOME_PAGE_URL ||
                currentUrl == Config.HOME_URL_ALIAS

        if (!isAlreadyHome) {
            currentTab.lastUrlBeforeHome = currentUrl
        }
        currentTab.url = Config.HOME_URL_ALIAS
        currentTab.title = getString(R.string.home_screen)
        currentTab.thumbnail = null
        currentTab.webEngine.clearHistory()
        currentTab.webEngine.loadUrl("about:blank")
        lifecycleScope.launch(Dispatchers.IO) {
            tabsModel.saveTab(currentTab)
        }
    }
    vb.vNativeHome.visibility = View.VISIBLE
    vb.vNativeHome.bringToFront()
    vb.rlActionBar.bringToFront()
    vb.vActionBar.setAddressBoxText("")
    updateBackForwardButtons(
        canGoBack = false,
        canGoForward = !currentTab?.lastUrlBeforeHome.isNullOrEmpty()
    )
    showMenuOverlay()
    vb.ibHome.requestFocus()
}

internal fun MainActivity.switchToTab(newTab: WebTabState) {
    val isHome = newTab.url == settingsModel.homePage || newTab.url == Config.HOME_PAGE_URL || newTab.url == Config.HOME_URL_ALIAS || newTab.url.isEmpty() || newTab.url == "about:blank"
    changeTab(newTab)
    if (isHome) {
        vb.vNativeHome.visibility = View.VISIBLE
        vb.vNativeHome.bringToFront()
        vb.rlActionBar.bringToFront()
        vb.vActionBar.setAddressBoxText("")
        updateBackForwardButtons(
            canGoBack = false,
            canGoForward = !newTab.lastUrlBeforeHome.isNullOrEmpty()
        )
        showMenuOverlay()
        vb.ibHome.requestFocus()
    } else {
        vb.vNativeHome.visibility = View.GONE
        vb.flWebViewContainer.visibility = View.VISIBLE
        vb.vActionBar.setAddressBoxText(newTab.url)
        updateBackForwardButtons(
            canGoBack = newTab.webEngine.canGoBack() == true,
            canGoForward = newTab.webEngine.canGoForward() == true
        )
        hideMenuOverlay(false)
        newTab.webEngine.getView()?.requestFocus()
    }
    updateTabCountBadge()
}

internal fun MainActivity.openInNewTab(
    url: String?,
    index: Int = 0,
    needToHideMenuOverlay: Boolean = false,
    navigateImmediately: Boolean = true
): WebEngine? {
    Log.d("MainActivity", "openInNewTab: url: $url, index: $index, needToHideMenuOverlay: $needToHideMenuOverlay, navigateImmediately: $navigateImmediately")
    if (url == null) {
        return null
    }
    val isHome = url.isEmpty() || url == settingsModel.homePage || url == Config.HOME_PAGE_URL || url == Config.HOME_URL_ALIAS
    val tab = WebTabState(
        url = url,
        title = if (isHome) getString(R.string.home_screen) else "",
        incognito = config.incognitoMode
    )
    createWebView(tab) ?: return null
    val targetIndex = if (index in 0..tabsModel.tabsStates.size) index else tabsModel.tabsStates.size
    tabsModel.tabsStates.add(targetIndex, tab)
    switchToTab(tab)
    if (navigateImmediately && url.isNotEmpty() && !isHome) {
        tab.webEngine.loadUrl(url)
    }
    return tab.webEngine
}

internal fun MainActivity.closeTab(tab: WebTabState?) {
    if (tab == null) return
    val position = tabsModel.tabsStates.indexOf(tab)
    if (position == -1) return
    val isCurrent = tabsModel.currentTab.value == tab
    tabsModel.onCloseTab(tab)
    if (tabsModel.tabsStates.isEmpty()) {
        openInNewTab(settingsModel.homePage, 0, needToHideMenuOverlay = false, navigateImmediately = true)
    } else if (isCurrent) {
        val nextIndex = if (position < tabsModel.tabsStates.size) position else tabsModel.tabsStates.size - 1
        val nextTab = tabsModel.tabsStates[nextIndex]
        switchToTab(nextTab)
    }
}

internal fun MainActivity.changeTab(newTab: WebTabState) {
    tabsModel.changeTab(newTab, { tab: WebTabState -> createWebView(tab) }, vb.flWebViewContainer, WebEngineCallback(this, newTab))
}

@SuppressLint("SetJavaScriptEnabled")
internal fun MainActivity.createWebView(tab: WebTabState): View? {
    val webView: View
    try {
        webView = tab.webEngine.getOrCreateView(this)
    } catch (e: Throwable) {
        e.printStackTrace()

        val dialogBuilder = AlertDialog.Builder(this)
            .setTitle(R.string.error)
            .setCancelable(false)
            .setMessage(R.string.err_webview_can_not_link)
            .setNegativeButton(R.string.exit) { _, _ -> finish() }

        val appPackageName = "com.google.android.webview"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName"))
        val activities = packageManager.queryIntentActivities(intent, 0)
        if (activities.size > 0) {
            dialogBuilder.setPositiveButton(R.string.find_in_apps_store) { _, _ ->
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                finish()
            }
        }
        dialogBuilder.show()
        return null
    }

    var ua = config.userAgentString.value
    if (ua == null && config.desktopMode.value) {
        ua = Config.DESKTOP_UA
    }
    if (ua?.contains("Browser/1.0 ") == true) {
        config.userAgentString.value = null
        ua = if (config.desktopMode.value) Config.DESKTOP_UA else null
    }
    tab.webEngine.userAgentString = ua

    return webView
}

internal fun MainActivity.onWebViewUpdated(tab: WebTabState) {
    if (tab != tabsModel.currentTab.value) return
    val isHome = tab.url == settingsModel.homePage || tab.url == Config.HOME_PAGE_URL || tab.url == Config.HOME_URL_ALIAS || tab.url.isEmpty() || tab.url == "about:blank"
    if (isHome) {
        updateBackForwardButtons(
            canGoBack = false,
            canGoForward = !tab.lastUrlBeforeHome.isNullOrEmpty()
        )
        vb.vNativeHome.visibility = View.VISIBLE
        vb.vNativeHome.bringToFront()
        vb.rlActionBar.bringToFront()
        vb.vActionBar.setAddressBoxText("")
        showMenuOverlay()
        val current = currentFocus
        if (current == null || (!isToolbarView(current) && !vb.vNativeHome.hasFocus())) {
            vb.ibHome.post { vb.ibHome.requestFocus() }
        }
    } else {
        updateBackForwardButtons(
            canGoBack = tab.webEngine.canGoBack() == true,
            canGoForward = tab.webEngine.canGoForward() == true
        )
        vb.vNativeHome.visibility = View.GONE
        vb.flWebViewContainer.visibility = View.VISIBLE
        hideMenuOverlay(false)
    }
}

internal fun MainActivity.syncTabWithTitles() {
    val tab = tabsModel.currentTab.value
    if (tab == null) {
        openInNewTab(settingsModel.homePage, tabsModel.tabsStates.size,
            needToHideMenuOverlay = false,
            navigateImmediately = true
        )
    } else {
        switchToTab(tab)
    }
}

internal fun MainActivity.navigateInternal(url: String) {
    Log.d("MainActivity", "navigate: $url")
    val isHome = url == settingsModel.homePage || url == Config.HOME_PAGE_URL || url == Config.HOME_URL_ALIAS || url.isEmpty()
    vb.vActionBar.setAddressBoxTextColor(ContextCompat.getColor(this, R.color.default_url_color))
    if (isHome) {
        showHomeScreen()
        return
    }
    val tab = tabsModel.currentTab.value
    if (tab != null) {
        val wasOnHome = vb.vNativeHome.isVisible || tab.url == Config.HOME_URL_ALIAS || tab.url.isEmpty() || tab.url == settingsModel.homePage
        if (wasOnHome) {
            tab.webEngine.clearHistory()
            tab.webEngine.loadUrl("about:blank")
        }
        tab.lastUrlBeforeHome = null
        tab.url = url
        vb.progressBarGeneric.visibility = View.VISIBLE
        tab.webEngine.loadUrl(url)
        switchToTab(tab)
    } else {
        openInNewTab(url, 0, needToHideMenuOverlay = true, navigateImmediately = true)
    }
}

internal fun MainActivity.navigateBackInternal(goHomeIfNoHistory: Boolean = false) {
    val currentTab = tabsModel.currentTab.value
    if (currentTab != null && currentTab.webEngine.canGoBack()) {
        currentTab.webEngine.goBack()
    } else {
        showHomeScreen()
    }
}

internal fun MainActivity.refreshInternal() {
    tabsModel.currentTab.value?.webEngine?.reload()
}

internal fun MainActivity.toggleIncognitoMode(andSwitchProcess: Boolean) = lifecycleScope.launch(Dispatchers.Main) {
    Log.d("MainActivity", "toggleIncognitoMode andSwitchProcess: $andSwitchProcess")
    val becomingIncognitoMode = !config.incognitoMode
    vb.progressBarGeneric.visibility = View.VISIBLE
    if (!becomingIncognitoMode) {
        withContext(Dispatchers.IO) {
            android.webkit.WebStorage.getInstance().deleteAllData()
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
        }

        WebEngineFactory.clearCache(this@toggleIncognitoMode)

        tabsModel.onCloseAllTabs().join()
        tabsModel.currentTab.value = null

        viewModel.clearIncognitoData().join()
    }
    vb.progressBarGeneric.visibility = View.GONE
    config.incognitoMode = becomingIncognitoMode
    if (andSwitchProcess) {
        switchProcess(becomingIncognitoMode)
    }
}

internal fun MainActivity.switchProcess(incognitoMode: Boolean, intentDataToCopy: android.os.Bundle? = null) {
    Log.d("MainActivity", "switchProcess incognitoMode: $incognitoMode")
    val activityClass = if (incognitoMode) IncognitoModeMainActivity::class.java
    else MainActivity::class.java
    val intent = Intent(this, activityClass)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    intent.putExtra(MainActivity.KEY_PROCESS_ID_TO_KILL, android.os.Process.myPid())
    intentDataToCopy?.let {
        intent.putExtras(it)
    }
    startActivity(intent)
    exitProcess(0)
}

internal fun MainActivity.applyScreenOrientationInternal() {
    requestedOrientation = when (config.screenOrientation) {
        Config.ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        Config.ORIENTATION_AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
}

internal fun MainActivity.toggleAdBlockForTab() {
    tabsModel.currentTab.value?.apply {
        val currentState = adblock ?: config.adBlockEnabled
        val newState = !currentState
        adblock = newState
        webEngine.onUpdateAdblockSetting(newState)
        onWebViewUpdated(this)
        refresh()
    }
}

internal suspend fun MainActivity.showPopupBlockOptionsInternal() {
    val tab = tabsModel.currentTab.value ?: return
    val currentHostConfig = tabsModel.findHostConfig(tab, false)
    val currentBlockPopupsLevelValue = currentHostConfig?.popupBlockLevel ?: HostConfig.DEFAULT_BLOCK_POPUPS_VALUE
    val hostName = currentHostConfig?.hostName ?: try { URL(tab.url).host } catch (e: Exception) { "" }
    AlertDialog.Builder(this)
        .setTitle(getString(R.string.block_popups_s, hostName))
        .setSingleChoiceItems(R.array.popup_blocking_level, currentBlockPopupsLevelValue) { dialog, itemId ->
            lifecycleScope.launch {
                tabsModel.changePopupBlockingLevel(itemId, tab)
                dialog.dismiss()
            }
        }
        .show()
}

fun MainActivity.openFileInNewTab(file: java.io.File) {
    val url = "file://${file.absolutePath}"
    openInNewTab(url, tabsModel.tabsStates.size, needToHideMenuOverlay = true, navigateImmediately = true)
}

fun MainActivity.openFileInApp(file: java.io.File) {
    openFileInNewTab(file)
}

