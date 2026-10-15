package com.gothwad.browser.settings

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ScrollView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.gothwad.browser.AppContext
import com.gothwad.browser.Config
import com.gothwad.browser.activity.main.MainActivity
import com.gothwad.browser.activity.main.applyDesktopModeDefault
import com.gothwad.browser.databinding.ViewSettingsMainBinding
import com.gothwad.browser.utils.activemodel.ActiveModelsRepository
import com.gothwad.browser.utils.activity
import com.gothwad.browser.webengine.WebEngineFactory
import kotlinx.coroutines.launch

class MainSettingsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    private var vb = ViewSettingsMainBinding.inflate(LayoutInflater.from(getContext()), this, true)
    var settingsModel = ActiveModelsRepository.get(SettingsModel::class, activity!!)
    var adblockModel = ActiveModelsRepository.get(com.gothwad.browser.activity.main.AdblockModel::class, activity!!)
    var config = AppContext.provideConfig()
    var onDismissDialog: (() -> Unit)? = null

    init {
        SettingsGeneralSection.initQuickToolsUI(context, vb, config, onDismissDialog, activity)
        SettingsGeneralSection.initDefaultBrowserUI(context, vb, onDismissDialog, activity)
        SettingsDisplaySection.initDisplayAndZoomSettingsUI(context, vb, config, onDismissDialog, activity)
        SettingsEngineSection.initWebBrowserEngineSettingsUI(context, vb, config, activity)
        SettingsEngineSection.initSafeBrowsingUI(context, vb, config)
        SettingsEngineSection.initHomePageAndSearchEngineConfigUI(context, vb, config, settingsModel)
        SettingsEngineSection.initUAStringConfigUI(context, vb, config, settingsModel)
        SettingsEngineSection.initAdBlockConfigUI(context, vb, config, adblockModel, activity)
        SettingsGeneralSection.initThemeSettingsUI(context, vb, config, onDismissDialog, activity)

        initSimpleToggles()
        SettingsRemoteSection.initVirtualCursorPhysicsSettingsUI(context, vb, config)
        SettingsRemoteSection.initKeyboardMouseSettingsUI(context, vb, config, activity)
        SettingsGeneralSection.initAppLockSettingsUI(context, vb)
        SettingsGeneralSection.initHistorySettingsUI(context, vb, config, activity)

        vb.btnClearWebCache.setOnClickListener {
            (activity as? MainActivity)?.lifecycleScope?.launch {
                WebEngineFactory.clearCache(context)
                Toast.makeText(context, android.R.string.ok, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initSimpleToggles() {
        vb.scAllowAutoplayMedia.isChecked = config.allowAutoplayMedia
        vb.scAllowAutoplayMedia.setOnCheckedChangeListener { _, isChecked ->
            config.allowAutoplayMedia = isChecked
        }

        vb.scKeepScreenOn.isChecked = settingsModel.keepScreenOn.value
        vb.scKeepScreenOn.setOnCheckedChangeListener { _, isChecked ->
            settingsModel.keepScreenOn.value = isChecked
        }

        vb.scDesktopModeDefault.isChecked = config.desktopMode.value
        vb.scDesktopModeDefault.setOnCheckedChangeListener { _, isChecked ->
            config.desktopMode.value = isChecked
            (activity as? MainActivity)?.applyDesktopModeDefault()
        }

        vb.scDisableVirtualKeyboard.isChecked = config.disableVirtualKeyboard
        vb.scDisableVirtualKeyboard.setOnCheckedChangeListener { _, isChecked ->
            config.disableVirtualKeyboard = isChecked
            (activity as? MainActivity)?.applySoftInputMode()
        }

        vb.scNavigateWithJoystickAxes.isChecked = !config.disableMotionAxesDpadNavigation
        vb.scNavigateWithJoystickAxes.setOnCheckedChangeListener { _, isChecked ->
            config.disableMotionAxesDpadNavigation = !isChecked
        }
    }

    fun save() {
        val customSearchEngineUrl = vb.etUrl.text.toString()
        settingsModel.setSearchEngineURL(customSearchEngineUrl)

        val homePageMode = Config.HomePageMode.entries[vb.spHomePage.selectedItemPosition]
        val customHomePageURL = vb.etCustomHomePageUrl.text.toString()
        val homePageLinksMode = Config.HomePageLinksMode.entries[vb.spHomePageLinks.selectedItemPosition]
        settingsModel.setHomePageProperties(homePageMode, customHomePageURL, homePageLinksMode)

        val userAgent = vb.etUAString.text.toString().trim(' ')
        config.userAgentString.value = userAgent.ifEmpty { null }

        SettingsEngineSection.saveAdBlockListUrl(vb, config)
    }

    fun showCategory(category: SettingsCategory) {
        vb.cardDisplayScale.visibility = if (category == SettingsCategory.DISPLAY_SCALE) View.VISIBLE else View.GONE
        vb.cardWebZoom.visibility = if (category == SettingsCategory.WEB_ZOOM) View.VISIBLE else View.GONE
        vb.cardThemes.visibility = if (category == SettingsCategory.THEMES) View.VISIBLE else View.GONE
        vb.cardMediaPlayback.visibility = if (category == SettingsCategory.MEDIA_PLAYBACK) View.VISIBLE else View.GONE

        vb.cardSearchEngine.visibility = if (category == SettingsCategory.SEARCH_ENGINE) View.VISIBLE else View.GONE
        vb.cardHomePage.visibility = if (category == SettingsCategory.HOME_PAGE) View.VISIBLE else View.GONE
        vb.cardUserAgent.visibility = if (category == SettingsCategory.USER_AGENT) View.VISIBLE else View.GONE
        vb.cardWebEngine.visibility = if (category == SettingsCategory.WEB_ENGINE) View.VISIBLE else View.GONE

        vb.cardAdBlock.visibility = if (category == SettingsCategory.AD_BLOCKER) View.VISIBLE else View.GONE
        vb.cardAppLock.visibility = if (category == SettingsCategory.APP_LOCK) View.VISIBLE else View.GONE
        vb.cardCacheStorage.visibility = if (category == SettingsCategory.CACHE_STORAGE) View.VISIBLE else View.GONE

        vb.cardQuickTools.visibility = if (category == SettingsCategory.QUICK_TOOLS) View.VISIBLE else View.GONE

        vb.cardRemoteNav.visibility = if (category == SettingsCategory.REMOTE_NAV) View.VISIBLE else View.GONE
        vb.cardCursorPhysics.visibility = if (category == SettingsCategory.CURSOR_PHYSICS) View.VISIBLE else View.GONE
        vb.cardKeyboardMouse.visibility = if (category == SettingsCategory.KEYBOARD_MOUSE) View.VISIBLE else View.GONE

        post { fullScroll(ScrollView.FOCUS_UP) }
    }
}
