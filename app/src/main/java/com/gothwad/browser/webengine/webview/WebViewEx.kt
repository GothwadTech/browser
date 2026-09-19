package com.gothwad.browser.webengine.webview

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebBackForwardList
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.gothwad.browser.AppContext
import com.gothwad.browser.Config
import com.gothwad.browser.utils.DPADNavigationEventsAdapter
import java.net.URLEncoder
import java.util.UUID

@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
open class WebViewEx(context: Context, val callback: Callback, val jsInterface: AndroidJSInterface) : WebView(context) {
    companion object {
        val TAG = WebViewEx::class.java.simpleName
        const val WEB_VIEW_TAG = "Browser WebView"
        const val INTERNAL_SCHEME = "internal://"
        const val INTERNAL_SCHEME_WARNING_DOMAIN = "warning"
        const val INTERNAL_SCHEME_WARNING_DOMAIN_TYPE_CERT = "certificate"
        val WIDEVINE_UUID = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)

        @Volatile
        private var cachedGenericInjects: String? = null
    }

    private var virtualCursorMode: Boolean = true
    private var webChromeClient_: WebChromeClient
    internal var fullscreenViewCallback: WebChromeClient.CustomViewCallback? = null
    internal var pickFileCallback: ValueCallback<Array<Uri>>? = null
    internal var permRequestDialog: AlertDialog? = null
    internal var webPermissionsRequest: PermissionRequest? = null
    internal var requestedWebResourcesThatDoNotNeedToGrantAndroidPermissions: ArrayList<String>? = null
    internal var geoPermissionOrigin: String? = null
    internal var geoPermissionsCallback: GeolocationPermissions.Callback? = null
    var lastSSLError: SslError? = null
    var trustSsl: Boolean = false
    var currentOriginalUrl: Uri? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    internal val config = AppContext.provideConfig()
    var pageZoomController: PageZoomController? = null
    val currentAppliedZoomPercent: Int
        get() = pageZoomController?.currentAppliedZoomPercent ?: 100

    interface Callback {
        fun getActivity(): Activity?
        fun onOpenInNewTabRequested(url: String)
        fun onDownloadRequested(url: String)
        fun onThumbnailError()
        fun onShowCustomView(view: View)
        fun onHideCustomView()
        fun onProgressChanged(newProgress: Int)
        fun onReceivedTitle(title: String)
        fun onShowFileChooser(intent: Intent): Boolean
        fun onReceivedIcon(icon: Bitmap)
        fun requestPermissions(array: Array<String>, geo: Boolean)
        fun shouldOverrideUrlLoading(url: String): Boolean
        fun onPageStarted(url: String?)
        fun onPageFinished(url: String?)
        fun onPageCertificateError(url: String?)
        fun isAdBlockingEnabled(): Boolean
        fun isDialogsBlockingEnabled(): Boolean
        fun isAd(request: WebResourceRequest, baseUri: Uri): Boolean
        fun onBlockedAd(url: Uri)
        fun onBlockedDialog(newTab: Boolean)
        fun onCreateWindow(dialog: Boolean, userGesture: Boolean): WebViewEx?
        fun closeWindow(window: WebView)
        fun onDownloadStart(url: String, userAgent: String, contentDisposition: String, mimetype: String?, contentLength: Long)
        fun onScaleChanged(oldScale: Float, newScale: Float)
        fun onCopyTextToClipboardRequested(url: String)
        fun onShareUrlRequested(url: String)
        fun onOpenInExternalAppRequested(url: String)
        fun onVisited(url: String)
        fun onContextMenu(baseUrl: String?, href: String?, x: Int, y: Int)
        fun onScrollChange(scrollY: Int, oldScrollY: Int, dy: Int) {}
        fun onRenderProcessGone(view: WebView, didCrash: Boolean): Boolean = true
    }

    fun handleRenderProcessGone(view: WebView, didCrash: Boolean): Boolean {
        return callback.onRenderProcessGone(view, didCrash)
    }

    init {
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        isScrollbarFadingEnabled = true
        
        with(settings) {
            javaScriptEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            defaultTextEncodingName = "UTF-8"
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            setRenderPriority(WebSettings.RenderPriority.HIGH)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                offscreenPreRaster = true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                try {
                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this@WebViewEx, true)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set third party cookies: ", e)
                }
            }
            mediaPlaybackRequiresUserGesture = !config.allowAutoplayMedia
            setGeolocationEnabled(true)
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(true)
            setNeedInitialFocus(false)

            val effectiveUa = config.userAgentString.value ?: if (config.desktopMode.value) Config.DESKTOP_UA else null
            if (effectiveUa != null) {
                userAgentString = effectiveUa
            }

            if (config.webEngineDebug) {
                setWebContentsDebuggingEnabled(true)
            }
        }

        isLongClickable = true
        isFocusable = true
        isFocusableInTouchMode = true

        webChromeClient_ = WebViewExClients.createWebChromeClient(
            webViewEx = this,
            callback = callback,
            onFullscreenCallbackSet = { fullscreenViewCallback = it },
            onPickFileCallbackSet = { pickFileCallback = it }
        )

        webViewClient = WebViewExClients.createWebViewClient(
            webViewEx = this,
            callback = callback,
            uiHandler = uiHandler
        )

        webChromeClient = webChromeClient_

        setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            if (!url.startsWith("blob:")) {
                callback.onDownloadStart(url, userAgent, contentDisposition, mimetype, contentLength)
            }
        }

        addJavascriptInterface(jsInterface, "BrowserApp")
        applyDesktopMode()
    }

    override fun restoreState(inState: Bundle): WebBackForwardList? {
        val result = super.restoreState(inState)
        currentOriginalUrl = url?.toUri()
        return result
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (virtualCursorMode && DPADNavigationEventsAdapter.isNavigationGenericMotionSource(event.source))
            return false
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onResolvePointerIcon(event: MotionEvent, pointerIndex: Int): PointerIcon? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val systemIcon = super.onResolvePointerIcon(event, pointerIndex)
            return com.gothwad.browser.utils.HardwareMousePointerManager.resolvePointerIcon(context, systemIcon)
        }
        return super.onResolvePointerIcon(event, pointerIndex)
    }

    internal fun showCertificateErrorPage(error: SslError) {
        callback.onPageCertificateError(error.url)
        lastSSLError = error
        val url = INTERNAL_SCHEME + INTERNAL_SCHEME_WARNING_DOMAIN +
                "?type=" + INTERNAL_SCHEME_WARNING_DOMAIN_TYPE_CERT +
                "&url=" + URLEncoder.encode(error.url, "UTF-8")
        loadUrl(url)
    }

    override fun loadUrl(url: String) {
        when {
            Config.HOME_URL_ALIAS == url -> {
                when (config.homePageMode) {
                    Config.HomePageMode.BLANK -> loadDataWithBaseURL(null, "", "text/html", "UTF-8", null)
                    Config.HomePageMode.CUSTOM, Config.HomePageMode.SEARCH_ENGINE -> {
                        try {
                            currentOriginalUrl = config.homePage.toUri()
                            super.loadUrl(config.homePage)
                        } catch (e: Exception) {
                            loadDataWithBaseURL(null, "", "text/html", "UTF-8", null)
                        }
                    }
                    Config.HomePageMode.HOME_PAGE -> {
                        currentOriginalUrl = Config.HOME_PAGE_URL.toUri()
                        super.loadUrl(Config.HOME_PAGE_URL)
                    }
                }
            }
            url.startsWith(INTERNAL_SCHEME) -> {
                val uri = Uri.parse(url)
                if (uri.authority == INTERNAL_SCHEME_WARNING_DOMAIN && uri.getQueryParameter("type") == INTERNAL_SCHEME_WARNING_DOMAIN_TYPE_CERT) {
                    val data = context.assets.open("pages/warning-certificate.html").bufferedReader().use { it.readText() }
                    loadDataWithBaseURL("file:///android_asset/", data, "text/html", "UTF-8", uri.getQueryParameter("url"))
                } else if (uri.authority == "fileviewer") {
                    val filePath = uri.getQueryParameter("path")
                    if (filePath != null) {
                        val file = java.io.File(filePath)
                        val data = com.gothwad.browser.filemanager.FileViewerContentHelper.generateHtmlForFile(context, file)
                        loadDataWithBaseURL("file://${file.parent ?: ""}/", data, "text/html", "UTF-8", url)
                    }
                }
            }
            url.startsWith("file://") -> {
                val file = java.io.File(url.removePrefix("file://"))
                val ext = file.extension.lowercase(java.util.Locale.ROOT)
                if (com.gothwad.browser.filemanager.FileViewerContentHelper.isMarkdown(ext) ||
                    com.gothwad.browser.filemanager.FileViewerContentHelper.isCodeFile(ext)) {
                    val data = com.gothwad.browser.filemanager.FileViewerContentHelper.generateHtmlForFile(context, file)
                    loadDataWithBaseURL("file://${file.parent ?: ""}/", data, "text/html", "UTF-8", url)
                } else {
                    currentOriginalUrl = Uri.parse(url)
                    super.loadUrl(url)
                }
            }
            else -> {
                currentOriginalUrl = Uri.parse(url)
                super.loadUrl(url)
            }
        }
    }

    internal fun getGenericJSInjects(): String {
        var injects = cachedGenericInjects
        if (injects == null) {
            injects = context.assets.open("generic_injects.js").bufferedReader().use { it.readText() }
            cachedGenericInjects = injects
        }
        return injects
    }

    fun renderThumbnail(bitmap: Bitmap?): Bitmap? {
        if (width == 0 || height == 0) return null
        var thumbnail = bitmap
        if (thumbnail == null) {
            try {
                thumbnail = createBitmap(width, height, Bitmap.Config.RGB_565)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        if (thumbnail == null) return null
        val canvas = Canvas(thumbnail)
        val scaleFactor = thumbnail.width / width.toFloat()
        canvas.scale(scaleFactor, scaleFactor)
        canvas.translate(-scrollX.toFloat() * scaleFactor, -scrollY.toFloat() * scaleFactor)
        super.draw(canvas)
        return thumbnail
    }

    fun hideCustomView() {
        webChromeClient_.onHideCustomView()
    }

    fun onFilePicked(data: Intent) {
        pickFileCallback?.apply {
            if (data.data != null) {
                val uris = arrayOf(data.data!!)
                onReceiveValue(uris)
            }
        }
    }

    fun onPermissionsResult(permissions: Array<String>, grantResults: IntArray, typeGeo: Boolean) {
        if (typeGeo) {
            geoPermissionsCallback?.apply {
                val granted = grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
                invoke(geoPermissionOrigin, granted, granted)
                geoPermissionsCallback = null
                geoPermissionOrigin = null
            }
        } else {
            webPermissionsRequest?.apply {
                val resources = ArrayList<String>()
                for (i in permissions.indices) {
                    if (grantResults[i] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        if (android.Manifest.permission.CAMERA == permissions[i]) {
                            resources.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                        } else if (android.Manifest.permission.RECORD_AUDIO == permissions[i]) {
                            resources.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                        }
                    }
                }
                requestedWebResourcesThatDoNotNeedToGrantAndroidPermissions?.apply {
                    resources.addAll(this)
                    requestedWebResourcesThatDoNotNeedToGrantAndroidPermissions = null
                }
                if (resources.isEmpty()) {
                    deny()
                } else {
                    grant(resources.toTypedArray())
                }
                webPermissionsRequest = null
            }
        }
    }

    private var perTabAdblockOverride: Boolean? = null

    fun onUpdateAdblockSetting(adblockEnabled: Boolean) {
        this.perTabAdblockOverride = adblockEnabled
    }

    fun isAdBlockingEnabled(): Boolean {
        return perTabAdblockOverride ?: callback.isAdBlockingEnabled()
    }

    fun setVirtualCursorMode(enabled: Boolean) {
        this.virtualCursorMode = enabled
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo?): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs)
        if (config.disableVirtualKeyboard) {
            outAttrs?.imeOptions = (outAttrs?.imeOptions ?: 0) or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
            post {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.hideSoftInputFromWindow(windowToken, 0)
            }
        }
        return connection
    }

    override fun onCheckIsTextEditor(): Boolean {
        return super.onCheckIsTextEditor()
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (com.gothwad.browser.utils.HardwareInputManager.getInstance(context).isDeviceBlocked(event)) {
            return true
        }
        if (config.disableVirtualKeyboard) {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.hideSoftInputFromWindow(windowToken, 0)
        }
        return super.dispatchKeyEvent(event)
    }

    private var lastPointerX: Int = 0
    private var lastPointerY: Int = 0

    fun showWebContextMenu(x: Int, y: Int) {
        val hit = hitTestResult
        val linkUrl = if (hit != null && (hit.type == HitTestResult.SRC_ANCHOR_TYPE || hit.type == HitTestResult.SRC_IMAGE_ANCHOR_TYPE)) hit.extra else null
        val srcUrl = if (hit != null && (hit.type == HitTestResult.IMAGE_TYPE || hit.type == HitTestResult.SRC_IMAGE_ANCHOR_TYPE)) hit.extra else null
        callback.onContextMenu(
            baseUrl = url,
            href = linkUrl ?: srcUrl,
            x = x,
            y = y
        )
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val hwInput = com.gothwad.browser.utils.HardwareInputManager.getInstance(context)
        if (hwInput.isDeviceBlocked(event)) return true

        lastPointerX = event.x.toInt()
        lastPointerY = event.y.toInt()

        return super.onGenericMotionEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val hwInput = com.gothwad.browser.utils.HardwareInputManager.getInstance(context)
        if (hwInput.isDeviceBlocked(event)) return true

        lastPointerX = event.x.toInt()
        lastPointerY = event.y.toInt()

        return super.onTouchEvent(event)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        val dy = t - oldt
        if (Math.abs(dy) > 4 || t <= 5) {
            callback.onScrollChange(t, oldt, dy)
        }
    }

    fun isDesktopModeEnabled(): Boolean {
        return config.isDesktopMode()
    }

    fun applyDesktopMode() {
        val isDesktop = isDesktopModeEnabled()
        val effectiveUa = config.userAgentString.value ?: if (isDesktop) Config.DESKTOP_UA else null
        settings.userAgentString = effectiveUa
        val targetZoom = config.getEffectiveZoom(isDesktop)
        applyZoom(targetZoom)
    }

    fun applyZoom(percent: Int) {
        pageZoomController?.setPageZoom(percent)
    }

    fun onExternalScaleChanged(oldScale: Float, newScale: Float) {
        pageZoomController?.onScaleChanged(oldScale, newScale)
    }

    fun onPageStartedResetZoom() {
        pageZoomController?.onPageStarted()
    }

    fun restoreZoomForTab() {
        pageZoomController?.restoreZoomForTab()
    }

    override fun destroy() {
        pageZoomController?.destroy()
        super.destroy()
    }
}
