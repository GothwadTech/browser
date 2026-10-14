package com.gothwad.browser.activity.main

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.gothwad.browser.BrowserApp
import com.gothwad.browser.Config
import com.gothwad.browser.R
import com.gothwad.browser.model.Download
import com.gothwad.browser.model.FavoriteItem
import com.gothwad.browser.model.HomePageLink
import com.gothwad.browser.model.WebTabState
import com.gothwad.browser.singleton.AppDatabase
import com.gothwad.browser.utils.DownloadUtils
import com.gothwad.browser.utils.Utils
import com.gothwad.browser.webengine.WebEngine
import com.gothwad.browser.webengine.WebEngineWindowProviderCallback
import com.gothwad.browser.widgets.cursor.CursorDrawerDelegate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.InputStream

internal class WebEngineCallback(val activity: MainActivity, val tab: WebTabState) : WebEngineWindowProviderCallback {
    private var thumbnailJob: Job? = null

    override fun getActivity(): Activity = activity

    override fun onOpenInNewTabRequested(url: String, navigateImmediately: Boolean): WebEngine? {
        var index = activity.tabsModel.tabsStates.indexOf(activity.tabsModel.currentTab.value)
        index = if (index == -1) activity.tabsModel.tabsStates.size else index + 1
        return activity.openInNewTab(url, index, true, navigateImmediately)
    }

    override fun onDownloadRequested(url: String) {
        val fileName = Uri.parse(url).lastPathSegment ?: "download"
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(url))
        activity.onDownloadRequested(url, tab.url, fileName, tab.webEngine.userAgentString, mimeType)
    }

    override fun onDownloadRequested(
        url: String,
        referer: String,
        originalDownloadFileName: String?,
        userAgent: String?,
        mimeType: String?,
        operationAfterDownload: Download.OperationAfterDownload,
        base64BlobData: String?,
        stream: InputStream?,
        size: Long,
        contentDisposition: String?
    ) {
        val fileName = DownloadUtils.guessFileName(url, contentDisposition, mimeType)
        activity.onDownloadRequested(url, referer, fileName, userAgent, mimeType, operationAfterDownload, base64BlobData, stream, size)
    }

    override fun onDownloadRequested(
        url: String,
        userAgent: String?,
        contentDisposition: String,
        mimetype: String?,
        contentLength: Long
    ) {
        val fileName = DownloadUtils.guessFileName(url, contentDisposition, mimetype)
        activity.onDownloadRequested(
            url = url, referer = tab.url, originalDownloadFileName = fileName,
            userAgent = userAgent, mimeType = mimetype, size = contentLength
        )
    }

    override fun onProgressChanged(newProgress: Int) {
        if (tab != activity.tabsModel.currentTab.value) return
        activity.vb.progressBar.visibility = View.VISIBLE
        if (newProgress > 60) {
            activity.vb.progressBarGeneric.visibility = View.GONE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            activity.vb.progressBar.setProgress(newProgress, true)
        } else {
            activity.vb.progressBar.progress = newProgress
        }
        activity.uiHandler.removeCallbacks(activity.progressBarHideRunnable)
        if (newProgress == 100) {
            activity.vb.progressBarGeneric.visibility = View.GONE
            activity.uiHandler.postDelayed(activity.progressBarHideRunnable, 1000)
        } else {
            activity.uiHandler.postDelayed(activity.progressBarHideRunnable, 5000)
        }
    }

    override fun onReceivedTitle(title: String) {
        tab.title = title
        activity.currentTabsDialog?.refreshData()
        activity.refreshTopTabs()
        activity.viewModel.onTabTitleUpdated(tab)
    }

    override fun requestPermissions(array: Array<String>): Int {
        val requestCode = activity.lastCommonRequestsCode++
        activity.requestPermissions(array, requestCode)
        return requestCode
    }

    override fun onShowFileChooser(intent: Intent): Boolean {
        try {
            activity.startActivityForResult(intent, MainActivity.PICK_FILE_REQUEST_CODE)
        } catch (e: ActivityNotFoundException) {
            try {
                intent.type = "*/*"
                activity.startActivityForResult(intent, MainActivity.PICK_FILE_REQUEST_CODE)
            } catch (e: ActivityNotFoundException) {
                Utils.showToast(activity.applicationContext, activity.getString(R.string.err_cant_open_file_chooser))
                return false
            }
        }
        return true
    }

    override fun onReceivedIcon(icon: Bitmap) {
        activity.currentTabsDialog?.refreshData()
        activity.refreshTopTabs()
    }

    override fun shouldOverrideUrlLoading(url: String): Boolean {
        tab.lastLoadingUrl = url
        val uri = try { Uri.parse(url) } catch (e: Exception) { return true }
        if (uri.scheme == null) return true
        if (URLUtil.isNetworkUrl(url) || uri.scheme.equals("javascript", true) ||
            uri.scheme.equals("data", true) || uri.scheme.equals("about", true) || uri.scheme.equals("blob", true)
        ) {
            return false
        }
        if (uri.scheme.equals("intent", true)) {
            onOpenInExternalAppRequested(url)
            return true
        }
        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            if (intent.resolveActivity(BrowserApp.instance.packageManager) != null) {
                activity.runOnUiThread { activity.askUserAndOpenInExternalApp(url, intent) }
                true
            } else {
                activity.runOnUiThread { Utils.showToast(activity.applicationContext, activity.getString(R.string.err_no_app_to_handle_url)) }
                true
            }
        } catch (e: Exception) { true }
    }

    override fun onPageStarted(url: String?) {
        tab.isPageLoading = true
        tab.lastActiveTimestamp = System.currentTimeMillis()
        val isCurrent = activity.tabsModel.currentTab.value == tab
        if (isCurrent) {
            activity.vb.progressBar.visibility = View.VISIBLE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                activity.vb.progressBar.setProgress(15, true)
            } else {
                activity.vb.progressBar.progress = 15
            }
            activity.onWebViewUpdated(tab)
        }
        val webViewUrl = tab.webEngine.url
        tab.url = webViewUrl ?: (url ?: "")
        if (isCurrent) {
            // A real navigation replaces whatever page Home was covering, so the restore marker
            // must not survive it (otherwise Back would reveal a page that no longer exists).
            tab.lastUrlBeforeHome = null
            tab.lastTitleBeforeHome = null
            activity.vb.vActionBar.setAddressBoxText(tab.url)
        }
        tab.blockedAds = 0
        tab.blockedPopups = 0
    }

    override fun onPageFinished(url: String?) {
        tab.isPageLoading = false
        tab.lastActiveTimestamp = System.currentTimeMillis()
        val isCurrent = activity.tabsModel.currentTab.value == tab
        if (isCurrent) {
            activity.vb.progressBarGeneric.visibility = View.GONE
            activity.onWebViewUpdated(tab)
        }
        val webViewUrl = tab.webEngine.url
        tab.url = webViewUrl ?: (url ?: "")
        if (isCurrent) {
            activity.vb.vActionBar.setAddressBoxText(tab.url)
        }
        thumbnailJob?.cancel()
        thumbnailJob = activity.lifecycleScope.launch {
            try {
                val newThumbnail = tab.webEngine.renderThumbnail(tab.thumbnail)
                if (newThumbnail != null) {
                    tab.updateThumbnail(activity, newThumbnail)
                }
            } catch (e: Exception) {}
        }
    }

    override fun onPageCertificateError(url: String?) {
        activity.vb.vActionBar.setAddressBoxTextColor(Color.RED)
    }

    override fun isAd(url: Uri, acceptHeader: String?, baseUri: Uri): Boolean? =
        MainActivityAdBlockHelper.isAd(activity, url, acceptHeader, baseUri)

    override fun isAdBlockingEnabled(): Boolean =
        MainActivityAdBlockHelper.isAdBlockingEnabled(activity, tab)

    override fun isDialogsBlockingEnabled(): Boolean =
        MainActivityAdBlockHelper.isDialogsBlockingEnabled(activity, tab)

    override fun shouldBlockNewWindow(dialog: Boolean, userGesture: Boolean): Boolean =
        MainActivityAdBlockHelper.shouldBlockNewWindow(activity, tab, dialog, userGesture)

    override fun onBlockedAd(uri: String) =
        MainActivityAdBlockHelper.onBlockedAd(activity, tab, uri)

    override fun onBlockedDialog(newTab: Boolean) =
        MainActivityAdBlockHelper.onBlockedDialog(activity, tab, newTab)

    override fun onCreateWindow(dialog: Boolean, userGesture: Boolean): View? =
        MainActivityAdBlockHelper.onCreateWindow(activity, dialog, userGesture, tab)

    override fun closeWindow(internalRepresentation: Any) =
        MainActivityAdBlockHelper.closeWindow(activity, internalRepresentation)

    override fun onScaleChanged(oldScale: Float, newScale: Float) {
        tab.scale = newScale
    }

    override fun onCopyTextToClipboardRequested(url: String) {
        MainActivityWebContextMenuHelper.handleCopyTextToClipboard(activity, url)
    }

    override fun onShareUrlRequested(url: String) {
        MainActivityWebContextMenuHelper.handleShareUrl(activity, url)
    }

    override fun onOpenInExternalAppRequested(url: String) {
        MainActivityWebContextMenuHelper.handleOpenInExternalApp(activity, url)
    }

    override fun initiateVoiceSearch() {
        activity.initiateVoiceSearch()
    }

    override fun onEditHomePageBookmarkSelected(index: Int) {
        activity.lifecycleScope.launch {
            val bookmark = activity.viewModel.homePageLinks.firstOrNull { it.order == index }
            var favoriteItem: FavoriteItem? = bookmark?.favoriteId?.let {
                AppDatabase.db.favoritesDao().getById(it)
            }
            if (favoriteItem == null) {
                favoriteItem = FavoriteItem().apply {
                    title = bookmark?.title
                    url = bookmark?.url
                    order = index
                    homePageBookmark = true
                }
                activity.onEditHomePageBookmark(favoriteItem)
            } else {
                AlertDialog.Builder(activity)
                    .setTitle(R.string.bookmarks)
                    .setItems(arrayOf(activity.getString(R.string.edit), activity.getString(R.string.delete))) { _, which ->
                        when (which) {
                            0 -> activity.onEditHomePageBookmark(favoriteItem)
                            1 -> activity.viewModel.removeHomePageLink(bookmark!!)
                        }
                    }
                    .show()
            }
        }
    }

    override fun getHomePageLinks(): List<HomePageLink> = activity.viewModel.homePageLinks

    override fun onPrepareForFullscreen() {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity.window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        activity.vb.rlActionBar.visibility = View.GONE
        activity.isFullscreen = true
    }

    override fun onExitFullscreen() {
        if (!activity.config.keepScreenOn) {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        activity.window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        activity.vb.rlActionBar.visibility = View.VISIBLE
        activity.isFullscreen = false
        activity.showMenuOverlay()
    }

    override fun onVisited(url: String) {
        val currentTab = activity.tabsModel.currentTab.value ?: return
        if (!activity.config.incognitoMode) {
            activity.viewModel.logVisitedHistory(currentTab.title, url, currentTab.faviconHash)
        }
    }

    override fun onContextMenu(
        cursorDrawer: CursorDrawerDelegate,
        baseUri: String?,
        linkUri: String?,
        srcUri: String?,
        title: String?,
        altText: String?,
        textContent: String?,
        x: Int,
        y: Int
    ) {
        activity.uiHandler.post {
            suggestActionsForLink(baseUri, linkUri, srcUri, title, altText, textContent, x, y)
        }
    }

    override fun suggestActionsForLink(
        baseUri: String?,
        linkUri: String?,
        srcUri: String?,
        title: String?,
        altText: String?,
        textContent: String?,
        x: Int,
        y: Int
    ) {
        MainActivityWebContextMenuHelper.suggestActionsForLink(
            activity, tab, this, baseUri, linkUri, srcUri, title, altText, textContent, x, y
        )
    }

    override fun markBookmarkRecommendationAsUseful(bookmarkOrder: Int) {
        activity.viewModel.markBookmarkRecommendationAsUseful(bookmarkOrder)
    }

    override fun onScrollChange(scrollY: Int, oldScrollY: Int, dy: Int) {
        // Header is permanently fixed at the top
    }
}
