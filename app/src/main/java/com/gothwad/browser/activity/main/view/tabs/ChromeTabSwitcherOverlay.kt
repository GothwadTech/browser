package com.gothwad.browser.activity.main.view.tabs

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.browser.R
import com.gothwad.browser.activity.main.MainActivity
import com.gothwad.browser.activity.main.toggleIncognitoMode
import com.gothwad.browser.databinding.ViewChromeTabSwitcherBinding
import com.gothwad.browser.model.WebTabState
import com.gothwad.browser.singleton.FaviconsPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChromeTabSwitcherOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val vb = ViewChromeTabSwitcherBinding.inflate(LayoutInflater.from(context), this, true)
    private var allTabs = listOf<WebTabState>()
    private var currentTab: WebTabState? = null
    private var filterQuery: String = ""

    private val adapter = ChromeTabGridAdapter(
        onTabSelected = { tab ->
            hide()
            onTabSelectListener?.invoke(tab)
        },
        onTabClose = { tab ->
            onTabCloseListener?.invoke(tab)
        }
    )

    var onTabSelectListener: ((WebTabState) -> Unit)? = null
    var onTabCloseListener: ((WebTabState) -> Unit)? = null
    var onNewTabListener: (() -> Unit)? = null
    var onCloseAllTabsListener: (() -> Unit)? = null

    init {
        val spanCount = if (resources.configuration.smallestScreenWidthDp >= 600 ||
            resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        ) 3 else 2

        vb.rvChromeTabsGrid.layoutManager = GridLayoutManager(context, spanCount)
        vb.rvChromeTabsGrid.adapter = adapter

        vb.btnNewTabTop.setOnClickListener {
            hide()
            onNewTabListener?.invoke()
        }

        vb.btnSegmentOpenTabs.setOnClickListener {
            vb.btnSegmentOpenTabs.setBackgroundResource(R.drawable.bg_chrome_segment_selected)
            vb.btnSegmentGroups.background = null
            vb.ivSegmentGridIcon.setColorFilter(ContextCompat.getColor(context, R.color.day_night_text_secondary))
            vb.rvChromeTabsGrid.visibility = View.VISIBLE
            vb.llTabGroupsContainer.visibility = View.GONE
        }

        vb.btnSegmentGroups.setOnClickListener {
            vb.btnSegmentGroups.setBackgroundResource(R.drawable.bg_chrome_segment_selected)
            vb.btnSegmentOpenTabs.background = null
            vb.ivSegmentGridIcon.setColorFilter(ContextCompat.getColor(context, R.color.day_night_text_color_contrast))
            vb.rvChromeTabsGrid.visibility = View.GONE
            vb.llTabGroupsContainer.visibility = View.VISIBLE
        }

        vb.btnTabsOverflowMenu.setOnClickListener { view ->
            showOverflowMenu(view)
        }

        vb.etSearchTabs.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterQuery = s?.toString()?.trim() ?: ""
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        vb.llSampleTabGroupCard.setOnClickListener {
            vb.btnSegmentOpenTabs.performClick()
        }
    }

    fun show(tabs: List<WebTabState>, activeTab: WebTabState?) {
        allTabs = tabs
        currentTab = activeTab
        visibility = View.VISIBLE
        bringToFront()
        vb.tvSegmentTabCount.text = tabs.size.toString()
        vb.tvGroupTitle.text = "● ${tabs.size} tabs"

        applyFilter()

        val activeIndex = tabs.indexOf(activeTab)
        if (activeIndex >= 0) {
            vb.rvChromeTabsGrid.post {
                vb.rvChromeTabsGrid.scrollToPosition(activeIndex)
            }
        }
    }

    fun hide() {
        visibility = View.GONE
        vb.etSearchTabs.clearFocus()
    }

    fun updateTabs(tabs: List<WebTabState>, activeTab: WebTabState?) {
        allTabs = tabs
        currentTab = activeTab
        vb.tvSegmentTabCount.text = tabs.size.toString()
        vb.tvGroupTitle.text = "● ${tabs.size} tabs"
        applyFilter()
    }

    private fun applyFilter() {
        val filtered = if (filterQuery.isBlank()) {
            allTabs
        } else {
            allTabs.filter {
                (it.title?.contains(filterQuery, ignoreCase = true) == true) ||
                (it.url?.contains(filterQuery, ignoreCase = true) == true)
            }
        }
        adapter.submitList(filtered, currentTab)
    }

    private fun showOverflowMenu(anchor: View) {
        val popupView = LayoutInflater.from(context).inflate(R.layout.popup_chrome_tabs_menu, null)
        val popup = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            elevation = 16f
        }

        popupView.findViewById<View>(R.id.btnTabsMenuNewTab).setOnClickListener {
            popup.dismiss()
            hide()
            onNewTabListener?.invoke()
        }

        popupView.findViewById<View>(R.id.btnTabsMenuIncognito).setOnClickListener {
            popup.dismiss()
            hide()
            (context as? MainActivity)?.toggleIncognitoMode()
        }

        popupView.findViewById<View>(R.id.btnTabsMenuNewGroup).setOnClickListener {
            popup.dismiss()
            vb.btnSegmentGroups.performClick()
        }

        popupView.findViewById<View>(R.id.btnTabsMenuCloseAll).setOnClickListener {
            popup.dismiss()
            hide()
            onCloseAllTabsListener?.invoke()
        }

        popupView.findViewById<View>(R.id.btnTabsMenuSelectTabs).setOnClickListener {
            popup.dismiss()
        }

        popupView.findViewById<View>(R.id.btnTabsMenuDeleteBrowsingData).setOnClickListener {
            popup.dismiss()
            hide()
            (context as? MainActivity)?.showHistory()
        }

        popupView.findViewById<View>(R.id.btnTabsMenuSettings).setOnClickListener {
            popup.dismiss()
            hide()
            (context as? MainActivity)?.showSettings()
        }

        popup.showAsDropDown(anchor, 0, 4, Gravity.END)
    }
}

class ChromeTabGridAdapter(
    private val onTabSelected: (WebTabState) -> Unit,
    private val onTabClose: (WebTabState) -> Unit
) : RecyclerView.Adapter<ChromeTabGridAdapter.ViewHolder>() {

    private val items = mutableListOf<WebTabState>()
    private var activeTab: WebTabState? = null

    fun submitList(tabs: List<WebTabState>, current: WebTabState?) {
        items.clear()
        items.addAll(tabs)
        activeTab = current
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: LinearLayout = view.findViewById(R.id.llTabCardRoot)
        val header: LinearLayout = view.findViewById(R.id.llTabCardHeader)
        val ivFavicon: ImageView = view.findViewById(R.id.ivTabFavicon)
        val tvTitle: TextView = view.findViewById(R.id.tvTabTitle)
        val btnClose: ImageButton = view.findViewById(R.id.btnTabClose)
        val ivThumbnail: ImageView = view.findViewById(R.id.ivTabThumbnail)
        val llPlaceholder: LinearLayout = view.findViewById(R.id.llThumbnailPlaceholder)
        val tvPlaceholderDomain: TextView = view.findViewById(R.id.tvPlaceholderDomain)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chrome_tab_grid, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tab = items[position]
        val isActive = (tab == activeTab)
        val context = holder.itemView.context

        val displayTitle = if (!tab.title.isNullOrBlank()) tab.title else "New Tab"
        holder.tvTitle.text = displayTitle

        if (isActive) {
            holder.root.setBackgroundResource(R.drawable.bg_tab_card_active)
            holder.header.setBackgroundResource(R.drawable.bg_tab_header_active)
            holder.tvTitle.setTextColor(Color.parseColor("#041E49"))
            holder.btnClose.setColorFilter(Color.parseColor("#041E49"))
        } else {
            holder.root.setBackgroundResource(R.drawable.bg_tab_card_normal)
            holder.header.setBackgroundResource(R.drawable.bg_tab_header_normal)
            holder.tvTitle.setTextColor(ContextCompat.getColor(context, R.color.day_night_text_color_contrast))
            holder.btnClose.setColorFilter(ContextCompat.getColor(context, R.color.day_night_text_secondary))
        }

        // Thumbnail
        if (tab.thumbnail != null) {
            holder.ivThumbnail.setImageBitmap(tab.thumbnail)
            holder.ivThumbnail.visibility = View.VISIBLE
            holder.llPlaceholder.visibility = View.GONE
        } else {
            holder.ivThumbnail.visibility = View.GONE
            holder.llPlaceholder.visibility = View.VISIBLE
            val host = try {
                Uri.parse(tab.url ?: "").host ?: tab.url ?: ""
            } catch (_: Exception) {
                tab.url ?: ""
            }
            holder.tvPlaceholderDomain.text = host
        }

        // Favicon
        holder.itemView.tag = tab
        holder.ivFavicon.setImageResource(R.drawable.ic_tab_default_favicon)
        val scope = (context as? AppCompatActivity)?.lifecycleScope
        scope?.launch(Dispatchers.Main) {
            try {
                val favicon = FaviconsPool.get(tab.url)
                if (holder.itemView.tag == tab && favicon != null) {
                    holder.ivFavicon.setImageBitmap(favicon)
                }
            } catch (_: Exception) {}
        }

        holder.root.setOnClickListener { onTabSelected(tab) }
        holder.btnClose.setOnClickListener { onTabClose(tab) }
    }

    override fun getItemCount(): Int = items.size
}
