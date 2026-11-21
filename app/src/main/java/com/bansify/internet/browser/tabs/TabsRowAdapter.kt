package com.bansify.internet.browser.tabs

import android.graphics.Bitmap
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
import androidx.recyclerview.widget.RecyclerView
import com.bansify.internet.Config
import com.bansify.internet.R
import com.bansify.internet.model.WebTabState
import com.bansify.internet.singleton.FaviconsPool
import com.bansify.internet.utils.activity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TabsRowAdapter(
    private var tabs: MutableList<WebTabState>,
    private var currentTab: WebTabState?,
    private val onTabClick: (WebTabState) -> Unit,
    private val onCloseTabClick: (WebTabState) -> Unit,
    private val onTabFocused: (WebTabState) -> Unit = {}
) : RecyclerView.Adapter<TabsRowAdapter.TabRowViewHolder>() {

    class TabRowViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val llTabRowCardRoot: LinearLayout = view.findViewById(R.id.llTabRowCardRoot)
        val ivTabFavicon: ImageView = view.findViewById(R.id.ivTabFavicon)
        val tvTabTitle: TextView = view.findViewById(R.id.tvTabTitle)
        val ibCloseTab: ImageButton = view.findViewById(R.id.ibCloseTab)
        val ivTabPreview: ImageView = view.findViewById(R.id.ivTabPreview)
        val tvTabDomain: TextView = view.findViewById(R.id.tvTabDomain)
        val tvActiveBadge: TextView = view.findViewById(R.id.tvActiveBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabRowViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tab_row_card, parent, false)
        return TabRowViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabRowViewHolder, position: Int) {
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
            holder.tvTabDomain.text = "Home Screen"
            holder.ivTabFavicon.setImageResource(R.drawable.ic_home_grey_900_24dp)
            holder.ivTabPreview.setImageResource(0)
        } else {
            holder.tvTabTitle.text = if (tab.title.isNotBlank()) tab.title else tab.url
            val domain = try {
                val uri = Uri.parse(tab.url)
                uri.host?.removePrefix("www.") ?: tab.url
            } catch (e: Exception) {
                tab.url
            }
            holder.tvTabDomain.text = domain
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

            // Thumbnail loading
            if (tab.thumbnail != null) {
                holder.ivTabPreview.setImageBitmap(tab.thumbnail)
            } else {
                scope?.launch(Dispatchers.IO) {
                    try {
                        val thumb = tab.loadThumbnail()
                        withContext(Dispatchers.Main) {
                            if (holder.itemView.tag == tab && thumb != null) {
                                holder.ivTabPreview.setImageBitmap(thumb)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        // Active Badge
        holder.tvActiveBadge.visibility = if (isActive) View.VISIBLE else View.GONE
        holder.llTabRowCardRoot.isSelected = isActive

        // TV Focus Animation
        holder.llTabRowCardRoot.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                onTabFocused(tab)
                v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start()
                v.elevation = 8f
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                v.elevation = 2f
            }
        }

        holder.llTabRowCardRoot.setOnClickListener {
            onTabClick(tab)
        }

        holder.ibCloseTab.setOnClickListener {
            onCloseTabClick(tab)
        }

        holder.ibCloseTab.isFocusable = true
        holder.ibCloseTab.isClickable = true

        holder.ibCloseTab.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.2f).scaleY(1.2f).setDuration(120).start()
                (v as? ImageButton)?.setColorFilter(android.graphics.Color.WHITE)
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                (v as? ImageButton)?.clearColorFilter()
            }
        }

        holder.llTabRowCardRoot.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                    holder.ibCloseTab.requestFocus()
                    return@setOnKeyListener true
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_FORWARD_DEL || keyCode == android.view.KeyEvent.KEYCODE_DEL) {
                    onCloseTabClick(tab)
                    return@setOnKeyListener true
                }
            }
            false
        }

        holder.ibCloseTab.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                    holder.llTabRowCardRoot.requestFocus()
                    return@setOnKeyListener true
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || keyCode == android.view.KeyEvent.KEYCODE_ENTER || keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == android.view.KeyEvent.KEYCODE_BUTTON_A) {
                    onCloseTabClick(tab)
                    return@setOnKeyListener true
                }
            }
            false
        }
    }

    override fun getItemCount(): Int = tabs.size

    override fun onViewRecycled(holder: TabRowViewHolder) {
        super.onViewRecycled(holder)
        holder.ivTabPreview.setImageBitmap(null)
        holder.ivTabFavicon.setImageBitmap(null)
    }

    fun updateTabs(newTabs: List<WebTabState>, newCurrentTab: WebTabState?) {
        val oldTabs = tabs
        val oldCurrentTab = currentTab
        tabs = newTabs.toMutableList()
        currentTab = newCurrentTab

        if (oldTabs == newTabs) {
            if (oldCurrentTab != newCurrentTab) {
                val oldPos = oldTabs.indexOf(oldCurrentTab)
                val newPos = newTabs.indexOf(newCurrentTab)
                if (oldPos != -1) notifyItemChanged(oldPos)
                if (newPos != -1) notifyItemChanged(newPos)
            }
            return
        }

        if (newTabs.size == oldTabs.size + 1 && oldTabs == newTabs.subList(0, oldTabs.size)) {
            val insertedPos = oldTabs.size
            notifyItemInserted(insertedPos)
            if (oldCurrentTab != newCurrentTab) {
                val oldPos = oldTabs.indexOf(oldCurrentTab)
                if (oldPos != -1) notifyItemChanged(oldPos)
            }
            return
        }

        if (newTabs.size == oldTabs.size - 1) {
            val removedIndex = oldTabs.indexOfFirst { !newTabs.contains(it) }
            if (removedIndex != -1) {
                notifyItemRemoved(removedIndex)
                if (oldCurrentTab != newCurrentTab) {
                    val newPos = newTabs.indexOf(newCurrentTab)
                    if (newPos != -1) notifyItemChanged(newPos)
                }
                return
            }
        }

        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(object : androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldTabs.size
            override fun getNewListSize(): Int = newTabs.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldTabs[oldItemPosition].id == newTabs[newItemPosition].id
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldTabs[oldItemPosition]
                val newItem = newTabs[newItemPosition]
                val oldIsActive = oldItem == oldCurrentTab
                val newIsActive = newItem == newCurrentTab
                return oldItem.title == newItem.title &&
                        oldItem.url == newItem.url &&
                        oldItem.thumbnail == newItem.thumbnail &&
                        oldIsActive == newIsActive
            }
        })
        diffResult.dispatchUpdatesTo(this)
    }
}
