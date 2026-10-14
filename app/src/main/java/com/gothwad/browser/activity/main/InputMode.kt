package com.gothwad.browser.activity.main

import android.widget.TextView
import com.gothwad.browser.Config
import com.gothwad.browser.R
import com.gothwad.browser.utils.HardwareInputManager
import com.gothwad.browser.utils.Utils

/**
 * Input mode plumbing: the single place where "remote only", "keyboard + mouse" and "touch" are
 * translated into the concrete switches the rest of the app already respects.
 *
 * Nothing here invents a new mechanism - every flag it writes is consumed by existing code:
 * - [Config.enableVirtualCursor] -> `CursorLayout.dispatchKeyEvent/dispatchGenericMotionEvent`,
 *   `MainActivityDpad`, `CursorDrawerDelegate`
 * - [Config.disableMotionAxesDpadNavigation] -> `CursorLayout.motionAxesTranslationEnabled`
 * - [Config.desktopMode] -> the default desktop/mobile identity for tabs (per-tab override lives in
 *   `activity.main.DesktopMode`)
 *
 * The point of the mode is that a TV user gets a working pointer + desktop pages with one tap
 * instead of hunting through four sub-screens, and that turning the virtual cursor off (mouse mode)
 * cannot leave two pointers on screen.
 */

/** The mode that is actually in effect right now (resolves [Config.InputMode.AUTO]). */
fun MainActivity.effectiveInputMode(): Config.InputMode {
    val mouseConnected = runCatching {
        HardwareInputManager.getInstance(this).isPhysicalMouseConnected()
    }.getOrDefault(false)
    val isTv = runCatching { Utils.isTV(this) }.getOrDefault(false)
    return config.resolveInputMode(isTv, mouseConnected)
}

fun MainActivity.inputModeTitle(mode: Config.InputMode): String = getString(
    when (mode) {
        Config.InputMode.AUTO -> R.string.input_mode_auto
        Config.InputMode.TV_REMOTE -> R.string.input_mode_tv_remote
        Config.InputMode.KEYBOARD_MOUSE -> R.string.input_mode_keyboard_mouse
        Config.InputMode.TOUCH -> R.string.input_mode_touch
    }
)

/**
 * Write the concrete switches for [mode]. Safe to call on every resume: it only persists values that
 * actually changed, and all consumers read the flags per event.
 */
fun MainActivity.applyInputMode(mode: Config.InputMode = effectiveInputMode()) {
    when (mode) {
        Config.InputMode.TV_REMOTE -> {
            config.enableVirtualCursor = true
            config.disableMotionAxesDpadNavigation = false
        }

        Config.InputMode.KEYBOARD_MOUSE -> {
            // The OS pointer already moves on its own - drawing our cursor on top of it gave TV users
            // a double cursor that fought with the real one.
            config.enableVirtualCursor = false
            config.disableMotionAxesDpadNavigation = false
        }

        Config.InputMode.TOUCH -> {
            config.enableVirtualCursor = false
            config.disableMotionAxesDpadNavigation = true
        }

        Config.InputMode.AUTO -> {
            // Never stored as a resolved state; callers resolve first. Keep the device default.
            applyInputMode(effectiveInputMode())
        }
    }
}

/**
 * Explicit user choice from Settings. TV modes also switch new tabs to the desktop page identity,
 * because "the TV shows the full desktop site" is a hard product requirement, not an extra toggle.
 */
fun MainActivity.setInputMode(mode: Config.InputMode) {
    config.inputMode = mode
    applyInputMode(if (mode == Config.InputMode.AUTO) effectiveInputMode() else mode)
    if (mode == Config.InputMode.TV_REMOTE || mode == Config.InputMode.KEYBOARD_MOUSE) {
        if (!config.desktopMode.value) {
            config.desktopMode.value = true
            applyDesktopModeDefault()
        }
    }
}

/** Bind the four segmented buttons at the top of the settings dialog to the current mode. */
fun MainActivity.bindInputModeSegments(
    auto: TextView?,
    tvRemote: TextView?,
    keyboardMouse: TextView?,
    touch: TextView?,
    onAfterChange: () -> Unit = {}
) {
    val buttons = mapOf(
        Config.InputMode.AUTO to auto,
        Config.InputMode.TV_REMOTE to tvRemote,
        Config.InputMode.KEYBOARD_MOUSE to keyboardMouse,
        Config.InputMode.TOUCH to touch
    )
    fun paint() {
        val stored = config.inputMode
        buttons.forEach { (mode, button) ->
            button?.isActivated = mode == stored
        }
    }
    buttons.forEach { (mode, button) ->
        button?.setOnClickListener {
            setInputMode(mode)
            paint()
            onAfterChange()
        }
    }
    paint()
}
