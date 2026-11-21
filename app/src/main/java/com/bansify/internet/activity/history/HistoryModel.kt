package com.bansify.internet.activity.history

import com.bansify.internet.model.HistoryItem
import com.bansify.internet.singleton.AppDatabase
import com.bansify.internet.utils.observable.ObservableValue
import com.bansify.internet.utils.activemodel.ActiveModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class HistoryModel: ActiveModel() {
    val lastLoadedItems = ObservableValue<List<HistoryItem>>(ArrayList())
    private var loading = false
    var searchQuery = ""
    var filterMode = FILTER_ALL

    fun loadItems(eraseOldResults: Boolean, offset: Long = 0) = modelScope.launch(Dispatchers.Main) {
        if (loading) {
            return@launch
        }
        loading = true

        val items = if (searchQuery.isNotBlank()) {
            val queryPattern = "%${searchQuery.trim()}%"
            AppDatabase.db.historyDao().search(queryPattern, queryPattern)
        } else {
            AppDatabase.db.historyDao().allByLimitOffset(offset)
        }

        val filtered = when (filterMode) {
            FILTER_TODAY -> {
                val startOfDay = getStartOfDay(0)
                items.filter { it.time >= startOfDay }
            }
            FILTER_YESTERDAY -> {
                val startOfYesterday = getStartOfDay(1)
                val startOfToday = getStartOfDay(0)
                items.filter { it.time in startOfYesterday until startOfToday }
            }
            FILTER_OLDER -> {
                val startOfYesterday = getStartOfDay(1)
                items.filter { it.time < startOfYesterday }
            }
            else -> items
        }

        lastLoadedItems.value = filtered
        loading = false
    }

    private fun getStartOfDay(daysAgo: Int): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    companion object {
        const val FILTER_ALL = 0
        const val FILTER_TODAY = 1
        const val FILTER_YESTERDAY = 2
        const val FILTER_OLDER = 3
    }
}
