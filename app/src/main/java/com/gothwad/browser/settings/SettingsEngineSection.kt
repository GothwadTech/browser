package com.gothwad.browser.settings

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.gothwad.browser.BrowserApp
import com.gothwad.browser.Config
import com.gothwad.browser.R
import com.gothwad.browser.databinding.ViewSettingsMainBinding
import com.gothwad.browser.webengine.WebEngineFactory

object SettingsEngineSection {

    fun initWebBrowserEngineSettingsUI(
        context: Context,
        vb: ViewSettingsMainBinding,
        config: Config,
        activity: Context?
    ) {
        if (WebEngineFactory.getProviders().size == 1) {
            vb.llWebEngine.visibility = View.GONE
            return
        }

        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, Config.SupportedWebEngines)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        vb.spWebEngine.adapter = adapter
        vb.spWebEngine.setSelection(Config.SupportedWebEngines.indexOf(config.webEngine), false)

        vb.spWebEngine.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                if (config.webEngine == Config.SupportedWebEngines[position]) return
                if (Config.SupportedWebEngines[position] == Config.ENGINE_WEB_VIEW) {
                    AlertDialog.Builder(context)
                        .setTitle(R.string.warning)
                        .setMessage(R.string.settings_engine_change_webview_msg)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            config.webEngine = Config.SupportedWebEngines[position]
                            showRestartDialog(context, activity)
                        }
                        .setNegativeButton(R.string.cancel) { _, _ ->
                            vb.spWebEngine.setSelection(Config.SupportedWebEngines.indexOf(config.webEngine), false)
                        }
                        .show()
                    return
                }
                config.webEngine = Config.SupportedWebEngines[position]
                showRestartDialog(context, activity)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun showRestartDialog(context: Context, activity: Context?) {
        AlertDialog.Builder(context)
            .setTitle(R.string.need_restart)
            .setMessage(R.string.need_restart_message)
            .setPositiveButton(R.string.exit) { _, _ ->
                BrowserApp.instance.needToExitProcessAfterMainActivityFinish = true
                BrowserApp.instance.needRestartMainActivityAfterExitingProcess = true
                (activity as? android.app.Activity)?.finish()
            }
            .setCancelable(false)
            .show()
    }

    fun initSafeBrowsingUI(
        context: Context,
        vb: ViewSettingsMainBinding,
        config: Config
    ) {
        vb.llSafeBrowsing.setOnLongClickListener {
            android.widget.Toast.makeText(context, R.string.safe_browsing_desc, android.widget.Toast.LENGTH_LONG).show()
            true
        }
        vb.scSafeBrowsing.isChecked = config.safeBrowsingEnabled
        vb.llSafeBrowsing.setOnClickListener { vb.scSafeBrowsing.toggle() }
        vb.scSafeBrowsing.setOnCheckedChangeListener { _, isChecked ->
            config.safeBrowsingEnabled = isChecked
            android.widget.Toast.makeText(
                context,
                "Safe browsing applies to newly opened tabs",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun initHomePageAndSearchEngineConfigUI(
        context: Context,
        vb: ViewSettingsMainBinding,
        config: Config,
        settingsModel: SettingsModel
    ) {
        var selected = 0
        if ("" != config.searchEngineURL.value) {
            selected = Config.SearchEnginesURLs.indexOf(config.searchEngineURL.value)
        }

        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, Config.SearchEnginesTitles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        vb.spEngine.adapter = adapter

        if (selected != -1) {
            vb.spEngine.setSelection(selected)
            vb.etUrl.setText(Config.SearchEnginesURLs[selected])
        } else {
            vb.spEngine.setSelection(Config.SearchEnginesTitles.size - 1)
            vb.llURL.visibility = View.VISIBLE
            vb.etUrl.setText(config.searchEngineURL.value)
            vb.etUrl.requestFocus()
        }
        vb.spEngine.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                if (position == (Config.SearchEnginesTitles.size - 1)) {
                    if (vb.llURL.visibility == View.GONE) {
                        vb.llURL.visibility = View.VISIBLE
                        vb.llURL.startAnimation(AnimationUtils.loadAnimation(context, android.R.anim.fade_in))
                    }
                    vb.etUrl.setText(config.searchEngineURL.value)
                    vb.etUrl.requestFocus()
                    return
                } else {
                    vb.llURL.visibility = View.GONE
                    vb.etUrl.setText(Config.SearchEnginesURLs[position])
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val homePageSpinnerAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, context.resources.getStringArray(R.array.home_page_modes))
        homePageSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        vb.spHomePage.adapter = homePageSpinnerAdapter
        vb.spHomePage.setSelection(settingsModel.homePageMode.ordinal)

        vb.spHomePage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val homePageMode = Config.HomePageMode.entries[position]
                vb.llCustomHomePage.visibility = if (homePageMode == Config.HomePageMode.CUSTOM) View.VISIBLE else View.GONE
                vb.llHomePageLinksMode.visibility = if (homePageMode == Config.HomePageMode.HOME_PAGE) View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val homePageLinksSpinnerAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, context.resources.getStringArray(R.array.home_page_links_modes))
        homePageLinksSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        vb.spHomePageLinks.adapter = homePageLinksSpinnerAdapter
        vb.spHomePageLinks.setSelection(settingsModel.homePageLinksMode.ordinal)

        vb.etCustomHomePageUrl.setText(settingsModel.homePage)
    }

    fun initUAStringConfigUI(
        context: Context,
        vb: ViewSettingsMainBinding,
        config: Config,
        settingsModel: SettingsModel
    ) {
        if (config.userAgentString.value?.contains("Browser/1.0 ") == true) {
            config.userAgentString.value = null
        }
        val selected = if (config.userAgentString.value == null) {
            0
        } else {
            settingsModel.uaStrings.indexOf(config.userAgentString.value ?: "")
        }

        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, settingsModel.userAgentStringTitles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        vb.spTitles.adapter = adapter

        if (selected != -1) {
            vb.spTitles.setSelection(selected, false)
            vb.etUAString.setText(settingsModel.uaStrings[selected])
        } else {
            vb.spTitles.setSelection(settingsModel.userAgentStringTitles.size - 1, false)
            vb.llUAString.visibility = View.VISIBLE
            vb.etUAString.setText(config.userAgentString.value ?: "")
            vb.etUAString.requestFocus()
        }
        vb.spTitles.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                if (position == settingsModel.userAgentStringTitles.size - 1 && vb.llUAString.visibility == View.GONE) {
                    vb.llUAString.visibility = View.VISIBLE
                    vb.llUAString.startAnimation(AnimationUtils.loadAnimation(context, android.R.anim.fade_in))
                    vb.etUAString.requestFocus()
                }
                vb.etUAString.setText(settingsModel.uaStrings[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    fun initAdBlockConfigUI(
        context: Context,
        vb: ViewSettingsMainBinding,
        config: Config,
        adblockModel: com.gothwad.browser.activity.main.AdblockModel,
        activity: Context?
    ) {
        vb.scAdBlock.isChecked = config.adBlockEnabled
        vb.llAdBlock.setOnClickListener {
            vb.scAdBlock.isChecked = !vb.scAdBlock.isChecked
        }
        vb.scAdBlock.setOnCheckedChangeListener { _, isChecked ->
            config.adBlockEnabled = isChecked
        }

        vb.etAdBlockListUrl.setText(config.adBlockListURL.value)

        adblockModel.clientLoading.subscribe(observer = { isLoading ->
            vb.pbAdBlockLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
            vb.btnUpdateAdBlockList.isEnabled = !isLoading
            vb.tvAdBlockStatus.text = if (isLoading) {
                "Updating filter list..."
            } else {
                "Filter list active and cached"
            }
        })

        vb.btnUpdateAdBlockList.setOnClickListener {
            val url = vb.etAdBlockListUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                config.adBlockListURL.value = url
            }
            adblockModel.loadAdBlockList(forceReload = true)
            Toast.makeText(context, "Updating ad blocker filters...", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveAdBlockListUrl(vb: ViewSettingsMainBinding, config: Config) {
        val url = vb.etAdBlockListUrl.text.toString().trim()
        if (url.isNotEmpty()) {
            config.adBlockListURL.value = url
        }
    }
}
