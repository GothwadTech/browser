package com.gothwad.browser.utils

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.gothwad.browser.AppContext

/**
 * Inventory of the physical input devices attached to this box, plus the user's per-device block list.
 *
 * Deliberately narrow: it answers "what is plugged in" ([getConnectedDevices], [isPhysicalMouseConnected],
 * [isPhysicalKeyboardConnected]) and "has the user ignored this device" ([isDeviceBlocked], [setDeviceBlocked]),
 * and reports changes through [InputDevicesListener]. Everything that *acts* on those answers — the overlay
 * cursor, joystick-axis D-pad translation, which [com.gothwad.browser.Config.InputMode] the app is in — lives in
 * the activity/overlay code and is driven by the platform's own flags.
 *
 * This class must never rewrite, inject or synthesize an input event: a real event reaches the WebView exactly as
 * the OS reported it (AGENTS.md §3, rules 2 and 3). The "precision mouse click" synthetic mouse→touch pipeline that
 * used to be advertised here is gone for good — it broke Chromium's native drag-to-select and only ever papered
 * over one box's input driver. If clicks are dropped on a device, that is a platform/OEM bug, not something to
 * compensate for in app code.
 */
class HardwareInputManager private constructor(private val context: Context) : InputManager.InputDeviceListener {

    companion object {
        @Volatile
        private var instance: HardwareInputManager? = null

        fun getInstance(context: Context): HardwareInputManager {
            return instance ?: synchronized(this) {
                instance ?: HardwareInputManager(context.applicationContext).also { instance = it }
            }
        }
    }

    data class DeviceInfo(
        val id: Int,
        val name: String,
        val descriptor: String,
        val isMouse: Boolean,
        val isKeyboard: Boolean,
        val isGamepad: Boolean,
        val isVirtual: Boolean,
        val isBlocked: Boolean
    )

    interface InputDevicesListener {
        fun onInputDevicesChanged(devices: List<DeviceInfo>)
    }

    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val listeners = mutableListOf<InputDevicesListener>()
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        try {
            inputManager.registerInputDeviceListener(this, mainHandler)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addListener(listener: InputDevicesListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
            listener.onInputDevicesChanged(getConnectedDevices())
        }
    }

    fun removeListener(listener: InputDevicesListener) {
        listeners.remove(listener)
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        notifyChanged()
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        notifyChanged()
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        notifyChanged()
    }

    fun notifyChanged() {
        val currentDevices = getConnectedDevices()
        for (l in listeners) {
            l.onInputDevicesChanged(currentDevices)
        }
    }

    fun getConnectedDevices(): List<DeviceInfo> {
        val config = AppContext.provideConfig()
        val list = mutableListOf<DeviceInfo>()
        val deviceIds = inputManager.inputDeviceIds
        for (id in deviceIds) {
            val device = inputManager.getInputDevice(id) ?: continue
            val descriptor = device.descriptor.ifEmpty { "device_id_$id" }
            val sources = device.sources
            val isMouse = (sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
                    (sources and InputDevice.SOURCE_MOUSE_RELATIVE) == InputDevice.SOURCE_MOUSE_RELATIVE ||
                    (sources and InputDevice.SOURCE_TRACKBALL) == InputDevice.SOURCE_TRACKBALL ||
                    (sources and InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD

            val isKeyboard = (sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD &&
                    device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC

            val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK

            val isVirtual = device.isVirtual
            val isBlocked = config.isInputDeviceBlocked(descriptor) || config.isInputDeviceBlocked(device.name)

            list.add(
                DeviceInfo(
                    id = device.id,
                    name = device.name,
                    descriptor = descriptor,
                    isMouse = isMouse,
                    isKeyboard = isKeyboard,
                    isGamepad = isGamepad,
                    isVirtual = isVirtual,
                    isBlocked = isBlocked
                )
            )
        }
        return list
    }

    fun setDeviceBlocked(deviceIdentifier: String, blocked: Boolean) {
        val config = AppContext.provideConfig()
        config.setInputDeviceBlocked(deviceIdentifier, blocked)
        notifyChanged()
    }

    fun unblockAllDevices() {
        val config = AppContext.provideConfig()
        config.blockedInputDeviceIds = emptySet()
        notifyChanged()
    }

    /**
     * Checks if the device sending this InputEvent is marked as blocked by the user.
     */
    fun isDeviceBlocked(event: InputEvent?): Boolean {
        if (event == null) return false
        val device = event.device ?: inputManager.getInputDevice(event.deviceId) ?: return false
        if (device.isVirtual) return false // Never block virtual system device
        val descriptor = device.descriptor.ifEmpty { "device_id_${device.id}" }
        val config = AppContext.provideConfig()
        return config.isInputDeviceBlocked(descriptor) || config.isInputDeviceBlocked(device.name)
    }

    fun isDeviceBlocked(deviceId: Int): Boolean {
        val device = inputManager.getInputDevice(deviceId) ?: return false
        if (device.isVirtual) return false
        val descriptor = device.descriptor.ifEmpty { "device_id_${device.id}" }
        val config = AppContext.provideConfig()
        return config.isInputDeviceBlocked(descriptor) || config.isInputDeviceBlocked(device.name)
    }

    fun isPhysicalMouseConnected(): Boolean {
        return getConnectedDevices().any { it.isMouse && !it.isVirtual }
    }

    fun isPhysicalKeyboardConnected(): Boolean {
        return getConnectedDevices().any { it.isKeyboard && !it.isVirtual }
    }
}
