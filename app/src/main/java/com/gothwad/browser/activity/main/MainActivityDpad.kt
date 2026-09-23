package com.gothwad.browser.activity.main

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import com.gothwad.browser.R
import com.gothwad.browser.singleton.shortcuts.ShortcutMgr
import com.gothwad.browser.utils.HardwareInputManager

fun MainActivity.getHeaderFocusableViews(): List<View> {
    val list = mutableListOf<View>()
    val candidateViews = listOf(
        vb.ibHistory,
        vb.ibHome,
        vb.ibBack,
        vb.ibForward,
        vb.ibRefresh,
        vb.vActionBar.getLockOrSearchEngineIcon(),
        vb.vActionBar.getUrlEditText(),
        vb.vActionBar.getVoiceSearchButton(),
        vb.ibNewTab,
        vb.flTabsSwitcher,
        vb.ibDownloads,
        vb.ibMenu
    )
    for (v in candidateViews) {
        if (v.isShown && v.visibility == View.VISIBLE) {
            list.add(v)
        }
    }
    return list
}

fun MainActivity.canWebPageScrollUp(): Boolean {
    val wv = tabsModel.currentTab.value?.webEngine?.getView()
    if (wv != null && (wv.canScrollVertically(-1) || wv.scrollY > 0)) {
        return true
    }
    for (i in 0 until vb.flWebViewContainer.childCount) {
        val child = vb.flWebViewContainer.getChildAt(i)
        if (child.canScrollVertically(-1) || child.scrollY > 0) {
            return true
        }
    }
    return false
}

fun MainActivity.focusHeaderViewNearX(cursorX: Float) {
    if (!vb.rlActionBar.isVisible) {
        showMenuOverlay()
    }
    val headerViews = getHeaderFocusableViews()
    if (headerViews.isEmpty()) {
        if (vb.ibHome.isShown && vb.ibHome.visibility == View.VISIBLE) {
            vb.ibHome.requestFocus()
        } else {
            vb.ibMenu.requestFocus()
        }
        return
    }

    val loc = IntArray(2)
    val surfaceLoc = IntArray(2)
    vb.flWebViewContainer.getLocationInWindow(surfaceLoc)
    val screenWidth = resources.displayMetrics.widthPixels
    val windowCursorX = if (cursorX >= 0 && cursorX <= screenWidth) {
        cursorX
    } else {
        surfaceLoc[0] + cursorX
    }

    var closestView: View = headerViews.first()
    var minDiff = Float.MAX_VALUE

    for (v in headerViews) {
        if (!v.isShown || v.visibility != View.VISIBLE) continue
        v.getLocationInWindow(loc)
        val centerX = loc[0] + v.width / 2.0f
        val diff = Math.abs(centerX - windowCursorX)
        if (diff < minDiff) {
            minDiff = diff
            closestView = v
        }
    }

    vb.flWebViewContainer.cursorDrawerDelegate.hideCursor()
    closestView.requestFocus()
}

fun MainActivity.isTopTabBarView(view: View): Boolean {
    var p: Any? = view
    while (p is View) {
        if (p.id == R.id.llTopTabBar) return true
        p = p.parent
    }
    return false
}

fun MainActivity.handleDpadEvent(event: KeyEvent): Boolean {
    val keyCode = if (event.keyCode != 0) event.keyCode else event.scanCode
    val isNativeHomeVisible = vb.vNativeHome.isVisible
    val focus = currentFocus

    // 1. Top Tab Bar D-Pad navigation
    if (focus != null && isTopTabBarView(focus)) {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (focus == vb.ibTopTabSearch) {
                        vb.ibHistory.requestFocus()
                        return true
                    }
                    if (focus == vb.ibTopHideBars) {
                        vb.ibDownloads.requestFocus()
                        return true
                    }
                    if (focus == vb.ibTopCloseApp) {
                        vb.ibMenu.requestFocus()
                        return true
                    }
                    val headerViews = getHeaderFocusableViews()
                    if (headerViews.isNotEmpty()) {
                        if (vb.rvTopTabs.childCount > 0) {
                            val childIdx = (0 until vb.rvTopTabs.childCount).indexOfFirst { idx ->
                                val child = vb.rvTopTabs.getChildAt(idx)
                                child == focus || isDescendantOrSelf(focus, child)
                            }
                            if (childIdx >= 0 && vb.rvTopTabs.childCount > 1) {
                                val ratio = childIdx.toFloat() / (vb.rvTopTabs.childCount - 1)
                                val targetIdx = (ratio * (headerViews.size - 1)).toInt().coerceIn(0, headerViews.size - 1)
                                headerViews[targetIdx].requestFocus()
                            } else {
                                headerViews.first().requestFocus()
                            }
                        } else {
                            headerViews.first().requestFocus()
                        }
                    } else if (isNativeHomeVisible) {
                        vb.vNativeHome.catchFocus()
                    } else {
                        vb.flWebViewContainer.requestFocus()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (focus == vb.ibTopCloseApp) {
                        return true
                    }
                    if (focus == vb.ibTopHideBars) {
                        vb.ibTopCloseApp.requestFocus()
                        return true
                    }
                    if (focus == vb.ibTopTabSearch) {
                        if (vb.rvTopTabs.childCount > 0) {
                            vb.rvTopTabs.getChildAt(0)?.requestFocus()
                        } else {
                            vb.ibTopHideBars.requestFocus()
                        }
                        return true
                    }
                    // Inside rvTopTabs
                    val tabChildCount = vb.rvTopTabs.childCount
                    var focusedTabHolderView: View? = null
                    var focusedTabChildIndex = -1
                    for (i in 0 until tabChildCount) {
                        val child = vb.rvTopTabs.getChildAt(i)
                        if (child == focus || isDescendantOrSelf(focus, child)) {
                            focusedTabHolderView = child
                            focusedTabChildIndex = i
                            break
                        }
                    }
                    if (focusedTabHolderView != null) {
                        val closeBtn = focusedTabHolderView.findViewById<View>(R.id.ibTabClose)
                        if (closeBtn != null && closeBtn.visibility == View.VISIBLE && focus != closeBtn) {
                            closeBtn.requestFocus()
                            return true
                        }
                        if (focusedTabChildIndex + 1 < tabChildCount) {
                            vb.rvTopTabs.getChildAt(focusedTabChildIndex + 1)?.requestFocus()
                        } else {
                            vb.ibTopHideBars.requestFocus()
                        }
                        return true
                    }
                    val next = focus.focusSearch(View.FOCUS_RIGHT)
                    if (next != null && isTopTabBarView(next)) {
                        next.requestFocus()
                    } else {
                        vb.ibTopHideBars.requestFocus()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (focus == vb.ibTopTabSearch) {
                        return true
                    }
                    if (focus == vb.ibTopCloseApp) {
                        vb.ibTopHideBars.requestFocus()
                        return true
                    }
                    if (focus == vb.ibTopHideBars) {
                        if (vb.rvTopTabs.childCount > 0) {
                            val lastChild = vb.rvTopTabs.getChildAt(vb.rvTopTabs.childCount - 1)
                            val closeBtn = lastChild?.findViewById<View>(R.id.ibTabClose)
                            if (closeBtn != null && closeBtn.visibility == View.VISIBLE) {
                                closeBtn.requestFocus()
                            } else {
                                lastChild?.requestFocus()
                            }
                        } else {
                            vb.ibTopTabSearch.requestFocus()
                        }
                        return true
                    }
                    // Inside rvTopTabs
                    val tabChildCount = vb.rvTopTabs.childCount
                    var focusedTabHolderView: View? = null
                    var focusedTabChildIndex = -1
                    for (i in 0 until tabChildCount) {
                        val child = vb.rvTopTabs.getChildAt(i)
                        if (child == focus || isDescendantOrSelf(focus, child)) {
                            focusedTabHolderView = child
                            focusedTabChildIndex = i
                            break
                        }
                    }
                    if (focusedTabHolderView != null) {
                        val closeBtn = focusedTabHolderView.findViewById<View>(R.id.ibTabClose)
                        if (closeBtn != null && closeBtn.visibility == View.VISIBLE && focus == closeBtn) {
                            focusedTabHolderView.requestFocus()
                            return true
                        }
                        if (focusedTabChildIndex > 0) {
                            val prevChild = vb.rvTopTabs.getChildAt(focusedTabChildIndex - 1)
                            val prevCloseBtn = prevChild?.findViewById<View>(R.id.ibTabClose)
                            if (prevCloseBtn != null && prevCloseBtn.visibility == View.VISIBLE) {
                                prevCloseBtn.requestFocus()
                            } else {
                                prevChild?.requestFocus()
                            }
                        } else {
                            vb.ibTopTabSearch.requestFocus()
                        }
                        return true
                    }
                    val next = focus.focusSearch(View.FOCUS_LEFT)
                    if (next != null && isTopTabBarView(next)) {
                        next.requestFocus()
                    } else {
                        vb.ibTopTabSearch.requestFocus()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                    focus.performClick()
                    return true
                }
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_BUTTON_A -> return true
            }
        }
        return false
    }

    // 2. Toolbar D-Pad navigation
    val inToolbar = focus != null && isToolbarView(focus)
    if (inToolbar) {
        val headerViews = getHeaderFocusableViews()
        val currentIndex = headerViews.indexOfFirst { it == focus || isDescendantOrSelf(focus, it) }

        if (event.action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (currentIndex > 0) {
                        headerViews[currentIndex - 1].requestFocus()
                    } else if (currentIndex == -1 && headerViews.isNotEmpty()) {
                        headerViews.first().requestFocus()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (currentIndex in 0 until headerViews.size - 1) {
                        headerViews[currentIndex + 1].requestFocus()
                    } else if (currentIndex == -1 && headerViews.isNotEmpty()) {
                        headerViews.first().requestFocus()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (vb.llTopTabBar.isVisible && event.repeatCount == 0) {
                        if (focus == vb.ibHistory) {
                            vb.ibTopTabSearch.requestFocus()
                            return true
                        }
                        if (focus == vb.ibDownloads) {
                            vb.ibTopHideBars.requestFocus()
                            return true
                        }
                        if (focus == vb.ibMenu) {
                            vb.ibTopCloseApp.requestFocus()
                            return true
                        }
                        if (vb.rvTopTabs.childCount > 0) {
                            val currentTab = tabsModel.currentTab.value
                            var targetView: View? = null
                            for (i in 0 until vb.rvTopTabs.childCount) {
                                val child = vb.rvTopTabs.getChildAt(i)
                                if (child.tag == currentTab) {
                                    targetView = child
                                    break
                                }
                            }
                            if (targetView == null) {
                                targetView = vb.rvTopTabs.getChildAt(0)
                            }
                            targetView?.requestFocus()
                            return true
                        } else {
                            vb.ibTopTabSearch.requestFocus()
                            return true
                        }
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (isNativeHomeVisible) {
                        val loc = IntArray(2)
                        focus?.getLocationInWindow(loc)
                        val headerCenterX = (loc[0] + (focus?.width ?: 0) / 2.0f).coerceAtLeast(0f)
                        val screenWidth = resources.displayMetrics.widthPixels
                        val col = ((headerCenterX / screenWidth) * 5.0f).toInt().coerceIn(0, 4)
                        vb.vNativeHome.focusShortcutAtColumn(col)
                    } else {
                        vb.flWebViewContainer.requestFocus()
                        if (config.enableVirtualCursor) {
                            val loc = IntArray(2)
                            val surfaceLoc = IntArray(2)
                            vb.flWebViewContainer.getLocationInWindow(surfaceLoc)
                            focus?.let { f ->
                                f.getLocationInWindow(loc)
                                val headerCenterX = loc[0] + f.width / 2.0f
                                val containerWidth = vb.flWebViewContainer.width.toFloat().takeIf { it > 100f } ?: resources.displayMetrics.widthPixels.toFloat()
                                val relativeCursorX = (headerCenterX - surfaceLoc[0]).coerceIn(50f, containerWidth - 50f)
                                vb.flWebViewContainer.cursorDrawerDelegate.cursorPosition.set(relativeCursorX, 50f)
                            }
                            vb.flWebViewContainer.cursorDrawerDelegate.showCursor()
                            vb.flWebViewContainer.cursorDrawerDelegate.dispatchKeyEvent(event)
                        }
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                    val etUrl = vb.vActionBar.getUrlEditText()
                    if (focus == etUrl || (focus != null && isDescendantOrSelf(focus, etUrl))) {
                        vb.vActionBar.performSearchInAddressBar()
                    } else {
                        focus?.performClick()
                    }
                    return true
                }
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_BUTTON_A -> return true
            }
        }
        return false
    }

    // 3. Native Home View D-Pad navigation
    if (isNativeHomeVisible) {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (focus == null) {
                        vb.vNativeHome.catchFocus()
                        return true
                    }
                    val canMoveUp = vb.vNativeHome.navigateFocus(KeyEvent.KEYCODE_DPAD_UP)
                    if (!canMoveUp) {
                        val col = vb.vNativeHome.getFocusedShortcutColumn()
                        val screenWidth = resources.displayMetrics.widthPixels
                        val approxX = (col + 0.5f) * (screenWidth / 5.0f)
                        focusHeaderViewNearX(approxX)
                        return true
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (focus == null) {
                        vb.vNativeHome.catchFocus()
                        return true
                    }
                    vb.vNativeHome.navigateFocus(KeyEvent.KEYCODE_DPAD_DOWN)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (focus == null) {
                        vb.vNativeHome.catchFocus()
                        return true
                    }
                    vb.vNativeHome.navigateFocus(KeyEvent.KEYCODE_DPAD_LEFT)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (focus == null) {
                        vb.vNativeHome.catchFocus()
                        return true
                    }
                    vb.vNativeHome.navigateFocus(KeyEvent.KEYCODE_DPAD_RIGHT)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                    focus?.performClick()
                    return true
                }
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_BUTTON_A -> return true
            }
        }
        return false
    } else {
        // 4. Web Page Active
        if (config.enableVirtualCursor) {
            // Physical Enter key from a keyboard should reach the webpage natively (e.g. submitting forms, sending messages in Telegram/Google)
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                return false
            }
            val canScrollUp = canWebPageScrollUp()
            // If cursor is at the very top edge and the webpage cannot scroll up anymore, navigate into Toolbar
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP &&
                !canScrollUp && vb.flWebViewContainer.cursorDrawerDelegate.isCursorNearTop()) {
                val surfaceLoc = IntArray(2)
                vb.flWebViewContainer.getLocationInWindow(surfaceLoc)
                val windowCursorX = surfaceLoc[0] + vb.flWebViewContainer.cursorDrawerDelegate.cursorPosition.x
                focusHeaderViewNearX(windowCursorX)
                return true
            }
            // Dispatch live stream (DOWN & UP) to virtual cursor
            if (vb.flWebViewContainer.cursorDrawerDelegate.dispatchKeyEvent(event)) {
                return true
            }
        }
    }
    return false
}

fun MainActivity.isToolbarView(view: View): Boolean {
    if (isTopTabBarView(view)) return false
    var p: Any? = view
    while (p is View) {
        if (p.id == R.id.rlActionBar || p.id == R.id.llBottomPanel) {
            return true
        }
        p = p.parent
    }
    return false
}

fun MainActivity.isDescendantOrSelf(view: View, ancestor: View): Boolean {
    var p: Any? = view
    while (p is View) {
        if (p == ancestor) return true
        p = p.parent
    }
    return false
}

fun MainActivity.sendDpadToCursor(keyCode: Int) {
    val downTime = SystemClock.uptimeMillis()
    val eventTime = SystemClock.uptimeMillis()
    val downEvent = KeyEvent(
        downTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
        KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD
    )
    val upEvent = KeyEvent(
        downTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0, 0,
        KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD
    )
    vb.flWebViewContainer.cursorDrawerDelegate.dispatchKeyEvent(downEvent)
    vb.flWebViewContainer.cursorDrawerDelegate.dispatchKeyEvent(upEvent)
}

fun MainActivity.setupWindowCallbacks() {
    val localCallback = window.callback
    window.callback = object : Window.Callback by localCallback {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            val hwInput = HardwareInputManager.getInstance(this@setupWindowCallbacks)
            if (hwInput.isDeviceBlocked(event)) {
                return true // Consume and discard blocked input device events
            }

            // Actively enforce soft keyboard suppression when typing with physical keyboard
            if (config.disableVirtualKeyboard) {
                try {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    val focused = currentFocus ?: window.decorView
                    imm?.hideSoftInputFromWindow(focused.windowToken, 0)
                } catch (e: Exception) {
                    // ignore
                }
            }

            backNavigationEventsAdapter.dispatchKeyEvent(event)
            val keyCode = if (event.keyCode != 0) event.keyCode else event.scanCode
            val keyCodeBackNavigation = keyCode == KeyEvent.KEYCODE_ESCAPE ||
                    keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_BACK
            val shortcutMgr = ShortcutMgr.getInstance()
            val currentTab = tabsModel.currentTab.value
            if (!keyCodeBackNavigation && shortcutMgr.handle(event, this@setupWindowCallbacks, currentTab)) {
                return true
            }

            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP_LEFT,
                KeyEvent.KEYCODE_DPAD_UP_RIGHT,
                KeyEvent.KEYCODE_DPAD_DOWN_LEFT,
                KeyEvent.KEYCODE_DPAD_DOWN_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_BUTTON_A -> {
                    if (handleDpadEvent(event)) {
                        return true
                    }
                }
            }

            return localCallback.dispatchKeyEvent(event)
        }

        override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
            val hwInput = HardwareInputManager.getInstance(this@setupWindowCallbacks)
            if (hwInput.isDeviceBlocked(event)) {
                return true // Drop blocked hardware input device events
            }

            if (backNavigationEventsAdapter.dispatchGenericMotionEvent(event)) {
                return true
            }
            return localCallback.dispatchGenericMotionEvent(event)
        }

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            val hwInput = HardwareInputManager.getInstance(this@setupWindowCallbacks)
            if (hwInput.isDeviceBlocked(event)) {
                return true // Drop blocked hardware input device events
            }
            return localCallback.dispatchTouchEvent(event)
        }
    }
}
