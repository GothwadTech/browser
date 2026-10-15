package com.gothwad.browser.activity.lock

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.gothwad.browser.R
import com.gothwad.browser.singleton.AppLockManager

class TvPinDialog(
    context: Context,
    private val mode: Mode,
    private val onSuccess: ((String) -> Unit)? = null,
    private val onCancel: (() -> Unit)? = null
) : Dialog(context) {

    enum class Mode {
        CREATE,
        VERIFY,
        CHANGE
    }

    private val inputPin = StringBuilder()
    private var tempNewPin: String? = null
    private var step = 1 // for multi-step flows

    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var dot1: View
    private lateinit var dot2: View
    private lateinit var dot3: View
    private lateinit var dot4: View
    private lateinit var dotsContainer: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_tv_pin)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setCancelable(true)
        setOnCancelListener { onCancel?.invoke() }

        tvTitle = findViewById(R.id.tvPinDialogTitle)
        tvSubtitle = findViewById(R.id.tvPinDialogSubtitle)
        dot1 = findViewById(R.id.pinDot1)
        dot2 = findViewById(R.id.pinDot2)
        dot3 = findViewById(R.id.pinDot3)
        dot4 = findViewById(R.id.pinDot4)
        dotsContainer = findViewById(R.id.llPinDotsContainer)

        setupStepUI()
        setupKeypad()

        findViewById<Button>(R.id.btnDlgCancel)?.setOnClickListener {
            dismiss()
            onCancel?.invoke()
        }
    }

    private fun setupStepUI() {
        inputPin.clear()
        updateDots()

        when (mode) {
            Mode.CREATE -> {
                if (step == 1) {
                    tvTitle.setText(R.string.app_lock_pin_title_set)
                    tvSubtitle.text = context.getString(R.string.app_lock_step_new, 2)
                    tvSubtitle.setTextColor(themedColor(R.color.google_text_secondary))
                } else {
                    tvTitle.setText(R.string.app_lock_pin_title_confirm)
                    tvSubtitle.text = context.getString(R.string.app_lock_step_confirm, 2)
                    tvSubtitle.setTextColor(themedColor(R.color.google_accent))
                }
            }
            Mode.VERIFY -> {
                tvTitle.setText(R.string.app_lock_pin_title_enter)
                tvSubtitle.setText(R.string.app_lock_pin_title_verify_current)
                tvSubtitle.setTextColor(themedColor(R.color.google_text_secondary))
            }
            Mode.CHANGE -> {
                when (step) {
                    1 -> {
                        tvTitle.setText(R.string.app_lock_pin_title_verify_current)
                        tvSubtitle.text = context.getString(R.string.app_lock_step_verify, 3)
                        tvSubtitle.setTextColor(themedColor(R.color.google_text_secondary))
                    }
                    2 -> {
                        tvTitle.setText(R.string.app_lock_pin_title_new)
                        tvSubtitle.text = context.getString(R.string.app_lock_step_new_3, 3)
                        tvSubtitle.setTextColor(themedColor(R.color.google_accent))
                    }
                    3 -> {
                        tvTitle.setText(R.string.app_lock_pin_title_confirm_new)
                        tvSubtitle.text = context.getString(R.string.app_lock_step_confirm_3, 3)
                        tvSubtitle.setTextColor(themedColor(R.color.google_accent))
                    }
                }
            }
        }
    }

    private fun setupKeypad() {
        val keys = listOf(
            R.id.btnDlgKey0 to "0",
            R.id.btnDlgKey1 to "1",
            R.id.btnDlgKey2 to "2",
            R.id.btnDlgKey3 to "3",
            R.id.btnDlgKey4 to "4",
            R.id.btnDlgKey5 to "5",
            R.id.btnDlgKey6 to "6",
            R.id.btnDlgKey7 to "7",
            R.id.btnDlgKey8 to "8",
            R.id.btnDlgKey9 to "9"
        )

        for ((btnId, digit) in keys) {
            findViewById<Button>(btnId)?.setOnClickListener {
                appendDigit(digit)
            }
        }

        findViewById<Button>(R.id.btnDlgKeyClear)?.setOnClickListener {
            inputPin.clear()
            updateDots()
        }

        findViewById<Button>(R.id.btnDlgKeyBackspace)?.setOnClickListener {
            if (inputPin.isNotEmpty()) {
                inputPin.deleteCharAt(inputPin.length - 1)
                updateDots()
            }
        }

        findViewById<Button>(R.id.btnDlgKey1)?.requestFocus()
    }

    private fun appendDigit(digit: String) {
        if (inputPin.length < 4) {
            inputPin.append(digit)
            updateDots()

            if (inputPin.length == 4) {
                handlePinComplete(inputPin.toString())
            }
        }
    }

    private fun updateDots() {
        dot1.setBackgroundResource(if (inputPin.length >= 1) R.drawable.pin_dot_filled else R.drawable.pin_dot_empty)
        dot2.setBackgroundResource(if (inputPin.length >= 2) R.drawable.pin_dot_filled else R.drawable.pin_dot_empty)
        dot3.setBackgroundResource(if (inputPin.length >= 3) R.drawable.pin_dot_filled else R.drawable.pin_dot_empty)
        dot4.setBackgroundResource(if (inputPin.length >= 4) R.drawable.pin_dot_filled else R.drawable.pin_dot_empty)
    }

    private fun handlePinComplete(entered: String) {
        when (mode) {
            Mode.CREATE -> {
                if (step == 1) {
                    tempNewPin = entered
                    step = 2
                    setupStepUI()
                } else {
                    if (entered == tempNewPin) {
                        AppLockManager.setPin(context, entered)
                        AppLockManager.setLockEnabled(context, true)
                        Toast.makeText(context, R.string.app_lock_pin_saved, Toast.LENGTH_SHORT).show()
                        dismiss()
                        onSuccess?.invoke(entered)
                    } else {
                        showError(context.getString(R.string.app_lock_pin_mismatch))
                        step = 1
                        tempNewPin = null
                        setupStepUI()
                    }
                }
            }
            Mode.VERIFY -> {
                val result = AppLockManager.verifyPinDetailed(context, entered)
                if (result.success) {
                    dismiss()
                    onSuccess?.invoke(entered)
                } else {
                    showError(
                        if (result.lockoutMs > 0) {
                            context.getString(R.string.app_lock_locked_out, (result.lockoutMs / 1000L).toInt())
                        } else {
                            context.getString(R.string.app_lock_pin_incorrect)
                        }
                    )
                }
            }
            Mode.CHANGE -> {
                when (step) {
                    1 -> {
                        val current = AppLockManager.verifyPinDetailed(context, entered)
                        if (current.success) {
                            step = 2
                            setupStepUI()
                        } else {
                            showError(context.getString(R.string.app_lock_pin_incorrect_current))
                        }
                    }
                    2 -> {
                        tempNewPin = entered
                        step = 3
                        setupStepUI()
                    }
                    3 -> {
                        if (entered == tempNewPin) {
                            AppLockManager.setPin(context, entered)
                            Toast.makeText(context, R.string.app_lock_pin_changed, Toast.LENGTH_SHORT).show()
                            dismiss()
                            onSuccess?.invoke(entered)
                        } else {
                            showError(context.getString(R.string.app_lock_pin_mismatch))
                            step = 2
                            tempNewPin = null
                            setupStepUI()
                        }
                    }
                }
            }
        }
    }

    private fun showError(msg: String) {
        tvSubtitle.text = msg
        tvSubtitle.setTextColor(themedColor(R.color.google_error))

        dotsContainer.animate()
            ?.translationXBy(20f)
            ?.setDuration(50)
            ?.withEndAction {
                dotsContainer.animate()
                    ?.translationXBy(-40f)
                    ?.setDuration(50)
                    ?.withEndAction {
                        dotsContainer.animate()
                            ?.translationX(0f)
                            ?.setDuration(50)
                            ?.start()
                    }?.start()
            }?.start()

        inputPin.clear()
        updateDots()
    }

    private fun themedColor(colorRes: Int): Int =
        androidx.core.content.ContextCompat.getColor(context, colorRes)

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> { appendDigit("0"); return true }
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> { appendDigit("1"); return true }
            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> { appendDigit("2"); return true }
            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> { appendDigit("3"); return true }
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> { appendDigit("4"); return true }
            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> { appendDigit("5"); return true }
            KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> { appendDigit("6"); return true }
            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> { appendDigit("7"); return true }
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> { appendDigit("8"); return true }
            KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> { appendDigit("9"); return true }
            KeyEvent.KEYCODE_DEL -> {
                if (inputPin.isNotEmpty()) {
                    inputPin.deleteCharAt(inputPin.length - 1)
                    updateDots()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
