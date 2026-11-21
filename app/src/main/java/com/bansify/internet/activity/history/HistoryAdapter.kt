package com.bansify.internet.activity.history

import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.bansify.internet.model.HistoryItem
import com.bansify.internet.utils.Utils
import de.halfbit.pinnedsection.PinnedSectionListView
import java.util.ArrayList

class HistoryAdapter : BaseAdapter(), PinnedSectionListView.PinnedSectionListAdapter {
    val items = ArrayList<HistoryItem>()
    private var lastHeaderDate: Long = -1
    var realCount: Long = 0
        private set
    var isMultiselectMode = false
        set(multiselectMode) {
            field = multiselectMode
            if (!multiselectMode) {
                for (hi in items) {
                    hi.selected = false
                }
            }
            notifyDataSetChanged()
        }
    private val _tmpSelected = ArrayList<HistoryItem>()

    var onDeleteClickListener: ((HistoryItem) -> Unit)? = null
    var onItemMenuClickListener: ((HistoryItem, View) -> Unit)? = null

    val selectedItems: List<HistoryItem>
        get() {
            _tmpSelected.clear()
            for (hi in items) {
                if (hi.selected) {
                    _tmpSelected.add(hi)
                }
            }
            return _tmpSelected
        }

    fun addItems(newItems: List<HistoryItem>) {
        if (newItems.isEmpty()) {
            return
        }
        for (hi in newItems) {
            if (!Utils.isSameDate(hi.time, lastHeaderDate)) {
                lastHeaderDate = hi.time
                this.items.add(HistoryItem.createDateHeaderInfo(hi.time))
            }
            this.items.add(hi)
            realCount++
        }
        notifyDataSetChanged()
    }

    override fun getCount(): Int {
        return items.size
    }

    override fun getItem(position: Int): Any {
        return items[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val hiv: HistoryItemView = if (convertView is HistoryItemView) {
            convertView
        } else {
            HistoryItemView(parent.context, getItemViewType(position))
        }
        hiv.setHistoryItem(
            items[position],
            isMultiselectMode,
            onDeleteClickListener,
            onItemMenuClickListener
        )
        return hiv
    }

    override fun getViewTypeCount(): Int {
        return 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].isDateHeader) VIEW_TYPE_HEADER else VIEW_TYPE_HISTORY_ITEM
    }

    override fun isItemViewTypePinned(viewType: Int): Boolean {
        return viewType == VIEW_TYPE_HEADER
    }

    fun erase() {
        items.clear()
        lastHeaderDate = -1
        realCount = 0
        notifyDataSetChanged()
    }

    fun remove(historyItem: HistoryItem) {
        items.remove(historyItem)
        if (!historyItem.isDateHeader && realCount > 0) {
            realCount--
        }
        // Cleanup orphaned date headers
        cleanupHeaders()
        notifyDataSetChanged()
    }

    fun remove(selected: List<HistoryItem>) {
        items.removeAll(selected)
        realCount = (realCount - selected.count { !it.isDateHeader }).coerceAtLeast(0)
        cleanupHeaders()
        notifyDataSetChanged()
    }

    private fun cleanupHeaders() {
        val toRemove = ArrayList<HistoryItem>()
        for (i in 0 until items.size) {
            val item = items[i]
            if (item.isDateHeader) {
                // If header is at end of list or followed by another header, remove it
                if (i == items.size - 1 || items[i + 1].isDateHeader) {
                    toRemove.add(item)
                }
            }
        }
        items.removeAll(toRemove)
    }

    companion object {
        const val VIEW_TYPE_HISTORY_ITEM = 0
        const val VIEW_TYPE_HEADER = 1
    }
}
