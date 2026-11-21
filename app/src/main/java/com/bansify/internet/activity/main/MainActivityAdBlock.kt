package com.bansify.internet.activity.main

import android.net.Uri
import android.view.View
import com.bansify.internet.Config
import com.bansify.internet.R
import com.bansify.internet.model.HostConfig
import com.bansify.internet.model.WebTabState
import com.bansify.internet.widgets.NotificationView

object MainActivityAdBlockHelper {

    fun isAd(activity: MainActivity, url: Uri, acceptHeader: String?, baseUri: Uri): Boolean? {
        return activity.adblockModel.isAd(url, acceptHeader, baseUri)
    }

    fun isAdBlockingEnabled(activity: MainActivity, tab: WebTabState): Boolean {
        tab.adblock?.let { return it }
        return activity.config.adBlockEnabled
    }

    fun isDialogsBlockingEnabled(activity: MainActivity, tab: WebTabState): Boolean {
        if (tab.url == Config.HOME_PAGE_URL) return false
        return shouldBlockNewWindow(activity, tab, dialog = true, userGesture = false)
    }

    fun shouldBlockNewWindow(activity: MainActivity, tab: WebTabState, dialog: Boolean, userGesture: Boolean): Boolean {
        val hostConfig = activity.tabsModel.getCachedHostConfig(tab)
        val currentBlockPopupsLevelValue = hostConfig?.popupBlockLevel ?: HostConfig.DEFAULT_BLOCK_POPUPS_VALUE
        return when (currentBlockPopupsLevelValue) {
            HostConfig.POPUP_BLOCK_NONE -> false
            HostConfig.POPUP_BLOCK_DIALOGS -> dialog
            HostConfig.POPUP_BLOCK_NEW_AUTO_OPENED_TABS -> dialog || !userGesture
            else -> true
        }
    }

    fun onBlockedAd(activity: MainActivity, tab: WebTabState, uri: String) {
        if (!isAdBlockingEnabled(activity, tab)) return
        tab.blockedAds++
    }

    fun onBlockedDialog(activity: MainActivity, tab: WebTabState, newTab: Boolean) {
        tab.blockedPopups++
        activity.runOnUiThread {
            val msg = activity.getString(if (newTab) R.string.new_tab_blocked else R.string.popup_dialog_blocked)
            NotificationView.showBottomRight(activity.vb.rlRoot, R.drawable.ic_block_popups, msg)
        }
    }

    fun onCreateWindow(activity: MainActivity, dialog: Boolean, userGesture: Boolean, tab: WebTabState): View? {
        if (shouldBlockNewWindow(activity, tab, dialog, userGesture)) {
            onBlockedDialog(activity, tab, !dialog)
            return null
        }
        val newTab = WebTabState(incognito = activity.config.incognitoMode)
        val webView = activity.createWebView(newTab) ?: return null
        val currentTab = activity.tabsModel.currentTab.value ?: return null
        val index = activity.tabsModel.tabsStates.indexOf(currentTab) + 1
        activity.tabsModel.tabsStates.add(index, newTab)
        activity.changeTab(newTab)
        return webView
    }

    fun closeWindow(activity: MainActivity, internalRepresentation: Any) {
        for (t in activity.tabsModel.tabsStates) {
            if (t.webEngine.isSameSession(internalRepresentation)) {
                activity.closeTab(t)
                break
            }
        }
    }
}
