package com.bansify.internet.browser.tabs

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bansify.internet.R
import com.bansify.internet.activity.main.MainActivity
import com.bansify.internet.model.WebTabState

class TabsRowDialog(
    private val activity: MainActivity,
    private val onTabSelected: (WebTabState) -> Unit,
    private val onNewTabRequested: () -> Unit,
    private val onCloseTabRequested: (WebTabState) -> Unit,
    private val onCloseAllTabsRequested: () -> Unit
) : Dialog(activity, R.style.TvSettingsDrawerDialog) {

    private lateinit var tvTabsHeaderTitle: TextView
    private lateinit var btnNewTabAction: Button
    private lateinit var btnCloseAllTabs: Button
    private lateinit var btnDoneTabs: ImageButton
    private lateinit var ibCloseSelectedTab: ImageButton
    private lateinit var ibAddNewTabFab: ImageButton
    private lateinit var rvTabsRow: RecyclerView
    private lateinit var adapter: TabsRowAdapter

    private var focusedTab: WebTabState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_tabs_row)

        window?.apply {
            val dm = activity.resources.displayMetrics
            val popupWidth = (dm.widthPixels * 0.45f).toInt().coerceIn(400, 800)
            setGravity(Gravity.END)
            setLayout(
                popupWidth,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        initViews()
        setupRecyclerView()
        setupActions()
    }

    private fun initViews() {
        tvTabsHeaderTitle = findViewById(R.id.tvTabsHeaderTitle)
        btnNewTabAction = findViewById(R.id.btnNewTabAction)
        btnCloseAllTabs = findViewById(R.id.btnCloseAllTabs)
        btnDoneTabs = findViewById(R.id.btnDoneTabs)
        ibCloseSelectedTab = findViewById(R.id.ibCloseSelectedTab)
        ibAddNewTabFab = findViewById(R.id.ibAddNewTabFab)
        rvTabsRow = findViewById(R.id.rvTabsRow)
    }

    private fun setupRecyclerView() {
        val tabs = activity.tabsModel.tabsStates
        val currentTab = activity.tabsModel.currentTab.value
        focusedTab = currentTab

        updateTitle(tabs.size)

        val layoutManager = androidx.recyclerview.widget.GridLayoutManager(context, 2)
        rvTabsRow.layoutManager = layoutManager
        adapter = TabsRowAdapter(
            tabs = tabs.toMutableList(),
            currentTab = currentTab,
            onTabClick = { tab ->
                dismiss()
                onTabSelected(tab)
            },
            onCloseTabClick = { tab ->
                onCloseTabRequested(tab)
                refreshData()
            },
            onTabFocused = { tab ->
                focusedTab = tab
            }
        )
        rvTabsRow.adapter = adapter

        // Focus active tab
        val activeIndex = tabs.indexOf(currentTab)
        if (activeIndex >= 0) {
            rvTabsRow.post {
                layoutManager.scrollToPosition(activeIndex)
                val activeView = layoutManager.findViewByPosition(activeIndex)
                activeView?.requestFocus()
            }
        }
    }

    private fun updateTitle(count: Int) {
        tvTabsHeaderTitle.text = "Tabs ($count)"
    }

    fun refreshData() {
        val tabs = activity.tabsModel.tabsStates
        val currentTab = activity.tabsModel.currentTab.value
        updateTitle(tabs.size)
        adapter.updateTabs(tabs, currentTab)
        if (tabs.isEmpty()) {
            dismiss()
        }
    }

    private fun setupActions() {
        btnNewTabAction.setOnClickListener {
            dismiss()
            onNewTabRequested()
        }

        ibAddNewTabFab.setOnClickListener {
            dismiss()
            onNewTabRequested()
        }

        ibCloseSelectedTab.setOnClickListener {
            val tabToClose = focusedTab ?: activity.tabsModel.currentTab.value
            if (tabToClose != null) {
                onCloseTabRequested(tabToClose)
                refreshData()
            }
        }

        btnCloseAllTabs.setOnClickListener {
            dismiss()
            onCloseAllTabsRequested()
        }

        btnDoneTabs.setOnClickListener {
            dismiss()
        }
    }
}
