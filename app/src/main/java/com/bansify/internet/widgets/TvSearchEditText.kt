package com.bansify.internet.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText
import com.bansify.internet.AppContext
import com.bansify.internet.utils.HardwareInputManager

/**
 * Custom EditText for Android TV that properly respects the disable on-screen virtual keyboard setting.
 * When disableVirtualKeyboard is enabled (e.g. physical keyboard attached or remote typing):
 * - showSoftInputOnFocus is disabled
 * - onCreateInputConnection avoids extracting UI and actively suppresses soft keyboard requests
 * - hideSoftInputFromWindow is immediately invoked on focus, text changes, and keystrokes
 * - Filter out inputs from blocked hardware devices
 */
class TvSearchEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    private val isVirtualKeyboardDisabled: Boolean
        get() = try {
            AppContext.provideConfig().disableVirtualKeyboard
        } catch (e: Exception) {
            false
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyVirtualKeyboardPolicy()
    }

    fun applyVirtualKeyboardPolicy() {
        if (isVirtualKeyboardDisabled) {
            showSoftInputOnFocus = false
            hideIme()
        } else {
            showSoftInputOnFocus = true
        }
    }

    private fun hideIme() {
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(windowToken, 0)
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        applyVirtualKeyboardPolicy()
        if (isVirtualKeyboardDisabled) {
            outAttrs.imeOptions = outAttrs.imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        }
        val connection = super.onCreateInputConnection(outAttrs)
        if (isVirtualKeyboardDisabled) {
            post { hideIme() }
        }
        return connection
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        if (isVirtualKeyboardDisabled) {
            hideIme()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (HardwareInputManager.getInstance(context).isDeviceBlocked(event)) {
            return true
        }
        if (isVirtualKeyboardDisabled) {
            hideIme()
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent?): Boolean {
        if (isVirtualKeyboardDisabled) {
            hideIme()
        }
        return super.onKeyPreIme(keyCode, event)
    }

    override fun onCheckIsTextEditor(): Boolean {
        return true
    }
}
