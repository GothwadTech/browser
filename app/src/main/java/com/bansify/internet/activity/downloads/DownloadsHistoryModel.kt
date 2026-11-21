package com.bansify.internet.activity.downloads

import com.bansify.internet.model.Download
import com.bansify.internet.singleton.AppDatabase
import com.bansify.internet.utils.observable.ObservableValue
import com.bansify.internet.utils.activemodel.ActiveModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DownloadsHistoryModel: ActiveModel() {
  val allItems = ArrayList<Download>()
  val lastLoadedItems = ObservableValue<List<Download>>(ArrayList())
  private var loading = false

  fun loadNextItems() = modelScope.launch(Dispatchers.Main) {
    if (loading) {
      return@launch
    }
    loading = true

    val newItems = AppDatabase.db.downloadDao().allByLimitOffset(allItems.size.toLong())
    lastLoadedItems.value = newItems
    allItems.addAll(newItems)

    loading = false
  }
}