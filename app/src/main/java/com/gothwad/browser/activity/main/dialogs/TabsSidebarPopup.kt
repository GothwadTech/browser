package com.gothwad.browser.activity.main.dialogs

import android.graphics.Color
import com.gothwad.browser.utils.ListDiff
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.browser.R
import com.gothwad.browser.activity.main.MainActivity
import com.gothwad.browser.model.WebTabState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TabsSidebarPopup(
    private val activity: MainActivity,
    private val onTabSelected: (WebTabState) -> Unit,
    private val onNewTabRequested: () -> Unit,
    private val onCloseTabRequested: (WebTabState) -> Unit,
    private val onCloseAllTabsRequested: () -> Unit
) {

    private val popupWindow: PopupWindow
    private val rootContainer: FrameLayout
    private val contentView: View

    private lateinit var btnTabsBack: ImageButton
    private lateinit var tvTabsTitle: TextView
    private lateinit var btnAddNewTab: Button
    private lateinit var btnCloseAllTabs: Button
    private lateinit var rvTabsGrid: RecyclerView
    private lateinit var adapter: TabsSidebarAdapter

    private var tabsList = mutableListOf<WebTabState>()

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

        contentView = LayoutInflater.from(activity).inflate(R.layout.dialog_sidebar_tabs, rootContainer, true)

        val popupWidth = SidebarHelper.calculateSidebarWidth(activity)

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
            animationStyle = R.style.SideDrawerAnimation
        }

        bindViews()
        setupListeners()
        setupRecyclerView()
    }

    private fun bindViews() {
        btnTabsBack = contentView.findViewById(R.id.btnTabsBack)
        tvTabsTitle = contentView.findViewById(R.id.tvTabsTitle)
        btnAddNewTab = contentView.findViewById(R.id.btnAddNewTab)
        btnCloseAllTabs = contentView.findViewById(R.id.btnCloseAllTabs)
        rvTabsGrid = contentView.findViewById(R.id.rvTabsGrid)

        contentView.findViewById<View>(R.id.vTabsBackdrop).setOnClickListener {
            dismiss()
        }
    }

    private fun setupRecyclerView() {
        val layoutManager = GridLayoutManager(activity, 2)
        rvTabsGrid.layoutManager = layoutManager

        adapter = TabsSidebarAdapter(
            initialTabs = tabsList,
            currentTab = activity.tabsModel.currentTab.value,
            onTabClick = { tab ->
                dismiss()
                onTabSelected(tab)
            },
            onCloseTabClick = { tab ->
                onCloseTabRequested(tab)
                refreshData()
            }
        )
        rvTabsGrid.adapter = adapter
    }

    private fun setupListeners() {
        btnTabsBack.setOnClickListener { dismiss() }

        btnAddNewTab.setOnClickListener {
            dismiss()
            onNewTabRequested()
        }

        btnCloseAllTabs.setOnClickListener {
            dismiss()
            onCloseAllTabsRequested()
        }
    }

    fun show(anchorView: View? = null) {
        val decorView = activity.window.decorView
        val header = activity.findViewById<View>(R.id.rlActionBar) ?: anchorView ?: decorView

        val loc = IntArray(2)
        header.getLocationInWindow(loc)
        if (loc[1] == 0) {
            header.getLocationOnScreen(loc)
        }
        val headerBottom = loc[1] + header.height

        val screenWidth = if (decorView.width > 0) decorView.width else activity.resources.displayMetrics.widthPixels
        val screenHeight = if (decorView.height > 0) decorView.height else activity.resources.displayMetrics.heightPixels

        val popupWidth = SidebarHelper.calculateSidebarWidth(activity)
        val popupHeight = (screenHeight - headerBottom).coerceAtLeast(100)

        popupWindow.width = popupWidth
        popupWindow.height = popupHeight
        popupWindow.isClippingEnabled = false

        val xPos = screenWidth - popupWidth
        popupWindow.showAtLocation(decorView, Gravity.TOP or Gravity.START, xPos, headerBottom)

        refreshData()

        val activeIndex = tabsList.indexOf(activity.tabsModel.currentTab.value)
        if (activeIndex >= 0) {
            rvTabsGrid.post {
                rvTabsGrid.scrollToPosition(activeIndex)
                val activeView = rvTabsGrid.layoutManager?.findViewByPosition(activeIndex)
                activeView?.requestFocus()
            }
        } else {
            contentView.post {
                btnTabsBack.requestFocus()
            }
        }
    }

    fun dismiss() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }

    fun refreshData() {
        val tabs = activity.tabsModel.tabsStates
        val currentTab = activity.tabsModel.currentTab.value

        tabsList.clear()
        tabsList.addAll(tabs)
        tvTabsTitle.text = "Open Tabs (${tabsList.size})"

        adapter.update(tabsList, currentTab)
    }
}

class TabsSidebarAdapter(
    initialTabs: List<WebTabState>,
    private var currentTab: WebTabState?,
    private val onTabClick: (WebTabState) -> Unit,
    private val onCloseTabClick: (WebTabState) -> Unit
) : RecyclerView.Adapter<TabsSidebarAdapter.ViewHolder>() {

    private val tabs = initialTabs.toMutableList()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: LinearLayout = view.findViewById(R.id.llTabItemRoot)
        val ivFavicon: ImageView = view.findViewById(R.id.ivTabFavicon)
        val tvActiveBadge: TextView = view.findViewById(R.id.tvTabActiveBadge)
        val btnClose: ImageButton = view.findViewById(R.id.btnTabClose)
        val tvTitle: TextView = view.findViewById(R.id.tvTabTitle)
        val tvUrl: TextView = view.findViewById(R.id.tvTabUrl)
    }

    fun update(newTabs: List<WebTabState>, newCurrentTab: WebTabState?) {
        val previousCurrent = currentTab
        currentTab = newCurrentTab
        // The active tab is part of the row content, so the old and the new side get their own
        // signature (otherwise switching tabs would not repaint the highlight).
        ListDiff.submit(
            this,
            tabs,
            newTabs,
            idOf = { it.id },
            contentOfOld = { "${it.title}|${it.url}|${if (it === previousCurrent) 1 else 0}" },
            contentOfNew = { "${it.title}|${it.url}|${if (it === newCurrentTab) 1 else 0}" }
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sidebar_tab, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tab = tabs[position]
        val isActive = tab == currentTab

        holder.tvTitle.text = if (tab.title.isNullOrBlank()) "New Tab" else tab.title
        holder.tvUrl.text = if (tab.url.isNullOrBlank()) "about:blank" else tab.url

        holder.tvActiveBadge.visibility = if (isActive) View.VISIBLE else View.GONE
        holder.root.isSelected = isActive

        holder.itemView.tag = tab
        holder.ivFavicon.setImageResource(R.drawable.ic_tab_default_favicon)

        val scope = (holder.itemView.context as? AppCompatActivity)?.lifecycleScope
        scope?.launch(Dispatchers.Main) {
            try {
                val favicon = com.gothwad.browser.singleton.FaviconsPool.get(tab.url)
                if (holder.itemView.tag == tab && favicon != null) {
                    holder.ivFavicon.setImageBitmap(favicon)
                }
            } catch (_: Exception) {}
        }

        holder.root.setOnClickListener { onTabClick(tab) }
        holder.btnClose.setOnClickListener { onCloseTabClick(tab) }

        holder.btnClose.isFocusable = true
        holder.btnClose.isClickable = true

        holder.root.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(100).start()
                v.elevation = 6f
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                v.elevation = 0f
            }
        }

        holder.btnClose.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.2f).scaleY(1.2f).setDuration(100).start()
                (v as? ImageButton)?.setColorFilter(Color.WHITE)
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                (v as? ImageButton)?.clearColorFilter()
            }
        }

        holder.root.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    holder.btnClose.requestFocus()
                    return@setOnKeyListener true
                }
                if (keyCode == KeyEvent.KEYCODE_FORWARD_DEL || keyCode == KeyEvent.KEYCODE_DEL) {
                    onCloseTabClick(tab)
                    return@setOnKeyListener true
                }
            }
            false
        }

        holder.btnClose.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    holder.root.requestFocus()
                    return@setOnKeyListener true
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                    onCloseTabClick(tab)
                    return@setOnKeyListener true
                }
            }
            false
        }
    }

    override fun getItemCount(): Int = tabs.size
}
