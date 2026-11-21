package com.bansify.internet.activity.main.view.tabs

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.bansify.internet.model.WebTabState

/**
 * Legacy TabsView maintained for backward compatibility.
 * Modern tab management is handled by TabsGridDialog in dialogs/tabs/.
 */
class TabsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    fun setTabs(tabs: List<WebTabState>) {
        // Handled via TabsGridDialog
    }
}
