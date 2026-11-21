package com.bansify.internet.activity.history

import android.content.Context
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bansify.internet.R
import com.bansify.internet.model.HistoryItem
import com.bansify.internet.singleton.FaviconsPool
import com.bansify.internet.utils.activity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryItemView(context: Context, private val viewType: Int) : FrameLayout(context) {
    private var tvDate: TextView? = null
    private var tvHeaderCount: TextView? = null
    private var tvTitle: TextView? = null
    private var tvURL: TextView? = null
    private var tvTime: TextView? = null
    private var ivHistoryFavicon: ImageView? = null
    private var cbSelection: CheckBox? = null
    private var ibItemDelete: ImageButton? = null
    private var ibItemMenu: ImageButton? = null
    var historyItem: HistoryItem? = null

    var onItemDeleteListener: ((HistoryItem) -> Unit)? = null
    var onItemMenuListener: ((HistoryItem, android.view.View) -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(
            if (viewType == HistoryAdapter.VIEW_TYPE_HEADER)
                R.layout.view_history_header_item
            else
                R.layout.view_history_item, this
        )
        when (viewType) {
            HistoryAdapter.VIEW_TYPE_HEADER -> {
                tvDate = findViewById(R.id.tvDate)
                tvHeaderCount = findViewById(R.id.tvHeaderCount)
            }
            HistoryAdapter.VIEW_TYPE_HISTORY_ITEM -> {
                tvTitle = findViewById(R.id.tvTitle)
                tvURL = findViewById(R.id.tvURL)
                tvTime = findViewById(R.id.tvTime)
                ivHistoryFavicon = findViewById(R.id.ivHistoryFavicon)
                cbSelection = findViewById(R.id.cbSelection)
                ibItemDelete = findViewById(R.id.ibItemDelete)
                ibItemMenu = findViewById(R.id.ibItemMenu)

                ibItemDelete?.setOnClickListener {
                    historyItem?.let { item -> onItemDeleteListener?.invoke(item) }
                }

                ibItemMenu?.setOnClickListener { v ->
                    historyItem?.let { item -> onItemMenuListener?.invoke(item, v) }
                }
            }
        }
    }

    fun setHistoryItem(
        historyItem: HistoryItem,
        multiselectMode: Boolean,
        onDelete: ((HistoryItem) -> Unit)? = null,
        onMenu: ((HistoryItem, android.view.View) -> Unit)? = null
    ) {
        this.historyItem = historyItem
        this.onItemDeleteListener = onDelete
        this.onItemMenuListener = onMenu

        when (viewType) {
            HistoryAdapter.VIEW_TYPE_HEADER -> {
                val time = historyItem.time
                val headerText = formatHeaderDate(time)
                tvDate?.text = headerText
            }
            HistoryAdapter.VIEW_TYPE_HISTORY_ITEM -> {
                tvTitle?.text = if (!historyItem.title.isNullOrBlank()) historyItem.title else historyItem.url
                tvURL?.text = historyItem.url
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                tvTime?.text = sdf.format(Date(historyItem.time))
                cbSelection?.visibility = if (multiselectMode) VISIBLE else GONE
                cbSelection?.isChecked = historyItem.selected

                // Favicon / logo
                val url = historyItem.url ?: ""
                val logoRes = getLogoForUrl(url)
                if (logoRes != 0) {
                    ivHistoryFavicon?.setImageResource(logoRes)
                } else {
                    ivHistoryFavicon?.setImageResource(R.drawable.ic_tab_default_favicon)
                    val activity = this.activity as? AppCompatActivity
                    activity?.lifecycleScope?.launch(Dispatchers.Main) {
                        try {
                            val favicon = FaviconsPool.get(url)
                            if (this@HistoryItemView.historyItem == historyItem && favicon != null) {
                                ivHistoryFavicon?.setImageBitmap(favicon)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    private fun formatHeaderDate(timestamp: Long): String {
        return try {
            if (DateUtils.isToday(timestamp)) {
                val dayFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
                "Today – " + dayFormat.format(Date(timestamp))
            } else if (DateUtils.isToday(timestamp + DateUtils.DAY_IN_MILLIS)) {
                val dayFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
                "Yesterday – " + dayFormat.format(Date(timestamp))
            } else {
                val fullFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
                fullFormat.format(Date(timestamp))
            }
        } catch (e: Exception) {
            SimpleDateFormat.getDateInstance().format(Date(timestamp))
        }
    }

    private fun getLogoForUrl(url: String): Int {
        val lower = url.lowercase()
        return when {
            lower.contains("google.com") -> R.drawable.ic_logo_google
            lower.contains("youtube.com") -> R.drawable.ic_logo_youtube
            lower.contains("whatsapp.com") -> R.drawable.ic_logo_whatsapp
            lower.contains("reddit.com") -> R.drawable.ic_logo_reddit
            lower.contains("netflix.com") -> R.drawable.ic_logo_netflix
            lower.contains("spotify.com") -> R.drawable.ic_logo_spotify
            lower.contains("amazon.com") -> R.drawable.ic_logo_amazon
            lower.contains("github.com") -> R.drawable.ic_logo_github
            lower.contains("facebook.com") -> R.drawable.ic_logo_facebook
            lower.contains("instagram.com") -> R.drawable.ic_logo_instagram
            lower.contains("telegram.org") -> R.drawable.ic_logo_telegram
            lower.contains("wikipedia.org") -> R.drawable.ic_logo_wikipedia
            else -> 0
        }
    }

    fun setSelection(selected: Boolean) {
        if (viewType == HistoryAdapter.VIEW_TYPE_HEADER) return
        cbSelection?.isChecked = selected
        historyItem?.selected = selected
    }
}
