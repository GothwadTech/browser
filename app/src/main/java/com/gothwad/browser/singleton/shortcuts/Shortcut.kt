package com.gothwad.browser.singleton.shortcuts

import android.content.Context
import android.view.KeyEvent

import com.gothwad.browser.R

/**
 * Created by PDT on 06.08.2017.
 */

enum class Shortcut private constructor(
    var titleResId: Int,
    var prefsKey: String,
    val defaultKeyCode: Int,
    val defaultModifiers: Int = 0,
    val defaultLongPress: Boolean = false,
    var keyCode: Int = defaultKeyCode,
    var modifiers: Int = defaultModifiers,
    var longPressFlag: Boolean = defaultLongPress
) {
    // 1. Navigation
    NAVIGATE_BACK(R.string.navigate_back, "shortcut_nav_back", KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.META_ALT_ON),
    NAVIGATE_FORWARD(R.string.navigate_forward, "shortcut_nav_forward", KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.META_ALT_ON),
    NAVIGATE_HOME(R.string.navigate_home, "shortcut_nav_home", KeyEvent.KEYCODE_H, KeyEvent.META_ALT_ON),
    REFRESH_PAGE(R.string.refresh_page, "shortcut_refresh_page", KeyEvent.KEYCODE_F5, 0),
    HARD_RELOAD(R.string.shortcut_hard_reload, "shortcut_hard_reload", KeyEvent.KEYCODE_F5, KeyEvent.META_CTRL_ON),

    // 2. Tabs Management
    NEW_TAB(R.string.shortcut_new_tab, "shortcut_new_tab", KeyEvent.KEYCODE_T, KeyEvent.META_CTRL_ON),
    CLOSE_TAB(R.string.shortcut_close_tab, "shortcut_close_tab", KeyEvent.KEYCODE_W, KeyEvent.META_CTRL_ON),
    NEXT_TAB(R.string.shortcut_next_tab, "shortcut_next_tab", KeyEvent.KEYCODE_TAB, KeyEvent.META_CTRL_ON),
    PREV_TAB(R.string.shortcut_prev_tab, "shortcut_prev_tab", KeyEvent.KEYCODE_TAB, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON),
    TABS_OVERVIEW(R.string.shortcut_tabs_overview, "shortcut_tabs_overview", KeyEvent.KEYCODE_M, KeyEvent.META_CTRL_ON),

    // 3. Search & Address Bar
    FOCUS_ADDRESS_BAR(R.string.shortcut_focus_address_bar, "shortcut_focus_address_bar", KeyEvent.KEYCODE_L, KeyEvent.META_CTRL_ON),
    VOICE_SEARCH(R.string.voice_search, "shortcut_voice_search", KeyEvent.KEYCODE_SEARCH, 0),

    // 4. Productivity Hub & Tools
    HISTORY(R.string.shortcut_history, "shortcut_history", KeyEvent.KEYCODE_H, KeyEvent.META_CTRL_ON),
    DOWNLOADS(R.string.shortcut_downloads, "shortcut_downloads", KeyEvent.KEYCODE_J, KeyEvent.META_CTRL_ON),
    BOOKMARKS(R.string.shortcut_bookmarks, "shortcut_bookmarks", KeyEvent.KEYCODE_B, KeyEvent.META_CTRL_ON),
    ADD_BOOKMARK(R.string.shortcut_add_bookmark, "shortcut_add_bookmark", KeyEvent.KEYCODE_D, KeyEvent.META_CTRL_ON),

    // 5. Display & Zoom
    ZOOM_IN(R.string.shortcut_zoom_in, "shortcut_zoom_in", KeyEvent.KEYCODE_EQUALS, KeyEvent.META_CTRL_ON),
    ZOOM_OUT(R.string.shortcut_zoom_out, "shortcut_zoom_out", KeyEvent.KEYCODE_MINUS, KeyEvent.META_CTRL_ON),
    ZOOM_RESET(R.string.shortcut_zoom_reset, "shortcut_zoom_reset", KeyEvent.KEYCODE_0, KeyEvent.META_CTRL_ON),
    FULLSCREEN(R.string.shortcut_fullscreen, "shortcut_fullscreen", KeyEvent.KEYCODE_F11, 0),
    SETTINGS(R.string.shortcut_settings, "shortcut_settings", KeyEvent.KEYCODE_COMMA, KeyEvent.META_CTRL_ON),
    TOGGLE_INCOGNITO(R.string.shortcut_toggle_incognito, "shortcut_toggle_incognito", KeyEvent.KEYCODE_N, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON),

    // 6. Scrolling
    SCROLL_TOP(R.string.shortcut_scroll_top, "shortcut_scroll_top", KeyEvent.KEYCODE_MOVE_HOME, 0),
    SCROLL_BOTTOM(R.string.shortcut_scroll_bottom, "shortcut_scroll_bottom", KeyEvent.KEYCODE_MOVE_END, 0),
    PAGE_DOWN(R.string.shortcut_scroll_page_down, "shortcut_scroll_page_down", KeyEvent.KEYCODE_PAGE_DOWN, 0),
    PAGE_UP(R.string.shortcut_scroll_page_up, "shortcut_scroll_page_up", KeyEvent.KEYCODE_PAGE_UP, 0),

    // 7. Media Controls
    PLAY_PAUSE(R.string.play_pause, "shortcut_play_pause", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0),
    MEDIA_STOP(R.string.media_stop, "shortcut_media_stop", KeyEvent.KEYCODE_MEDIA_STOP, 0),
    MEDIA_REWIND(R.string.media_rewind, "shortcut_media_rewind", KeyEvent.KEYCODE_MEDIA_REWIND, 0),
    MEDIA_FAST_FORWARD(R.string.media_fast_forward, "shortcut_media_fast_forward", KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, 0);

    companion object {
        val PURE_MODIFIER_KEYS: Set<Int> = setOf(
            KeyEvent.KEYCODE_CTRL_LEFT,
            KeyEvent.KEYCODE_CTRL_RIGHT,
            KeyEvent.KEYCODE_ALT_LEFT,
            KeyEvent.KEYCODE_ALT_RIGHT,
            KeyEvent.KEYCODE_SHIFT_LEFT,
            KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.KEYCODE_META_LEFT,
            KeyEvent.KEYCODE_META_RIGHT,
            KeyEvent.KEYCODE_FUNCTION,
            KeyEvent.KEYCODE_SYM,
            KeyEvent.KEYCODE_NUM_LOCK,
            KeyEvent.KEYCODE_CAPS_LOCK
        )

        fun isPureModifierKey(keyCode: Int): Boolean {
            return keyCode in PURE_MODIFIER_KEYS || (android.os.Build.VERSION.SDK_INT >= 11 && KeyEvent.isModifierKey(keyCode))
        }

        fun resetToDefaults() {
            for (s in entries) {
                s.keyCode = s.defaultKeyCode
                s.modifiers = s.defaultModifiers
                s.longPressFlag = s.defaultLongPress
            }
        }
        private fun modifiersToString(modifiers: Int): String {
            var result = ""
            if (modifiers and KeyEvent.META_ALT_ON != 0) {
                result += "ALT+"
            }
            if (modifiers and KeyEvent.META_CTRL_ON != 0) {
                result += "CTRL+"
            }
            if (modifiers and KeyEvent.META_SHIFT_ON != 0) {
                result += "SHIFT+"
            }
            return result
        }

        fun shortcutKeysToString(shortcut: Shortcut, context: Context): String {
            var allKeys = ""
            if (shortcut.longPressFlag) {
                allKeys += context.getString(R.string.long_press) + " "
            }
            if (shortcut.modifiers != 0) {
                allKeys += modifiersToString(shortcut.modifiers)
            }
            allKeys += KeyEvent.keyCodeToString(shortcut.keyCode)
            return allKeys
        }
    }
}
