package com.gothwad.browser.activity.main.view.home

import android.app.ActivityManager
import com.gothwad.browser.R
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Environment
import android.os.StatFs
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object SystemMonitorHelper {

    private var lastRxBytes: Long = 0L
    private var lastTxBytes: Long = 0L
    private var lastSampleTime: Long = 0L

    var currentDownloadSpeedStr: String = "0 KB/s"
        private set
    var currentUploadSpeedStr: String = "0 KB/s"
        private set

    init {
        try {
            lastRxBytes = TrafficStats.getTotalRxBytes()
            lastTxBytes = TrafficStats.getTotalTxBytes()
            lastSampleTime = System.currentTimeMillis()
        } catch (e: Throwable) {
            lastRxBytes = 0L
            lastTxBytes = 0L
            lastSampleTime = System.currentTimeMillis()
        }
    }

    fun updateNetworkSpeed() {
        try {
            val now = System.currentTimeMillis()
            val timeDiff = (now - lastSampleTime).coerceAtLeast(1)
            val curRx = TrafficStats.getTotalRxBytes()
            val curTx = TrafficStats.getTotalTxBytes()

            if (lastRxBytes > 0 && curRx >= lastRxBytes) {
                val rxDiff = curRx - lastRxBytes
                val rxSpeedPerSec = (rxDiff * 1000) / timeDiff
                currentDownloadSpeedStr = formatSpeed(rxSpeedPerSec)
            } else {
                currentDownloadSpeedStr = "0 KB/s"
            }

            if (lastTxBytes > 0 && curTx >= lastTxBytes) {
                val txDiff = curTx - lastTxBytes
                val txSpeedPerSec = (txDiff * 1000) / timeDiff
                currentUploadSpeedStr = formatSpeed(txSpeedPerSec)
            } else {
                currentUploadSpeedStr = "0 KB/s"
            }

            lastRxBytes = curRx
            lastTxBytes = curTx
            lastSampleTime = now
        } catch (e: Throwable) {
            currentDownloadSpeedStr = "0 KB/s"
            currentUploadSpeedStr = "0 KB/s"
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return try {
            when {
                bytesPerSec >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f GB/s", bytesPerSec / (1024.0 * 1024.0 * 1024.0))
                bytesPerSec >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
                bytesPerSec >= 1024 -> String.format(Locale.US, "%.0f KB/s", bytesPerSec / 1024.0)
                else -> "$bytesPerSec B/s"
            }
        } catch (e: Throwable) {
            "0 KB/s"
        }
    }

    fun getFormattedTime(context: Context): String {
        return try {
            val sdf = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
            sdf.format(Date())
        } catch (e: Throwable) {
            context.getString(R.string.home_time_fallback)
        }
    }

    fun getTimeSubtitle(context: Context): String {
        return try {
            val cal = Calendar.getInstance()
            cal.timeZone.displayName ?: context.getString(R.string.home_local_time)
        } catch (e: Throwable) {
            context.getString(R.string.home_local_time)
        }
    }

    fun getFormattedDate(context: Context): String {
        return try {
            val sdf = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
            sdf.format(Date())
        } catch (e: Throwable) {
            context.getString(R.string.home_date_fallback)
        }
    }

    fun getCalendarSubtitle(context: Context): String {
        return try {
            val cal = Calendar.getInstance()
            val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
            val weekOfYear = cal.get(Calendar.WEEK_OF_YEAR)
            context.getString(R.string.home_calendar_day_week, dayOfYear, weekOfYear)
        } catch (e: Throwable) {
            context.getString(R.string.home_calendar_fallback)
        }
    }

    fun getNetworkInfo(context: Context): Pair<String, String> {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm == null) {
                return Pair(
                    context.getString(R.string.home_network_offline),
                    context.getString(R.string.home_network_none)
                )
            }

            val activeNetwork = cm.activeNetwork
            val capabilities = cm.getNetworkCapabilities(activeNetwork)

            if (capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return Pair(
                    context.getString(R.string.home_network_offline),
                    context.getString(R.string.home_network_no_internet)
                )
            }

            val typeStr: String = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                    context.getString(R.string.home_network_wifi)
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                    context.getString(R.string.home_network_ethernet)
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                    context.getString(R.string.home_network_cellular)
                else -> context.getString(R.string.home_network_online)
            }

            val detailStr = "↓ $currentDownloadSpeedStr  ↑ $currentUploadSpeedStr"
            return Pair(typeStr, detailStr)
        } catch (e: Throwable) {
            return Pair(
                context.getString(R.string.home_network_online),
                "↓ $currentDownloadSpeedStr  ↑ $currentUploadSpeedStr"
            )
        }
    }

    data class RamStats(
        val totalBytes: Long,
        val availBytes: Long,
        val usedBytes: Long,
        val usedPercent: Int
    )

    fun getRamStats(context: Context): RamStats {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)

            val total = memInfo.totalMem
            val avail = memInfo.availMem
            val used = (total - avail).coerceAtLeast(0)
            val percent = if (total > 0) ((used * 100) / total).toInt().coerceIn(0, 100) else 0

            RamStats(
                totalBytes = total,
                availBytes = avail,
                usedBytes = used,
                usedPercent = percent
            )
        } catch (e: Throwable) {
            RamStats(0L, 0L, 0L, 0)
        }
    }

    fun formatBytes(bytes: Long): String {
        return try {
            when {
                bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
                bytes >= 1024L * 1024L -> String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0))
                bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
                else -> "$bytes B"
            }
        } catch (e: Throwable) {
            "0 B"
        }
    }

    data class StorageStats(
        val totalBytes: Long,
        val freeBytes: Long,
        val usedBytes: Long,
        val usedPercent: Int
    )

    fun getStorageStats(): StorageStats {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val total = totalBlocks * blockSize
            val free = availableBlocks * blockSize
            val used = (total - free).coerceAtLeast(0)
            val percent = if (total > 0) ((used * 100) / total).toInt().coerceIn(0, 100) else 0

            StorageStats(
                totalBytes = total,
                freeBytes = free,
                usedBytes = used,
                usedPercent = percent
            )
        } catch (e: Throwable) {
            StorageStats(0L, 0L, 0L, 0)
        }
    }

}
