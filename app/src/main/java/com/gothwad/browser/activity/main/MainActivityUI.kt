package com.gothwad.browser.activity.main

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.gothwad.browser.Config
import com.gothwad.browser.R
import com.gothwad.browser.activity.main.dialogs.BrowserSidebarPopup
import com.gothwad.browser.activity.main.dialogs.ChromeMenuPopup
import com.gothwad.browser.activity.main.dialogs.DownloadsSidebarPopup
import com.gothwad.browser.activity.main.dialogs.FavoritesSidebarPopup
import com.gothwad.browser.activity.main.dialogs.HistorySidebarPopup
import com.gothwad.browser.activity.main.dialogs.ShortcutDialog
import com.gothwad.browser.activity.main.dialogs.TabSearchSidebarPopup
import com.gothwad.browser.activity.main.dialogs.favorites.FavoriteEditorDialog
import com.gothwad.browser.model.FavoriteItem
import com.gothwad.browser.settings.SettingsDialog
import com.gothwad.browser.singleton.shortcuts.Shortcut
import com.gothwad.browser.utils.Utils
import com.gothwad.browser.utils.VoiceSearchHelper
import com.gothwad.browser.webengine.WebEngineFactory

internal fun MainActivity.showMenuOverlay() {
    if (isFullscreen) return
    vb.vActionBar.dismissExtendedAddressBarMode()
    vb.rlActionBar.visibility = View.VISIBLE
    vb.rlActionBar.translationY = 0f
}

internal fun MainActivity.hideMenuOverlay(hideBottomButtons: Boolean = true) {
    if (isFullscreen) {
        vb.rlActionBar.visibility = View.GONE
    } else {
        vb.rlActionBar.visibility = View.VISIBLE
        vb.rlActionBar.translationY = 0f
    }
}

internal fun MainActivity.toggleMenu() {
    if (isFullscreen) return
    vb.rlActionBar.visibility = View.VISIBLE
    vb.rlActionBar.translationY = 0f
}

internal fun MainActivity.setupHeaderClickListeners(incognitoMode: Boolean) {
    vb.ibMenu.setOnClickListener { showChromeMenu(vb.ibMenu) }
    vb.ibHistory.setOnClickListener { showHistoryActivity(vb.ibHistory) }
    vb.ibHome.setOnClickListener {
        if (vb.vNativeHome.visibility == View.VISIBLE) {
            vb.vNativeHome.scrollToTop()
        } else {
            showHomeScreen()
        }
    }
    vb.ibNewTab.setOnClickListener {
        openInNewTab(settingsModel.homePage, tabsModel.tabsStates.size, needToHideMenuOverlay = false, navigateImmediately = true)
    }
    vb.flTabsSwitcher.setOnClickListener { showTabsRowDialog() }
    vb.tvTabCountBadge.setOnClickListener { showTabsRowDialog() }
    vb.ibBack.setOnClickListener { navigateBack() }
    vb.ibForward.setOnClickListener {
        val tab = tabsModel.currentTab.value ?: return@setOnClickListener
        val isHome = vb.vNativeHome.isVisible || tab.url == Config.HOME_URL_ALIAS || tab.url.isEmpty()
        if (isHome && !tab.lastUrlBeforeHome.isNullOrEmpty()) {
            val restoreUrl = tab.lastUrlBeforeHome!!
            tab.lastUrlBeforeHome = null
            navigate(restoreUrl)
        } else if (tab.webEngine.canGoForward()) {
            tab.webEngine.goForward()
        }
    }
    vb.ibRefresh.setOnClickListener { refresh() }
    vb.ibDownloads.setOnClickListener { showDownloads(vb.ibDownloads) }

    // Top Tab Bar Buttons
    vb.ibTopTabSearch.setOnClickListener {
        TabSearchSidebarPopup(
            activity = this,
            onTabSelected = { tab -> switchToTab(tab) },
            onCloseTab = { tab -> closeTab(tab) },
            onReopenTab = { closedTab ->
                openInNewTab(closedTab.url, needToHideMenuOverlay = true)
            },
            onNewTab = {
                openInNewTab(settingsModel.homePage, needToHideMenuOverlay = true)
            }
        ).show()
    }
    vb.ibTopHideBars.setOnClickListener {
        vb.rlActionBar.visibility = View.GONE
    }
    vb.ibTopCloseApp.setOnClickListener {
        exitAppCompletely()
    }

    if (incognitoMode) {
        vb.rlActionBar.setBackgroundColor(Color.parseColor("#1F1F1F"))
    } else {
        applyAppTheme(config.theme.value)
    }

    vb.vActionBar.callback = this

    listOf(
        vb.ibTopTabSearch, vb.ibTopHideBars, vb.ibTopCloseApp,
        vb.ibTopNewTab,
        vb.ibHistory, vb.ibHome, vb.ibBack, vb.ibForward, vb.ibRefresh,
        vb.ibNewTab, vb.flTabsSwitcher, vb.ibDownloads, vb.ibMenu
    ).forEach {
        it.isFocusable = true
        it.isFocusableInTouchMode = false
        it.setOnTouchListener(bottomButtonsOnTouchListener)
        it.onFocusChangeListener = bottomButtonsFocusListener
    }
    updateBackForwardButtons(canGoBack = false, canGoForward = false)
}

fun MainActivity.exitAppCompletely() {
    try {
        com.gothwad.browser.service.keepalive.BrowserKeepAliveService.stop(this)
    } catch (e: Exception) {
        // ignore
    }
    try {
        tabsModel.onDetachActivity()
    } catch (e: Throwable) {
        // ignore
    }
    finishAffinity()
    android.os.Process.killProcess(android.os.Process.myPid())
}

fun getThemeBackgroundColor(context: android.content.Context, theme: Config.Theme): Int {
    return when (theme) {
        Config.Theme.BLACK_AMOLED -> Color.BLACK
        Config.Theme.BLACK_CHARCOAL -> Color.parseColor("#181818")
        Config.Theme.BLACK_MIDNIGHT -> Color.parseColor("#0F172A")
        Config.Theme.WHITE_PURE -> Color.WHITE
        Config.Theme.WHITE_WARM -> Color.parseColor("#FAF8F5")
        Config.Theme.WHITE_COOL -> Color.parseColor("#F1F5F9")
        Config.Theme.SYSTEM -> {
            val isNight = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            if (isNight) Color.parseColor("#181818") else Color.parseColor("#F1F5F9")
        }
    }
}

fun MainActivity.applyAppTheme(theme: Config.Theme) {
    val bgColor = getThemeBackgroundColor(this, theme)
    vb.rlRoot.setBackgroundColor(bgColor)
    if (!config.incognitoMode) {
        vb.rlActionBar.setBackgroundColor(bgColor)
    }
    vb.vNativeHome.applyThemeColor(bgColor)
}

internal fun MainActivity.setupSettingsSubscriptions() {
    config.userAgentString.subscribe(this.lifecycle, false) {
        for (tab in tabsModel.tabsStates) {
            tab.webEngine.userAgentString = it
        }
    }

    config.theme.subscribe(this.lifecycle, false) {
        when (it) {
            Config.Theme.BLACK_AMOLED,
            Config.Theme.BLACK_CHARCOAL,
            Config.Theme.BLACK_MIDNIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            Config.Theme.WHITE_PURE,
            Config.Theme.WHITE_WARM,
            Config.Theme.WHITE_COOL -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        applyAppTheme(it)
        WebEngineFactory.onThemeSettingUpdated(it)
    }

    settingsModel.keepScreenOn.subscribe(this.lifecycle) {
        if (it) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    viewModel.homePageLinks.subscribe(this) {
        val currentUrl = tabsModel.currentTab.value?.url ?: return@subscribe
        if (Config.HOME_PAGE_URL == currentUrl) {
            tabsModel.currentTab.value?.webEngine?.reload()
        }
    }
}

internal fun MainActivity.initiateVoiceSearchInternal() {
    hideMenuOverlay()
    voiceSearchHelper.initiateVoiceSearch(object : VoiceSearchHelper.Callback {
        override fun onPartialResult(text: String) {
            vb.vActionBar.setAddressBoxText(text)
        }

        override fun onResult(text: String?) {
            if (text.isNullOrBlank()) {
                Utils.showToast(this@initiateVoiceSearchInternal, getString(R.string.can_not_recognize))
                return
            }
            vb.vActionBar.setAddressBoxText(text)
            search(text)
            hideMenuOverlay()
        }

        override fun onError(errorMessage: String?) {
            if (!errorMessage.isNullOrBlank()) {
                Utils.showToast(this@initiateVoiceSearchInternal, errorMessage)
            }
        }
    })
}

internal fun MainActivity.onEditHomePageBookmark(favoriteItem: FavoriteItem) {
    FavoriteEditorDialog(this, object : FavoriteEditorDialog.Callback {
        override fun onDone(item: FavoriteItem) {
            viewModel.onHomePageLinkEdited(item)
        }
    }, favoriteItem).show()
}

internal fun MainActivity.showFavoritesDialog(anchorView: View? = null) {
    FavoritesSidebarPopup(this) { item ->
        item.url?.let { navigate(it) }
    }.show(anchorView ?: vb.ibMenu)
    hideMenuOverlay()
}

internal fun MainActivity.showFavorites(anchorView: View? = null) {
    showFavoritesDialog(anchorView)
}

internal fun MainActivity.showHistoryActivity(anchorView: View? = null) {
    HistorySidebarPopup(this) { item ->
        item.url?.let { navigate(it) }
    }.show(anchorView ?: vb.ibHistory)
    hideMenuOverlay()
}

internal fun MainActivity.showSettingsDialog(anchorView: View? = null) {
    SettingsDialog(this, settingsModel).show(anchorView ?: vb.ibMenu)
}

internal fun MainActivity.showChromeMenu(anchorView: View? = null) {
    ChromeMenuPopup(this).show(anchorView ?: vb.ibMenu)
}

internal fun MainActivity.showBrowserSidebar(anchorView: View? = null) {
    showChromeMenu(anchorView)
}

internal fun MainActivity.showDownloads(anchorView: View? = null) {
    DownloadsSidebarPopup(this).show(anchorView ?: vb.ibDownloads)
}

internal fun MainActivity.showDownloadsActivity(anchorView: View? = null) {
    showDownloads(anchorView)
}

fun MainActivity.showShortcutDialog(shortcut: Shortcut) {
    ShortcutDialog(this, shortcut).show()
}

internal val MainActivity.bottomButtonsOnTouchListener
    get() = View.OnTouchListener { v, e ->
        when (e.action) {
            MotionEvent.ACTION_DOWN -> true
            MotionEvent.ACTION_UP -> {
                hideMenuOverlay(false)
                v.performClick()
                true
            }
            else -> false
        }
    }

internal val MainActivity.bottomButtonsFocusListener
    get() = View.OnFocusChangeListener { _, hasFocus ->
        if (hasFocus) {
            hideMenuOverlay(false)
        }
    }

internal fun MainActivity.setupDragAndDropListener() {
    val dragListener = View.OnDragListener { v, event ->
        when (event.action) {
            android.view.DragEvent.ACTION_DRAG_STARTED -> true
            android.view.DragEvent.ACTION_DRAG_ENTERED -> {
                v.alpha = 0.95f
                true
            }
            android.view.DragEvent.ACTION_DRAG_EXITED,
            android.view.DragEvent.ACTION_DRAG_ENDED -> {
                v.alpha = 1.0f
                true
            }
            android.view.DragEvent.ACTION_DROP -> {
                v.alpha = 1.0f
                val clipData = event.clipData
                val localFile = event.localState as? java.io.File
                if (localFile != null) {
                    openFileInApp(localFile)
                    true
                } else if (clipData != null && clipData.itemCount > 0) {
                    val item = clipData.getItemAt(0)
                    val uri = item.uri
                    val text = item.text?.toString()
                    if (uri != null && uri.scheme == "file") {
                        val file = java.io.File(uri.path ?: "")
                        if (file.exists()) {
                            openFileInApp(file)
                        } else {
                            navigate(uri.toString())
                        }
                    } else if (uri != null) {
                        navigate(uri.toString())
                    } else if (!text.isNullOrEmpty()) {
                        val file = java.io.File(text)
                        if (file.exists()) {
                            openFileInApp(file)
                        } else {
                            navigate(text)
                        }
                    }
                    true
                } else {
                    false
                }
            }
            else -> true
        }
    }

    vb.flWebViewContainer.setOnDragListener(dragListener)
    vb.vNativeHome.setOnDragListener(dragListener)
}

