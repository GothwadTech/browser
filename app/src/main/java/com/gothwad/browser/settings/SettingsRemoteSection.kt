package com.gothwad.browser.settings

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.gothwad.browser.Config
import com.gothwad.browser.R
import com.gothwad.browser.activity.main.MainActivity
import com.gothwad.browser.databinding.ViewSettingsMainBinding
import com.gothwad.browser.utils.HardwareInputManager
import com.gothwad.browser.utils.HardwareMousePointerManager
import com.gothwad.browser.utils.Utils

object SettingsRemoteSection {

    fun initVirtualCursorPhysicsSettingsUI(
        context: Context,
        vb: ViewSettingsMainBinding,
        config: Config
    ) {
        vb.scEnableVirtualCursor.isChecked = config.enableVirtualCursor
        vb.llVirtualCursorDetails.visibility = if (config.enableVirtualCursor) View.VISIBLE else View.GONE
        vb.scEnableVirtualCursor.setOnCheckedChangeListener { _, isChecked ->
            config.enableVirtualCursor = isChecked
            vb.llVirtualCursorDetails.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        vb.spCursorStyle.setSelection(config.cursorStyle.coerceIn(0, 4))
        vb.spCursorStyle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                config.cursorStyle = position
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val minSize = Config.CURSOR_SIZE_PERCENT_MIN
        val maxSize = Config.CURSOR_SIZE_PERCENT_MAX
        vb.sbCursorSize.max = maxSize - minSize
        vb.sbCursorSize.progress = config.cursorSizePercent - minSize
        vb.tvCursorSizeValue.text = context.getString(R.string.cursor_physics_percent, config.cursorSizePercent)
        vb.sbCursorSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                config.cursorSizePercent = minSize + progress
                vb.tvCursorSizeValue.text = context.getString(R.string.cursor_physics_percent, config.cursorSizePercent)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val minP = Config.CURSOR_PHYSICS_PERCENT_MIN
        val maxP = Config.CURSOR_PHYSICS_PERCENT_MAX
        val range = maxP - minP
        vb.sbCursorMaxSpeed.max = range
        vb.sbCursorAcceleration.max = range

        fun refreshValueLabels() {
            vb.tvCursorMaxSpeedValue.text = context.getString(R.string.cursor_physics_percent, config.cursorMaxSpeedPercent)
            vb.tvCursorAccelerationValue.text = context.getString(R.string.cursor_physics_percent, config.cursorAccelerationPercent)
        }

        vb.sbCursorMaxSpeed.progress = config.cursorMaxSpeedPercent - minP
        vb.sbCursorAcceleration.progress = config.cursorAccelerationPercent - minP
        refreshValueLabels()

        vb.sbCursorMaxSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                config.cursorMaxSpeedPercent = minP + progress
                refreshValueLabels()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        vb.sbCursorAcceleration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                config.cursorAccelerationPercent = minP + progress
                refreshValueLabels()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    fun initKeyboardMouseSettingsUI(
        context: Context,
        vb: ViewSettingsMainBinding,
        config: Config,
        activity: Context?
    ) {
        val mainAct = activity as? MainActivity

        // Virtual Keyboard Suppression Settings
        vb.scDisableVirtualKeyboardKm.isChecked = config.disableVirtualKeyboard
        vb.scDisableVirtualKeyboardKm.setOnCheckedChangeListener { _, isChecked ->
            config.disableVirtualKeyboard = isChecked
            vb.scDisableVirtualKeyboard.isChecked = isChecked
            mainAct?.applySoftInputMode()
        }

        vb.llDisableVirtualKeyboardKm.setOnClickListener {
            vb.scDisableVirtualKeyboardKm.toggle()
        }

        val hwInput = HardwareInputManager.getInstance(context)

        val renderDeviceItem: (HardwareInputManager.DeviceInfo) -> View = { dev ->
            val itemLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = Utils.D2P(context, 6f).toInt()
                    bottomMargin = Utils.D2P(context, 6f).toInt()
                }
                gravity = Gravity.CENTER_VERTICAL
                background = ContextCompat.getDrawable(context, R.drawable.bg_tv_setting_item)
                isFocusable = true
                isClickable = true
                val padH = Utils.D2P(context, 12f).toInt()
                val padV = Utils.D2P(context, 10f).toInt()
                setPadding(padH, padV, padH, padV)
            }

            val devIdStr = dev.id.toString()
            val isBlocked = config.isInputDeviceBlocked(devIdStr) || config.isInputDeviceBlocked(dev.name)

            val typeIcon = when {
                dev.isMouse && dev.isKeyboard -> "⌨️🖱️"
                dev.isMouse -> "🖱️"
                dev.isKeyboard -> "⌨️"
                dev.isGamepad -> "🎮"
                else -> "🔌"
            }

            val iconTv = TextView(context).apply {
                text = typeIcon
                textSize = 20f
                setPadding(0, 0, Utils.D2P(context, 10f).toInt(), 0)
            }
            itemLayout.addView(iconTv)

            val textColumn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val nameTv = TextView(context).apply {
                text = dev.name
                setTextColor(ContextCompat.getColor(context, R.color.google_text_primary))
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            textColumn.addView(nameTv)

            val descTv = TextView(context).apply {
                val stateText = if (isBlocked) "🚫 Blocked (Input Ignored)" else "✅ Allowed (Active & Optimized)"
                val stateColor = if (isBlocked) "#F87171" else "#4ADE80"
                text = "ID: ${dev.id}  •  $stateText"
                setTextColor(Color.parseColor(stateColor))
                textSize = 12f
                setPadding(0, Utils.D2P(context, 2f).toInt(), 0, 0)
            }
            textColumn.addView(descTv)
            itemLayout.addView(textColumn)

            val blockSwitch = SwitchCompat(context).apply {
                isChecked = !isBlocked // Checked means Allowed/Active
                isFocusable = true
                isClickable = true
                text = if (isBlocked) "Block" else "Allow"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 12f
            }

            blockSwitch.setOnCheckedChangeListener { _, isAllowed ->
                val newBlocked = !isAllowed
                config.setInputDeviceBlocked(devIdStr, newBlocked)
                config.setInputDeviceBlocked(dev.name, newBlocked)
                if (newBlocked) {
                    descTv.text = "ID: ${dev.id}  •  🚫 Blocked (Input Ignored)"
                    descTv.setTextColor(Color.parseColor("#F87171"))
                    blockSwitch.text = "Block"
                    Toast.makeText(context, context.getString(R.string.settings_device_blocked, dev.name), Toast.LENGTH_SHORT).show()
                } else {
                    descTv.text = "ID: ${dev.id}  •  ✅ Allowed (Active & Optimized)"
                    descTv.setTextColor(Color.parseColor("#4ADE80"))
                    blockSwitch.text = "Allow"
                    Toast.makeText(context, context.getString(R.string.settings_device_allowed, dev.name), Toast.LENGTH_SHORT).show()
                }
            }

            itemLayout.setOnClickListener {
                blockSwitch.toggle()
            }

            itemLayout.addView(blockSwitch)
            itemLayout
        }

        val updateDeviceList: (List<HardwareInputManager.DeviceInfo>) -> Unit = { devices ->
            val nonVirtual = devices.filter { !it.isVirtual }
            vb.llConnectedDevicesContainer.removeAllViews()

            if (nonVirtual.isEmpty()) {
                vb.tvConnectedHardwareStatus.text = "⚠️ No external physical keyboard or mouse currently detected.\n(Using standard TV Remote / D-Pad navigation)"
            } else {
                vb.tvConnectedHardwareStatus.text = "Found ${nonVirtual.size} connected input device(s). You can toggle Allow/Block for each device below:"
                for (dev in nonVirtual) {
                    val itemView = renderDeviceItem(dev)
                    vb.llConnectedDevicesContainer.addView(itemView)
                }
            }
        }

        hwInput.addListener(object : HardwareInputManager.InputDevicesListener {
            override fun onInputDevicesChanged(devices: List<HardwareInputManager.DeviceInfo>) {
                vb.root.post { updateDeviceList(devices) }
            }
        })

        vb.btnRefreshDevices.setOnClickListener {
            val devs = hwInput.getConnectedDevices()
            updateDeviceList(devs)
            Toast.makeText(
                context,
                context.resources.getQuantityString(
                    R.plurals.settings_devices_found,
                    devs.count { !it.isVirtual },
                    devs.count { !it.isVirtual }
                ),
                Toast.LENGTH_SHORT
            ).show()
        }

        updateDeviceList(hwInput.getConnectedDevices())

        initHardwareMouseSettingsUI(context, vb, config, activity)
    }

    private fun initHardwareMouseSettingsUI(
        context: Context,
        vb: ViewSettingsMainBinding,
        config: Config,
        activity: Context?
    ) {
        val mainAct = activity as? MainActivity

        vb.scEnableHardwareMouse.isChecked = config.enableHardwareMouseCustomization
        vb.llHardwareMouseDetails.visibility = if (config.enableHardwareMouseCustomization) View.VISIBLE else View.GONE

        val minSize = Config.HARDWARE_MOUSE_SIZE_PERCENT_MIN
        val maxSize = Config.HARDWARE_MOUSE_SIZE_PERCENT_MAX
        vb.sbHardwareMouseSize.max = maxSize - minSize
        vb.sbHardwareMouseSize.progress = config.hardwareMouseSizePercent - minSize

        fun updateSizeLabelAndPreview(size: Int) {
            val desc = when {
                size <= 40 -> "Mini"
                size <= 70 -> "Small"
                size <= 120 -> "Normal"
                size <= 180 -> "Large"
                size <= 240 -> "Extra Large"
                else -> "Huge"
            }
            vb.tvHardwareMouseSizeValue.text = "$size% ($desc)"
            val previewBm = HardwareMousePointerManager.createPreviewBitmap(context, size, config.hardwareMouseStyle)
            vb.ivHardwareMousePreview.setImageBitmap(previewBm)
        }

        updateSizeLabelAndPreview(config.hardwareMouseSizePercent)

        vb.scEnableHardwareMouse.setOnCheckedChangeListener { _, isChecked ->
            config.enableHardwareMouseCustomization = isChecked
            vb.llHardwareMouseDetails.visibility = if (isChecked) View.VISIBLE else View.GONE
            HardwareMousePointerManager.invalidateCache()
            mainAct?.let { HardwareMousePointerManager.applyToWindow(it.window) }
        }

        vb.sbHardwareMouseSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = minSize + progress
                config.hardwareMouseSizePercent = size
                updateSizeLabelAndPreview(size)
                if (fromUser) {
                    HardwareMousePointerManager.invalidateCache()
                    mainAct?.let { HardwareMousePointerManager.applyToWindow(it.window) }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        fun applyPreset(presetSize: Int) {
            val clamped = presetSize.coerceIn(minSize, maxSize)
            config.hardwareMouseSizePercent = clamped
            vb.sbHardwareMouseSize.progress = clamped - minSize
            updateSizeLabelAndPreview(clamped)
            HardwareMousePointerManager.invalidateCache()
            mainAct?.let { HardwareMousePointerManager.applyToWindow(it.window) }
        }

        vb.btnMouseSizeMini.setOnClickListener { applyPreset(30) }
        vb.btnMouseSizeCompact.setOnClickListener { applyPreset(60) }
        vb.btnMouseSizeNormal.setOnClickListener { applyPreset(100) }
        vb.btnMouseSizeLarge.setOnClickListener { applyPreset(150) }
        vb.btnMouseSizeHuge.setOnClickListener { applyPreset(250) }

        vb.spHardwareMouseStyle.setSelection(config.hardwareMouseStyle.coerceIn(0, 4))
        vb.spHardwareMouseStyle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (config.hardwareMouseStyle != position) {
                    config.hardwareMouseStyle = position
                    HardwareMousePointerManager.invalidateCache()
                    updateSizeLabelAndPreview(config.hardwareMouseSizePercent)
                    mainAct?.let { HardwareMousePointerManager.applyToWindow(it.window) }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
}
