package com.gothwad.browser.settings

import android.content.Context
import android.widget.SeekBar
import android.widget.Toast
import com.gothwad.browser.Config
import com.gothwad.browser.R
import com.gothwad.browser.activity.main.MainActivity
import com.gothwad.browser.activity.main.applyWebPageZoom
import com.gothwad.browser.databinding.ViewSettingsMainBinding

object SettingsDisplaySection {

    fun initDisplayAndZoomSettingsUI(
        context: Context,
        vb: ViewSettingsMainBinding,
        config: Config,
        onDismissDialog: (() -> Unit)?,
        activity: Context?
    ) {
        val mainAct = activity as? MainActivity

        // Top Tab Bar toggle
        vb.scShowTopTabBar.isChecked = config.showTopTabBar.value
        vb.llShowTopTabBar.setOnClickListener {
            val newState = !vb.scShowTopTabBar.isChecked
            vb.scShowTopTabBar.isChecked = newState
            config.showTopTabBar.value = newState
        }

        // UI Scaling controls
        val minUiScale = Config.UI_SCALE_PERCENT_MIN
        val maxUiScale = Config.UI_SCALE_PERCENT_MAX
        vb.sbUiScale.max = maxUiScale - minUiScale
        vb.sbUiScale.progress = config.uiScalePercent - minUiScale
        vb.tvUiScaleValue.text = "${config.uiScalePercent}%"

        vb.sbUiScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = minUiScale + progress
                config.uiScalePercent = value
                vb.tvUiScaleValue.text = "$value%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        fun setUiScale(percent: Int) {
            config.uiScalePercent = percent
            vb.sbUiScale.progress = (percent - minUiScale).coerceIn(0, maxUiScale - minUiScale)
            vb.tvUiScaleValue.text = if (percent == 50) "50% (Default)" else "$percent%"
        }

        vb.btnUiScale1.setOnClickListener { setUiScale(1) }
        vb.btnUiScale25.setOnClickListener { setUiScale(25) }
        vb.btnUiScale50.setOnClickListener { setUiScale(50) }
        vb.btnUiScale75.setOnClickListener { setUiScale(75) }
        vb.btnUiScale100.setOnClickListener { setUiScale(100) }
        vb.btnUiScaleApply.setOnClickListener {
            onDismissDialog?.invoke()
            mainAct?.applyUiScale()
            Toast.makeText(context, R.string.apply_ui_scale, Toast.LENGTH_SHORT).show()
        }

        // Web Page Zoom controls (Separate Mobile & Desktop Zoom)
        var editingDesktopMode: Boolean = config.desktopMode.value || config.userAgentString.value?.contains("Windows") == true
        val minWebZoom = Config.WEB_PAGE_ZOOM_PERCENT_MIN
        val maxWebZoom = Config.WEB_PAGE_ZOOM_PERCENT_MAX
        vb.sbWebPageZoom.max = maxWebZoom - minWebZoom

        fun updateZoomUI() {
            val current = config.getEffectiveZoom(editingDesktopMode)
            vb.sbWebPageZoom.progress = (current - minWebZoom).coerceIn(0, maxWebZoom - minWebZoom)
            val modeTitle = if (editingDesktopMode) "Desktop" else "Mobile"
            vb.tvWebPageZoomValue.text = if (current == 100) "$modeTitle: 100% (Default)" else "$modeTitle: $current%"

            vb.btnZoomTargetMobile.isSelected = !editingDesktopMode
            vb.btnZoomTargetDesktop.isSelected = editingDesktopMode
            vb.btnZoomTargetMobile.alpha = if (!editingDesktopMode) 1.0f else 0.6f
            vb.btnZoomTargetDesktop.alpha = if (editingDesktopMode) 1.0f else 0.6f
        }

        updateZoomUI()

        vb.btnZoomTargetMobile.setOnClickListener {
            editingDesktopMode = false
            updateZoomUI()
            val zoom = config.getEffectiveZoom(false)
            mainAct?.applyWebPageZoom(zoom, targetIsDesktop = false)
        }

        vb.btnZoomTargetDesktop.setOnClickListener {
            editingDesktopMode = true
            updateZoomUI()
            val zoom = config.getEffectiveZoom(true)
            mainAct?.applyWebPageZoom(zoom, targetIsDesktop = true)
        }

        vb.sbWebPageZoom.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val value = minWebZoom + progress
                config.setEffectiveZoom(editingDesktopMode, value)
                val modeTitle = if (editingDesktopMode) "Desktop" else "Mobile"
                vb.tvWebPageZoomValue.text = if (value == 100) "$modeTitle: 100% (Default)" else "$modeTitle: $value%"
                mainAct?.applyWebPageZoom(value, targetIsDesktop = editingDesktopMode)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        fun setWebZoom(percent: Int) {
            config.setEffectiveZoom(editingDesktopMode, percent)
            vb.sbWebPageZoom.progress = (percent - minWebZoom).coerceIn(0, maxWebZoom - minWebZoom)
            val modeTitle = if (editingDesktopMode) "Desktop" else "Mobile"
            vb.tvWebPageZoomValue.text = if (percent == 100) "$modeTitle: 100% (Default)" else "$modeTitle: $percent%"
            mainAct?.applyWebPageZoom(percent, targetIsDesktop = editingDesktopMode)
        }

        vb.btnWebZoom50.setOnClickListener { setWebZoom(50) }
        vb.btnWebZoom75.setOnClickListener { setWebZoom(75) }
        vb.btnWebZoom90.setOnClickListener { setWebZoom(90) }
        vb.btnWebZoom100.setOnClickListener { setWebZoom(100) }
        vb.btnWebZoom110.setOnClickListener { setWebZoom(110) }
        vb.btnWebZoom125.setOnClickListener { setWebZoom(125) }
        vb.btnWebZoom150.setOnClickListener { setWebZoom(150) }
        vb.btnWebZoom175.setOnClickListener { setWebZoom(175) }
        vb.btnWebZoom200.setOnClickListener { setWebZoom(200) }
        vb.btnWebZoom250.setOnClickListener { setWebZoom(250) }
        vb.btnWebZoom300.setOnClickListener { setWebZoom(300) }
    }
}
