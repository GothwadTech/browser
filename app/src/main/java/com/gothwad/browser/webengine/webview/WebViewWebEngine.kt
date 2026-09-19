package com.gothwad.browser.webengine.webview

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import com.gothwad.browser.AppContext
import com.gothwad.browser.Config
import com.gothwad.browser.R
import com.gothwad.browser.model.WebTabState
import com.gothwad.browser.utils.Utils
import com.gothwad.browser.webengine.WebEngine
import com.gothwad.browser.webengine.WebEngineFactory
import com.gothwad.browser.webengine.WebEngineProvider
import com.gothwad.browser.webengine.WebEngineProviderCallback
import com.gothwad.browser.webengine.WebEngineWindowProviderCallback
import com.gothwad.browser.widgets.cursor.CursorDrawerDelegate
import com.gothwad.browser.widgets.cursor.CursorLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WebViewWebEngine(val tab: WebTabState) : WebEngine, CursorDrawerDelegate.Callback {
    private var webView: WebViewEx? = null
    var pageZoomController: PageZoomController? = null
    internal var callback: WebEngineWindowProviderCallback? = null
    private var viewParent: CursorLayout? = null
    private var fullScreenView: View? = null
    private val permissionsRequests = HashMap<Int, Boolean>()
    private val jsInterface = AndroidJSInterface(this)

    private val webViewCallback = WebViewWebEngineCallback(
        getCallback = { callback },
        getWebView = { webView },
        getViewParent = { viewParent },
        onCustomViewChanged = { view, isShow ->
            if (isShow) {
                fullScreenView = view
            } else {
                fullScreenView?.let { viewParent?.removeView(it) }
                fullScreenView = null
            }
        },
        onPermissionRecord = { reqCode, isGeo ->
            permissionsRequests[reqCode] = isGeo
        },
        onRenderProcessGoneHandler = { view, didCrash ->
            handleRenderProcessGone(view, didCrash)
        }
    )

    override fun getWebEngineName(): String = "WebView"

    override fun isSameSession(internalRepresentation: Any): Boolean = internalRepresentation == webView

    override val url: String? get() = webView?.url

    override var userAgentString: String? = null
        set(value) {
            field = value
            webView?.settings?.userAgentString = value
            webView?.applyDesktopMode()
        }

    override fun saveState(): Any {
        val bundle = Bundle()
        webView?.saveState(bundle)
        return bundle
    }

    override fun restoreState(savedInstanceState: Any) {
        if (savedInstanceState is Bundle) {
            webView?.restoreState(savedInstanceState)
        } else {
            throw IllegalArgumentException("savedInstanceState must be Bundle")
        }
    }

    override fun stateFromBytes(bytes: ByteArray): Any? = Utils.bytesToBundle(bytes)

    override fun loadUrl(url: String) { webView?.loadUrl(url) }
    override fun canGoForward(): Boolean = webView?.canGoForward() ?: false
    override fun goForward() { webView?.goForward() }
    override fun canZoomIn(): Boolean = pageZoomController?.canZoomIn() ?: (webView?.canZoomIn() ?: true)

    override fun zoomIn() {
        pageZoomController?.zoomIn() ?: webView?.zoomIn()
    }

    override fun canZoomOut(): Boolean = pageZoomController?.canZoomOut() ?: (webView?.canZoomOut() ?: true)

    override fun zoomOut() {
        pageZoomController?.zoomOut() ?: webView?.zoomOut()
    }

    override fun zoomBy(zoomBy: Float) {
        pageZoomController?.zoomBy(zoomBy) ?: webView?.zoomBy(zoomBy)
    }

    override fun setPageZoom(percent: Int) {
        pageZoomController?.setPageZoom(percent) ?: webView?.applyZoom(percent)
    }

    override fun evaluateJavascript(script: String) { webView?.evaluateJavascript(script, null) }
    override fun setNetworkAvailable(connected: Boolean) { webView?.setNetworkAvailable(connected) }
    override fun getView(): View? = webView

    @Throws(Exception::class)
    override fun getOrCreateView(activityContext: Context): View {
        if (webView == null) {
            val wv = WebViewEx(activityContext, webViewCallback, jsInterface)
            val cfg = AppContext.provideConfig()
            val effectiveUa = userAgentString ?: cfg.userAgentString.value ?: if (cfg.desktopMode.value) Config.DESKTOP_UA else null
            userAgentString = effectiveUa
            wv.settings.userAgentString = effectiveUa
            val pzc = PageZoomController(wv, tab)
            wv.pageZoomController = pzc
            this.pageZoomController = pzc
            this.webView = wv
            pzc.restoreZoomForTab()
            tab.adblock?.let { wv.onUpdateAdblockSetting(it) }
        }
        return webView!!
    }

    override fun canGoBack(): Boolean = webView?.canGoBack() ?: false
    override fun goBack() { webView?.goBack() }
    override fun clearHistory() { webView?.clearHistory() }
    override fun reload() { webView?.reload() }

    override fun onFilePicked(resultCode: Int, data: Intent?) {
        if (resultCode != android.app.Activity.RESULT_OK || data == null) return
        webView?.onFilePicked(data)
    }

    override fun onResume() { webView?.onResume() }
    override fun onPause() { webView?.onPause() }
    override fun onUpdateAdblockSetting(newState: Boolean) {
        tab.adblock = newState
        webView?.onUpdateAdblockSetting(newState)
    }
    override fun hideFullscreenView() { webView?.hideCustomView() }
    override fun togglePlayback() { webView?.evaluateJavascript("tvBroTogglePlayback()", null) }
    override fun stopPlayback() { webView?.evaluateJavascript("tvBroStopPlayback()", null) }
    override fun rewind() { webView?.evaluateJavascript("tvBroRewind()", null) }
    override fun fastForward() { webView?.evaluateJavascript("tvBroFastForward()", null) }
    override suspend fun renderThumbnail(bitmap: Bitmap?): Bitmap? =
        withContext(Dispatchers.Main.immediate) {
            webView?.renderThumbnail(bitmap)
        }

    override fun onAttachToWindow(callback: WebEngineWindowProviderCallback, parent: ViewGroup) {
        this.callback = callback
        val wv = webView ?: return
        this.viewParent = parent as? CursorLayout
        (wv.parent as? ViewGroup)?.removeView(wv)
        parent.removeAllViews()
        val lp = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        parent.addView(wv, lp)
        viewParent?.cursorDrawerDelegate?.callback = this
        pageZoomController?.restoreZoomForTab()
        onResume()
    }

    override fun onDetachFromWindow(completely: Boolean, destroyTab: Boolean) {
        try { onPause() } catch (e: Exception) {}
        (webView?.parent as? ViewGroup)?.removeView(webView)
        viewParent = null
        callback = null
        if (completely || destroyTab) {
            try { webView?.destroy() } catch (e: Exception) {}
            webView = null
            pageZoomController = null
        }
    }

    override fun trimMemory() {
        val wv = webView
        if (wv != null && !wv.isAttachedToWindow) {
            try {
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.destroy()
            } catch (e: Exception) {
                Log.w("WebViewWebEngine", "Error destroying webView in trimMemory: $e")
            }
            this.webView = null
            this.pageZoomController = null
        }
    }

    private fun handleRenderProcessGone(view: WebView, didCrash: Boolean): Boolean {
        Log.e("WebViewWebEngine", "Handling renderProcessGone for tab=${tab.id}, url=${tab.url}, didCrash=$didCrash, selected=${tab.selected}")
        val deadWv = webView
        try {
            (deadWv?.parent as? ViewGroup)?.removeView(deadWv)
        } catch (e: Throwable) {
            Log.w("WebViewWebEngine", "Error removing crashed WebView from parent: $e")
        }
        try {
            deadWv?.destroy()
        } catch (e: Throwable) {
            Log.w("WebViewWebEngine", "Error destroying crashed WebView: $e")
        }
        webView = null

        val isForeground = tab.selected && viewParent != null
        if (isForeground || didCrash) {
            val act = callback?.getActivity()
            act?.runOnUiThread {
                try {
                    Utils.showToast(act.applicationContext, act.getString(R.string.page_crashed_and_reloaded))
                } catch (e: Throwable) {
                    Log.w("WebViewWebEngine", "Failed to show crash toast: $e")
                }
            }

            if (isForeground) {
                val vp = viewParent
                val cb = callback
                if (vp != null && cb != null) {
                    try {
                        val ctx = vp.context
                        val freshWv = getOrCreateView(ctx)
                        (freshWv.parent as? ViewGroup)?.removeView(freshWv)
                        val lp = android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        vp.addView(freshWv, lp)
                        vp.cursorDrawerDelegate?.callback = this

                        val reloadUrl = if (tab.url.isNotBlank() && tab.url != "about:blank") tab.url else Config.HOME_PAGE_URL
                        loadUrl(reloadUrl)
                    } catch (e: Throwable) {
                        Log.e("WebViewWebEngine", "Error recovering from render process crash: $e")
                    }
                }
            }
        } else {
            tab.savedState = null
        }

        return true
    }

    override fun onPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
        val isGeo = permissionsRequests[requestCode] ?: return false
        permissionsRequests.remove(requestCode)
        if (grantResults.isEmpty()) return true
        webView?.onPermissionsResult(permissions, grantResults, isGeo)
        return true
    }

    override fun onLongPress(x: Int, y: Int) {
        webView?.let {
            it.evaluateJavascript(Scripts.LONG_PRESS_SCRIPT) { href ->
                val linkUrl = if (href == "null") null else href
                webViewCallback.onContextMenu(it.currentOriginalUrl.toString(), linkUrl, x, y)
            }
        }
    }

    override fun onCursorNearTop() { callback?.onScrollChange(0, 0, -100) }
    override fun onScrollDirection(dy: Int) { callback?.onScrollChange(0, 0, dy) }
    override fun isVirtualCursorMode(): Boolean = viewParent?.cursorEnabled ?: true

    override fun setVirtualCursorMode(enabled: Boolean) {
        viewParent?.cursorEnabled = enabled
        if (enabled) {
            viewParent?.cursorDrawerDelegate?.animateAppearing()
        }
        webView?.setVirtualCursorMode(enabled)
    }

    override fun getCursorDrawerDelegate(): CursorDrawerDelegate? = viewParent?.cursorDrawerDelegate

    companion object {
        init {
            WebEngineFactory.registerProvider(WebEngineProvider("WebView", object : WebEngineProviderCallback {
                override suspend fun initialize(context: Context, webViewContainer: CursorLayout) {}
                override fun createWebEngine(tab: WebTabState): WebEngine = WebViewWebEngine(tab)
                override suspend fun clearCache(ctx: Context) { WebView(ctx).clearCache(true) }
                override fun onThemeSettingUpdated(value: Config.Theme) {}
                override fun getWebEngineVersionString(): String {
                    val pkg = WebViewCompat.getCurrentWebViewPackage(AppContext.get())
                    return (pkg?.packageName ?: "unknown") + ":" + (pkg?.versionName ?: "unknown")
                }
            }))
        }
    }
}
