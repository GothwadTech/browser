package com.gothwad.browser.webengine.webview

import android.Manifest
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaDrm
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.webkit.WebViewFeature
import com.gothwad.browser.Config
import com.gothwad.browser.R

object WebViewExClients {

    fun createWebChromeClient(
        webViewEx: WebViewEx,
        callback: WebViewEx.Callback,
        onFullscreenCallbackSet: (WebChromeClient.CustomViewCallback?) -> Unit,
        onPickFileCallbackSet: (ValueCallback<Array<Uri>>?) -> Unit
    ): WebChromeClient {
        return object : WebChromeClient() {
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                return if (callback.isDialogsBlockingEnabled()) {
                    callback.onBlockedDialog(false)
                    result.cancel()
                    true
                } else super.onJsAlert(view, url, message, result)
            }

            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                return if (callback.isDialogsBlockingEnabled()) {
                    callback.onBlockedDialog(false)
                    result.cancel()
                    true
                } else super.onJsConfirm(view, url, message, result)
            }

            override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String, result: JsPromptResult): Boolean {
                return if (callback.isDialogsBlockingEnabled()) {
                    callback.onBlockedDialog(false)
                    result.cancel()
                    true
                } else super.onJsPrompt(view, url, message, defaultValue, result)
            }

            override fun onShowCustomView(view: View, cb: CustomViewCallback) {
                callback.onShowCustomView(view)
                onFullscreenCallbackSet(cb)
            }

            override fun onHideCustomView() {
                callback.onHideCustomView()
                onFullscreenCallbackSet(null)
            }

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                callback.onProgressChanged(newProgress)
            }

            override fun onReceivedTitle(view: WebView, title: String) {
                callback.onReceivedTitle(title)
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                if (request.resources.size == 1 &&
                    PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID == request.resources[0]) {
                    if (MediaDrm.isCryptoSchemeSupported(WebViewEx.WIDEVINE_UUID)) {
                        request.grant(request.resources)
                    } else {
                        request.deny()
                    }
                    return
                }

                val activity = callback.getActivity() ?: return
                webViewEx.webPermissionsRequest = request
                webViewEx.permRequestDialog = AlertDialog.Builder(activity)
                    .setMessage(activity.getString(R.string.web_perm_request_confirmation, TextUtils.join("\n", request.resources)))
                    .setCancelable(false)
                    .setNegativeButton(R.string.deny) { _, _ ->
                        webViewEx.webPermissionsRequest?.deny()
                        webViewEx.permRequestDialog = null
                        webViewEx.webPermissionsRequest = null
                    }
                    .setPositiveButton(R.string.allow) { _, _ ->
                        val req = webViewEx.webPermissionsRequest
                        webViewEx.webPermissionsRequest = null
                        if (req == null) return@setPositiveButton

                        val neededPermissions = ArrayList<String>()
                        val resourcesThatDoNotNeedToGrantPerms = ArrayList<String>()
                        for (resource in req.resources) {
                            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE == resource) {
                                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                    neededPermissions.add(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    resourcesThatDoNotNeedToGrantPerms.add(resource)
                                }
                            } else if (PermissionRequest.RESOURCE_VIDEO_CAPTURE == resource) {
                                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                    neededPermissions.add(Manifest.permission.CAMERA)
                                } else {
                                    resourcesThatDoNotNeedToGrantPerms.add(resource)
                                }
                            } else {
                                resourcesThatDoNotNeedToGrantPerms.add(resource)
                            }
                        }

                        if (neededPermissions.isNotEmpty()) {
                            webViewEx.requestedWebResourcesThatDoNotNeedToGrantAndroidPermissions = resourcesThatDoNotNeedToGrantPerms
                            callback.requestPermissions(neededPermissions.toTypedArray(), false)
                        } else {
                            req.grant(req.resources)
                        }

                        webViewEx.permRequestDialog = null
                    }
                    .create()
                webViewEx.permRequestDialog?.show()
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest) {
                webViewEx.permRequestDialog?.apply {
                    dismiss()
                    webViewEx.permRequestDialog = null
                }
                webViewEx.webPermissionsRequest = null
            }

            override fun onGeolocationPermissionsShowPrompt(origin: String, cb: GeolocationPermissions.Callback) {
                val activity = callback.getActivity() ?: return
                webViewEx.geoPermissionOrigin = origin
                webViewEx.geoPermissionsCallback = cb
                webViewEx.permRequestDialog = AlertDialog.Builder(activity)
                    .setMessage(activity.getString(R.string.web_perm_request_confirmation, activity.getString(R.string.location)))
                    .setCancelable(false)
                    .setNegativeButton(R.string.deny) { _, _ ->
                        webViewEx.geoPermissionsCallback?.invoke(webViewEx.geoPermissionOrigin, false, false)
                        webViewEx.permRequestDialog = null
                        webViewEx.geoPermissionsCallback = null
                    }
                    .setPositiveButton(R.string.allow) { _, _ ->
                        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            callback.requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), true)
                        } else {
                            webViewEx.geoPermissionsCallback?.invoke(webViewEx.geoPermissionOrigin, true, true)
                            webViewEx.geoPermissionsCallback = null
                        }
                        webViewEx.permRequestDialog = null
                    }
                    .create()
                webViewEx.permRequestDialog?.show()
            }

            override fun onGeolocationPermissionsHidePrompt() {
                webViewEx.permRequestDialog?.dismiss()
                webViewEx.permRequestDialog = null
                webViewEx.geoPermissionsCallback = null
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean = true

            override fun onShowFileChooser(mWebView: WebView, cb: ValueCallback<Array<Uri>>, fileChooserParams: FileChooserParams): Boolean {
                onPickFileCallbackSet(cb)
                val result = callback.onShowFileChooser(fileChooserParams.createIntent())
                if (!result) {
                    onPickFileCallbackSet(null)
                }
                return result
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap) {
                callback.onReceivedIcon(icon)
            }

            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                val newWv = callback.onCreateWindow(isDialog, isUserGesture) ?: return false
                (resultMsg.obj as WebView.WebViewTransport).webView = newWv
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView) {
                callback.closeWindow(window)
            }
        }
    }

    fun createWebViewClient(
        webViewEx: WebViewEx,
        callback: WebViewEx.Callback,
        uiHandler: Handler
    ): WebViewClient {
        return object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return callback.shouldOverrideUrlLoading(request.url.toString())
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val currentPageUrl = webViewEx.currentOriginalUrl
                if (currentPageUrl != null && currentPageUrl.toString().startsWith(Config.HOME_PAGE_URL, ignoreCase = true)) {
                    HomePageHelper.shouldInterceptRequest(view, request)?.let {
                        return it
                    }
                }
                if (webViewEx.isAdBlockingEnabled()) {
                    val ad = currentPageUrl?.let { callback.isAd(request, it) } ?: false
                    if (ad) {
                        uiHandler.post { callback.onBlockedAd(request.url) }
                        return WebResourceResponse("text/plain", "utf-8", "".byteInputStream())
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                webViewEx.currentOriginalUrl = url.toUri()
                webViewEx.onPageStartedResetZoom()
                callback.onPageStarted(url)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                callback.onPageFinished(url)
                webViewEx.evaluateJavascript(webViewEx.getGenericJSInjects(), null)
                webViewEx.post {
                    webViewEx.restoreZoomForTab()
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                if (webViewEx.trustSsl && webViewEx.lastSSLError?.certificate?.toString()?.equals(error.certificate.toString()) == true) {
                    webViewEx.trustSsl = false
                    webViewEx.lastSSLError = null
                    handler.proceed()
                    return
                }
                handler.cancel()
                val errUrl = error.url ?: return
                val origUrl = webViewEx.currentOriginalUrl ?: return
                if (Uri.parse(errUrl).host == origUrl.host) {
                    webViewEx.showCertificateErrorPage(error)
                }
            }

            override fun onScaleChanged(view: WebView?, oldScale: Float, newScale: Float) {
                super.onScaleChanged(view, oldScale, newScale)
                webViewEx.onExternalScaleChanged(oldScale, newScale)
                callback.onScaleChanged(oldScale, newScale)
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String, isReload: Boolean) {
                if (!isReload) {
                    callback.onVisited(url)
                }
            }

            override fun onReceivedHttpAuthRequest(
                view: WebView?,
                handler: HttpAuthHandler?,
                host: String?,
                realm: String?
            ) {
                val ctx = webViewEx.context
                val userNameEdit = EditText(ctx).also {
                    it.hint = ctx.getString(com.gothwad.browser.common.R.string.username)
                    it.isSingleLine = true
                }
                val passwordEdit = EditText(ctx).also {
                    it.hint = ctx.getString(com.gothwad.browser.common.R.string.password)
                    it.isSingleLine = true
                    it.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                val container = LinearLayout(ctx).also {
                    it.orientation = LinearLayout.VERTICAL
                    it.addView(userNameEdit)
                    it.addView(passwordEdit)
                }
                AlertDialog.Builder(ctx)
                    .setTitle(R.string.http_auth_title)
                    .setCancelable(false)
                    .setView(container)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        handler?.proceed(userNameEdit.text.toString(), passwordEdit.text.toString())
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ ->
                        handler?.cancel()
                    }
                    .show()
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                val didCrash = detail.didCrash()
                val priority = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) detail.rendererPriorityAtExit() else -1
                Log.e(WebViewEx.TAG, "onRenderProcessGone: didCrash=$didCrash, rendererPriority=$priority")
                return webViewEx.handleRenderProcessGone(view, didCrash)
            }
        }
    }
}
