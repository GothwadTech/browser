package com.bansify.internet.activity.main.dialogs.favorites

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bansify.internet.R
import com.bansify.internet.model.FavoriteItem
import com.bansify.internet.singleton.FaviconsPool
import com.bansify.internet.utils.activity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BookmarksGridAdapter(
    private var items: List<FavoriteItem>,
    private val onItemClick: (FavoriteItem) -> Unit,
    private val onItemLongClick: (FavoriteItem) -> Unit
) : RecyclerView.Adapter<BookmarksGridAdapter.BookmarkViewHolder>() {

    class BookmarkViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: LinearLayout = view.findViewById(R.id.llBookmarkCardRoot)
        val tvBookmarkTag: TextView = view.findViewById(R.id.tvBookmarkTag)
        val ivBookmarkIcon: ImageView = view.findViewById(R.id.ivBookmarkIcon)
        val tvBookmarkTitle: TextView = view.findViewById(R.id.tvBookmarkTitle)
        val tvBookmarkUrl: TextView = view.findViewById(R.id.tvBookmarkUrl)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bookmark_card, parent, false)
        return BookmarkViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookmarkViewHolder, position: Int) {
        val item = items[position]
        holder.itemView.tag = item

        holder.tvBookmarkTitle.text = if (!item.title.isNullOrBlank()) item.title else item.url
        val url = item.url ?: ""
        val domain = try {
            val uri = Uri.parse(url)
            uri.host?.removePrefix("www.") ?: url
        } catch (e: Exception) {
            url
        }
        holder.tvBookmarkUrl.text = domain
        holder.tvBookmarkTag.text = if (item.isFolder) "folder" else "page"

        // Set icon based on domain or fallback to favicon
        val iconRes = getLogoForUrl(url)
        if (iconRes != 0) {
            holder.ivBookmarkIcon.setImageResource(iconRes)
        } else {
            holder.ivBookmarkIcon.setImageResource(R.drawable.ic_tab_default_favicon)
            val activity = holder.itemView.activity as? AppCompatActivity
            activity?.lifecycleScope?.launch(Dispatchers.Main) {
                try {
                    val favicon = FaviconsPool.get(url)
                    if (holder.itemView.tag == item && favicon != null) {
                        holder.ivBookmarkIcon.setImageBitmap(favicon)
                    }
                } catch (_: Exception) {}
            }
        }

        // TV Focus animation
        holder.root.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).start()
                v.elevation = 8f
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                v.elevation = 2f
            }
        }

        holder.root.setOnClickListener { onItemClick(item) }
        holder.root.setOnLongClickListener {
            onItemLongClick(item)
            true
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<FavoriteItem>) {
        val oldItems = items
        items = newItems
        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(object : androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldItems.size
            override fun getNewListSize(): Int = newItems.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldItems[oldItemPosition].id == newItems[newItemPosition].id
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = oldItems[oldItemPosition]
                val new = newItems[newItemPosition]
                return old.title == new.title &&
                        old.url == new.url &&
                        old.isFolder == new.isFolder &&
                        old.order == new.order
            }
        })
        diffResult.dispatchUpdatesTo(this)
    }

    private fun getLogoForUrl(url: String): Int {
        val lower = url.lowercase()
        return when {
            lower.contains("whatsapp") -> R.drawable.ic_logo_whatsapp
            lower.contains("youtube") -> R.drawable.ic_logo_youtube
            lower.contains("google") -> R.drawable.ic_logo_google
            lower.contains("reddit") -> R.drawable.ic_logo_reddit
            lower.contains("netflix") -> R.drawable.ic_logo_netflix
            lower.contains("spotify") -> R.drawable.ic_logo_spotify
            lower.contains("amazon") -> R.drawable.ic_logo_amazon
            lower.contains("github") -> R.drawable.ic_logo_github
            lower.contains("facebook") -> R.drawable.ic_logo_facebook
            lower.contains("instagram") -> R.drawable.ic_logo_instagram
            lower.contains("telegram") -> R.drawable.ic_logo_telegram
            lower.contains("wikipedia") -> R.drawable.ic_logo_wikipedia
            else -> 0
        }
    }
}
