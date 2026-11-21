package com.bansify.internet.activity.main.dialogs.favorites

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bansify.internet.R
import com.bansify.internet.model.FavoriteItem
import com.bansify.internet.singleton.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class FavoritesDialog(
    context: Context,
    val scope: CoroutineScope,
    private val callback: Callback,
    private val currentPageTitle: String?,
    private val currentPageUrl: String?
) : Dialog(context, R.style.TvSideDrawerDialog) {

    private var allItems: MutableList<FavoriteItem> = ArrayList()
    private var displayedItems: MutableList<FavoriteItem> = ArrayList()
    private lateinit var adapter: BookmarksGridAdapter

    private lateinit var tvBookmarksSubtitle: TextView
    private lateinit var tvSectionCount: TextView
    private lateinit var etSearchBookmarks: EditText
    private lateinit var btnSortBookmarks: Button
    private lateinit var btnAddBookmark: Button
    private lateinit var ibCloseBookmarks: ImageButton
    private lateinit var rvBookmarksGrid: RecyclerView
    private lateinit var llEmptyBookmarks: View
    private lateinit var pbLoading: ProgressBar

    interface Callback {
        fun onFavoriteChoosen(item: FavoriteItem?)
    }

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_favorites)
        val dm = context.resources.displayMetrics
        val popupWidth = (dm.widthPixels * 0.45f).toInt().coerceIn(400, 800)
        window?.setLayout(popupWidth, ViewGroup.LayoutParams.MATCH_PARENT)
        window?.setGravity(android.view.Gravity.START)

        initViews()
        setupListeners()
        loadBookmarks()
    }

    private fun initViews() {
        tvBookmarksSubtitle = findViewById(R.id.tvBookmarksSubtitle)
        tvSectionCount = findViewById(R.id.tvSectionCount)
        etSearchBookmarks = findViewById(R.id.etSearchBookmarks)
        btnSortBookmarks = findViewById(R.id.btnSortBookmarks)
        btnAddBookmark = findViewById(R.id.btnAddBookmark)
        ibCloseBookmarks = findViewById(R.id.ibCloseBookmarks)
        rvBookmarksGrid = findViewById(R.id.rvBookmarksGrid)
        llEmptyBookmarks = findViewById(R.id.llEmptyBookmarks)
        pbLoading = findViewById(R.id.pbLoading)

        rvBookmarksGrid.layoutManager = GridLayoutManager(context, 2)
        adapter = BookmarksGridAdapter(
            displayedItems,
            onItemClick = { item ->
                callback.onFavoriteChoosen(item)
                dismiss()
            },
            onItemLongClick = { item ->
                showBookmarkOptionsDialog(item)
            }
        )
        rvBookmarksGrid.adapter = adapter
    }

    private fun setupListeners() {
        ibCloseBookmarks.setOnClickListener { dismiss() }
        btnAddBookmark.setOnClickListener { showAddItemDialog() }

        etSearchBookmarks.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterBookmarks(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnSortBookmarks.setOnClickListener {
            allItems.reverse()
            filterBookmarks(etSearchBookmarks.text.toString())
        }
    }

    private fun loadBookmarks() {
        pbLoading.visibility = View.VISIBLE
        scope.launch(Dispatchers.Main) {
            val list = AppDatabase.db.favoritesDao().getAll()
            allItems.clear()
            allItems.addAll(list)
            filterBookmarks("")
            pbLoading.visibility = View.GONE
        }
    }

    private fun filterBookmarks(query: String) {
        displayedItems.clear()
        if (query.isBlank()) {
            displayedItems.addAll(allItems)
        } else {
            val q = query.lowercase().trim()
            displayedItems.addAll(allItems.filter {
                (it.title?.lowercase()?.contains(q) == true) || (it.url?.lowercase()?.contains(q) == true)
            })
        }
        adapter.updateData(displayedItems)

        val totalCount = allItems.size
        tvBookmarksSubtitle.text = "$totalCount saved"
        tvSectionCount.text = "${displayedItems.size} items"

        if (displayedItems.isEmpty()) {
            llEmptyBookmarks.visibility = View.VISIBLE
            rvBookmarksGrid.visibility = View.GONE
        } else {
            llEmptyBookmarks.visibility = View.GONE
            rvBookmarksGrid.visibility = View.VISIBLE
        }
    }

    private fun showBookmarkOptionsDialog(item: FavoriteItem) {
        AlertDialog.Builder(context)
            .setTitle(item.title ?: item.url)
            .setItems(arrayOf("Open", context.getString(R.string.edit), context.getString(R.string.delete))) { _, which ->
                when (which) {
                    0 -> {
                        callback.onFavoriteChoosen(item)
                        dismiss()
                    }
                    1 -> {
                        FavoriteEditorDialog(context, object : FavoriteEditorDialog.Callback {
                            override fun onDone(edited: FavoriteItem) {
                                onItemEdited(edited)
                            }
                        }, item).show()
                    }
                    2 -> {
                        scope.launch(Dispatchers.Main) {
                            AppDatabase.db.favoritesDao().delete(item)
                            allItems.remove(item)
                            filterBookmarks(etSearchBookmarks.text.toString())
                        }
                    }
                }
            }
            .show()
    }

    private fun showAddItemDialog() {
        val newItem = FavoriteItem().apply {
            title = currentPageTitle
            url = currentPageUrl
        }
        FavoriteEditorDialog(context, object : FavoriteEditorDialog.Callback {
            override fun onDone(item: FavoriteItem) {
                onItemEdited(item)
            }
        }, newItem).show()
    }

    private fun onItemEdited(item: FavoriteItem) {
        scope.launch(Dispatchers.Main) {
            if (item.id == 0L) {
                val lastId = AppDatabase.db.favoritesDao().insert(item)
                item.id = lastId
                allItems.add(0, item)
            } else {
                AppDatabase.db.favoritesDao().update(item)
                val index = allItems.indexOfFirst { it.id == item.id }
                if (index >= 0) allItems[index] = item
            }
            filterBookmarks(etSearchBookmarks.text.toString())
        }
    }
}
