package com.gothwad.browser.activity.main

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.gothwad.browser.Config
import com.gothwad.browser.R
import com.gothwad.browser.model.Download
import com.gothwad.browser.utils.Utils
import com.gothwad.browser.activity.main.view.CursorMenuView
import java.io.File
import java.io.InputStream

internal fun MainActivity.onDownloadRequested(
    url: String,
    referer: String,
    originalDownloadFileName: String,
    userAgent: String?,
    mimeType: String? = null,
    operationAfterDownload: Download.OperationAfterDownload = Download.OperationAfterDownload.NOP,
    base64BlobData: String? = null,
    stream: InputStream? = null,
    size: Long = 0L
) {
    val proceedDownload = {
        downloadIntent = Download(
            url, originalDownloadFileName, null, operationAfterDownload,
            mimeType, referer, userAgent, base64BlobData, stream, size
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                MainActivity.MY_PERMISSIONS_REQUEST_EXTERNAL_STORAGE_ACCESS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                MainActivity.MY_PERMISSIONS_REQUEST_POST_NOTIFICATIONS_ACCESS
            )
        } else {
            startDownload()
        }
    }

    com.gothwad.browser.activity.downloads.DownloadPromptDialog(
        this,
        originalDownloadFileName,
        size,
        onConfirm = { proceedDownload() }
    ).show()
}

internal fun MainActivity.startDownload() {
    val download = this.downloadIntent ?: return
    this.downloadIntent = null
    downloadService?.startDownload(download)
    onDownloadStarted(download.filename)
}

internal fun MainActivity.onDownloadStarted(fileName: String) {
    Utils.showToast(
        this, getString(
            R.string.download_started,
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString() + File.separator + fileName
        )
    )
    showMenuOverlay()
}

internal fun MainActivity.hideSoftwareKeyboardIfVisible(): Boolean {
    val root = window.decorView.rootView
    val insets = ViewCompat.getRootWindowInsets(root) ?: return false
    if (!insets.isVisible(WindowInsetsCompat.Type.ime())) {
        return false
    }
    val view = currentFocus ?: root
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
    imm.hideSoftInputFromWindow(view.windowToken, 0)
    return true
}

internal fun MainActivity.handleBackNavigation() {
    Log.d("MainActivity", "handleBackNavigation")
    if (vb.vSearchOverlay.isVisible) {
        vb.vSearchOverlay.hide()
        vb.vActionBar.dismissExtendedAddressBarMode()
        return
    }

    if (tabsModel.currentTab.value?.webEngine?.isVirtualCursorMode() == false) {
        tabsModel.currentTab.value?.webEngine?.setVirtualCursorMode(true)
        backNavigationEventsAdapter.gameControllersLongPressBForBackNavigation = false
        return
    }

    if (vb.vCursorMenu.isVisible) {
        vb.vCursorMenu.close(CursorMenuView.CloseAnimation.ROTATE_OUT)
        return
    }
    if (vb.flWebViewContainer.cursorDrawerDelegate.canHandleBackNavigation()) {
        vb.flWebViewContainer.cursorDrawerDelegate.handleBackNavigation()
        return
    }
    if (isFullscreen) {
        tabsModel.currentTab.value?.webEngine?.hideFullscreenView()
        return
    }
    if (currentTabsDialog?.isShowing == true) {
        currentTabsDialog?.dismiss()
        return
    }

    if (!vb.rlActionBar.isVisible) {
        showMenuOverlay()
        return
    }

    val isNativeHomeVisible = vb.vNativeHome.isVisible
    val currentTab = tabsModel.currentTab.value
    val currentUrl = currentTab?.url ?: ""
    val isHome = isNativeHomeVisible || currentTab == null ||
            currentUrl.isEmpty() ||
            currentUrl == settingsModel.homePage ||
            currentUrl == Config.HOME_PAGE_URL ||
            currentUrl == Config.HOME_URL_ALIAS

    if (isHome) {
        val now = System.currentTimeMillis()
        if (now - lastBackPressTime < 2500L) {
            closeWindow()
        } else {
            lastBackPressTime = now
            Utils.showToast(this, R.string.press_back_again_to_exit)
        }
    } else {
        // Website is showing with fixed header
        if (currentTab != null && currentTab.webEngine.canGoBack()) {
            currentTab.webEngine.goBack()
        } else {
            showHomeScreen()
        }
    }
}

internal fun MainActivity.askUserAndOpenInExternalApp(url: String, intent: Intent) {
    if (openUrlInExternalAppDialog != null) {
        return
    }
    openUrlInExternalAppDialog = AlertDialog.Builder(this)
        .setTitle(R.string.site_asks_to_open_unknown_url)
        .setMessage(getString(R.string.site_asks_to_open_unknown_url_message) + "\n\n" + url)
        .setPositiveButton(R.string.yes) { _, _ ->
            try {
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, R.string.external_app_open_error, Toast.LENGTH_SHORT).show()
            }
        }
        .setNegativeButton(R.string.no, null)
        .setOnDismissListener {
            openUrlInExternalAppDialog = null
        }
        .show()
}
