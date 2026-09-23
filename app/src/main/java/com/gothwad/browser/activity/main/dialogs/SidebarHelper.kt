package com.gothwad.browser.activity.main.dialogs

import android.app.Activity
import android.view.View
import com.gothwad.browser.R

object SidebarHelper {
    /**
     * Calculates the uniform horizontal width for right-hand sidebars (Settings, Downloads,
     * Tabs, etc.).
     * The width is anchored dynamically to header geometry, extending from the starting edge
     * of the microphone/voice search icon inside the address bar to the right edge of the screen.
     */
    fun calculateSidebarWidth(activity: Activity): Int = calculateRightSidebarWidth(activity)

    fun calculateRightSidebarWidth(activity: Activity): Int {
        val decorView = activity.window.decorView
        val screenWidth = if (decorView.width > 0) decorView.width else activity.resources.displayMetrics.widthPixels
        val micIcon = activity.findViewById<View>(R.id.ibVoiceSearch)
        val searchBar = activity.findViewById<View>(R.id.vActionBar)
        val dynamicWidth: Int = if (micIcon != null && micIcon.width > 0) {
            val micLoc = IntArray(2)
            micIcon.getLocationInWindow(micLoc)
            val micStart = micLoc[0]
            (screenWidth - micStart).coerceIn(240, (screenWidth * 0.60f).toInt())
        } else if (searchBar != null && searchBar.width > 0) {
            val searchLoc = IntArray(2)
            searchBar.getLocationInWindow(searchLoc)
            val micOffset = (34 * activity.resources.displayMetrics.density).toInt()
            val estimatedMicStart = (searchLoc[0] + searchBar.width - micOffset).coerceAtLeast(searchLoc[0])
            (screenWidth - estimatedMicStart).coerceIn(240, (screenWidth * 0.60f).toInt())
        } else {
            (screenWidth * 0.32f).toInt().coerceIn(280, 560)
        }
        return dynamicWidth
    }

    /**
     * Calculates the horizontal width for left-hand sidebars (Browser Menu, Bookmarks/Favorites, History).
     * The width extends from the left edge of the screen (x = 0) up to the lock icon inside the address bar,
     * keeping left navigation sidebars strictly aligned with the lock icon.
     */
    fun calculateLeftSidebarWidth(activity: Activity): Int {
        val decorView = activity.window.decorView
        val screenWidth = if (decorView.width > 0) decorView.width else activity.resources.displayMetrics.widthPixels
        val lockIcon = activity.findViewById<View>(R.id.ivLockIcon)
        val searchBar = activity.findViewById<View>(R.id.vActionBar)
        val dynamicWidth: Int = if (lockIcon != null && lockIcon.width > 0) {
            val lockLoc = IntArray(2)
            lockIcon.getLocationInWindow(lockLoc)
            val lockEnd = lockLoc[0] + lockIcon.width
            lockEnd.coerceIn(180, (screenWidth * 0.60f).toInt())
        } else if (searchBar != null && searchBar.width > 0) {
            val searchLoc = IntArray(2)
            searchBar.getLocationInWindow(searchLoc)
            val lockOffset = (34 * activity.resources.displayMetrics.density).toInt()
            val estimatedLockEnd = searchLoc[0] + lockOffset
            estimatedLockEnd.coerceIn(180, (screenWidth * 0.60f).toInt())
        } else {
            (screenWidth * 0.28f).toInt().coerceIn(220, 480)
        }
        return dynamicWidth
    }

    /**
     * Calculates comfortable horizontal width for Chrome-style History panel.
     * Ensures generous horizontal space for timestamps, favicons, titles, and actions.
     */
    fun calculateHistorySidebarWidth(activity: Activity): Int {
        val decorView = activity.window.decorView
        val screenWidth = if (decorView.width > 0) decorView.width else activity.resources.displayMetrics.widthPixels
        val density = activity.resources.displayMetrics.density
        val minWidth = (360 * density).toInt()
        val idealWidth = (screenWidth * 0.38f).toInt()
        val maxWidth = (screenWidth * 0.55f).toInt()
        return idealWidth.coerceIn(minWidth.coerceAtMost(screenWidth), maxWidth.coerceAtLeast(minWidth).coerceAtMost(screenWidth))
    }
}
