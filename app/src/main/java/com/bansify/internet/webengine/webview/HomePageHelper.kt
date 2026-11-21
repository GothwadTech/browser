package com.bansify.internet.webengine.webview

import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import com.bansify.internet.singleton.FaviconsPool

object HomePageHelper {
    private val TAG = HomePageHelper::class.java.simpleName

    fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        Log.d(TAG, "shouldInterceptRequest: " + request.url)
        val url = request.url.toString()
        //check is scheme is favicon
        if (request.url.scheme == "favicon") {
            val host = request.url.host ?: return null
            val inputStream = FaviconsPool.getFaviconFileInputStream(host)
            if (inputStream != null) {
                Log.d(TAG, "shouldInterceptRequest: favicon stream found for $host")
                return WebResourceResponse("image/png", "utf-8", inputStream)
            } else {
                return WebResourceResponse(null, null, 404, "Not Found", null, null)
            }
        }
        return null
    }
}