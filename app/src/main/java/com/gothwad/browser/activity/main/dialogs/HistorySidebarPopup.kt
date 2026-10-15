package com.gothwad.browser.activity.main.dialogs

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.browser.R
import com.gothwad.browser.activity.history.HistoryActivity
import com.gothwad.browser.activity.main.MainActivity
import com.gothwad.browser.activity.main.openInNewTab
import com.gothwad.browser.model.HistoryItem
import com.gothwad.browser.singleton.AppDatabase
import com.gothwad.browser.singleton.FaviconsPool
import com.gothwad.browser.utils.VoiceSearchHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class HistorySidebarListItem {
    data class Header(val dateString: String, val visitCount: Int) : HistorySidebarListItem()
    data class Entry(val item: HistoryItem) : HistorySidebarListItem()
}

class HistorySidebarPopup(
    private val activity: MainActivity,
    private val onHistoryItemSelected: (HistoryItem) -> Unit
) {

    private val popupWindow: PopupWindow
    private val rootContainer: FrameLayout
    private val contentView: View

    private lateinit var btnHistoryBack: ImageButton
    private lateinit var tvHistoryTitle: TextView
    private lateinit var btnHistoryExpand: ImageButton
    private lateinit var btnClearHistory: Button

    private lateinit var llHistoryPausedBanner: LinearLayout
    private lateinit var btnResumeHistory: Button

    private lateinit var llSelectionBar: LinearLayout
    private lateinit var tvSelectedCount: TextView
    private lateinit var btnSelectAll: Button
    private lateinit var btnDeleteSelected: Button
    private lateinit var btnCancelSelection: Button

    private lateinit var etHistorySearch: EditText
    private lateinit var ibClearHistorySearch: ImageButton
    private lateinit var ibVoiceSearch: ImageButton

    private lateinit var btnHistoryFilterAll: Button
    private lateinit var btnHistoryFilterToday: Button
    private lateinit var btnHistoryFilterYesterday: Button
    private lateinit var btnHistoryFilterOlder: Button

    private lateinit var rvHistory: RecyclerView
    private lateinit var pbHistoryLoading: ProgressBar
    private lateinit var llEmptyHistory: LinearLayout

    private val allHistoryItems = mutableListOf<HistoryItem>()
    private val currentListItems = mutableListOf<HistorySidebarListItem>()
    private val selectedItemIds = mutableSetOf<Long>()
    private var isMultiselectMode = false

    private lateinit var adapter: HistorySidebarDiffAdapter
    private var currentFilter = 0 // 0: All, 1: Today, 2: Yesterday, 3: Older
    private var searchQuery = ""

    init {
        rootContainer = object : FrameLayout(activity) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_BACK,
                        KeyEvent.KEYCODE_ESCAPE,
                        KeyEvent.KEYCODE_BUTTON_B -> {
                            if (isMultiselectMode) {
                                exitMultiselectMode()
                                return true
                            }
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

        contentView = LayoutInflater.from(activity).inflate(R.layout.dialog_sidebar_history, rootContainer, true)
        val popupWidth = SidebarHelper.calculateHistorySidebarWidth(activity)

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

        bindViews()
        setupListeners()
        setupRecyclerView()
    }

    private fun bindViews() {
        btnHistoryBack = contentView.findViewById(R.id.btnHistoryBack)
        tvHistoryTitle = contentView.findViewById(R.id.tvHistoryTitle)
        btnHistoryExpand = contentView.findViewById(R.id.btnHistoryExpand)
        btnClearHistory = contentView.findViewById(R.id.btnClearHistory)

        llHistoryPausedBanner = contentView.findViewById(R.id.llHistoryPausedBanner)
        btnResumeHistory = contentView.findViewById(R.id.btnResumeHistory)

        llSelectionBar = contentView.findViewById(R.id.llSelectionBar)
        tvSelectedCount = contentView.findViewById(R.id.tvSelectedCount)
        btnSelectAll = contentView.findViewById(R.id.btnSelectAll)
        btnDeleteSelected = contentView.findViewById(R.id.btnDeleteSelected)
        btnCancelSelection = contentView.findViewById(R.id.btnCancelSelection)

        etHistorySearch = contentView.findViewById(R.id.etHistorySearch)
        ibClearHistorySearch = contentView.findViewById(R.id.ibClearHistorySearch)
        ibVoiceSearch = contentView.findViewById(R.id.ibVoiceSearch)

        btnHistoryFilterAll = contentView.findViewById(R.id.btnHistoryFilterAll)
        btnHistoryFilterToday = contentView.findViewById(R.id.btnHistoryFilterToday)
        btnHistoryFilterYesterday = contentView.findViewById(R.id.btnHistoryFilterYesterday)
        btnHistoryFilterOlder = contentView.findViewById(R.id.btnHistoryFilterOlder)

        rvHistory = contentView.findViewById(R.id.rvHistory)
        pbHistoryLoading = contentView.findViewById(R.id.pbHistoryLoading)
        llEmptyHistory = contentView.findViewById(R.id.llEmptyHistory)

        contentView.findViewById<View>(R.id.vHistoryBackdrop).setOnClickListener {
            if (isMultiselectMode) {
                exitMultiselectMode()
            } else {
                dismiss()
            }
        }
    }

    private fun setupRecyclerView() {
        rvHistory.layoutManager = LinearLayoutManager(activity)
        adapter = HistorySidebarDiffAdapter(
            items = currentListItems,
            selectedIds = selectedItemIds,
            isMultiselectProvider = { isMultiselectMode },
            onItemClick = { item ->
                if (isMultiselectMode) {
                    toggleItemSelection(item.id)
                } else {
                    dismiss()
                    onHistoryItemSelected(item)
                }
            },
            onItemLongClick = { item ->
                if (!isMultiselectMode) {
                    isMultiselectMode = true
                    selectedItemIds.clear()
                    selectedItemIds.add(item.id)
                    updateSelectionBar()
                    adapter.notifyItemRangeChanged(0, currentListItems.size)
                }
            },
            onDeleteClick = { item -> deleteHistoryItem(item) },
            onMenuClick = { item, anchorView -> showItemOptionsMenu(item, anchorView) }
        )
        rvHistory.adapter = adapter
    }

    private fun setupListeners() {
        btnHistoryBack.setOnClickListener {
            if (isMultiselectMode) {
                exitMultiselectMode()
            } else {
                dismiss()
            }
        }

        btnHistoryExpand.setOnClickListener {
            dismiss()
            val intent = Intent(activity, HistoryActivity::class.java)
            activity.startActivity(intent)
        }

        btnClearHistory.setOnClickListener {
            showClearHistoryDialog()
        }

        btnResumeHistory.setOnClickListener {
            activity.config.saveHistory = true
            llHistoryPausedBanner.visibility = View.GONE
            Toast.makeText(activity, activity.getString(R.string.history_browsing_history_tracking_enable), Toast.LENGTH_SHORT).show()
        }

        btnSelectAll.setOnClickListener {
            selectedItemIds.clear()
            currentListItems.forEach { listItem ->
                if (listItem is HistorySidebarListItem.Entry) {
                    selectedItemIds.add(listItem.item.id)
                }
            }
            updateSelectionBar()
            adapter.notifyItemRangeChanged(0, currentListItems.size)
        }

        btnDeleteSelected.setOnClickListener {
            if (selectedItemIds.isEmpty()) return@setOnClickListener
            AlertDialog.Builder(activity)
                .setTitle(R.string.history_delete_selected)
                .setMessage(
                    activity.resources.getQuantityString(
                        R.plurals.history_remove_items_confirm,
                        selectedItemIds.size,
                        selectedItemIds.size
                    )
                )
                .setPositiveButton("Delete") { _, _ ->
                    deleteSelectedItems()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        btnCancelSelection.setOnClickListener {
            exitMultiselectMode()
        }

        etHistorySearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty().trim().lowercase(Locale.getDefault())
                ibClearHistorySearch.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                rebuildDisplayedList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        ibClearHistorySearch.setOnClickListener {
            etHistorySearch.text.clear()
        }

        ibVoiceSearch.setOnClickListener {
            activity.voiceSearchHelper.initiateVoiceSearch(object : VoiceSearchHelper.Callback {
                override fun onPartialResult(text: String) {
                    etHistorySearch.setText(text)
                    etHistorySearch.setSelection(text.length)
                }

                override fun onResult(text: String?) {
                    if (!text.isNullOrBlank()) {
                        etHistorySearch.setText(text)
                        etHistorySearch.setSelection(text.length)
                    }
                }
            })
        }

        btnHistoryFilterAll.setOnClickListener {
            currentFilter = 0
            updateFilterButtons()
            rebuildDisplayedList()
        }
        btnHistoryFilterToday.setOnClickListener {
            currentFilter = 1
            updateFilterButtons()
            rebuildDisplayedList()
        }
        btnHistoryFilterYesterday.setOnClickListener {
            currentFilter = 2
            updateFilterButtons()
            rebuildDisplayedList()
        }
        btnHistoryFilterOlder.setOnClickListener {
            currentFilter = 3
            updateFilterButtons()
            rebuildDisplayedList()
        }
    }

    private fun updateFilterButtons() {
        btnHistoryFilterAll.isSelected = currentFilter == 0
        btnHistoryFilterToday.isSelected = currentFilter == 1
        btnHistoryFilterYesterday.isSelected = currentFilter == 2
        btnHistoryFilterOlder.isSelected = currentFilter == 3

        btnHistoryFilterAll.setTextColor(if (currentFilter == 0) Color.WHITE else Color.parseColor("#94A3B8"))
        btnHistoryFilterToday.setTextColor(if (currentFilter == 1) Color.WHITE else Color.parseColor("#94A3B8"))
        btnHistoryFilterYesterday.setTextColor(if (currentFilter == 2) Color.WHITE else Color.parseColor("#94A3B8"))
        btnHistoryFilterOlder.setTextColor(if (currentFilter == 3) Color.WHITE else Color.parseColor("#94A3B8"))
    }

    private fun toggleItemSelection(id: Long) {
        if (selectedItemIds.contains(id)) {
            selectedItemIds.remove(id)
        } else {
            selectedItemIds.add(id)
        }
        if (selectedItemIds.isEmpty()) {
            exitMultiselectMode()
        } else {
            updateSelectionBar()
            adapter.notifyItemRangeChanged(0, currentListItems.size)
        }
    }

    private fun updateSelectionBar() {
        if (isMultiselectMode) {
            llSelectionBar.visibility = View.VISIBLE
            tvSelectedCount.text = "${selectedItemIds.size} selected"
        } else {
            llSelectionBar.visibility = View.GONE
        }
    }

    private fun exitMultiselectMode() {
        isMultiselectMode = false
        selectedItemIds.clear()
        updateSelectionBar()
        adapter.notifyItemRangeChanged(0, currentListItems.size)
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

        val screenHeight = if (decorView.height > 0) decorView.height else activity.resources.displayMetrics.heightPixels
        val popupWidth = SidebarHelper.calculateHistorySidebarWidth(activity)
        val popupHeight = (screenHeight - headerBottom).coerceAtLeast(100)

        popupWindow.width = popupWidth
        popupWindow.height = popupHeight
        popupWindow.isClippingEnabled = false

        // Check if history tracking is turned off in Settings
        if (!activity.config.saveHistory) {
            llHistoryPausedBanner.visibility = View.VISIBLE
        } else {
            llHistoryPausedBanner.visibility = View.GONE
        }

        popupWindow.showAtLocation(decorView, Gravity.TOP or Gravity.START, 0, headerBottom)
        loadHistory()

        contentView.post {
            btnHistoryBack.requestFocus()
        }
    }

    fun dismiss() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }

    private fun loadHistory() {
        pbHistoryLoading.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val all = AppDatabase.db.historyDao().getAll()
            withContext(Dispatchers.Main) {
                pbHistoryLoading.visibility = View.GONE
                allHistoryItems.clear()
                allHistoryItems.addAll(all)
                rebuildDisplayedList()
            }
        }
    }

    private fun rebuildDisplayedList() {
        val now = System.currentTimeMillis()
        val oneDayMillis = DateUtils.DAY_IN_MILLIS

        // 1. Filter raw items
        val filtered = allHistoryItems.filter { item ->
            val matchesSearch = searchQuery.isEmpty() ||
                    item.title?.lowercase(Locale.getDefault())?.contains(searchQuery) == true ||
                    item.url?.lowercase(Locale.getDefault())?.contains(searchQuery) == true

            val matchesFilter = when (currentFilter) {
                1 -> DateUtils.isToday(item.time)
                2 -> DateUtils.isToday(item.time + oneDayMillis)
                3 -> !DateUtils.isToday(item.time) && !DateUtils.isToday(item.time + oneDayMillis)
                else -> true
            }

            matchesSearch && matchesFilter
        }

        // 2. Group items into Chrome Date sections
        val newItems = mutableListOf<HistorySidebarListItem>()
        var lastDateKey = ""
        val currentGroupItems = mutableListOf<HistoryItem>()

        fun flushGroup() {
            if (currentGroupItems.isNotEmpty() && lastDateKey.isNotEmpty()) {
                val headerDateStr = formatChromeDateHeader(currentGroupItems[0].time)
                newItems.add(HistorySidebarListItem.Header(headerDateStr, currentGroupItems.size))
                currentGroupItems.forEach { newItems.add(HistorySidebarListItem.Entry(it)) }
                currentGroupItems.clear()
            }
        }

        for (item in filtered) {
            val cal = Calendar.getInstance().apply { timeInMillis = item.time }
            val dateKey = "${cal.get(Calendar.YEAR)}_${cal.get(Calendar.DAY_OF_YEAR)}"
            if (dateKey != lastDateKey) {
                flushGroup()
                lastDateKey = dateKey
            }
            currentGroupItems.add(item)
        }
        flushGroup()

        // 3. DiffUtil update (strictly respects AGENTS.md rule 5: no blind notifyDataSetChanged)
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = currentListItems.size
            override fun getNewListSize(): Int = newItems.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = currentListItems[oldItemPosition]
                val new = newItems[newItemPosition]
                return when {
                    old is HistorySidebarListItem.Header && new is HistorySidebarListItem.Header ->
                        old.dateString == new.dateString
                    old is HistorySidebarListItem.Entry && new is HistorySidebarListItem.Entry ->
                        old.item.id == new.item.id
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = currentListItems[oldItemPosition]
                val new = newItems[newItemPosition]
                return old == new
            }
        })

        currentListItems.clear()
        currentListItems.addAll(newItems)
        diffResult.dispatchUpdatesTo(adapter)

        val isEmpty = currentListItems.isEmpty()
        llEmptyHistory.visibility = if (isEmpty) View.VISIBLE else View.GONE
        rvHistory.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun formatChromeDateHeader(timestamp: Long): String {
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

    private fun showItemOptionsMenu(item: HistoryItem, anchorView: View) {
        val popup = PopupMenu(activity, anchorView)
        popup.menu.add(0, 1, 0, "Open in current tab")
        popup.menu.add(0, 2, 1, "Open in new tab")
        popup.menu.add(0, 3, 2, "Copy link address")
        popup.menu.add(0, 4, 3, "Delete from history")

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    dismiss()
                    onHistoryItemSelected(item)
                    true
                }
                2 -> {
                    item.url?.let { url ->
                        activity.openInNewTab(url)
                        Toast.makeText(activity, activity.getString(R.string.history_opened_in_new_tab), Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                3 -> {
                    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("URL", item.url ?: ""))
                    Toast.makeText(activity, activity.getString(R.string.history_link_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                    true
                }
                4 -> {
                    deleteHistoryItem(item)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun deleteHistoryItem(item: HistoryItem) {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.db.historyDao().delete(item)
            withContext(Dispatchers.Main) {
                allHistoryItems.remove(item)
                selectedItemIds.remove(item.id)
                rebuildDisplayedList()
            }
        }
    }

    private fun deleteSelectedItems() {
        val idsToDelete = selectedItemIds.toSet()
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.db.historyDao()
            val itemsToDelete = allHistoryItems.filter { idsToDelete.contains(it.id) }
            itemsToDelete.forEach { db.delete(it) }
            withContext(Dispatchers.Main) {
                allHistoryItems.removeAll(itemsToDelete)
                exitMultiselectMode()
                rebuildDisplayedList()
                Toast.makeText(
                    activity,
                    activity.resources.getQuantityString(R.plurals.history_deleted_items, itemsToDelete.size, itemsToDelete.size),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showClearHistoryDialog() {
        val options = arrayOf(
            "Clear all browsing history",
            "Clear today's history only",
            "Clear cache and cookies"
        )
        AlertDialog.Builder(activity)
            .setTitle(R.string.history_clear_browsing_data)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> clearAllHistory()
                    1 -> clearTodayHistory()
                    2 -> {
                        CoroutineScope(Dispatchers.IO).launch {
                            com.gothwad.browser.webengine.WebEngineFactory.clearCache(activity)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(activity, activity.getString(R.string.menu_cache_and_cookies_cleared), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun clearAllHistory() {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.db.historyDao().deleteAll()
            withContext(Dispatchers.Main) {
                allHistoryItems.clear()
                selectedItemIds.clear()
                exitMultiselectMode()
                rebuildDisplayedList()
                Toast.makeText(activity, activity.getString(R.string.history_all_browsing_history_cleared), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun clearTodayHistory() {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.db.historyDao()
            val todayItems = allHistoryItems.filter { DateUtils.isToday(it.time) }
            todayItems.forEach { db.delete(it) }
            withContext(Dispatchers.Main) {
                allHistoryItems.removeAll(todayItems)
                rebuildDisplayedList()
                Toast.makeText(activity, activity.getString(R.string.history_today_s_history_cleared), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

class HistorySidebarDiffAdapter(
    private val items: List<HistorySidebarListItem>,
    private val selectedIds: Set<Long>,
    private val isMultiselectProvider: () -> Boolean,
    private val onItemClick: (HistoryItem) -> Unit,
    private val onItemLongClick: (HistoryItem) -> Unit,
    private val onDeleteClick: (HistoryItem) -> Unit,
    private val onMenuClick: (HistoryItem, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ENTRY = 1
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvHeaderDate: TextView = view.findViewById(R.id.tvHeaderDate)
        val tvHeaderCount: TextView = view.findViewById(R.id.tvHeaderCount)
    }

    class EntryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: LinearLayout = view.findViewById(R.id.llHistoryItemRoot)
        val cbSelect: CheckBox = view.findViewById(R.id.cbHistorySelect)
        val tvTime: TextView = view.findViewById(R.id.tvHistoryTime)
        val ivFavicon: ImageView = view.findViewById(R.id.ivHistoryFavicon)
        val tvTitle: TextView = view.findViewById(R.id.tvHistoryTitle)
        val tvUrl: TextView = view.findViewById(R.id.tvHistoryUrl)
        val btnDelete: ImageButton = view.findViewById(R.id.btnHistoryDelete)
        val btnMenu: ImageButton = view.findViewById(R.id.btnHistoryMenu)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is HistorySidebarListItem.Header -> VIEW_TYPE_HEADER
            is HistorySidebarListItem.Entry -> VIEW_TYPE_ENTRY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = inflater.inflate(R.layout.item_sidebar_history_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_sidebar_history, parent, false)
            EntryViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val listItem = items[position]) {
            is HistorySidebarListItem.Header -> {
                val h = holder as HeaderViewHolder
                h.tvHeaderDate.text = listItem.dateString
                h.tvHeaderCount.text = "${listItem.visitCount} visit${if (listItem.visitCount > 1) "s" else ""}"
            }
            is HistorySidebarListItem.Entry -> {
                val h = holder as EntryViewHolder
                val item = listItem.item
                val isMultiselect = isMultiselectProvider()

                h.tvTitle.text = if (!item.title.isNullOrBlank()) item.title else item.url
                h.tvUrl.text = item.url

                // Timestamp formatted like Chrome (e.g., 10:45 AM)
                val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                h.tvTime.text = timeFormat.format(Date(item.time))

                // Checkbox for multiselect
                h.cbSelect.visibility = if (isMultiselect) View.VISIBLE else View.GONE
                h.cbSelect.isChecked = selectedIds.contains(item.id)

                // Favicon loading
                val url = item.url ?: ""
                val logoRes = getKnownLogoForUrl(url)
                if (logoRes != 0) {
                    h.ivFavicon.setImageResource(logoRes)
                } else {
                    h.ivFavicon.setImageResource(R.drawable.ic_tab_default_favicon)
                    // Async favicon fetch via FaviconsPool
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val bitmap = FaviconsPool.get(url)
                            if (bitmap != null) {
                                withContext(Dispatchers.Main) {
                                    if (holder.adapterPosition == position) {
                                        h.ivFavicon.setImageBitmap(bitmap)
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }

                h.root.setOnClickListener { onItemClick(item) }
                h.root.setOnLongClickListener {
                    onItemLongClick(item)
                    true
                }
                h.btnDelete.setOnClickListener { onDeleteClick(item) }
                h.btnMenu.setOnClickListener { v -> onMenuClick(item, v) }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    private fun getKnownLogoForUrl(url: String): Int {
        val lower = url.lowercase(Locale.getDefault())
        return when {
            lower.contains("google.com") -> R.drawable.ic_logo_google
            lower.contains("youtube.com") -> R.drawable.ic_logo_youtube
            lower.contains("github.com") -> R.drawable.ic_logo_github
            lower.contains("reddit.com") -> R.drawable.ic_logo_reddit
            lower.contains("wikipedia.org") -> R.drawable.ic_logo_wikipedia
            lower.contains("amazon.") -> R.drawable.ic_logo_amazon
            lower.contains("twitter.com") || lower.contains("x.com") -> R.drawable.ic_logo_x
            else -> 0
        }
    }
}
