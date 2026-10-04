package com.gothwad.browser.activity.main.view.search

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.browser.R
import com.gothwad.browser.databinding.ViewChromeSearchScreenBinding
import com.gothwad.browser.singleton.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchSuggestionItem(
    val title: String,
    val query: String,
    val isSearchAction: Boolean = false
)

class SearchOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val vb = ViewChromeSearchScreenBinding.inflate(LayoutInflater.from(context), this, true)
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var searchJob: Job? = null
    private val adapter = SearchSuggestionsAdapter(
        onItemClick = { item ->
            performNavigation(item.query)
        },
        onInsertClick = { item ->
            vb.etSearchInput.setText(item.query)
            vb.etSearchInput.setSelection(item.query.length)
        }
    )

    var onPerformSearch: ((String) -> Unit)? = null
    var onVoiceSearchRequested: (() -> Unit)? = null
    var onCloseRequested: (() -> Unit)? = null

    init {
        vb.rvSearchSuggestions.layoutManager = LinearLayoutManager(context)
        vb.rvSearchSuggestions.adapter = adapter

        vb.ibSearchBack.setOnClickListener {
            hide()
            onCloseRequested?.invoke()
        }

        vb.ibSearchClear.setOnClickListener {
            vb.etSearchInput.setText("")
            loadRecentSearches()
        }

        vb.ibSearchVoice.setOnClickListener {
            onVoiceSearchRequested?.invoke()
        }

        vb.ibSearchLens.setOnClickListener {
            onVoiceSearchRequested?.invoke()
        }

        vb.etSearchInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)
            ) {
                val q = vb.etSearchInput.text.toString().trim()
                if (q.isNotEmpty()) {
                    performNavigation(q)
                }
                true
            } else false
        }

        vb.etSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString()?.trim() ?: ""
                vb.ibSearchClear.isVisible = text.isNotEmpty()
                filterSuggestions(text)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    fun show(initialQuery: String = "") {
        visibility = View.VISIBLE
        bringToFront()
        vb.etSearchInput.setText(initialQuery)
        if (initialQuery.isNotEmpty()) {
            vb.etSearchInput.setSelection(initialQuery.length)
            filterSuggestions(initialQuery)
        } else {
            loadRecentSearches()
        }
        vb.etSearchInput.requestFocus()
        postDelayed({
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(vb.etSearchInput, InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }

    fun hide() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(vb.etSearchInput.windowToken, 0)
        visibility = View.GONE
        vb.etSearchInput.clearFocus()
    }

    private fun performNavigation(query: String) {
        val clean = query.trim()
        if (clean.isNotEmpty()) {
            hide()
            onPerformSearch?.invoke(clean)
        }
    }

    private fun loadRecentSearches() {
        searchJob?.cancel()
        searchJob = scope.launch {
            val recent = withContext(Dispatchers.IO) {
                try {
                    AppDatabase.db.historyDao().last(20)
                } catch (e: Exception) {
                    emptyList()
                }
            }
            val items = recent.map {
                val display = if (!it.title.isNullOrBlank()) it.title else it.url
                SearchSuggestionItem(title = display, query = it.url, isSearchAction = false)
            }
            adapter.submitList(items)
            vb.tvEmptySuggestions.isVisible = items.isEmpty()
        }
    }

    private fun filterSuggestions(query: String) {
        if (query.isBlank()) {
            loadRecentSearches()
            return
        }
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(120) // debounce
            val results = withContext(Dispatchers.IO) {
                try {
                    val pattern = "%$query%"
                    AppDatabase.db.historyDao().search(pattern, pattern)
                } catch (e: Exception) {
                    emptyList()
                }
            }
            val list = mutableListOf<SearchSuggestionItem>()
            list.add(SearchSuggestionItem(title = query, query = query, isSearchAction = true))
            for (h in results) {
                val display = if (!h.title.isNullOrBlank()) h.title else h.url
                if (!list.any { it.query.equals(h.url, ignoreCase = true) || it.title.equals(display, ignoreCase = true) }) {
                    list.add(SearchSuggestionItem(title = display, query = h.url, isSearchAction = false))
                }
                if (list.size >= 15) break
            }
            adapter.submitList(list)
            vb.tvEmptySuggestions.isVisible = false
        }
    }
}

class SearchSuggestionsAdapter(
    private val onItemClick: (SearchSuggestionItem) -> Unit,
    private val onInsertClick: (SearchSuggestionItem) -> Unit
) : RecyclerView.Adapter<SearchSuggestionsAdapter.VH>() {

    private val items = mutableListOf<SearchSuggestionItem>()

    fun submitList(newItems: List<SearchSuggestionItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivIcon: ImageView = itemView.findViewById(R.id.ivSuggestionIcon)
        val tvText: TextView = itemView.findViewById(R.id.tvSuggestionText)
        val ibInsert: ImageButton = itemView.findViewById(R.id.ibInsertQuery)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_suggestion, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvText.text = item.title
        if (item.isSearchAction) {
            holder.ivIcon.setImageResource(R.drawable.ic_search)
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_history_grey_900_36dp)
        }
        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.ibInsert.setOnClickListener { onInsertClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
