package com.gothwad.browser.activity.main.dialogs

import android.app.AlertDialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.gothwad.browser.R
import androidx.core.content.ContextCompat
import com.gothwad.browser.activity.main.MainActivity

/**
 * Chrome-style "Find in page".
 *
 * The previous implementation was a plain prompt dialog that called `findAllAsync()` and then
 * forgot about the result: no match counter, no next/previous, no way to leave the highlights
 * behind. WebView already exposes all of that natively through [WebView.setFindListener],
 * `findNext()` and `clearMatches()` - this only wires those three up (AGENTS.md §0: use the
 * native mechanism instead of faking a browser feature).
 */
internal object FindInPageDialog {

    /** Remembered between openings so re-opening the bar keeps the last term, like Chrome. */
    private var lastQuery: String = ""

    fun show(activity: MainActivity) {
        val tab = activity.tabsModel.currentTab.value ?: return
        val webView = tab.webEngine.getView() as? WebView ?: return
        val ctx: Context = activity

        val density = ctx.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val input = EditText(ctx).apply {
            hint = ctx.getString(R.string.find_in_page_hint)
            setText(lastQuery)
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isFocusableInTouchMode = true
        }

        val counter = TextView(ctx).apply {
            setTextColor(ContextCompat.getColor(ctx, R.color.day_night_text_secondary))
            textSize = 13f
            gravity = Gravity.CENTER
            minWidth = dp(72)
            text = ""
        }

        val btnPrevious = imageButton(ctx, R.drawable.ic_keyboard_arrow_up, R.string.find_previous)
        val btnNext = imageButton(ctx, R.drawable.ic_keyboard_arrow_down, R.string.find_next)
        val btnClose = imageButton(ctx, R.drawable.ic_close_grey_900_24dp, R.string.find_done)

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(6), dp(6))
            addView(
                input,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(counter, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)))
            addView(btnPrevious, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(btnNext, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(btnClose, LinearLayout.LayoutParams(dp(40), dp(40)))
        }

        var totalMatches = 0
        var activeMatch = 0
        var isDoneCounting = false

        fun refreshCounter() {
            counter.text = when {
                input.text.isNullOrEmpty() -> ""
                !isDoneCounting -> "…"
                totalMatches == 0 -> ctx.getString(R.string.find_no_matches)
                else -> "$activeMatch of $totalMatches"
            }
            val found = isDoneCounting && totalMatches > 0
            btnPrevious.isEnabled = found
            btnNext.isEnabled = found
            btnPrevious.alpha = if (found) 1f else 0.35f
            btnNext.alpha = if (found) 1f else 0.35f
        }

        val findListener = WebView.FindListener { activeMatchOrdinal, numberOfMatches, doneCounting ->
            activeMatch = activeMatchOrdinal
            totalMatches = numberOfMatches
            isDoneCounting = doneCounting
            refreshCounter()
        }
        webView.setFindListener(findListener)

        fun runFind() {
            val query = input.text?.toString().orEmpty()
            lastQuery = query
            isDoneCounting = false
            if (query.isEmpty()) {
                totalMatches = 0
                activeMatch = 0
                webView.clearMatches()
                isDoneCounting = true
                refreshCounter()
            } else {
                // findAllAsync re-runs the whole search; Chromium keeps the active match index
                // when the query is unchanged, which is exactly what Chrome does while typing.
                webView.findAllAsync(query)
            }
            refreshCounter()
        }

        var pendingSearch: Runnable? = null
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                pendingSearch?.let { input.removeCallbacks(it) }
                val runnable = Runnable { runFind() }
                pendingSearch = runnable
                input.postDelayed(runnable, 220L) // debounce, like Chrome's incremental search
                refreshCounter()
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        input.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_SEARCH) {
                runFind()
                true
            } else {
                false
            }
        }

        btnNext.setOnClickListener {
            if (totalMatches > 0) webView.findNext(true) else runFind()
        }
        btnPrevious.setOnClickListener {
            if (totalMatches > 0) webView.findNext(false) else runFind()
        }

        input.setOnEditorActionListener { _, actionId, event ->
            val searchAction = actionId == EditorInfo.IME_ACTION_SEARCH ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)
            if (searchAction) {
                pendingSearch?.let { input.removeCallbacks(it) }
                if (totalMatches > 0 && !input.text.isNullOrEmpty()) {
                    webView.findNext(true)
                } else {
                    runFind()
                }
                true
            } else {
                false
            }
        }

        val dialog: AlertDialog = AlertDialog.Builder(ctx)
            .setTitle(R.string.find_in_page)
            .setView(row)
            .setOnDismissListener {
                pendingSearch?.let { input.removeCallbacks(it) }
                try {
                    webView.setFindListener(null)
                    webView.clearMatches()
                } catch (e: Throwable) {
                    // WebView may already be detached/destroyed (tab closed while searching)
                }
                val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(input.windowToken, 0)
            }
            .create()

        btnClose.setOnClickListener { dialog.dismiss() }

        dialog.setOnShowListener {
            input.requestFocus()
            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            if (!input.text.isNullOrEmpty()) {
                runFind()
            } else {
                refreshCounter()
            }
        }
        dialog.show()
    }

    private fun imageButton(
        ctx: Context,
        iconRes: Int,
        descriptionRes: Int
    ): ImageButton = ImageButton(ctx).apply {
        setImageResource(iconRes)
        contentDescription = ctx.getString(descriptionRes)
        setBackgroundResource(R.drawable.menu_item_selector)
        isFocusable = true
        isFocusableInTouchMode = true
        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        val pad = (6 * ctx.resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)
    }
}
