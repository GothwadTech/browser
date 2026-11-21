package com.bansify.internet.activity.main.dialogs

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bansify.internet.Config
import com.bansify.internet.R
import com.bansify.internet.activity.main.MainActivity
import com.bansify.internet.activity.main.TabsModel
import com.bansify.internet.model.WebTabState
import com.bansify.internet.singleton.FaviconsPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TabSearchSidebarPopup(
    private val activity: MainActivity,
    private val onTabSelected: (WebTabState) -> Unit,
    private val onCloseTab: (WebTabState) -> Unit,
    private val onReopenTab: (TabsModel.RecentlyClosedTab) -> Unit,
    private val onNewTab: () -> Unit
) {
    private val popupWindow: PopupWindow
    private val contentView: View
    private val rootContainer: FrameLayout
    private val adapter: TabSearchAdapter
    private var allOpenTabs: List<WebTabState> = emptyList()
    private var allClosedTabs: List<TabsModel.RecentlyClosedTab> = emptyList()
    private var currentFilter: String = ""

    init {
        rootContainer = object : FrameLayout(activity) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_BACK,
                        KeyEvent.KEYCODE_ESCAPE,
                        KeyEvent.KEYCODE_BUTTON_B -> {
                            dismiss()
                            return true
                        }
                    }
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        contentView = LayoutInflater.from(activity).inflate(
            R.layout.dialog_tab_search_dropdown,
            rootContainer,
            true
        )

        val popupWidth = SidebarHelper.calculateLeftSidebarWidth(activity)

        popupWindow = PopupWindow(
            rootContainer,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 24f
            animationStyle = R.style.SideDrawerLeftAnimation
        }

        adapter = TabSearchAdapter(
            currentTab = activity.tabsModel.currentTab.value,
            onOpenTabClick = { tab ->
                dismiss()
                onTabSelected(tab)
            },
            onCloseOpenTabClick = { tab ->
                onCloseTab(tab)
                refreshData()
            },
            onClosedTabClick = { closedTab ->
                dismiss()
                onReopenTab(closedTab)
            }
        )

        setupViews()
    }

    private fun setupViews() {
        val btnBack: ImageButton = contentView.findViewById(R.id.btnTabSearchBack)
        val btnNewTab: Button = contentView.findViewById(R.id.btnTabSearchNewTab)
        val etSearch: EditText = contentView.findViewById(R.id.etTabSearchInput)
        val rvList: RecyclerView = contentView.findViewById(R.id.rvTabSearchResults)
        val vBackdrop: View = contentView.findViewById(R.id.vTabSearchBackdrop)

        vBackdrop.setOnClickListener { dismiss() }
        btnBack.setOnClickListener { dismiss() }

        btnNewTab.setOnClickListener {
            dismiss()
            onNewTab()
        }

        btnNewTab.setOnFocusChangeListener { v, hasFocus ->
            v.animate().scaleX(if (hasFocus) 1.05f else 1.0f).scaleY(if (hasFocus) 1.05f else 1.0f).setDuration(100).start()
        }

        rvList.layoutManager = LinearLayoutManager(activity)
        rvList.adapter = adapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentFilter = s?.toString()?.trim() ?: ""
                filterAndSubmitList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    fun show() {
        if (popupWindow.isShowing) return

        refreshData()

        val decorView = activity.window.decorView
        val header = activity.findViewById<View>(R.id.rlActionBar) ?: decorView

        val loc = IntArray(2)
        header.getLocationInWindow(loc)
        if (loc[1] == 0) {
            header.getLocationOnScreen(loc)
        }
        val headerBottom = loc[1] + header.height

        val screenHeight = if (decorView.height > 0) decorView.height else activity.resources.displayMetrics.heightPixels
        val popupWidth = SidebarHelper.calculateLeftSidebarWidth(activity)
        val popupHeight = (screenHeight - headerBottom).coerceAtLeast(100)

        popupWindow.width = popupWidth
        popupWindow.height = popupHeight
        popupWindow.isClippingEnabled = false

        popupWindow.showAtLocation(
            decorView,
            android.view.Gravity.TOP or android.view.Gravity.START,
            0,
            headerBottom
        )

        contentView.post {
            contentView.findViewById<View>(R.id.etTabSearchInput)?.requestFocus()
        }
    }

    fun dismiss() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }

    private fun refreshData() {
        allOpenTabs = activity.tabsModel.tabsStates.toList()
        allClosedTabs = activity.tabsModel.recentlyClosedTabs.toList()
        filterAndSubmitList()
    }

    private fun filterAndSubmitList() {
        val items = mutableListOf<TabSearchItem>()

        val filteredOpen = if (currentFilter.isEmpty()) {
            allOpenTabs
        } else {
            allOpenTabs.filter {
                it.title.contains(currentFilter, ignoreCase = true) ||
                        it.url.contains(currentFilter, ignoreCase = true)
            }
        }

        if (filteredOpen.isNotEmpty()) {
            items.add(TabSearchItem.Header("OPEN TABS (${filteredOpen.size})"))
            filteredOpen.forEach { tab ->
                items.add(TabSearchItem.OpenTab(tab))
            }
        }

        val filteredClosed = if (currentFilter.isEmpty()) {
            allClosedTabs
        } else {
            allClosedTabs.filter {
                it.title.contains(currentFilter, ignoreCase = true) ||
                        it.url.contains(currentFilter, ignoreCase = true)
            }
        }

        if (filteredClosed.isNotEmpty()) {
            items.add(TabSearchItem.Header("RECENTLY CLOSED (${filteredClosed.size})"))
            filteredClosed.forEach { closed ->
                items.add(TabSearchItem.ClosedTab(closed))
            }
        }

        adapter.submitList(items, activity.tabsModel.currentTab.value)
    }

    sealed class TabSearchItem {
        data class Header(val title: String) : TabSearchItem()
        data class OpenTab(val tab: WebTabState) : TabSearchItem()
        data class ClosedTab(val closed: TabsModel.RecentlyClosedTab) : TabSearchItem()
    }

    class TabSearchAdapter(
        private var currentTab: WebTabState?,
        private val onOpenTabClick: (WebTabState) -> Unit,
        private val onCloseOpenTabClick: (WebTabState) -> Unit,
        private val onClosedTabClick: (TabsModel.RecentlyClosedTab) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var items = listOf<TabSearchItem>()

        companion object {
            private const val TYPE_HEADER = 0
            private const val TYPE_ROW = 1
        }

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is TabSearchItem.Header -> TYPE_HEADER
                else -> TYPE_ROW
            }
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                val view = inflater.inflate(R.layout.item_tab_search_header, parent, false)
                HeaderViewHolder(view)
            } else {
                val view = inflater.inflate(R.layout.item_tab_search_row, parent, false)
                RowViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is TabSearchItem.Header -> (holder as HeaderViewHolder).bind(item)
                is TabSearchItem.OpenTab -> (holder as RowViewHolder).bindOpenTab(item.tab, currentTab, onOpenTabClick, onCloseOpenTabClick)
                is TabSearchItem.ClosedTab -> (holder as RowViewHolder).bindClosedTab(item.closed, onClosedTabClick)
            }
        }

        fun submitList(newItems: List<TabSearchItem>, newCurrentTab: WebTabState?) {
            val oldItems = items
            val oldCurrent = currentTab
            items = newItems
            currentTab = newCurrentTab

            val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = oldItems.size
                override fun getNewListSize(): Int = newItems.size

                override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                    val old = oldItems[oldPos]
                    val new = newItems[newPos]
                    return when {
                        old is TabSearchItem.Header && new is TabSearchItem.Header -> old.title == new.title
                        old is TabSearchItem.OpenTab && new is TabSearchItem.OpenTab -> old.tab.id == new.tab.id
                        old is TabSearchItem.ClosedTab && new is TabSearchItem.ClosedTab -> old.closed.url == new.closed.url
                        else -> false
                    }
                }

                override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                    val old = oldItems[oldPos]
                    val new = newItems[newPos]
                    if (old is TabSearchItem.OpenTab && new is TabSearchItem.OpenTab) {
                        val wasActive = old.tab == oldCurrent
                        val isActive = new.tab == newCurrentTab
                        return old.tab.title == new.tab.title &&
                                old.tab.url == new.tab.url &&
                                wasActive == isActive
                    }
                    return old == new
                }
            })
            diffResult.dispatchUpdatesTo(this)
        }

        class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val tvTitle: TextView = view.findViewById(R.id.tvSectionHeaderTitle)
            fun bind(header: TabSearchItem.Header) {
                tvTitle.text = header.title
            }
        }

        class RowViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val ivFavicon: ImageView = view.findViewById(R.id.ivTabSearchFavicon)
            private val tvTitle: TextView = view.findViewById(R.id.tvTabSearchTitle)
            private val tvUrl: TextView = view.findViewById(R.id.tvTabSearchUrl)
            private val tvActiveBadge: TextView = view.findViewById(R.id.tvTabSearchActiveBadge)
            private val ibClose: ImageButton = view.findViewById(R.id.ibTabSearchClose)

            fun bindOpenTab(
                tab: WebTabState,
                currentTab: WebTabState?,
                onClick: (WebTabState) -> Unit,
                onClose: (WebTabState) -> Unit
            ) {
                val isActive = tab == currentTab
                val isHome = tab.url.isEmpty() ||
                        tab.url == Config.HOME_PAGE_URL ||
                        tab.url == Config.HOME_URL_ALIAS ||
                        tab.url == "about:blank" ||
                        tab.title.equals("Home Screen", ignoreCase = true) ||
                        tab.title.equals("Home", ignoreCase = true)

                if (isHome) {
                    tvTitle.text = itemView.context.getString(R.string.home_screen)
                    tvUrl.text = Config.HOME_PAGE_URL
                    ivFavicon.setImageResource(R.drawable.ic_home_grey_900_24dp)
                } else {
                    tvTitle.text = if (tab.title.isNotBlank()) tab.title else tab.url
                    tvUrl.text = tab.url
                    ivFavicon.setImageResource(R.drawable.ic_tab_default_favicon)

                    val appCompat = itemView.context as? AppCompatActivity
                    appCompat?.lifecycleScope?.launch(Dispatchers.Main) {
                        try {
                            val bitmap = FaviconsPool.get(tab.url)
                            if (bitmap != null) {
                                ivFavicon.setImageBitmap(bitmap)
                            }
                        } catch (_: Exception) {}
                    }
                }

                tvActiveBadge.isVisible = isActive
                ibClose.isVisible = true
                ibClose.isFocusable = true
                ibClose.isClickable = true

                itemView.setOnClickListener { onClick(tab) }
                ibClose.setOnClickListener { onClose(tab) }

                itemView.setOnFocusChangeListener { v, hasFocus ->
                    v.animate().scaleX(if (hasFocus) 1.02f else 1.0f).scaleY(if (hasFocus) 1.02f else 1.0f).setDuration(100).start()
                }

                ibClose.setOnFocusChangeListener { v, hasFocus ->
                    v.animate().scaleX(if (hasFocus) 1.2f else 1.0f).scaleY(if (hasFocus) 1.2f else 1.0f).setDuration(100).start()
                    if (hasFocus) {
                        ibClose.setColorFilter(Color.WHITE)
                    } else {
                        ibClose.clearColorFilter()
                    }
                }

                itemView.setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                            ibClose.requestFocus()
                            return@setOnKeyListener true
                        }
                        if (keyCode == KeyEvent.KEYCODE_FORWARD_DEL || keyCode == KeyEvent.KEYCODE_DEL) {
                            onClose(tab)
                            return@setOnKeyListener true
                        }
                    }
                    false
                }

                ibClose.setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                            itemView.requestFocus()
                            return@setOnKeyListener true
                        }
                        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                            onClose(tab)
                            return@setOnKeyListener true
                        }
                    }
                    false
                }
            }

            fun bindClosedTab(
                closed: TabsModel.RecentlyClosedTab,
                onClick: (TabsModel.RecentlyClosedTab) -> Unit
            ) {
                tvTitle.text = closed.title
                tvUrl.text = closed.url
                ivFavicon.setImageResource(R.drawable.ic_history_grey_900_36dp)
                tvActiveBadge.isVisible = false
                ibClose.isVisible = false

                val appCompat = itemView.context as? AppCompatActivity
                appCompat?.lifecycleScope?.launch(Dispatchers.Main) {
                    try {
                        val bitmap = FaviconsPool.get(closed.url)
                        if (bitmap != null) {
                            ivFavicon.setImageBitmap(bitmap)
                        }
                    } catch (_: Exception) {}
                }

                itemView.setOnClickListener { onClick(closed) }
                itemView.setOnFocusChangeListener { v, hasFocus ->
                    v.animate().scaleX(if (hasFocus) 1.02f else 1.0f).scaleY(if (hasFocus) 1.02f else 1.0f).setDuration(100).start()
                }
            }
        }
    }
}
