package com.gothwad.browser.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.gothwad.browser.AppContext
import com.gothwad.browser.utils.Utils
import com.gothwad.browser.webengine.WebEngineFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset


/**
 * Created by PDT on 24.08.2016.
 *
 * Class to store state of tab with webView
 */
@Entity(tableName = "tabs")
data class WebTabState(@PrimaryKey(autoGenerate = true)
                       var id: Long = 0,
                       var url: String = "",
                       var title: String = "",
                       var selected: Boolean = false,
                       var thumbnailHash: String? = null,
                       var faviconHash: String? = null,
                       var incognito: Boolean = false,
                       var position: Int = 0,
                       @Deprecated("This field is not used anymore")
                       @ColumnInfo(name = "wv_state", typeAffinity = ColumnInfo.BLOB)
                       var wvState: ByteArray? = null,
                       @ColumnInfo(name = "wv_state_file")
                       var wvStateFileName: String? = null,
                       var adblock: Boolean? = null,
                       var scale: Float? = null,
                       /**
                        * Per-tab "Desktop site" override. `null` means "follow the global default"
                        * (Config.desktopMode), which keeps existing installs behaving as before until
                        * the user actually toggles the menu entry for one tab.
                        */
                       @ColumnInfo(name = "desktop_mode")
                       var desktopMode: Boolean? = null) {
    companion object {
        val TAG: String = WebTabState::class.java.simpleName

        const val TAB_THUMBNAILS_DIR = "tabthumbs"
        const val TAB_WVSTATES_DIR = "wvstates"
        const val GECKO_SESSION_STATE_HASH_PREFIX = "gecko:"
    }

    @Ignore
    var thumbnail: Bitmap? = null
    @Ignore
    var savedState: Any? = null
    @Ignore
    var lastActiveTimestamp: Long = System.currentTimeMillis()
    @Ignore
    var isPageLoading: Boolean = false
    @delegate:Ignore
    val webEngine by lazy { WebEngineFactory.createWebEngine(this) }
    @Ignore
    var lastLoadingUrl: String? = null //this is last url appeared in WebViewClient.shouldOverrideUrlLoading callback
    @Ignore
    var lastUrlBeforeHome: String? = null
    @Ignore
    var blockedAds = 0
    @Ignore
    var blockedPopups = 0
    @Ignore
    var cachedHostConfig: HostConfig? = null

    constructor(context: Context, json: JSONObject) : this() {
        try {
            url = json.getString("url")
            title = json.getString("title")
            selected = json.getBoolean("selected")
            if (json.has("thumbnail")) {
                thumbnailHash = json.getString("thumbnail")
            }
            if (json.has("wv_state")) {
                val state = Utils.convertJsonToBundle(json.getJSONObject("wv_state"))
                if (state != null && !state.isEmpty) {
                    savedState = state
                }
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }

    }

    private suspend fun saveThumbnail(context: Context) {
        val originalThumb = this.thumbnail ?: return
        val thumbnailHash = this.thumbnailHash
        val url = url
        withContext(Dispatchers.IO) {
            synchronized(this@WebTabState) {
                val tabsThumbsDir = File(context.cacheDir.absolutePath + File.separator + TAB_THUMBNAILS_DIR)
                if (tabsThumbsDir.exists() || tabsThumbsDir.mkdir()) {
                    try {
                        val hash = Utils.MD5_Hash(url.toByteArray(Charset.defaultCharset()))
                        if (hash != null && hash != thumbnailHash) {
                            if (thumbnailHash != null) {
                                removeThumbnailFile()
                            }
                            val file = File(getThumbnailPath(hash))
                            var fos: FileOutputStream? = null
                            try {
                                fos = FileOutputStream(file)
                                // TV Optimization: Scale down thumbnail to max 360px width to save 85% RAM & storage
                                val maxDimension = 360
                                val scaledBitmap = if (originalThumb.width > maxDimension || originalThumb.height > maxDimension) {
                                    val scale = maxDimension.toFloat() / Math.max(originalThumb.width, originalThumb.height)
                                    val targetW = (originalThumb.width * scale).toInt().coerceAtLeast(1)
                                    val targetH = (originalThumb.height * scale).toInt().coerceAtLeast(1)
                                    Bitmap.createScaledBitmap(originalThumb, targetW, targetH, true)
                                } else {
                                    originalThumb
                                }
                                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, fos)
                                if (scaledBitmap != originalThumb) {
                                    scaledBitmap.recycle()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                fos?.close()
                            }

                            this@WebTabState.thumbnailHash = hash
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun getThumbnailPath(hash: String) =
        AppContext.get().cacheDir.absolutePath + File.separator + TAB_THUMBNAILS_DIR + File.separator + hash + ".png"

    private fun getWVStatePath(hash: String): String {
        return if (hash.startsWith(GECKO_SESSION_STATE_HASH_PREFIX)) {
            AppContext.get().filesDir.absolutePath + File.separator + TAB_WVSTATES_DIR + File.separator + hash.substring(
                GECKO_SESSION_STATE_HASH_PREFIX.length)
        } else {
            AppContext.get().filesDir.absolutePath + File.separator + TAB_WVSTATES_DIR + File.separator + hash
        }
    }

    fun removeFiles() {
        if (thumbnailHash != null) {
            removeThumbnailFile()
        }
        wvStateFileName?.apply {
            File(getWVStatePath(this)).delete()
            wvStateFileName = null
        }
    }

    private fun removeThumbnailFile() {
        if (thumbnailHash == null) return
        val thumbnailFile = File(getThumbnailPath(thumbnailHash!!))
        thumbnailFile.delete()
        thumbnailHash = null
    }

    fun restoreWebView(): Boolean {
        var state = savedState
        val stateFileName = wvStateFileName
        if (state != null) {
            webEngine.restoreState(state)
            return true
        } else if (stateFileName != null) {
            if (stateFileName.startsWith(GECKO_SESSION_STATE_HASH_PREFIX)) {
                return false
            }
            try {
                val stateBytes = File(getWVStatePath(stateFileName)).readBytes()
                state = webEngine.stateFromBytes(stateBytes)
                if (state == null) return false
                this.savedState = state
                webEngine.restoreState(state)
                return true
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }
        return false
    }

    fun saveWebViewStateToFile() {
        val state = savedState
        var stateFileName = wvStateFileName
        if (stateFileName != null && stateFileName.startsWith(GECKO_SESSION_STATE_HASH_PREFIX)) {
            File(getWVStatePath(stateFileName)).delete()
            stateFileName = null
        }
        if (state == null) return
        val stateBytes = when (state) {
            is Bundle -> {
                Utils.bundleToBytes(state) ?: return
            }
            else -> {
                state.toString().toByteArray(Charsets.UTF_8)
            }
        }
        if (stateFileName == null) {
            stateFileName = Utils.MD5_Hash(stateBytes) ?: return
        }
        try {
            val statesDir = File(AppContext.get().filesDir.absolutePath + File.separator + TAB_WVSTATES_DIR)
            if (statesDir.exists() || statesDir.mkdir()) {
                File(getWVStatePath(stateFileName)).writeBytes(stateBytes)
                wvStateFileName = stateFileName
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun trimMemory() {
        if (savedState == null) {
            try {
                savedState = webEngine.saveState()
            } catch (e: Throwable) {
                // ignore
            }
        }
        webEngine.trimMemory()
        thumbnail?.recycle()
        thumbnail = null
    }

    fun onPause() {
        webEngine.let {
            savedState = it.saveState()
        }
    }

    suspend fun updateThumbnail(context: Context, thumbnail: Bitmap) {
        this.thumbnail = thumbnail
        val url = url
        var hash = Utils.MD5_Hash(url.toByteArray(Charset.defaultCharset()))
        if (hash != null) {
            hash += hashCode()//to make thumbnails from different tabs unique even with same url
            if (hash != thumbnailHash) {
                saveThumbnail(context)
            }
        }
    }

    fun loadThumbnail(): Bitmap? {
        val hash = thumbnailHash ?: return null
        val thumbnailFile = File(getThumbnailPath(hash))
        if (thumbnailFile.exists()) {
            thumbnail = BitmapFactory.decodeFile(
                thumbnailFile.absolutePath,
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inMutable = false
                }
            )
            return thumbnail
        } else {
            thumbnailHash = null
        }
        return null
    }
}
