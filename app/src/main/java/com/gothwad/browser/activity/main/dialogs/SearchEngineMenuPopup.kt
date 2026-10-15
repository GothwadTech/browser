package com.gothwad.browser.activity.main.dialogs

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.Toast
import com.gothwad.browser.Config
import com.gothwad.browser.R
import com.gothwad.browser.activity.main.MainActivity

class SearchEngineMenuPopup(private val activity: MainActivity) {

    private val popupWindow: PopupWindow
    private val contentView: View
    private val popupWidth: Int

    init {
        contentView = LayoutInflater.from(activity).inflate(R.layout.popup_search_engine_menu, null)
        popupWidth = (250 * activity.resources.displayMetrics.density).toInt()
        popupWindow = PopupWindow(
            contentView,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 24f
            setOnDismissListener {
                // Return focus to URL edit text or home if needed
            }
        }

        setupViews()
    }

    private fun bindMenuItem(view: View, action: () -> Unit) {
        view.setOnClickListener {
            dismiss()
            action()
        }
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_BUTTON_A -> {
                        view.performClick()
                        true
                    }
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_ESCAPE,
                    KeyEvent.KEYCODE_BUTTON_B -> {
                        dismiss()
                        true
                    }
                    else -> false
                }
            } else false
        }
    }

    private fun setupViews() {
        val config = activity.config
        val currentEngine = config.guessSearchEngineName()

        // Checkmark visibility based on current selection
        val ivCheckGoogle: ImageView = contentView.findViewById(R.id.ivCheckGoogle)
        val ivCheckBing: ImageView = contentView.findViewById(R.id.ivCheckBing)
        val ivCheckDuckDuckGo: ImageView = contentView.findViewById(R.id.ivCheckDuckDuckGo)
        val ivCheckYahoo: ImageView = contentView.findViewById(R.id.ivCheckYahoo)
        val ivCheckYandex: ImageView = contentView.findViewById(R.id.ivCheckYandex)
        val ivCheckStartpage: ImageView = contentView.findViewById(R.id.ivCheckStartpage)

        ivCheckGoogle.visibility = if (currentEngine == "google") View.VISIBLE else View.GONE
        ivCheckBing.visibility = if (currentEngine == "bing") View.VISIBLE else View.GONE
        ivCheckDuckDuckGo.visibility = if (currentEngine == "ddg") View.VISIBLE else View.GONE
        ivCheckYahoo.visibility = if (currentEngine == "yahoo") View.VISIBLE else View.GONE
        ivCheckYandex.visibility = if (currentEngine == "yandex") View.VISIBLE else View.GONE
        ivCheckStartpage.visibility = if (currentEngine == "startpage") View.VISIBLE else View.GONE

        // Search engine items
        bindEngineItem(
            view = contentView.findViewById(R.id.llEngineGoogle),
            engineName = "Google",
            urlPattern = "https://www.google.com/search?q=[query]"
        )

        bindEngineItem(
            view = contentView.findViewById(R.id.llEngineBing),
            engineName = "Bing",
            urlPattern = "https://www.bing.com/search?q=[query]"
        )

        bindEngineItem(
            view = contentView.findViewById(R.id.llEngineDuckDuckGo),
            engineName = "DuckDuckGo",
            urlPattern = "https://duckduckgo.com/?q=[query]"
        )

        bindEngineItem(
            view = contentView.findViewById(R.id.llEngineYahoo),
            engineName = "Yahoo!",
            urlPattern = "https://search.yahoo.com/search?p=[query]"
        )

        bindEngineItem(
            view = contentView.findViewById(R.id.llEngineYandex),
            engineName = "Yandex",
            urlPattern = "https://yandex.com/search/?text=[query]"
        )

        bindEngineItem(
            view = contentView.findViewById(R.id.llEngineStartpage),
            engineName = "Startpage",
            urlPattern = "https://www.startpage.com/sp/search?query=[query]"
        )

        // Bottom: Search Settings
        bindMenuItem(contentView.findViewById(R.id.llSearchSettings)) {
            SearchEngineConfigDialogFactory.show(
                context = activity,
                settings = activity.settingsModel,
                cancellable = true,
                callback = object : SearchEngineConfigDialogFactory.Callback {
                    override fun onDone(url: String) {
                        activity.vb.vActionBar.updateAddressBarIcon(activity.vb.vActionBar.getUrlEditText().let {
                            (it as? EditText)?.text?.toString() ?: ""
                        })
                    }
                }
            )
        }
    }

    private fun bindEngineItem(view: View, engineName: String, urlPattern: String) {
        bindMenuItem(view) {
            val etUrl = activity.vb.vActionBar.getUrlEditText() as? EditText
            val enteredQuery = etUrl?.text?.toString()?.trim() ?: ""

            // Update default search engine in Config
            activity.config.searchEngineURL.value = urlPattern
            activity.settingsModel.setSearchEngineURL(urlPattern)

            // Update search bar icon immediately to reflect new chosen engine
            activity.vb.vActionBar.updateAddressBarIcon(enteredQuery)

            Toast.makeText(activity, activity.getString(R.string.menu_search_engine_selected, engineName), Toast.LENGTH_SHORT).show()
            etUrl?.requestFocus()
        }
    }

    fun show(anchorView: View) {
        val density = activity.resources.displayMetrics.density
        val xOffset = (-2 * density).toInt()
        val yOffset = (4 * density).toInt()

        popupWindow.showAsDropDown(anchorView, xOffset, yOffset)

        contentView.post {
            // Find which view to focus first
            val currentEngine = activity.config.guessSearchEngineName()
            val viewToFocus = when (currentEngine) {
                "google" -> contentView.findViewById<View>(R.id.llEngineGoogle)
                "bing" -> contentView.findViewById<View>(R.id.llEngineBing)
                "ddg" -> contentView.findViewById<View>(R.id.llEngineDuckDuckGo)
                "yahoo" -> contentView.findViewById<View>(R.id.llEngineYahoo)
                "yandex" -> contentView.findViewById<View>(R.id.llEngineYandex)
                "startpage" -> contentView.findViewById<View>(R.id.llEngineStartpage)
                else -> contentView.findViewById<View>(R.id.llEngineGoogle)
            }
            viewToFocus?.requestFocus()
        }
    }

    fun dismiss() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }
}
