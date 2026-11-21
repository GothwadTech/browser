package com.bansify.internet.activity.main

import android.net.Uri
import android.util.Log
import com.brave.adblock.AdBlockClient
import com.brave.adblock.AdBlockClient.FilterOption
import com.brave.adblock.Utils
import com.bansify.internet.AppContext
import com.bansify.internet.BrowserApp
import com.bansify.internet.Config
import com.bansify.internet.utils.activemodel.ActiveModel
import com.bansify.internet.utils.observable.ObservableValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.net.URL
import java.util.Calendar

class AdblockModel : ActiveModel() {
    companion object {
        val TAG: String = AdblockModel::class.java.simpleName

        const val SERIALIZED_LIST_FILE = "adblock_ser.dat"
        const val AUTO_UPDATE_INTERVAL_MINUTES = 60 * 24 * 30 // 30 days
    }

    @Volatile
    private var client: AdBlockClient? = null
    val clientLoading = ObservableValue(false)
    val config = AppContext.provideConfig()

    init {
        loadAdBlockList(false)
    }

    fun loadAdBlockList(forceReload: Boolean) = modelScope.launch(Dispatchers.IO) {
        if (clientLoading.value) return@launch
        clientLoading.value = true

        val checkDate = Calendar.getInstance()
        checkDate.timeInMillis = config.adBlockListLastUpdate
        checkDate.add(Calendar.MINUTE, AUTO_UPDATE_INTERVAL_MINUTES)
        val now = Calendar.getInstance()
        val needUpdate = forceReload || checkDate.before(now)

        val newClient = AdBlockClient()
        var success = false
        val serializedFile = File(BrowserApp.instance.filesDir, SERIALIZED_LIST_FILE)

        // Try fast disk deserialization if up-to-date
        if (!needUpdate && serializedFile.exists()) {
            try {
                if (newClient.deserialize(serializedFile.absolutePath)) {
                    success = true
                    Log.d(TAG, "AdBlock list deserialized successfully from disk cache.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to deserialize cached AdBlock list: ${e.message}")
            }
        }

        // If not cached or update needed, download and parse
        if (!success) {
            try {
                val urlStr = config.adBlockListURL.value.ifEmpty { Config.DEFAULT_ADBLOCK_LIST_URL }
                Log.d(TAG, "Downloading AdBlock filter list from: $urlStr")
                val connection = URL(urlStr).openConnection()
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                val easyList = connection.inputStream.bufferedReader().use { it.readText() }
                if (newClient.parse(easyList)) {
                    success = true
                    try {
                        newClient.serialize(serializedFile.absolutePath)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to serialize AdBlock client to disk: ${e.message}")
                    }
                    config.adBlockListLastUpdate = now.timeInMillis
                    Log.d(TAG, "AdBlock list downloaded, parsed and cached.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading AdBlock list: ${e.message}")
                // Fallback to existing disk cache if available
                if (!success && serializedFile.exists()) {
                    try {
                        if (newClient.deserialize(serializedFile.absolutePath)) {
                            success = true
                            Log.d(TAG, "Fallback to existing disk cache succeeded.")
                        }
                    } catch (e2: Exception) {
                        Log.e(TAG, "Fallback deserialize failed: ${e2.message}")
                    }
                }
            }
        }

        if (success) {
            this@AdblockModel.client = newClient
        }
        clientLoading.value = false
    }

    fun isAd(url: Uri, type: String?, baseUri: Uri): Boolean {
        val currentClient = client ?: return false
        val baseHost = baseUri.host ?: return false
        val filterOption = try {
            mapRequestToFilterOption(url, type)
        } catch (e: Exception) {
            return false
        }
        val urlString = url.toString()
        return try {
            currentClient.matches(urlString, filterOption, baseHost)
        } catch (e: Exception) {
            false
        }
    }

    private fun mapRequestToFilterOption(url: Uri?, type: String?): FilterOption? {
        if (type != null) {
            if (type == "image" || type.contains("image/")) {
                return FilterOption.IMAGE
            }
            if (type == "style" || type.contains("/css")) {
                return FilterOption.CSS
            }
            if (type == "script" || type.contains("javascript")) {
                return FilterOption.SCRIPT
            }
            if (type.contains("video/")) {
                return FilterOption.OBJECT
            }
        }
        if (url != null) {
            if (Utils.uriHasExtension(url, "css")) {
                return FilterOption.CSS
            }
            if (Utils.uriHasExtension(url, "js")) {
                return FilterOption.SCRIPT
            }
            if (Utils.uriHasExtension(
                    url,
                    "png",
                    "jpg",
                    "jpeg",
                    "webp",
                    "svg",
                    "gif",
                    "bmp",
                    "tiff"
                )
            ) {
                return FilterOption.IMAGE
            }
            if (Utils.uriHasExtension(url, "mp4", "mov", "avi")) {
                return FilterOption.OBJECT
            }
        }
        return FilterOption.UNKNOWN
    }
}