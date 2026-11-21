package com.bansify.internet.webengine.webview

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import com.bansify.internet.webengine.WebEngineWindowProviderCallback
import com.bansify.internet.widgets.cursor.CursorLayout

class WebViewWebEngineCallback(
    private val getCallback: () -> WebEngineWindowProviderCallback?,
    private val getWebView: () -> WebViewEx?,
    private val getViewParent: () -> CursorLayout?,
    private val onCustomViewChanged: (View?, Boolean) -> Unit,
    private val onPermissionRecord: (Int, Boolean) -> Unit,
    private val onRenderProcessGoneHandler: (WebView, Boolean) -> Boolean = { _, _ -> true }
) : WebViewEx.Callback {

    override fun getActivity(): Activity? = getCallback()?.getActivity()

    override fun onOpenInNewTabRequested(url: String) {
        getCallback()?.onOpenInNewTabRequested(url, true)
    }

    override fun onDownloadRequested(url: String) {
        getCallback()?.onDownloadRequested(url)
    }

    override fun onThumbnailError() {}

    override fun onShowCustomView(view: View) {
        val cb = getCallback() ?: return
        cb.onPrepareForFullscreen()
        getWebView()?.visibility = View.GONE
        getViewParent()?.addView(view)
        onCustomViewChanged(view, true)
    }

    override fun onHideCustomView() {
        onCustomViewChanged(null, false)
        getWebView()?.visibility = View.VISIBLE
        getCallback()?.onExitFullscreen()
    }

    override fun onProgressChanged(newProgress: Int) {
        getCallback()?.onProgressChanged(newProgress)
    }

    override fun onReceivedTitle(title: String) {
        getCallback()?.onReceivedTitle(title)
    }

    override fun requestPermissions(array: Array<String>, geo: Boolean) {
        val requestCode = getCallback()?.requestPermissions(array) ?: return
        onPermissionRecord(requestCode, geo)
    }

    override fun onShowFileChooser(intent: Intent): Boolean {
        return getCallback()?.onShowFileChooser(intent) ?: false
    }

    override fun onReceivedIcon(icon: Bitmap) {
        getCallback()?.onReceivedIcon(icon)
    }

    override fun shouldOverrideUrlLoading(url: String): Boolean {
        return getCallback()?.shouldOverrideUrlLoading(url) ?: false
    }

    override fun onPageStarted(url: String?) {
        getCallback()?.onPageStarted(url)
    }

    override fun onPageFinished(url: String?) {
        getCallback()?.onPageFinished(url)
    }

    override fun onPageCertificateError(url: String?) {
        getCallback()?.onPageCertificateError(url)
    }

    override fun isAd(request: WebResourceRequest, baseUri: Uri): Boolean {
        return getCallback()?.isAd(request.url, request.requestHeaders?.get("Accept"), baseUri) ?: false
    }

    override fun isAdBlockingEnabled(): Boolean {
        return getCallback()?.isAdBlockingEnabled() ?: false
    }

    override fun isDialogsBlockingEnabled(): Boolean {
        return getCallback()?.isDialogsBlockingEnabled() ?: false
    }

    override fun onBlockedAd(url: Uri) {
        getCallback()?.onBlockedAd(url.toString())
    }

    override fun onBlockedDialog(newTab: Boolean) {
        getCallback()?.onBlockedDialog(newTab)
    }

    override fun onCreateWindow(dialog: Boolean, userGesture: Boolean): WebViewEx? {
        return getCallback()?.onCreateWindow(dialog, userGesture) as? WebViewEx
    }

    override fun closeWindow(window: WebView) {
        getCallback()?.closeWindow(window)
    }

    override fun onDownloadStart(url: String, userAgent: String, contentDisposition: String, mimetype: String?, contentLength: Long) {
        getCallback()?.onDownloadRequested(url, userAgent, contentDisposition, mimetype, contentLength)
    }

    override fun onScaleChanged(oldScale: Float, newScale: Float) {
        getCallback()?.onScaleChanged(oldScale, newScale)
    }

    override fun onCopyTextToClipboardRequested(url: String) {
        getCallback()?.onCopyTextToClipboardRequested(url)
    }

    override fun onShareUrlRequested(url: String) {
        getCallback()?.onShareUrlRequested(url)
    }

    override fun onOpenInExternalAppRequested(url: String) {
        getCallback()?.onOpenInExternalAppRequested(url)
    }

    override fun onVisited(url: String) {
        getCallback()?.onVisited(url)
    }

    override fun onScrollChange(scrollY: Int, oldScrollY: Int, dy: Int) {
        getCallback()?.onScrollChange(scrollY, oldScrollY, dy)
    }

    override fun onRenderProcessGone(view: WebView, didCrash: Boolean): Boolean {
        return onRenderProcessGoneHandler(view, didCrash)
    }

    override fun onContextMenu(baseUrl: String?, href: String?, x: Int, y: Int) {
        val drawer = getViewParent()?.cursorDrawerDelegate ?: return
        getCallback()?.onContextMenu(
            drawer,
            baseUri = baseUrl,
            linkUri = href,
            srcUri = null,
            title = null,
            altText = null,
            textContent = null,
            x = x,
            y = y
        )
    }
}
