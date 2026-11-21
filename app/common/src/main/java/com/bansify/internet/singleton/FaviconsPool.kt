package com.bansify.internet.singleton

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.bansify.internet.AppContext
import com.bansify.internet.model.HostConfig
import com.bansify.internet.utils.FaviconExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

object FaviconsPool {
    const val FAVICONS_DIR = "favicons"
    const val FAVICON_PREFERRED_SIDE_SIZE = 120
    private val TAG: String = FaviconsPool::class.java.simpleName

    val faviconExtractor = FaviconExtractor()
    var databaseDelegate: DatabaseDelegate = object : DatabaseDelegate {}

    interface DatabaseDelegate {
        fun findByHostName(host: String): HostConfig? = null
        suspend fun update(hostConfig: HostConfig) {}
        suspend fun insert(newHostConfig: HostConfig) {}
    }

    private val cache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    // In-memory LRU cache mapping host to favicon filename (or empty string if known to not exist)
    private val hostFileCache: LruCache<String, String> = LruCache(500)

    fun getFaviconFile(host: String): File? {
        val cachedFileName = hostFileCache.get(host)
        val favIconsDir = File(favIconsDir())

        if (cachedFileName != null) {
            if (cachedFileName.isEmpty()) {
                return null
            }
            val file = File(favIconsDir, cachedFileName)
            if (file.exists() && file.length() > 0) {
                return file
            }
        }

        try {
            val hostConfig = databaseDelegate.findByHostName(host)
            val faviconFileName = hostConfig?.favicon ?: (host.hashCode().toString() + ".png")
            val faviconFile = File(favIconsDir, faviconFileName)
            if (faviconFile.exists() && faviconFile.length() > 0) {
                hostFileCache.put(host, faviconFileName)
                return faviconFile
            } else {
                hostFileCache.put(host, "")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed finding favicon file for $host: ${e.message}")
        }

        // Trigger background fetch if not found
        CoroutineScope(Dispatchers.IO).launch {
            try {
                get(host)
            } catch (_: Exception) {}
        }
        return null
    }

    fun getFaviconFileInputStream(host: String): java.io.InputStream? {
        val file = getFaviconFile(host) ?: return null
        return try {
            java.io.FileInputStream(file)
        } catch (e: Exception) {
            Log.w(TAG, "Failed opening favicon stream for $host: ${e.message}")
            null
        }
    }

    fun getFromMemoryOrDisk(host: String): Bitmap? {
        val cached = cache.get(host)
        if (cached != null) return cached
        try {
            val faviconFile = getFaviconFile(host)
            if (faviconFile != null && faviconFile.exists() && faviconFile.length() > 0) {
                val bitmap = BitmapFactory.decodeFile(faviconFile.absolutePath)
                if (bitmap != null) {
                    cache.put(host, bitmap)
                    return bitmap
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fast favicon disk read failed for $host: ${e.message}")
        }
        return null
    }

    suspend fun get(urlOrHost: String): Bitmap? {
        Log.d(TAG, "get: $urlOrHost")
        if (!urlOrHost.startsWith("http://", true) && !urlOrHost.startsWith("https://", true)) {
            //host passed?
            if (urlOrHost.contains("://")) {
                //not http or https
                return null
            }
            //try https first
            val httpsResult = get("https://$urlOrHost")
            if (httpsResult != null) {
                return httpsResult
            }
            return get("http://$urlOrHost")
        }
        try {
            val urlObj = URL(urlOrHost)
            val host = urlObj.host
            if (host != null) {
                val hostBitmap = cache.get(host)
                if (hostBitmap != null) {
                    return hostBitmap
                }
                val hostConfig = databaseDelegate.findByHostName(host)
                if (hostConfig != null) {
                    val faviconFileName = hostConfig.favicon
                    if (faviconFileName != null) {
                        Log.d(TAG, "get: favicon found in db for $host")
                        val bitmap = withContext(Dispatchers.IO) {
                            val favIconsDir =
                                File(favIconsDir())
                            if (!favIconsDir.exists() && !favIconsDir.mkdir()) return@withContext null
                            val faviconFile = File(favIconsDir, faviconFileName)
                            if (faviconFile.exists()) {
                                BitmapFactory.decodeFile(faviconFile.absolutePath)
                            } else {
                                null
                            }
                        }
                        if (bitmap != null) {
                            Log.d(TAG, "get: favicon loaded from file for $host")
                            cache.put(host, bitmap)
                            return bitmap
                        }
                    }
                } else {
                    Log.d(TAG, "get: favicon not found in db for $host")
                }

                val favicons = try {
                    withContext(Dispatchers.IO) { faviconExtractor.extractFavIconsFromURL(urlObj) }
                } catch (e: Exception) {
                    e.printStackTrace()
                    ArrayList()
                }
                Log.d(TAG, "get: favicons found: ${favicons.size}")
                while (favicons.isNotEmpty()) {
                    val icon = chooseNearestSizeIcon(favicons, FAVICON_PREFERRED_SIDE_SIZE, FAVICON_PREFERRED_SIDE_SIZE)!!
                    val bitmap = try {
                        downloadIcon(icon)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                    if (bitmap != null) {
                        Log.d(TAG, "get: favicon downloaded for $host")
                        cache.put(host, bitmap)
                        saveFavicon(host, bitmap, hostConfig)
                        return bitmap
                    } else {
                        Log.d(TAG, "get: favicon download failed for ${icon.src}")
                    }
                    favicons.remove(icon)
                }
                //try to get favicon from webview
                withContext(Dispatchers.Main) {
                    WebView(AppContext.get()).apply {
                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                                super.onReceivedIcon(view, icon)
                                if (icon != null) {
                                    Log.d(TAG, "get: favicon received from webview for $host")
                                    cache.put(host, icon)
                                    CoroutineScope(Dispatchers.IO).launch {
                                        saveFavicon(host, icon, hostConfig)
                                    }
                                }
                            }
                        }
                        loadUrl(urlOrHost)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun clear() {
        cache.evictAll()
        hostFileCache.evictAll()
    }

    fun favIconsDir(): String {
        return AppContext.get().cacheDir.absolutePath + File.separator + FAVICONS_DIR
    }

    private suspend fun saveFavicon(host: String, bitmap: Bitmap, hostConfig: HostConfig?) = withContext(Dispatchers.IO) {
        val favIconsDir = File(favIconsDir())
        if (!favIconsDir.exists() && !favIconsDir.mkdir()) return@withContext
        val faviconFileName = host.hashCode().toString() + ".png"
        val faviconFile = File(favIconsDir, faviconFileName)
        if (faviconFile.exists()) {
            faviconFile.delete()
        }
        faviconFile.createNewFile()
        faviconFile.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        hostFileCache.put(host, faviconFileName)
        if (hostConfig != null) {
            hostConfig.favicon = faviconFileName
            databaseDelegate.update(hostConfig)
        } else {
            val newHostConfig = HostConfig(host)
            newHostConfig.favicon = faviconFileName
            databaseDelegate.insert(newHostConfig)
        }
    }

    private suspend fun downloadIcon(iconInfo: FaviconExtractor.IconInfo): Bitmap? = withContext(Dispatchers.IO) {
        if (iconInfo.type?.contains("svg", ignoreCase = true) == true || iconInfo.src.endsWith(".svg", ignoreCase = true)) {
            return@withContext null
        }
        try {
            val url = URL(iconInfo.src)
            val connection = url.openConnection()
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.connect()
            val bytes = connection.getInputStream().use { it.readBytes() }
            if (bytes.isEmpty()) return@withContext null
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            val width = options.outWidth
            val height = options.outHeight
            if (width <= 0 || height <= 0) return@withContext null
            val scale = Math.max(1, Math.max(width / 512, height / 512))
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = scale
            }
            return@withContext BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        } catch (e: Throwable) {
            return@withContext null
        }
    }

    private fun chooseNearestSizeIcon(icons: List<FaviconExtractor.IconInfo>, w: Int, h: Int): FaviconExtractor.IconInfo? {
        var nearestIcon: FaviconExtractor.IconInfo? = null
        var nearestDiff = Int.MAX_VALUE
        for (icon in icons) {
            val diff = Math.abs(icon.width - w) + Math.abs(icon.height - h)
            if (diff < nearestDiff) {
                nearestDiff = diff
                nearestIcon = icon
            }
        }
        return nearestIcon
    }
}