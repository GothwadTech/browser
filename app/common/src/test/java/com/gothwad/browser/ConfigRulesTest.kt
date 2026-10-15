package com.gothwad.browser

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the rules in [Config] that the app's behaviour depends on.
 *
 * These are the decisions that were silently wrong more than once during development (the live-tab
 * cap ignoring its own bounds, the desktop/mobile zoom buckets overwriting each other, an unknown
 * stored input mode crashing instead of falling back), so they are pinned down here without needing
 * Robolectric or a device.
 */
class ConfigRulesTest {

    private class FakePrefs : SharedPreferences {
        val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map

        override fun getString(key: String, defValue: String?): String? =
            if (map.containsKey(key)) map[key] as String? else defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: MutableSet<String?>?): MutableSet<String?>? =
            if (map.containsKey(key)) map[key] as MutableSet<String?> else defValues

        override fun getInt(key: String, defValue: Int): Int =
            if (map.containsKey(key)) (map[key] as Number).toInt() else defValue

        override fun getLong(key: String, defValue: Long): Long =
            if (map.containsKey(key)) (map[key] as Number).toLong() else defValue

        override fun getFloat(key: String, defValue: Float): Float =
            if (map.containsKey(key)) (map[key] as Number).toFloat() else defValue

        override fun getBoolean(key: String, defValue: Boolean): Boolean =
            if (map.containsKey(key)) map[key] as Boolean else defValue

        override fun contains(key: String): Boolean = map.containsKey(key)

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            override fun putString(key: String, value: String?): SharedPreferences.Editor =
                apply { map[key] = value }

            override fun putStringSet(
                key: String,
                values: MutableSet<String?>?
            ): SharedPreferences.Editor = apply { map[key] = values }

            override fun putInt(key: String, value: Int): SharedPreferences.Editor =
                apply { map[key] = value }

            override fun putLong(key: String, value: Long): SharedPreferences.Editor =
                apply { map[key] = value }

            override fun putFloat(key: String, value: Float): SharedPreferences.Editor =
                apply { map[key] = value }

            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor =
                apply { map[key] = value }

            override fun remove(key: String): SharedPreferences.Editor = apply { map.remove(key) }

            override fun clear(): SharedPreferences.Editor = apply { map.clear() }

            override fun commit(): Boolean = true

            override fun apply() {}
        }

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {
        }

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {
        }
    }

    private fun configWith(vararg seed: Pair<String, Any?>): Config {
        val prefs = FakePrefs()
        seed.forEach { (k, v) -> prefs.map[k] = v }
        return Config(prefs)
    }

    // ------------------------------------------------------------------ live tab cap

    @Test
    fun maxLiveTabs_usesTheAdaptiveDefault_whenTheUserNeverSetOne() {
        val config = configWith()
        assertEquals(Config.DEFAULT_MAX_LIVE_TABS, config.maxLiveTabs)
        config.maxLiveTabsDefault = 3
        assertEquals(3, config.maxLiveTabs)
    }

    @Test
    fun maxLiveTabs_isClampedToTheSaneRange_andRemembered() {
        val config = configWith()
        config.maxLiveTabs = 1
        assertEquals(2, config.maxLiveTabs)
        config.maxLiveTabs = 99
        assertEquals(20, config.maxLiveTabs)
        config.maxLiveTabs = 5
        assertEquals(5, config.maxLiveTabs)
        // An explicit value must win over the adaptive default.
        config.maxLiveTabsDefault = 3
        assertEquals(5, config.maxLiveTabs)
    }

    @Test
    fun maxLiveTabs_reparsesPersistedValueInAFreshInstance() {
        val prefs = FakePrefs()
        Config(prefs).maxLiveTabs = 4
        assertEquals(4, Config(prefs).maxLiveTabs)
    }

    // ------------------------------------------------------------------ input mode

    @Test
    fun inputMode_fallsBackToAuto_onAnUnreadableStoredValue() {
        val config = configWith(Config.INPUT_MODE_KEY to "TOUCHSCREEN_OF_THE_FUTURE")
        assertEquals(Config.InputMode.AUTO, config.inputMode)
    }

    @Test
    fun inputMode_autoResolvesPerDevice() {
        val config = configWith()
        assertEquals(Config.InputMode.AUTO, config.inputMode)
        assertEquals(Config.InputMode.TOUCH, config.resolveInputMode(isTvDevice = false, physicalMouseConnected = false))
        assertEquals(Config.InputMode.TV_REMOTE, config.resolveInputMode(isTvDevice = true, physicalMouseConnected = false))
        assertEquals(Config.InputMode.KEYBOARD_MOUSE, config.resolveInputMode(isTvDevice = true, physicalMouseConnected = true))
        // A phone with a mouse stays a touch device: AUTO only upgrades TV hardware.
        assertEquals(Config.InputMode.TOUCH, config.resolveInputMode(isTvDevice = false, physicalMouseConnected = true))
    }

    @Test
    fun inputMode_explicitChoiceIsNeverOverriddenByTheDevice() {
        val config = configWith()
        config.inputMode = Config.InputMode.TOUCH
        assertEquals(Config.InputMode.TOUCH, config.resolveInputMode(isTvDevice = true, physicalMouseConnected = true))
        config.inputMode = Config.InputMode.TV_REMOTE
        assertEquals(Config.InputMode.TV_REMOTE, config.resolveInputMode(isTvDevice = false, physicalMouseConnected = false))
    }

    // ------------------------------------------------------------------ per-mode zoom

    @Test
    fun zoom_keepsMobileAndDesktopBucketsSeparate() {
        val config = configWith()
        assertEquals(Config.WEB_PAGE_ZOOM_PERCENT_DEFAULT, config.getEffectiveZoom(false))
        assertEquals(Config.WEB_PAGE_ZOOM_PERCENT_DEFAULT, config.getEffectiveZoom(true))

        config.setEffectiveZoom(true, 150)
        assertEquals(150, config.getEffectiveZoom(true))
        assertEquals(Config.WEB_PAGE_ZOOM_PERCENT_DEFAULT, config.getEffectiveZoom(false))
    }

    @Test
    fun zoom_clampsToTheSupportedRange() {
        val config = configWith()
        config.setEffectiveZoom(false, 10)
        assertEquals(Config.WEB_PAGE_ZOOM_PERCENT_MIN, config.getEffectiveZoom(false))
        config.setEffectiveZoom(false, 5000)
        assertEquals(Config.WEB_PAGE_ZOOM_PERCENT_MAX, config.getEffectiveZoom(false))
    }

    // ------------------------------------------------------------------ desktop identity

    @Test
    fun desktopUa_isRecognisedFromTheUaString() {
        val config = configWith()
        assertTrue(config.isDesktopUa(Config.DESKTOP_UA))
        assertFalse(config.isDesktopUa("Mozilla/5.0 (Linux; Android 13; SM-A135F) Mobile Safari/537.36"))
        assertFalse(config.isDesktopUa(null))
    }

    @Test
    fun desktopMode_globalFlagOrCustomUaBothTurnItOn() {
        val config = configWith()
        assertFalse(config.isDesktopMode())
        config.desktopMode.value = true
        assertTrue(config.isDesktopMode())

        val custom = configWith(Config.USER_AGENT_PREF_KEY to "Firefox/128.0 on X11; Linux x86_64")
        assertFalse(custom.desktopMode.value)
        assertTrue(custom.isDesktopMode())
    }

    @Test
    fun privacyAndSafetyFlags_defaultOnAndPersistWhenDisabled() {
        // The shipped app once advertised "securest browser" while Safe Browsing was pinned to
        // false; these defaults are the product promise, so they are asserted, not assumed.
        val config = configWith()
        assertTrue("safe browsing must default to on", config.safeBrowsingEnabled)
        assertTrue("history must default to on", config.saveHistory)
        assertFalse("autoplay must default to off", config.allowAutoplayMedia)

        config.safeBrowsingEnabled = false
        config.saveHistory = false
        assertFalse(config.safeBrowsingEnabled)
        assertFalse(config.saveHistory)

        val persisted = FakePrefs().apply { map[Config.SAFE_BROWSING_KEY] = false }
        assertFalse(Config(persisted).safeBrowsingEnabled)
    }
}
