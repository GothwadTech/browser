package com.gothwad.browser.webengine.webview

import android.util.Log
import com.gothwad.browser.Config
import com.gothwad.browser.model.WebTabState

/**
 * ⚠️ PROTECTED FILE — READ BEFORE MODIFYING ⚠️
 *
 * This file has been rewritten twice already after two separate incorrect
 * implementations broke page zoom in different ways:
 *   1. A custom CSS `zoom` property injected via JavaScript — broke
 *      position:fixed element placement site-wide.
 *   2. WebSettings.textZoom + WebView.setInitialScale() — only scaled text
 *      (not buttons/images/layout) and broke layout on sites with their own
 *      viewport meta tag.
 *
 * The ONLY correct way to implement page zoom in an Android WebView is via
 * WebView's own native zoom API: zoomIn(), zoomOut(), zoomBy(float),
 * canZoomIn(), canZoomOut(). These already scale the entire rendered page
 * (text, images, buttons, layout) uniformly, exactly like Chrome's own
 * zoom, with no custom CSS/JS injection and no layout reflow.
 *
 * Do NOT reintroduce textZoom, setInitialScale(), or any custom CSS/JS
 * zoom injection into this file, or move zoom logic back into WebViewEx.kt
 * or WebViewWebEngine.kt, without a very deliberate, explicit reason —
 * if you are an AI agent modifying this codebase for an unrelated task,
 * do not touch this file unless the task is specifically about page zoom.
 */
class PageZoomController(
    private val webView: WebViewEx,
    private val tab: WebTabState
) {
    companion object {
        private const val TAG = "PageZoomController"
        const val DEFAULT_SCALE = 1.0f
    }

    var currentAppliedZoomPercent: Int = 100
        private set

    private var isProgrammaticZooming = false

    fun canZoomIn(): Boolean {
        return currentAppliedZoomPercent < Config.WEB_PAGE_ZOOM_PERCENT_MAX && webView.canZoomIn()
    }

    fun zoomIn(): Boolean {
        if (currentAppliedZoomPercent >= Config.WEB_PAGE_ZOOM_PERCENT_MAX) return false
        val success = webView.zoomIn()
        if (!success) {
            zoomBy(1.25f)
            return true
        }
        return true
    }

    fun canZoomOut(): Boolean {
        return currentAppliedZoomPercent > Config.WEB_PAGE_ZOOM_PERCENT_MIN && webView.canZoomOut()
    }

    fun zoomOut(): Boolean {
        if (currentAppliedZoomPercent <= Config.WEB_PAGE_ZOOM_PERCENT_MIN) return false
        val success = webView.zoomOut()
        if (!success) {
            zoomBy(0.8f)
            return true
        }
        return true
    }

    fun zoomBy(factor: Float) {
        if (factor <= 0.001f || Math.abs(factor - 1.0f) < 0.001f) return
        isProgrammaticZooming = true
        try {
            webView.zoomBy(factor)
            val prevScale = tab.scale ?: (currentAppliedZoomPercent / 100f)
            val newScale = (prevScale * factor).coerceIn(
                Config.WEB_PAGE_ZOOM_PERCENT_MIN / 100f,
                Config.WEB_PAGE_ZOOM_PERCENT_MAX / 100f
            )
            tab.scale = newScale
            currentAppliedZoomPercent = Math.round(newScale * 100).coerceIn(
                Config.WEB_PAGE_ZOOM_PERCENT_MIN,
                Config.WEB_PAGE_ZOOM_PERCENT_MAX
            )
        } catch (e: Throwable) {
            Log.w(TAG, "zoomBy failed: ", e)
        } finally {
            webView.post { isProgrammaticZooming = false }
        }
    }

    fun setPageZoom(percent: Int) {
        val clamped = percent.coerceIn(Config.WEB_PAGE_ZOOM_PERCENT_MIN, Config.WEB_PAGE_ZOOM_PERCENT_MAX)
        if (currentAppliedZoomPercent == clamped) return
        val current = if (currentAppliedZoomPercent <= 0) 100 else currentAppliedZoomPercent
        val factor = clamped.toFloat() / current.toFloat()
        zoomBy(factor)
    }

    fun onScaleChanged(oldScale: Float, newScale: Float) {
        if (!isProgrammaticZooming && oldScale > 0.001f && newScale > 0.001f) {
            val ratio = newScale / oldScale
            val prevScale = tab.scale ?: (currentAppliedZoomPercent / 100f)
            val updatedScale = (prevScale * ratio).coerceIn(
                Config.WEB_PAGE_ZOOM_PERCENT_MIN / 100f,
                Config.WEB_PAGE_ZOOM_PERCENT_MAX / 100f
            )
            tab.scale = updatedScale
            currentAppliedZoomPercent = Math.round(updatedScale * 100).coerceIn(
                Config.WEB_PAGE_ZOOM_PERCENT_MIN,
                Config.WEB_PAGE_ZOOM_PERCENT_MAX
            )
        }
    }

    fun restoreZoomForTab() {
        val savedScale = tab.scale
        if (savedScale != null) {
            val targetPercent = Math.round(savedScale * 100).coerceIn(
                Config.WEB_PAGE_ZOOM_PERCENT_MIN,
                Config.WEB_PAGE_ZOOM_PERCENT_MAX
            )
            if (currentAppliedZoomPercent != targetPercent) {
                val current = if (currentAppliedZoomPercent <= 0) 100 else currentAppliedZoomPercent
                val factor = targetPercent.toFloat() / current.toFloat()
                webView.post {
                    zoomBy(factor)
                }
            }
        }
    }

    fun onPageStarted() {
        currentAppliedZoomPercent = 100
        tab.scale = DEFAULT_SCALE
    }
}
