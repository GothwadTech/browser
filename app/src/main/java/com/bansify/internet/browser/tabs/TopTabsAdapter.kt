package com.bansify.internet.browser.tabs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bansify.internet.Config
import com.bansify.internet.R
import com.bansify.internet.model.WebTabState
import com.bansify.internet.singleton.FaviconsPool
import com.bansify.internet.utils.activity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TopTabsAdapter(
    private var tabs: MutableList<WebTabState>,
    private var currentTab: WebTabState?,
    private val onTabClick: (WebTabState) -> Unit,
    private val onCloseTabClick: (WebTabState) -> Unit,
    private val onNewTabClick: () -> Unit = {},
    private val onTabFocused: (WebTabState, Int, View) -> Unit = { _, _, _ -> }
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_TAB = 0
        const val TYPE_NEW_TAB = 1
    }

    class TopTabViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val llChromeTabRoot: LinearLayout = view.findViewById(R.id.llChromeTabRoot)
        val ivTabFavicon: ImageView = view.findViewById(R.id.ivTabFavicon)
        val tvTabTitle: TextView = view.findViewById(R.id.tvTabTitle)
        val ibTabClose: ImageButton = view.findViewById(R.id.ibTabClose)
    }

    class NewTabViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ibNewTabButton: ImageButton = view.findViewById(R.id.ibNewTabButton)
    }

    override fun getItemCount(): Int = tabs.size + 1

    override fun getItemViewType(position: Int): Int {
        return if (position < tabs.size) TYPE_TAB else TYPE_NEW_TAB
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_TAB) {
            val view = inflater.inflate(R.layout.item_top_chrome_tab, parent, false)
            TopTabViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_top_chrome_new_tab, parent, false)
            NewTabViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is TopTabViewHolder) {
            val tab = tabs[position]
            val isActive = tab == currentTab
            holder.itemView.tag = tab

            val isHome = tab.url.isEmpty() ||
                    tab.url == Config.HOME_PAGE_URL ||
                    tab.url == Config.HOME_URL_ALIAS ||
                    tab.url == "about:blank" ||
                    tab.title.equals("Home Screen", ignoreCase = true) ||
                    tab.title.equals("Home", ignoreCase = true)

            if (isHome) {
                holder.tvTabTitle.text = holder.itemView.context.getString(R.string.home_screen)
                holder.ivTabFavicon.setImageResource(R.drawable.ic_home_grey_900_24dp)
            } else {
                holder.tvTabTitle.text = if (tab.title.isNotBlank()) tab.title else tab.url
                holder.ivTabFavicon.setImageResource(R.drawable.ic_tab_default_favicon)

                // Async Favicon loading
                val activity = holder.itemView.activity as? AppCompatActivity
                val scope = activity?.lifecycleScope
                scope?.launch(Dispatchers.Main) {
                    try {
                        val favicon = FaviconsPool.get(tab.url)
                        if (holder.itemView.tag == tab && favicon != null) {
                            holder.ivTabFavicon.setImageBitmap(favicon)
                        }
                    } catch (_: Exception) {}
                }
            }

            // Active styling
            holder.llChromeTabRoot.isSelected = isActive
            holder.llChromeTabRoot.isActivated = isActive
            holder.tvTabTitle.setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
            holder.tvTabTitle.alpha = if (isActive) 1.0f else 0.85f

            // Click actions
            holder.llChromeTabRoot.setOnClickListener {
                onTabClick(tab)
            }

            holder.ibTabClose.setOnClickListener {
                onCloseTabClick(tab)
            }

            // TV Focus Animation
            holder.ibTabClose.isFocusable = true
            holder.ibTabClose.isClickable = true

            holder.llChromeTabRoot.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    onTabFocused(tab, holder.bindingAdapterPosition, v)
                    v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(100).start()
                    v.elevation = 6f
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    v.elevation = 0f
                }
            }

            holder.ibTabClose.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    onTabFocused(tab, holder.bindingAdapterPosition, holder.llChromeTabRoot)
                    v.animate().scaleX(1.2f).scaleY(1.2f).setDuration(100).start()
                    (v as? ImageButton)?.setColorFilter(android.graphics.Color.WHITE)
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    (v as? ImageButton)?.clearColorFilter()
                }
            }

            holder.llChromeTabRoot.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    if (keyCode == android.view.KeyEvent.KEYCODE_FORWARD_DEL || keyCode == android.view.KeyEvent.KEYCODE_DEL) {
                        onCloseTabClick(tab)
                        return@setOnKeyListener true
                    }
                }
                false
            }
        } else if (holder is NewTabViewHolder) {
            holder.ibNewTabButton.setOnClickListener {
                onNewTabClick()
            }
            holder.ibNewTabButton.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(100).start()
                    val lastTab = tabs.lastOrNull() ?: WebTabState()
                    onTabFocused(lastTab, holder.bindingAdapterPosition, v)
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                }
            }
        }
    }

    fun updateData(newTabs: List<WebTabState>, newCurrentTab: WebTabState?) {
        val oldTabs = tabs
        val oldCurrentTab = currentTab
        tabs = newTabs.toMutableList()
        currentTab = newCurrentTab

        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldTabs.size + 1
            override fun getNewListSize(): Int = newTabs.size + 1
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldIsNewTab = oldItemPosition == oldTabs.size
                val newIsNewTab = newItemPosition == newTabs.size
                if (oldIsNewTab && newIsNewTab) return true
                if (oldIsNewTab || newIsNewTab) return false
                return oldTabs[oldItemPosition].id == newTabs[newItemPosition].id
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldIsNewTab = oldItemPosition == oldTabs.size
                val newIsNewTab = newItemPosition == newTabs.size
                if (oldIsNewTab && newIsNewTab) return true
                if (oldIsNewTab || newIsNewTab) return false
                val oldItem = oldTabs[oldItemPosition]
                val newItem = newTabs[newItemPosition]
                val oldIsActive = oldItem == oldCurrentTab
                val newIsActive = newItem == newCurrentTab
                return oldItem.title == newItem.title &&
                        oldItem.url == newItem.url &&
                        oldIsActive == newIsActive
            }
        })
        diffResult.dispatchUpdatesTo(this)
    }
}
