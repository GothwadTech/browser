package com.bansify.internet.activity.main.view.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bansify.internet.R

class HomeCardAdapter(
    private var items: List<HomeShortcutItem>,
    private val onItemClick: (HomeShortcutItem) -> Unit,
    private val onAddClick: () -> Unit,
    private val onRemoveClick: () -> Unit,
    private val onItemLongClick: ((HomeShortcutItem) -> Boolean)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    fun updateItems(newItems: List<HomeShortcutItem>) {
        val oldItems = this.items
        this.items = newItems
        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(object : androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldItems.size
            override fun getNewListSize(): Int = newItems.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = oldItems[oldItemPosition]
                val new = newItems[newItemPosition]
                return if (old.isHeader && new.isHeader) {
                    old.title == new.title
                } else if (old.isAddButton && new.isAddButton) {
                    true
                } else if (old.isDeleteButton && new.isDeleteButton) {
                    true
                } else if (old.isDashboardCard && new.isDashboardCard) {
                    old.dashboardType == new.dashboardType
                } else {
                    old.url == new.url && old.title == new.title
                }
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = oldItems[oldItemPosition]
                val new = newItems[newItemPosition]
                return old == new
            }
        })
        diffResult.dispatchUpdatesTo(this)
    }

    companion object {
        const val VIEW_TYPE_SHORTCUT = 0
        const val VIEW_TYPE_HEADER = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].isHeader) VIEW_TYPE_HEADER else VIEW_TYPE_SHORTCUT
    }

    class HeaderViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val tvCategoryTitle: TextView = view.findViewById(R.id.tvCategoryTitle)
    }

    class ShortcutViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val flIconCircle: FrameLayout = view.findViewById(R.id.flIconCircle)
        val ivIconDrawable: ImageView = view.findViewById(R.id.ivIconDrawable)
        val tvIconText: TextView = view.findViewById(R.id.tvIconText)
        val tvShortcutTitle: TextView = view.findViewById(R.id.tvShortcutTitle)
        val tvShortcutDomain: TextView = view.findViewById(R.id.tvShortcutDomain)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_home_category_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_home_card, parent, false)
            ShortcutViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && holder is ShortcutViewHolder && position in items.indices) {
            val item = items[position]
            holder.tvShortcutTitle.text = item.title
            holder.tvShortcutDomain.text = item.domainText
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]

        if (holder is HeaderViewHolder) {
            holder.tvCategoryTitle.text = item.title
            holder.itemView.isFocusable = false
            holder.itemView.isFocusableInTouchMode = false
            holder.itemView.isClickable = false
            return
        }

        if (holder is ShortcutViewHolder) {
            holder.tvShortcutTitle.text = item.title
            holder.tvShortcutDomain.text = item.domainText

            if (item.isAddButton) {
                holder.ivIconDrawable.visibility = View.VISIBLE
                holder.tvIconText.visibility = View.GONE
                holder.ivIconDrawable.setImageResource(R.drawable.ic_add)
                holder.ivIconDrawable.imageTintList = ContextCompat.getColorStateList(holder.view.context, R.color.day_night_icon_color)
            } else if (item.isDeleteButton) {
                holder.ivIconDrawable.visibility = View.VISIBLE
                holder.tvIconText.visibility = View.GONE
                holder.ivIconDrawable.setImageResource(R.drawable.ic_delete)
                holder.ivIconDrawable.imageTintList = ContextCompat.getColorStateList(holder.view.context, R.color.day_night_icon_color)
            } else if (item.iconDrawableRes != null && item.iconDrawableRes != 0) {
                holder.ivIconDrawable.visibility = View.VISIBLE
                holder.tvIconText.visibility = View.GONE
                holder.ivIconDrawable.imageTintList = null
                try {
                    val d = ContextCompat.getDrawable(holder.view.context, item.iconDrawableRes)
                    holder.ivIconDrawable.setImageDrawable(d)
                } catch (e: Throwable) {
                    holder.ivIconDrawable.setImageResource(item.iconDrawableRes)
                }
            } else {
                holder.ivIconDrawable.visibility = View.GONE
                holder.tvIconText.visibility = View.VISIBLE
                holder.tvIconText.text = item.singleLetter
                holder.tvIconText.textSize = 17f
            }

            holder.itemView.isFocusable = true
            holder.itemView.isFocusableInTouchMode = true
            holder.itemView.isClickable = true

            holder.itemView.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate()
                        .scaleX(1.05f)
                        .scaleY(1.05f)
                        .setDuration(150)
                        .start()
                    v.elevation = 8f
                    holder.tvShortcutTitle.setTextColor(ContextCompat.getColor(v.context, R.color.day_night_text_color_contrast))
                    holder.tvShortcutDomain.setTextColor(ContextCompat.getColor(v.context, R.color.progressbar_tint))
                    if (item.isAddButton || item.isDeleteButton) {
                        holder.ivIconDrawable.imageTintList = ContextCompat.getColorStateList(v.context, R.color.progressbar_tint)
                    }
                } else {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(150)
                        .start()
                    v.elevation = 1f
                    holder.tvShortcutTitle.setTextColor(ContextCompat.getColor(v.context, R.color.day_night_text_color_contrast))
                    holder.tvShortcutDomain.setTextColor(ContextCompat.getColor(v.context, R.color.day_night_text_secondary))
                    if (item.isAddButton || item.isDeleteButton) {
                        holder.ivIconDrawable.imageTintList = ContextCompat.getColorStateList(v.context, R.color.day_night_icon_color)
                    }
                }
            }

            holder.itemView.setOnClickListener {
                when {
                    item.isAddButton -> onAddClick()
                    item.isDeleteButton -> onRemoveClick()
                    else -> onItemClick(item)
                }
            }

            holder.itemView.setOnLongClickListener {
                if (!item.isAddButton && !item.isDeleteButton && !item.isActionCard && !item.isHeader) {
                    onItemLongClick?.invoke(item) ?: false
                } else {
                    false
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size
}
