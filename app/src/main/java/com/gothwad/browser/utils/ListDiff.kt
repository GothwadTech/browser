package com.gothwad.browser.utils

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

/**
 * Small DiffUtil helper so the RecyclerView adapters in this app can obey AGENTS.md rule 5
 * ("never `notifyDataSetChanged()` for an arbitrary list change") without each popup re-implementing
 * the same 25-line [DiffUtil.Callback].
 *
 * Diffing is intentionally synchronous: every list in this app that goes through here is a
 * user-visible sidebar/grid of at most a few hundred rows, and `DiffUtil` on 300 rows is sub-millisecond.
 * Move detection is switched off above [MOVE_DETECTION_LIMIT] so a pathological list can never stall
 * the UI thread (a full rebind is cheaper than an O(D²) diff walk).
 */
object ListDiff {

    private const val MOVE_DETECTION_LIMIT = 200

    fun <T> submit(
        adapter: RecyclerView.Adapter<out RecyclerView.ViewHolder>,
        current: MutableList<T>,
        next: List<T>,
        idOf: (T) -> Any?,
        contentOf: (T) -> Any? = { it },
        contentOfOld: ((T) -> Any?)? = null,
        contentOfNew: ((T) -> Any?)? = null
    ) {
        val old = current
        if (old === next) return
        val oldSnapshot = ArrayList(old)
        val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldSnapshot.size
            override fun getNewListSize(): Int = next.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val a = idOf(oldSnapshot[oldItemPosition])
                val b = idOf(next[newItemPosition])
                // Fall back to identity when a row has no usable id (e.g. a not-yet-persisted item).
                return if (a == null || b == null) a === b else a == b
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val a = contentOfOld?.invoke(oldSnapshot[oldItemPosition]) ?: contentOf(oldSnapshot[oldItemPosition])
                val b = contentOfNew?.invoke(next[newItemPosition]) ?: contentOf(next[newItemPosition])
                return a == b
            }
        }, oldSnapshot.size <= MOVE_DETECTION_LIMIT)

        old.clear()
        old.addAll(next)
        result.dispatchUpdatesTo(adapter)
    }
}
