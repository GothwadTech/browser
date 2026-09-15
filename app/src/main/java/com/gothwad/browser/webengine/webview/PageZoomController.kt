package com.gothwad.browser.webengine.webview

import android.util.Log
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.gothwad.browser.Config
import com.gothwad.browser.model.WebTabState
import java.util.Locale
import kotlin.math.roundToInt

/**
 * ⚠️ PROTECTED FILE — READ BEFORE MODIFYING ⚠️
 *
 * This file has been rewritten THREE times to arrive at the correct zoom model:
 *   1. Custom CSS `zoom` property injected via JS — broke position:fixed element
 *      placement site-wide (e.g. Google dropdown displaced).
 *   2. WebSettings.textZoom + WebView.setInitialScale() — only scaled text (not layout)
 *      and broke layout on sites with their own viewport meta tag.
 *   3. WebView.zoomIn() / zoomOut() / zoomBy() — Android's native page-scale zoom.
 *      Magnified the rendered image uniformly like a photo, but did NOT reflow
 *      the layout into denser/wider arrangements. The page's responsive design
 *      never adapted, so "zoomed out" felt like a shrunk mobile page instead of
 *      a desktop-density view.
 *
 * The CORRECT technique (currently implemented): dynamically overwrite the page's
 * <meta name="viewport"> content attribute's width value, injected via
 * document-start JavaScript and re-applied immediately when the zoom level
 * changes on an already-loaded page. This changes the CSS layout viewport width
 * (the same mechanism desktop Chrome uses for Ctrl+/Ctrl- zoom), causing
 * responsive layouts to naturally reflow into desktop-density multi-column
 * arrangements at lower density levels and mobile-friendly arrangements at higher
 * density levels, without breaking position:fixed coordinate math.
 *
 * Do NOT reintroduce textZoom, setInitialScale(), a CSS `zoom` property injection,
 * or switch back to native WebView.zoomIn()/zoomOut()/zoomBy() as the primary zoom
 * mechanism.
 */
class PageZoomController(
    private val webView: WebViewEx,
    private val tab: WebTabState
) {
    companion object {
        private const val TAG = "PageZoomController"
        const val DEFAULT_SCALE = 1.0f

        fun computeViewportContent(densityPercent: Int, isDesktop: Boolean): String {
            val clamped = densityPercent.coerceIn(Config.WEB_PAGE_ZOOM_PERCENT_MIN, Config.WEB_PAGE_ZOOM_PERCENT_MAX)
            val scale = clamped / 100.0
            val scaleStr = String.format(Locale.US, "%.2f", scale)

            return if (isDesktop) {
                val baseDesktopWidth = 1024
                val targetWidth = (baseDesktopWidth / scale).roundToInt().coerceIn(320, 4096)
                "width=$targetWidth, initial-scale=$scaleStr"
            } else {
                when {
                    clamped == 100 -> "width=device-width, initial-scale=1.0"
                    clamped > 100 -> "width=device-width, initial-scale=$scaleStr"
                    else -> {
                        val targetWidth = when (clamped) {
                            90 -> 980
                            80 -> 1080
                            75 -> 1150
                            67 -> 1280
                            50 -> 1440
                            33 -> 1600
                            25 -> 1920
                            else -> {
                                val t = (100 - clamped) / 75.0
                                (960 + t * (1920 - 960)).roundToInt().coerceIn(960, 2560)
                            }
                        }
                        "width=$targetWidth, initial-scale=$scaleStr"
                    }
                }
            }
        }

        fun generateDocumentStartScript(targetContent: String): String {
            return """
                (function() {
                    var desired = "$targetContent";
                    window.__viewportDensityTarget = desired;
                    function applyViewport() {
                        var target = window.__viewportDensityTarget;
                        if (!target) return;
                        var metas = document.querySelectorAll('meta[name="viewport"]');
                        var meta = null;
                        for (var i = 0; i < metas.length; i++) {
                            if (!meta) {
                                meta = metas[i];
                            } else if (metas[i].parentNode) {
                                metas[i].parentNode.removeChild(metas[i]);
                            }
                        }
                        if (!meta) {
                            meta = document.createElement('meta');
                            meta.setAttribute('name', 'viewport');
                            var head = document.head || document.getElementsByTagName('head')[0] || document.documentElement;
                            if (head) {
                                head.insertBefore(meta, head.firstChild);
                            }
                        }
                        if (meta && meta.getAttribute('content') !== target) {
                            meta.setAttribute('content', target);
                        }
                    }
                    applyViewport();
                    if (window.MutationObserver && !window.__viewportDensityObserver) {
                        window.__viewportDensityObserver = new MutationObserver(function(mutations) {
                            var m = document.querySelector('meta[name="viewport"]');
                            if (!m || m.getAttribute('content') !== window.__viewportDensityTarget) {
                                applyViewport();
                            }
                        });
                        var root = document.head || document.documentElement;
                        if (root) {
                            window.__viewportDensityObserver.observe(root, { childList: true, subtree: true, attributes: true, attributeFilter: ['content'] });
                        } else {
                            document.addEventListener('DOMContentLoaded', function() {
                                var r = document.head || document.documentElement;
                                if (r && window.__viewportDensityObserver) {
                                    window.__viewportDensityObserver.observe(r, { childList: true, subtree: true, attributes: true, attributeFilter: ['content'] });
                                }
                            });
                        }
                    }
                    document.addEventListener('DOMContentLoaded', applyViewport);
                    window.addEventListener('load', applyViewport);
                })();
            """.trimIndent()
        }

        fun generateImmediateScript(targetContent: String): String {
            return """
                (function() {
                    var target = "$targetContent";
                    window.__viewportDensityTarget = target;
                    var metas = document.querySelectorAll('meta[name="viewport"]');
                    var meta = null;
                    for (var i = 0; i < metas.length; i++) {
                        if (!meta) {
                            meta = metas[i];
                        } else if (metas[i].parentNode) {
                            metas[i].parentNode.removeChild(metas[i]);
                        }
                    }
                    if (!meta) {
                        meta = document.createElement('meta');
                        meta.setAttribute('name', 'viewport');
                        var head = document.head || document.getElementsByTagName('head')[0] || document.documentElement;
                        if (head) {
                            head.insertBefore(meta, head.firstChild);
                        }
                    }
                    if (meta) {
                        meta.setAttribute('content', target);
                    }
                })();
            """.trimIndent()
        }
    }

    var currentAppliedZoomPercent: Int = 100
        private set

    private var documentStartScriptRef: ScriptHandler? = null

    init {
        val savedScale = tab.scale
        currentAppliedZoomPercent = if (savedScale != null) {
            (savedScale * 100).roundToInt().coerceIn(
                Config.WEB_PAGE_ZOOM_PERCENT_MIN,
                Config.WEB_PAGE_ZOOM_PERCENT_MAX
            )
        } else {
            val isDesktop = webView.isDesktopModeEnabled()
            webView.config.getEffectiveZoom(isDesktop)
        }
        tab.scale = currentAppliedZoomPercent / 100f
        updateDocumentStartScript()
    }

    fun canZoomIn(): Boolean {
        return currentAppliedZoomPercent < Config.WEB_PAGE_ZOOM_PERCENT_MAX
    }

    fun zoomIn(): Boolean {
        if (!canZoomIn()) return false
        val current = currentAppliedZoomPercent
        val next = Config.STANDARD_ZOOM_LEVELS.firstOrNull { it > current } ?: Config.WEB_PAGE_ZOOM_PERCENT_MAX
        setPageZoom(next)
        return true
    }

    fun canZoomOut(): Boolean {
        return currentAppliedZoomPercent > Config.WEB_PAGE_ZOOM_PERCENT_MIN
    }

    fun zoomOut(): Boolean {
        if (!canZoomOut()) return false
        val current = currentAppliedZoomPercent
        val prev = Config.STANDARD_ZOOM_LEVELS.lastOrNull { it < current } ?: Config.WEB_PAGE_ZOOM_PERCENT_MIN
        setPageZoom(prev)
        return true
    }

    fun zoomBy(factor: Float) {
        if (factor <= 0.001f || Math.abs(factor - 1.0f) < 0.001f) return
        val current = if (currentAppliedZoomPercent <= 0) 100 else currentAppliedZoomPercent
        val target = (current * factor).roundToInt()
        setPageZoom(target)
    }

    fun setPageZoom(percent: Int) {
        val clamped = percent.coerceIn(Config.WEB_PAGE_ZOOM_PERCENT_MIN, Config.WEB_PAGE_ZOOM_PERCENT_MAX)
        currentAppliedZoomPercent = clamped
        tab.scale = clamped / 100f
        applyDensityToWebView()
    }

    fun onScaleChanged(oldScale: Float, newScale: Float) {
        // No-op: pinch-zoom remains independent native photo-zoom;
        // density-zoom is controlled via viewport-width injection.
    }

    fun restoreZoomForTab() {
        val savedScale = tab.scale
        currentAppliedZoomPercent = if (savedScale != null) {
            (savedScale * 100).roundToInt().coerceIn(
                Config.WEB_PAGE_ZOOM_PERCENT_MIN,
                Config.WEB_PAGE_ZOOM_PERCENT_MAX
            )
        } else {
            val isDesktop = webView.isDesktopModeEnabled()
            webView.config.getEffectiveZoom(isDesktop)
        }
        tab.scale = currentAppliedZoomPercent / 100f
        applyDensityToWebView()
    }

    fun onPageStarted() {
        applyDensityToWebView()
    }

    fun applyDensityToWebView() {
        updateDocumentStartScript()
        applyToCurrentPage()
    }

    private fun updateDocumentStartScript() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        try {
            documentStartScriptRef?.remove()
            documentStartScriptRef = null
            val isDesktop = webView.isDesktopModeEnabled()
            val targetContent = computeViewportContent(currentAppliedZoomPercent, isDesktop)
            val script = generateDocumentStartScript(targetContent)
            documentStartScriptRef = WebViewCompat.addDocumentStartJavaScript(
                webView,
                script,
                setOf("*")
            )
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to update document start script: ", e)
        }
    }

    private fun applyToCurrentPage() {
        try {
            val isDesktop = webView.isDesktopModeEnabled()
            val targetContent = computeViewportContent(currentAppliedZoomPercent, isDesktop)
            val script = generateImmediateScript(targetContent)
            webView.evaluateJavascript(script, null)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to evaluate viewport script on page: ", e)
        }
    }

    fun destroy() {
        try {
            documentStartScriptRef?.remove()
            documentStartScriptRef = null
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to remove document start script on destroy: ", e)
        }
    }
}

