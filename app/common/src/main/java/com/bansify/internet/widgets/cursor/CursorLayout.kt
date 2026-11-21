package com.bansify.internet.widgets.cursor

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bansify.internet.AppContext
import com.bansify.internet.utils.DPADNavigationEventsAdapter


/**
 * Created by PDT on 25.08.2016.
 */
class CursorLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null):
    FrameLayout(context, attrs) {
    var cursorEnabled: Boolean
        get() = !willNotDraw()
        set(value) {
            setWillNotDraw(!value)
        }
    lateinit var cursorDrawerDelegate: CursorDrawerDelegate
    private val inputEventsAdapter = DPADNavigationEventsAdapter(
        onEmulatedKeyEvent = { keyEvent ->
            cursorDrawerDelegate.dispatchKeyEvent(keyEvent)
        },
        motionAxesTranslationEnabled = { !AppContext.provideConfig().disableMotionAxesDpadNavigation },
        isSoftwareKeyboardVisible = {
            ViewCompat.getRootWindowInsets(rootView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        },
    )

    init {
        init()
    }

    private fun init() {
        if (isInEditMode) {
            return
        }
        setWillNotDraw(false)
        cursorDrawerDelegate = CursorDrawerDelegate(context, this)
        cursorDrawerDelegate.init()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (isInEditMode || willNotDraw()) {
            return
        }
        cursorDrawerDelegate.onSizeChanged(w, h, ow, oh)
    }

    override fun setWillNotDraw(willNotDraw: Boolean) {
        inputEventsAdapter.resetState()
        super.setWillNotDraw(willNotDraw)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Log.d("CursorLayout", "dispatchKeyEvent: $event")

        if (willNotDraw() || !AppContext.provideConfig().enableVirtualCursor) return super.dispatchKeyEvent(event)

        if (inputEventsAdapter.dispatchKeyEvent(event)) {
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        Log.d("CursorLayout", "dispatchGenericMotionEvent: $event")
        val hwInput = com.bansify.internet.utils.HardwareInputManager.getInstance(context)
        if (hwInput.isDeviceBlocked(event)) return true

        val isMouseOrTouch = (event.source and InputDevice.SOURCE_MOUSE) != 0 ||
                (event.source and InputDevice.SOURCE_TOUCHPAD) != 0 ||
                (event.source and InputDevice.SOURCE_TOUCHSCREEN) != 0 ||
                event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE

        if (isMouseOrTouch) {
            cursorDrawerDelegate.hideCursor()
            return super.dispatchGenericMotionEvent(event)
        }

        if (willNotDraw() || !AppContext.provideConfig().enableVirtualCursor) return super.dispatchGenericMotionEvent(event)

        if (inputEventsAdapter.dispatchGenericMotionEvent(event)) {
            return true
        }

        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val hwInput = com.bansify.internet.utils.HardwareInputManager.getInstance(context)
        if (hwInput.isDeviceBlocked(ev)) return true
        if (!cursorDrawerDelegate.isDispatchingSyntheticEvent) {
            cursorDrawerDelegate.hideCursor()
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onResolvePointerIcon(event: MotionEvent, pointerIndex: Int): android.view.PointerIcon? {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            val systemIcon = super.onResolvePointerIcon(event, pointerIndex)
            return com.bansify.internet.utils.HardwareMousePointerManager.resolvePointerIcon(context, systemIcon)
        }
        return super.onResolvePointerIcon(event, pointerIndex)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (isInEditMode || willNotDraw()) {
            return
        }

        cursorDrawerDelegate.dispatchDraw(canvas)
    }
}
