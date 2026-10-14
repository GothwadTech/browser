package com.gothwad.browser.activity.main.dialogs

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.browser.R
import com.gothwad.browser.activity.main.MainActivity
import com.gothwad.browser.model.FavoriteItem
import com.gothwad.browser.utils.ListDiff
import com.gothwad.browser.singleton.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class FavoritesSidebarPopup(
    private val activity: MainActivity,
    private val onFavoriteSelected: (FavoriteItem) -> Unit
) {

    private val popupWindow: PopupWindow
    private val rootContainer: FrameLayout
    private val contentView: View

    private lateinit var btnFavBack: ImageButton
    private lateinit var tvFavTitle: TextView
    private lateinit var btnAddCurrentBookmark: Button
    private lateinit var etFavSearch: EditText
    private lateinit var rvFavGrid: RecyclerView
    private lateinit var pbFavLoading: ProgressBar
    private lateinit var llEmptyFavorites: LinearLayout

    private val allFavorites = mutableListOf<FavoriteItem>()
    private val displayedFavorites = mutableListOf<FavoriteItem>()
    private lateinit var adapter: FavoritesSidebarAdapter
    private var searchQuery = ""

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

        contentView = LayoutInflater.from(activity).inflate(R.layout.dialog_sidebar_favorites, rootContainer, true)

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

        bindViews()
        setupListeners()
        setupRecyclerView()
    }

    private fun bindViews() {
        btnFavBack = contentView.findViewById(R.id.btnFavBack)
        tvFavTitle = contentView.findViewById(R.id.tvFavTitle)
        btnAddCurrentBookmark = contentView.findViewById(R.id.btnAddCurrentBookmark)
        etFavSearch = contentView.findViewById(R.id.etFavSearch)
        rvFavGrid = contentView.findViewById(R.id.rvFavGrid)
        pbFavLoading = contentView.findViewById(R.id.pbFavLoading)
        llEmptyFavorites = contentView.findViewById(R.id.llEmptyFavorites)

        contentView.findViewById<View>(R.id.vFavoritesBackdrop).setOnClickListener {
            dismiss()
        }
    }

    private fun setupRecyclerView() {
        val layoutManager = GridLayoutManager(activity, 2)
        rvFavGrid.layoutManager = layoutManager
        adapter = FavoritesSidebarAdapter(
            items = displayedFavorites,
            onItemClick = { item ->
                dismiss()
                onFavoriteSelected(item)
            },
            onDeleteClick = { item -> deleteBookmark(item) }
        )
        rvFavGrid.adapter = adapter
    }

    private fun setupListeners() {
        btnFavBack.setOnClickListener { dismiss() }

        btnAddCurrentBookmark.setOnClickListener {
            showAddBookmarkDialog()
        }

        etFavSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty().trim().lowercase(Locale.getDefault())
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
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

        val popupWidth = SidebarHelper.calculateLeftSidebarWidth(activity)
        val popupHeight = (screenHeight - headerBottom).coerceAtLeast(100)

        popupWindow.width = popupWidth
        popupWindow.height = popupHeight
        popupWindow.isClippingEnabled = false

        // LEFT SIDEBAR: xPos = 0
        popupWindow.showAtLocation(decorView, Gravity.TOP or Gravity.START, 0, headerBottom)

        loadBookmarks()

        contentView.post {
            btnFavBack.requestFocus()
        }
    }

    fun dismiss() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }

    private fun loadBookmarks() {
        pbFavLoading.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.db
            val all = db.favoritesDao().getAll()
            withContext(Dispatchers.Main) {
                pbFavLoading.visibility = View.GONE
                allFavorites.clear()
                allFavorites.addAll(all)
                applyFilters()
            }
        }
    }

    private fun applyFilters() {
        val filtered = if (searchQuery.isEmpty()) {
            allFavorites
        } else {
            allFavorites.filter { item ->
                item.title?.lowercase(Locale.getDefault())?.contains(searchQuery) == true ||
                        item.url?.lowercase(Locale.getDefault())?.contains(searchQuery) == true
            }
        }

        // DiffUtil update instead of notifyDataSetChanged (AGENTS.md rule 5): a re-typed search box
        // must not rebind the whole grid.
        val favoritesAdapter = adapter
        if (favoritesAdapter != null) {
            ListDiff.submit(favoritesAdapter, displayedFavorites, filtered, idOf = { it.id },
                contentOf = { "${it.title}|${it.url}|${it.favicon}" })
        } else {
            displayedFavorites.clear()
            displayedFavorites.addAll(filtered)
        }

        llEmptyFavorites.visibility = if (displayedFavorites.isEmpty()) View.VISIBLE else View.GONE
        rvFavGrid.visibility = if (displayedFavorites.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun deleteBookmark(item: FavoriteItem) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.db
            db.favoritesDao().delete(item)
            withContext(Dispatchers.Main) {
                allFavorites.remove(item)
                applyFilters()
            }
        }
    }

    private fun showAddBookmarkDialog() {
        val currentTab = activity.tabsModel.currentTab.value
        val title = currentTab?.title ?: ""
        val url = currentTab?.url ?: ""

        if (url.isBlank() || url == "about:blank") {
            Toast.makeText(activity, activity.getString(R.string.bookmarks_no_active_page_to), Toast.LENGTH_SHORT).show()
            return
        }

        val item = FavoriteItem().apply {
            this.title = title.ifEmpty { url }
            this.url = url
            this.order = allFavorites.size
        }

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.db
            db.favoritesDao().insert(item)
            withContext(Dispatchers.Main) {
                Toast.makeText(activity, activity.getString(R.string.bookmarks_bookmark_added), Toast.LENGTH_SHORT).show()
                loadBookmarks()
            }
        }
    }
}

class FavoritesSidebarAdapter(
    private val items: MutableList<FavoriteItem>,
    private val onItemClick: (FavoriteItem) -> Unit,
    private val onDeleteClick: (FavoriteItem) -> Unit
) : RecyclerView.Adapter<FavoritesSidebarAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: LinearLayout = view.findViewById(R.id.llFavItemRoot)
        val ivIcon: ImageView = view.findViewById(R.id.ivFavIcon)
        val tvTitle: TextView = view.findViewById(R.id.tvFavTitle)
        val tvUrl: TextView = view.findViewById(R.id.tvFavUrl)
        val btnDelete: ImageButton = view.findViewById(R.id.btnFavDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sidebar_favorite, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = if (item.title.isNullOrBlank()) item.url else item.title
        holder.tvUrl.text = item.url

        holder.root.setOnClickListener { onItemClick(item) }
        holder.btnDelete.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
