package com.gothwad.browser.activity.main.view

import android.content.Context
import android.content.res.ColorStateList
import android.transition.TransitionManager
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.View.OnFocusChangeListener
import android.view.View.OnKeyListener
import android.view.animation.Animation
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.gothwad.browser.AppContext
import com.gothwad.browser.Config
import com.gothwad.browser.R
import com.gothwad.browser.activity.downloads.ActiveDownloadsModel
import com.gothwad.browser.databinding.ViewActionbarBinding
import com.gothwad.browser.utils.Utils
import com.gothwad.browser.utils.activemodel.ActiveModelsRepository

class ActionBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val vb = ViewActionbarBinding.inflate(LayoutInflater.from(context), this)
    var callback: Callback? = null
    private var downloadAnimation: Animation? = null
    private var downloadsModel = ActiveModelsRepository.get(ActiveDownloadsModel::class, context)
    private var extendedAddressBarMode = false
    var isHttpsSecure: Boolean = false
        private set

    interface Callback {
        fun closeWindow()
        fun showDownloads()
        fun showFavorites()
        fun showHistory()
        fun showSettings()
        fun initiateVoiceSearch()
        fun search(text: String)
        fun onExtendedAddressBarMode()
        fun onUrlInputDone()
        fun toggleIncognitoMode()
        fun toggleHeader()
        fun onSearchEngineIconClicked(anchorView: View) {}
        fun onSecurityIconClicked(anchorView: View) {}
    }

    private val etUrlFocusChangeListener = OnFocusChangeListener { _, focused ->
        if (focused) {
            enterExtendedAddressBarMode()

            val isVkDisabled = AppContext.provideConfig().disableVirtualKeyboard
            vb.etUrl.applyVirtualKeyboardPolicy()

            if (!isVkDisabled) {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(vb.etUrl, InputMethodManager.SHOW_IMPLICIT)
            } else {
                vb.etUrl.showSoftInputOnFocus = false
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(vb.etUrl.windowToken, 0)
                imm?.hideSoftInputFromWindow(windowToken, 0)
            }
            postDelayed(//workaround an android TV bug
                {
                    vb.etUrl.selectAll()
                    if (AppContext.provideConfig().disableVirtualKeyboard) {
                        vb.etUrl.applyVirtualKeyboardPolicy()
                        val imm2 = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm2?.hideSoftInputFromWindow(vb.etUrl.windowToken, 0)
                        imm2?.hideSoftInputFromWindow(windowToken, 0)
                    }
                }, 300)
        } else {
            dismissExtendedAddressBarMode()
        }
    }

    private val etUrlKeyListener = OnKeyListener { view, i, keyEvent ->
        when (keyEvent.keyCode) {
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (keyEvent.action == KeyEvent.ACTION_UP) {
                    performSearchInAddressBar()
                }
                return@OnKeyListener true
            }
        }
        false
    }

    init {
        init()
    }

    fun init() {
        orientation = HORIZONTAL

        if (isInEditMode) return

        if (Utils.isFireTV(context)) {
            (vb.ibVoiceSearch.parent as? ViewGroup)?.removeView(vb.ibVoiceSearch)
        } else {
            vb.ibVoiceSearch.setOnClickListener { callback?.initiateVoiceSearch() }
        }

        vb.ivLockIcon.setOnClickListener { view ->
            if (isHttpsSecure) {
                callback?.onSecurityIconClicked(view)
            } else {
                callback?.onSearchEngineIconClicked(view)
            }
        }

        vb.ivLockIcon.setOnKeyListener { view, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_BUTTON_A -> {
                        view.performClick()
                        true
                    }
                    else -> false
                }
            } else false
        }

        vb.etUrl.onFocusChangeListener = etUrlFocusChangeListener
        vb.etUrl.setOnKeyListener(etUrlKeyListener)
        vb.etUrl.setOnEditorActionListener { _, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)
            ) {
                performSearchInAddressBar()
                true
            } else {
                false
            }
        }
        vb.etUrl.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateAddressBarIcon(s?.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        updateAddressBarIcon(vb.etUrl.text.toString())

        AppContext.provideConfig().searchEngineURL.subscribe({ _ ->
            post { updateAddressBarIcon(vb.etUrl.text.toString()) }
        })
    }

    fun setAddressBoxText(text: String) {
        if (text == Config.HOME_PAGE_URL || text == Config.HOME_URL_ALIAS || text.isEmpty() || text == "about:blank") {
            vb.etUrl.setText("")
            updateAddressBarIcon("")
        } else {
            vb.etUrl.setText(text)
            updateAddressBarIcon(text)
        }
    }

    fun setHeaderToggleIcon(isExpanded: Boolean) {
        // No-op (header toggle icon removed from inside actionbar)
    }

    private fun getSearchEngineIcon(engineName: String): Int {
        return when (engineName.lowercase()) {
            "google" -> R.drawable.ic_logo_google
            "bing" -> R.drawable.ic_logo_bing
            "ddg", "duckduckgo" -> R.drawable.ic_logo_duckduckgo
            "yahoo" -> R.drawable.ic_logo_yahoo
            "yandex" -> R.drawable.ic_logo_yandex
            "startpage" -> R.drawable.ic_logo_startpage
            else -> R.drawable.ic_logo_google
        }
    }

    fun updateAddressBarIcon(url: String?) {
        val cleanUrl = (url ?: "").trim()
        val isHome = cleanUrl.isEmpty() ||
                cleanUrl == Config.HOME_PAGE_URL ||
                cleanUrl == Config.HOME_URL_ALIAS ||
                cleanUrl == "about:blank" ||
                cleanUrl.startsWith("file:///android_asset/home")

        if (!isHome && cleanUrl.startsWith("https://", ignoreCase = true)) {
            isHttpsSecure = true
            vb.ivLockIcon.setImageResource(R.drawable.ic_lock_security)
            vb.ivLockIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.day_night_icon_color))
            vb.ivLockIcon.contentDescription = context.getString(R.string.connection_is_secure)
        } else if (!isHome && cleanUrl.startsWith("http://", ignoreCase = true)) {
            isHttpsSecure = false
            vb.ivLockIcon.setImageResource(R.drawable.ic_menu_info)
            vb.ivLockIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.day_night_icon_color))
            vb.ivLockIcon.contentDescription = "Not secure"
        } else {
            isHttpsSecure = false
            val config = AppContext.provideConfig()
            val engineIconRes = getSearchEngineIcon(config.guessSearchEngineName())
            vb.ivLockIcon.setImageResource(engineIconRes)
            vb.ivLockIcon.imageTintList = null
            vb.ivLockIcon.clearColorFilter()
            vb.ivLockIcon.contentDescription = context.getString(R.string.choose_default_search_engine)
        }
    }

    fun setAddressBoxTextColor(color: Int) {
        vb.etUrl.setTextColor(color)
    }

    private fun enterExtendedAddressBarMode() {
        if (extendedAddressBarMode) return
        extendedAddressBarMode = true
        TransitionManager.beginDelayedTransition(this)
        callback?.onExtendedAddressBarMode()
    }

    fun dismissExtendedAddressBarMode() {
        if (!extendedAddressBarMode) return
        extendedAddressBarMode = false
        TransitionManager.beginDelayedTransition(this)
    }

    fun updateVirtualKeyboardPolicy() {
        vb.etUrl.applyVirtualKeyboardPolicy()
    }

    fun catchFocus() {
        vb.etUrl.requestFocus()
    }

    fun performSearchInAddressBar() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(vb.etUrl.windowToken, 0)
        val query = vb.etUrl.text.toString().trim()
        if (query.isNotEmpty()) {
            callback?.search(query)
            dismissExtendedAddressBarMode()
            callback?.onUrlInputDone()
        }
    }

    fun getUrlEditText(): View = vb.etUrl

    fun getVoiceSearchButton(): View = vb.ibVoiceSearch

    fun getLockOrSearchEngineIcon(): View = vb.ivLockIcon
}
