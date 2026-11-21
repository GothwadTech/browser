package com.bansify.internet.utils

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.WindowManager

fun Activity.setupAsSidebar(isRight: Boolean) {
    val dm = resources.displayMetrics
    val popupWidth = (dm.widthPixels * 0.32f).toInt().coerceIn(350, 600)
    
    window.setLayout(popupWidth, WindowManager.LayoutParams.MATCH_PARENT)
    window.setGravity(if (isRight) Gravity.END else Gravity.START)
    window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    setFinishOnTouchOutside(true)
}
