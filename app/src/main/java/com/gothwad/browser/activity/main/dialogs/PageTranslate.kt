package com.gothwad.browser.activity.main.dialogs

import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import com.gothwad.browser.AppContext
import java.util.Locale

/**
 * Page translation helper.
 *
 * The old menu entry pointed at `https://translate.google.com/translate?sl=..&tl=..&u=..`, an
 * endpoint Google shut down - it silently landed on a "feature moved" page, so the menu item was
 * broken for every user. Chrome itself translates pages through the `*.translate.goog` reverse
 * proxy; that is what this builds, so the whole site (markup, CSS, images, relative links) is
 * served translated and stays browsable, instead of only translating the URL string.
 *
 * `_x_tr_pto=wapp` suppresses Google's own toolbar banner, so no Google account/UI chrome leaks
 * into the browsing experience.
 */
internal object PageTranslate {

    /** Remembered so "Show original" keeps working while the app process lives. */
    private const val MAX_TRACKED_PAGES = 50
    private val translatedFrom = LinkedHashMap<String, String>()

    private val languages = linkedMapOf(
        "Device language" to "",
        "English" to "en",
        "हिन्दी (Hindi)" to "hi",
        "Español (Spanish)" to "es",
        "Français (French)" to "fr",
        "Deutsch (German)" to "de",
        "Italiano (Italian)" to "it",
        "Português (Portuguese)" to "pt",
        "Русский (Russian)" to "ru",
        "Українська (Ukrainian)" to "uk",
        "Polski (Polish)" to "pl",
        "Türkçe (Turkish)" to "tr",
        "العربية (Arabic)" to "ar",
        "فارسی (Persian)" to "fa",
        "中文 (Chinese)" to "zh-CN",
        "日本語 (Japanese)" to "ja",
        "한국어 (Korean)" to "ko",
        "తెలుగు (Telugu)" to "te",
        "தமிழ் (Tamil)" to "ta",
        "বাংলা (Bengali)" to "bn",
        "मराठी (Marathi)" to "mr",
        "ગુજરાતી (Gujarati)" to "gu",
        "ಕನ್ನಡ (Kannada)" to "kn",
        "മലയാളം (Malayalam)" to "ml",
        "Tiếng Việt (Vietnamese)" to "vi",
        "ไทย (Thai)" to "th"
    )

    fun isTranslatable(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        return url.startsWith("http://", true) || url.startsWith("https://", true)
    }

    fun defaultTargetLang(): String = Locale.getDefault().language

    /**
     * Builds the page-proxy URL for [url]. Returns null when the host cannot be proxied
     * (IP literal hostnames have no dot to encode, so they are not covered by the proxy).
     */
    fun buildProxyUrl(url: String, targetLang: String): String? {
        if (!isTranslatable(url)) return null
        val uri = try {
            Uri.parse(url)
        } catch (e: Exception) {
            return null
        }
        val host = uri.host ?: return null
        if (host.isEmpty() || !host.contains('.')) return null
        val proxyHost = host.replace('.', '-') + ".translate.goog"
        val path = uri.encodedPath.orEmpty()
        val query = uri.encodedQuery
        val source = StringBuilder(uri.scheme ?: "https").append("://").append(proxyHost).append(path)
        if (!query.isNullOrEmpty()) {
            source.append('?').append(query)
        }
        val separator = if (query.isNullOrEmpty()) "?" else "&"
        val tl = targetLang.takeIf { it.isNotBlank() } ?: defaultTargetLang()
        source.append(separator)
            .append("_x_tr_sl=auto")
            .append("&_x_tr_tl=").append(tl)
            .append("&_x_tr_hl=").append(tl)
            .append("&_x_tr_pto=wapp")
        val result = source.toString()
        remember(result, url)
        return result
    }

    private fun remember(proxyUrl: String, originalUrl: String) {
        synchronized(translatedFrom) {
            translatedFrom[proxyUrl] = originalUrl
            if (translatedFrom.size > MAX_TRACKED_PAGES) {
                val firstKey = translatedFrom.keys.firstOrNull()
                if (firstKey != null) translatedFrom.remove(firstKey)
            }
        }
    }

    /** If [url] is one we produced earlier, return the page it was translated from. */
    fun originalUrlOf(url: String): String? =
        synchronized(translatedFrom) { translatedFrom[url] }

    fun pickTargetLanguage(context: Context, onPicked: (String) -> Unit) {
        val titles = languages.keys.toTypedArray()
        val config = AppContext.provideConfig()
        val saved = config.translateTargetLanguage
        val preselected = languages.values.indexOf(saved).let { if (it >= 0) it else 0 }
        AlertDialog.Builder(context)
            .setTitle(com.gothwad.browser.R.string.translate_this_page)
            .setSingleChoiceItems(titles, preselected) { dialog, which ->
                val lang = languages.values.toList()[which]
                val effective = lang.ifBlank { defaultTargetLang() }
                config.translateTargetLanguage = effective
                dialog.dismiss()
                onPicked(effective)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
