package com.gothwad.browser.activity.main

import com.gothwad.browser.R
import com.gothwad.browser.model.WebTabState

/**
 * Desktop-site mode.
 *
 * Historically this app had exactly one desktop switch (`Config.desktopMode`) that was applied to
 * *every* live tab at once, so turning "Desktop site" on in one tab also reloaded whatever the user
 * was reading in another tab. Chrome's model - and therefore the model used here - is:
 *
 *  - `Config.desktopMode` is only the **default for newly created tabs** (it is the switch in
 *    Settings, plus the value restored from a persisted tab that has no override yet);
 *  - `WebTabState.desktopMode` is the per-tab override (null = follow the default);
 *  - the WebView itself keeps the resolved value in [com.gothwad.browser.webengine.webview.WebViewEx.desktopModeOverride]
 *    so page-zoom and the UA stay correct while the user switches tabs.
 *
 * All menu / sidebar entries must go through [toggleDesktopModeForCurrentTab] instead of poking
 * `config.desktopMode` directly, otherwise the three entry points drift apart again.
 */

/** Effective desktop-mode state of a tab (per-tab override first, then the global default). */
fun MainActivity.effectiveDesktopMode(tab: WebTabState?): Boolean {
    val override = tab?.desktopMode
    if (override != null) {
        return override
    }
    val customUa = tab?.webEngine?.userAgentString ?: config.userAgentString.value
    return config.isDesktopMode(customUa)
}

/**
 * Set (or clear, with `desktop = null`) the desktop-mode override of one tab.
 *
 * Only the affected tab is re-zoomed and reloaded; other tabs keep their own state.
 */
fun MainActivity.setTabDesktopMode(tab: WebTabState?, desktop: Boolean?, reload: Boolean = true) {
    tab ?: return
    if (tab.desktopMode == desktop) {
        return
    }
    tab.desktopMode = desktop
    val isDesktop = effectiveDesktopMode(tab)
    tab.webEngine.setDesktopModeOverride(desktop ?: config.desktopMode.value)
    tab.webEngine.setPageZoom(config.getEffectiveZoom(isDesktop))
    if (reload && tab === tabsModel.currentTab.value) {
        tab.webEngine.reload()
    }
    tabsModel.persistDesktopMode(tab)
}

/**
 * Toggle desktop mode for the current tab.
 *
 * @param force explicit target state, `null` = flip whatever the tab is showing now.
 * @return the new state, so callers can show a matching toast / checkbox.
 */
fun MainActivity.toggleDesktopModeForCurrentTab(force: Boolean? = null, reload: Boolean = true): Boolean {
    val tab = tabsModel.currentTab.value
    val next = force ?: !effectiveDesktopMode(tab)
    setTabDesktopMode(tab, next, reload)
    return next
}

/**
 * The global default changed (Settings -> "Desktop mode"). Push it to every tab that has no
 * explicit per-tab override, which is exactly the set of tabs that follow the global value.
 */
fun MainActivity.applyDesktopModeDefault() {
    val isDesktop = config.desktopMode.value
    val zoom = config.getEffectiveZoom(isDesktop)
    for (tab in tabsModel.tabsStates) {
        if (tab.desktopMode == null) {
            tab.webEngine.setDesktopModeOverride(isDesktop)
            tab.webEngine.setPageZoom(zoom)
        }
    }
}

/** Text used by the menu / sidebar entries and their toasts. */
fun MainActivity.desktopModeToastText(desktop: Boolean): String =
    getString(if (desktop) R.string.desktop_site_on else R.string.desktop_site_off)
