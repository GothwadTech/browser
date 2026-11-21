package com.bansify.internet.activity.main.view.tabs

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bansify.internet.model.WebTabState

/**
 * Legacy TabsAdapter maintained for backward compatibility.
 * Modern tab management is handled by TabsGridAdapter in dialogs/tabs/.
 */
class TabsAdapter(
    private var tabs: List<WebTabState> = emptyList(),
    private val onTabClick: ((WebTabState) -> Unit)? = null
) : RecyclerView.Adapter<TabsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = View(parent.context)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tab = tabs.getOrNull(position) ?: return
        holder.itemView.setOnClickListener { onTabClick?.invoke(tab) }
    }

    override fun getItemCount(): Int = tabs.size

    fun updateTabs(newTabs: List<WebTabState>) {
        val oldTabs = this.tabs
        this.tabs = newTabs
        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(object : androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldTabs.size
            override fun getNewListSize(): Int = newTabs.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldTabs[oldItemPosition].id == newTabs[newItemPosition].id
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldTabs[oldItemPosition] == newTabs[newItemPosition]
            }
        })
        diffResult.dispatchUpdatesTo(this)
    }
}
