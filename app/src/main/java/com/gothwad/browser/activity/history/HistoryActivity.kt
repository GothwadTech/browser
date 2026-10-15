package com.gothwad.browser.activity.history

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gothwad.browser.R
import com.gothwad.browser.databinding.ActivityHistoryBinding
import com.gothwad.browser.model.HistoryItem
import com.gothwad.browser.singleton.AppDatabase
import com.gothwad.browser.utils.Utils
import com.gothwad.browser.utils.setupAsSidebar
import com.gothwad.browser.utils.VoiceSearchHelper
import com.gothwad.browser.utils.activemodel.ActiveModelsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity(), AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {

    private lateinit var vb: ActivityHistoryBinding
    private var adapter: HistoryAdapter? = null
    private lateinit var historyModel: HistoryModel
    private val voiceSearchHelper = VoiceSearchHelper(
        this,
        VOICE_SEARCH_REQUEST_CODE,
        VOICE_SEARCH_PERMISSIONS_REQUEST_CODE
    )

    private val onListScrollListener = object : AbsListView.OnScrollListener {
        override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {}

        override fun onScroll(view: AbsListView, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
            if (totalItemCount != 0 && firstVisibleItem + visibleItemCount >= totalItemCount - 1 && historyModel.searchQuery.isEmpty()) {
                historyModel.loadItems(false, adapter?.realCount ?: 0)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupAsSidebar(false)
        vb = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(vb.root)

        historyModel = ActiveModelsRepository.get(HistoryModel::class, this)

        setupTopBar()
        setupSidebar()
        setupListView()
        observeHistory()

        historyModel.loadItems(false)
    }

    private fun setupTopBar() {
        vb.ibHistoryBack.setOnClickListener { finish() }

        vb.etSearchHistory.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty()
                vb.ibClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                adapter?.erase()
                historyModel.searchQuery = query
                historyModel.loadItems(true)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        vb.ibClearSearch.setOnClickListener {
            vb.etSearchHistory.text.clear()
        }

        vb.ibVoiceSearch.setOnClickListener {
            initiateVoiceSearch()
        }

        vb.btnDeleteSelected.setOnClickListener {
            showDeleteDialog(false)
        }

        vb.btnCancelSelection.setOnClickListener {
            adapter?.isMultiselectMode = false
            updateSelectionBar()
        }
    }

    private fun setupSidebar() {
        vb.llNavChromeHistory.setOnClickListener {
            setFilter(HistoryModel.FILTER_ALL)
        }

        vb.btnFilterAll.setOnClickListener { setFilter(HistoryModel.FILTER_ALL) }
        vb.btnFilterToday.setOnClickListener { setFilter(HistoryModel.FILTER_TODAY) }
        vb.btnFilterYesterday.setOnClickListener { setFilter(HistoryModel.FILTER_YESTERDAY) }
        vb.btnFilterOlder.setOnClickListener { setFilter(HistoryModel.FILTER_OLDER) }

        vb.btnClearHistory.setOnClickListener {
            showClearBrowsingDataDialog()
        }
    }

    private fun setFilter(filter: Int) {
        historyModel.filterMode = filter
        updateFilterUI(filter)
        adapter?.erase()
        historyModel.loadItems(true)
    }

    private fun updateFilterUI(filter: Int) {
        val activeColor = androidx.core.content.ContextCompat.getColor(this, R.color.app_accent)
        val inactiveColor = androidx.core.content.ContextCompat.getColor(this, R.color.app_on_surface_muted)

        vb.btnFilterAll.setTextColor(if (filter == HistoryModel.FILTER_ALL) activeColor else inactiveColor)
        vb.btnFilterToday.setTextColor(if (filter == HistoryModel.FILTER_TODAY) activeColor else inactiveColor)
        vb.btnFilterYesterday.setTextColor(if (filter == HistoryModel.FILTER_YESTERDAY) activeColor else inactiveColor)
        vb.btnFilterOlder.setTextColor(if (filter == HistoryModel.FILTER_OLDER) activeColor else inactiveColor)
    }

    private fun setupListView() {
        adapter = HistoryAdapter()
        adapter?.onDeleteClickListener = { item ->
            deleteSingleItem(item)
        }
        adapter?.onItemMenuClickListener = { item, anchorView ->
            showItemContextMenu(item, anchorView)
        }

        vb.listView.adapter = adapter
        vb.listView.setOnScrollListener(onListScrollListener)
        vb.listView.onItemClickListener = this
        vb.listView.onItemLongClickListener = this
    }

    private fun observeHistory() {
        historyModel.lastLoadedItems.subscribe(this, false) { items ->
            if (items.isEmpty()) {
                if (adapter?.realCount == 0L) {
                    vb.llEmptyHistory.visibility = View.VISIBLE
                    vb.listView.visibility = View.GONE
                }
                updateSubtitle()
                return@subscribe
            }
            vb.llEmptyHistory.visibility = View.GONE
            vb.listView.visibility = View.VISIBLE
            adapter?.addItems(items)
            updateSubtitle()
        }
    }

    private fun updateSubtitle() {
        val count = adapter?.realCount ?: 0
        vb.tvHistorySubtitle.text = "$count visits recorded"
    }

    private fun deleteSingleItem(item: HistoryItem) {
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                AppDatabase.db.historyDao().delete(item)
                adapter?.remove(item)
                updateSubtitle()
                if (adapter?.realCount == 0L) {
                    vb.llEmptyHistory.visibility = View.VISIBLE
                    vb.listView.visibility = View.GONE
                }
                Toast.makeText(this@HistoryActivity, "Removed from history", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@HistoryActivity, "Error deleting item", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showItemContextMenu(item: HistoryItem, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Open in browser")
        popup.menu.add(0, 2, 1, "Open in new tab")
        popup.menu.add(0, 3, 2, "Copy link")
        popup.menu.add(0, 4, 3, "Remove from history")

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    val resultIntent = Intent()
                    resultIntent.putExtra(KEY_URL, item.url)
                    resultIntent.putExtra(KEY_OPEN_IN_NEW_TAB, false)
                    setResult(RESULT_OK, resultIntent)
                    finish()
                    true
                }
                2 -> {
                    val resultIntent = Intent()
                    resultIntent.putExtra(KEY_URL, item.url)
                    resultIntent.putExtra(KEY_OPEN_IN_NEW_TAB, true)
                    setResult(RESULT_OK, resultIntent)
                    finish()
                    true
                }
                3 -> {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("URL", item.url)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, this.getString(R.string.history_link_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                    true
                }
                4 -> {
                    deleteSingleItem(item)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        val hiv = view as? HistoryItemView ?: return
        val hi = hiv.historyItem ?: return
        if (hi.isDateHeader) return

        if (adapter?.isMultiselectMode == true) {
            hiv.setSelection(!hi.selected)
            updateSelectionBar()
        } else {
            val resultIntent = Intent()
            resultIntent.putExtra(KEY_URL, hi.url)
            resultIntent.putExtra(KEY_OPEN_IN_NEW_TAB, false)
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    override fun onItemLongClick(adapterView: AdapterView<*>, view: View, i: Int, l: Long): Boolean {
        val hiv = view as? HistoryItemView ?: return false
        val hi = hiv.historyItem ?: return false
        if (hi.isDateHeader) return false

        if (adapter?.isMultiselectMode == false) {
            adapter?.isMultiselectMode = true
            hiv.setSelection(true)
            updateSelectionBar()
            return true
        }
        return false
    }

    private fun updateSelectionBar() {
        val selection = adapter?.selectedItems ?: emptyList()
        if (selection.isEmpty()) {
            vb.llSelectionBar.visibility = View.GONE
            vb.llHistorySearchContainer.visibility = View.VISIBLE
        } else {
            vb.tvSelectedCount.text = "${selection.size} selected"
            vb.llSelectionBar.visibility = View.VISIBLE
            vb.llHistorySearchContainer.visibility = View.GONE
        }
    }

    override fun onBackPressed() {
        if (adapter?.isMultiselectMode == true) {
            adapter?.isMultiselectMode = false
            updateSelectionBar()
            return
        }
        super.onBackPressed()
    }

    private fun showClearBrowsingDataDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.history_clear_browsing_data)
            .setMessage(R.string.history_delete_all_confirm)
            .setPositiveButton("Clear Everything") { _, _ ->
                lifecycleScope.launch(Dispatchers.Main) {
                    try {
                        AppDatabase.db.historyDao().deleteWhereTimeLessThan(Long.MAX_VALUE)
                        adapter?.erase()
                        CookieManager.getInstance().removeAllCookies(null)
                        WebStorage.getInstance().deleteAllData()
                        WebView(this@HistoryActivity).clearCache(true)
                        updateSubtitle()
                        vb.llEmptyHistory.visibility = View.VISIBLE
                        vb.listView.visibility = View.GONE
                        Toast.makeText(this@HistoryActivity, "Browsing history and data cleared", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@HistoryActivity, "History cleared", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteDialog(deleteAll: Boolean) {
        val selected = adapter?.selectedItems ?: emptyList()
        if (adapter?.items?.isEmpty() == true || (selected.isEmpty() && !deleteAll)) return

        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage(if (deleteAll) R.string.msg_delete_history_all else R.string.msg_delete_history)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch(Dispatchers.Main) {
                    if (deleteAll) {
                        AppDatabase.db.historyDao().deleteWhereTimeLessThan(Long.MAX_VALUE)
                        adapter?.erase()
                    } else {
                        AppDatabase.db.historyDao().delete(*selected.toTypedArray())
                        adapter?.remove(selected)
                    }
                    adapter?.isMultiselectMode = false
                    updateSelectionBar()
                    updateSubtitle()
                    if (adapter?.realCount == 0L) {
                        vb.llEmptyHistory.visibility = View.VISIBLE
                        vb.listView.visibility = View.GONE
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun initiateVoiceSearch() {
        voiceSearchHelper.initiateVoiceSearch(object : VoiceSearchHelper.Callback {
            override fun onPartialResult(text: String) {
                vb.etSearchHistory.setText(text)
                vb.etSearchHistory.setSelection(text.length)
            }

            override fun onResult(text: String?) {
                if (text.isNullOrBlank()) {
                    Utils.showToast(this@HistoryActivity, getString(R.string.can_not_recognize))
                    return
                }
                vb.etSearchHistory.setText(text)
                vb.etSearchHistory.setSelection(text.length)
            }
        })
    }

    override fun onDestroy() {
        voiceSearchHelper.destroy()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_SEARCH && event.action == KeyEvent.ACTION_UP) {
            initiateVoiceSearch()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!voiceSearchHelper.processActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (!voiceSearchHelper.processPermissionsResult(requestCode, permissions, grantResults)) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    companion object {
        private const val VOICE_SEARCH_REQUEST_CODE = 10001
        private const val VOICE_SEARCH_PERMISSIONS_REQUEST_CODE = 10002

        const val KEY_URL = "url"
        const val KEY_OPEN_IN_NEW_TAB = "open_in_new_tab"
    }
}
