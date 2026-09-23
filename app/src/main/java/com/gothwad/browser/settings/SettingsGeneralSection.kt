package com.gothwad.browser.settings

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.gothwad.browser.Config
import com.gothwad.browser.R
import com.gothwad.browser.activity.main.MainActivity
import com.gothwad.browser.activity.main.applyWebPageZoom
import com.gothwad.browser.activity.main.zoomWebIn
import com.gothwad.browser.activity.main.zoomWebOut
import com.gothwad.browser.databinding.ViewSettingsMainBinding
import com.gothwad.browser.webengine.WebEngineFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object SettingsGeneralSection {

    fun initThemeSettingsUI(
        context: Context,
        vb: ViewSettingsMainBinding,
        config: Config,
        onDismissDialog: (() -> Unit)?,
        activity: Context?
    ) {
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, context.resources.getStringArray(R.array.themes))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        vb.spTheme.adapter = adapter
        vb.spTheme.setSelection(config.theme.value.ordinal, false)

        vb.spTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                if (config.theme.value.ordinal == position) return
                val newTheme = Config.Theme.values()[position]
                config.theme.value = newTheme
                val nightMode = when (newTheme) {
                    Config.Theme.BLACK_AMOLED,
                    Config.Theme.BLACK_CHARCOAL,
                    Config.Theme.BLACK_MIDNIGHT -> AppCompatDelegate.MODE_NIGHT_YES
                    Config.Theme.WHITE_PURE,
                    Config.Theme.WHITE_WARM,
                    Config.Theme.WHITE_COOL -> AppCompatDelegate.MODE_NIGHT_NO
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(nightMode)
                WebEngineFactory.onThemeSettingUpdated(newTheme)

                onDismissDialog?.invoke()
                (activity as? AppCompatActivity)?.recreate()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    fun initAppLockSettingsUI(context: Context, vb: ViewSettingsMainBinding) {
        var isUpdatingProgrammatically = false

        fun syncSwitchState() {
            isUpdatingProgrammatically = true
            vb.scAppLock.isChecked = com.gothwad.browser.singleton.AppLockManager.isLockEnabled(context)
            isUpdatingProgrammatically = false
            updateAppLockStatus(context, vb)
        }

        syncSwitchState()

        val toggleAction = {
            val currentlyEnabled = com.gothwad.browser.singleton.AppLockManager.isLockEnabled(context)
            if (!currentlyEnabled) {
                // User wants to enable PIN lock
                if (!com.gothwad.browser.singleton.AppLockManager.hasPinSet(context)) {
                    // Force user to create and confirm 4-digit PIN first
                    val dlg = com.gothwad.browser.activity.lock.TvPinDialog(
                        context = context,
                        mode = com.gothwad.browser.activity.lock.TvPinDialog.Mode.CREATE,
                        onSuccess = {
                            syncSwitchState()
                        },
                        onCancel = {
                            syncSwitchState()
                        }
                    )
                    dlg.show()
                } else {
                    com.gothwad.browser.singleton.AppLockManager.setLockEnabled(context, true)
                    syncSwitchState()
                }
            } else {
                // User wants to disable PIN lock -> Require PIN verification
                val dlg = com.gothwad.browser.activity.lock.TvPinDialog(
                    context = context,
                    mode = com.gothwad.browser.activity.lock.TvPinDialog.Mode.VERIFY,
                    onSuccess = {
                        com.gothwad.browser.singleton.AppLockManager.setLockEnabled(context, false)
                        syncSwitchState()
                        Toast.makeText(context, "PIN Lock Disabled", Toast.LENGTH_SHORT).show()
                    },
                    onCancel = {
                        syncSwitchState()
                    }
                )
                dlg.show()
            }
        }

        vb.llAppLock.setOnClickListener {
            toggleAction()
        }

        vb.scAppLock.setOnClickListener {
            toggleAction()
        }

        vb.scAppLock.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingProgrammatically) return@setOnCheckedChangeListener
            // Revert direct toggle and route through secure action
            syncSwitchState()
            toggleAction()
        }

        vb.btnChangePin.setOnClickListener {
            val mode = if (com.gothwad.browser.singleton.AppLockManager.hasPinSet(context)) {
                com.gothwad.browser.activity.lock.TvPinDialog.Mode.CHANGE
            } else {
                com.gothwad.browser.activity.lock.TvPinDialog.Mode.CREATE
            }
            val dlg = com.gothwad.browser.activity.lock.TvPinDialog(
                context = context,
                mode = mode,
                onSuccess = {
                    syncSwitchState()
                }
            )
            dlg.show()
        }
    }

    private fun updateAppLockStatus(context: Context, vb: ViewSettingsMainBinding) {
        val isEnabled = com.gothwad.browser.singleton.AppLockManager.isLockEnabled(context)
        val hasPin = com.gothwad.browser.singleton.AppLockManager.hasPinSet(context)
        if (isEnabled) {
            vb.tvAppLockStatus.text = "PIN Lock Active (Protected)"
            vb.tvAppLockStatus.setTextColor(0xFF38BDF8.toInt())
            vb.btnChangePin.visibility = View.VISIBLE
            vb.btnChangePin.text = "Change PIN"
        } else if (hasPin) {
            vb.tvAppLockStatus.text = "PIN Lock Disabled (PIN configured)"
            vb.tvAppLockStatus.setTextColor(0xFF94A3B8.toInt())
            vb.btnChangePin.visibility = View.VISIBLE
            vb.btnChangePin.text = "Change PIN"
        } else {
            vb.tvAppLockStatus.text = "No PIN set (Set 4-digit PIN to enable)"
            vb.tvAppLockStatus.setTextColor(0xFF94A3B8.toInt())
            vb.btnChangePin.visibility = View.GONE
        }
    }

    fun initQuickToolsUI(
        context: Context,
        vb: ViewSettingsMainBinding,
        config: Config,
        onDismissDialog: (() -> Unit)?,
        activity: Context?
    ) {
        val mainAct = activity as? MainActivity
        vb.btnQuickHistory.setOnClickListener {
            onDismissDialog?.invoke()
            mainAct?.showHistory()
        }
        vb.btnQuickFavorites.setOnClickListener {
            onDismissDialog?.invoke()
            mainAct?.showFavorites()
        }
        vb.btnQuickDownloads.setOnClickListener {
            onDismissDialog?.invoke()
            mainAct?.showDownloads()
        }
        vb.btnQuickIncognito.setOnClickListener {
            mainAct?.toggleIncognitoMode()
            Toast.makeText(context, R.string.incognito_mode, Toast.LENGTH_SHORT).show()
        }
        vb.btnQuickPopupBlock.setOnClickListener {
            onDismissDialog?.invoke()
            mainAct?.apply {
                lifecycleScope.launch {
                    showPopupBlockOptions()
                }
            }
        }
        vb.btnQuickRotate.setOnClickListener {
            val nextOrientation = when (config.screenOrientation) {
                Config.ORIENTATION_LANDSCAPE -> Config.ORIENTATION_PORTRAIT
                Config.ORIENTATION_PORTRAIT -> Config.ORIENTATION_AUTO
                else -> Config.ORIENTATION_LANDSCAPE
            }
            config.screenOrientation = nextOrientation
            mainAct?.applyScreenOrientation()
            val label = when (nextOrientation) {
                Config.ORIENTATION_PORTRAIT -> "Screen Orientation: Portrait"
                Config.ORIENTATION_AUTO -> "Screen Orientation: Auto Rotate"
                else -> "Screen Orientation: Landscape"
            }
            Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
        }

        vb.btnQuickZoomIn.setOnClickListener {
            mainAct?.zoomWebIn()
            val isDesk = config.isDesktopMode()
            val zoom = config.getEffectiveZoom(isDesk)
            vb.sbWebPageZoom.progress = (zoom - Config.WEB_PAGE_ZOOM_PERCENT_MIN).coerceIn(0, Config.WEB_PAGE_ZOOM_PERCENT_MAX - Config.WEB_PAGE_ZOOM_PERCENT_MIN)
            val modeTitle = if (isDesk) "Desktop" else "Mobile"
            vb.tvWebPageZoomValue.text = if (zoom == 100) "$modeTitle: 100% (Default)" else "$modeTitle: $zoom%"
            Toast.makeText(context, "$modeTitle: $zoom%", Toast.LENGTH_SHORT).show()
        }

        vb.btnQuickZoomOut.setOnClickListener {
            mainAct?.zoomWebOut()
            val isDesk = config.isDesktopMode()
            val zoom = config.getEffectiveZoom(isDesk)
            vb.sbWebPageZoom.progress = (zoom - Config.WEB_PAGE_ZOOM_PERCENT_MIN).coerceIn(0, Config.WEB_PAGE_ZOOM_PERCENT_MAX - Config.WEB_PAGE_ZOOM_PERCENT_MIN)
            val modeTitle = if (isDesk) "Desktop" else "Mobile"
            vb.tvWebPageZoomValue.text = if (zoom == 100) "$modeTitle: 100% (Default)" else "$modeTitle: $zoom%"
            Toast.makeText(context, "$modeTitle: $zoom%", Toast.LENGTH_SHORT).show()
        }

        vb.btnQuickZoomReset.setOnClickListener {
            val isDesk = config.isDesktopMode()
            mainAct?.applyWebPageZoom(100, isDesk)
            vb.sbWebPageZoom.progress = 100 - Config.WEB_PAGE_ZOOM_PERCENT_MIN
            val modeTitle = if (isDesk) "Desktop" else "Mobile"
            vb.tvWebPageZoomValue.text = "$modeTitle: 100% (Default)"
            Toast.makeText(context, R.string.quick_zoom_reset, Toast.LENGTH_SHORT).show()
        }
    }

    fun initDefaultBrowserUI(
        context: Context,
        vb: ViewSettingsMainBinding,
        onDismissDialog: (() -> Unit)?,
        activity: Context?
    ) {
        fun updateDefaultBrowserStatus() {
            val isDefault = com.gothwad.browser.utils.DefaultBrowserHelper.isDefaultBrowser(context)
            if (isDefault) {
                vb.tvDefaultBrowserStatus.text = "Status: Currently Default Browser (Active)"
                vb.tvDefaultBrowserStatus.setTextColor(0xFF38BDF8.toInt())
            } else {
                vb.tvDefaultBrowserStatus.text = "Status: Not Default Browser (Click to Set)"
                vb.tvDefaultBrowserStatus.setTextColor(0xFF94A3B8.toInt())
            }
        }

        updateDefaultBrowserStatus()

        val action = {
            com.gothwad.browser.utils.DefaultBrowserHelper.requestSetDefaultBrowser(context)
            vb.root.postDelayed({
                updateDefaultBrowserStatus()
            }, 1000)
        }

        vb.llSetDefaultBrowser.setOnClickListener {
            action()
        }

        vb.btnQuickDefaultBrowser.setOnClickListener {
            action()
        }
    }

    fun initHistorySettingsUI(
        context: Context,
        vb: ViewSettingsMainBinding,
        config: Config,
        activity: Context?
    ) {
        vb.scSaveHistory.isChecked = config.saveHistory
        vb.scSaveHistory.setOnCheckedChangeListener { _, isChecked ->
            config.saveHistory = isChecked
            val msg = if (isChecked) "Browsing history tracking enabled" else "Browsing history tracking paused"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }

        vb.llSaveHistory.setOnClickListener {
            vb.scSaveHistory.toggle()
        }

        vb.btnClearHistorySettings.setOnClickListener {
            android.app.AlertDialog.Builder(context)
                .setTitle("Clear Browsing History")
                .setMessage("Are you sure you want to delete all saved browsing history? This action cannot be undone.")
                .setPositiveButton("Clear History") { _, _ ->
                    (activity as? androidx.appcompat.app.AppCompatActivity)?.lifecycleScope?.launch(Dispatchers.IO) {
                        com.gothwad.browser.singleton.AppDatabase.db.historyDao().deleteAll()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Browsing history cleared", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}
