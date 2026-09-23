package com.gothwad.browser

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.gothwad.browser.activity.IncognitoModeMainActivity
import com.gothwad.browser.activity.main.MainActivity
import com.gothwad.browser.model.HostConfig
import com.gothwad.browser.service.keepalive.BrowserKeepAliveService
import com.gothwad.browser.singleton.AppDatabase
import com.gothwad.browser.singleton.FaviconsPool
import com.gothwad.browser.utils.activemodel.ActiveModelsRepository
import com.gothwad.browser.webengine.webview.WebViewWebEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.net.CookieHandler
import java.net.CookieManager
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Created by PDT on 09.09.2016.
 */
class BrowserApp : Application(), Application.ActivityLifecycleCallbacks {
    companion object {
        lateinit var instance: BrowserApp
        const val CHANNEL_ID_DOWNLOADS: String = "downloads"
        const val MAIN_PREFS_NAME = "main.xml"
        val TAG = BrowserApp::class.simpleName
    }

    lateinit var threadPool: ThreadPoolExecutor
        private set

    var needToExitProcessAfterMainActivityFinish = false
    var needRestartMainActivityAfterExitingProcess = false

    var isAppInForeground: Boolean = false
        private set
    private var currentActivityRef: WeakReference<Activity>? = null

    val currentActivity: Activity?
        get() = currentActivityRef?.get()

    override fun onCreate() {
        Log.i(TAG, "onCreate")
        super.onCreate()

        instance = this

        AppContext.init(this, Config(getSharedPreferences(MAIN_PREFS_NAME, MODE_MULTI_PROCESS)))

        val maxThreadsInOfflineJobsPool = Runtime.getRuntime().availableProcessors()
        threadPool = ThreadPoolExecutor(0, maxThreadsInOfflineJobsPool, 20,
                TimeUnit.SECONDS, ArrayBlockingQueue(maxThreadsInOfflineJobsPool))

        initWebEngineStuff()

        initNotificationChannels()

        ActiveModelsRepository.init(this)

        when (AppContext.provideConfig().theme.value) {
            Config.Theme.BLACK_AMOLED,
            Config.Theme.BLACK_CHARCOAL,
            Config.Theme.BLACK_MIDNIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            Config.Theme.WHITE_PURE,
            Config.Theme.WHITE_WARM,
            Config.Theme.WHITE_COOL -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        registerActivityLifecycleCallbacks(this)

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                isAppInForeground = true
                Log.d(TAG, "Process entered foreground -> stopping keep-alive service")
                BrowserKeepAliveService.stop(this@BrowserApp)
            }

            override fun onStop(owner: LifecycleOwner) {
                isAppInForeground = false
                Log.d(TAG, "Process entered background -> starting keep-alive service")
                BrowserKeepAliveService.start(this@BrowserApp)
            }
        })
    }

    fun isCurrentSessionIncognito(): Boolean {
        val act = currentActivity
        if (act is IncognitoModeMainActivity) return true
        if (act is MainActivity) {
            if (act.config.incognitoMode) return true
            if (act.tabsModel.currentTab.value?.incognito == true) return true
        }
        return AppContext.provideConfig().incognitoMode
    }

    @Suppress("KotlinConstantConditions")
    private fun initWebEngineStuff() {
        Log.i(TAG, "initWebEngineStuff")

        try {
            Class.forName("com.gothwad.browser.webengine.webview.WebViewWebEngine")
        } catch (e: ClassNotFoundException) {
            throw AssertionError(e) // WebViews are always available
        }

        val cookieManager = CookieManager()
        CookieHandler.setDefault(cookieManager)
        FaviconsPool.databaseDelegate = object : FaviconsPool.DatabaseDelegate {
            override fun findByHostName(host: String): HostConfig? {
                return AppDatabase.db.hostsDao().findByHostName(host)
            }

            override suspend fun update(hostConfig: HostConfig) {
                AppDatabase.db.hostsDao().update(hostConfig)
            }

            override suspend fun insert(newHostConfig: HostConfig) {
                AppDatabase.db.hostsDao().insert(newHostConfig)
            }
        }
    }

    private fun initNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.downloads)
            val descriptionText = getString(R.string.downloads_notifications_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID_DOWNLOADS, name, importance)
            channel.description = descriptionText
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {
        currentActivityRef = WeakReference(activity)
    }
    override fun onActivityPaused(activity: Activity) {
        if (currentActivityRef?.get() === activity) {
            currentActivityRef = null
        }
    }
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        Log.i(TAG, "onActivityDestroyed: " + activity.javaClass.simpleName)
        if (needToExitProcessAfterMainActivityFinish && activity is MainActivity) {
            Log.i(TAG, "onActivityDestroyed: exiting process")
            if (needRestartMainActivityAfterExitingProcess) {
                Log.i(TAG, "onActivityDestroyed: restarting main activity")
                val intent = Intent(this@BrowserApp, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
            }
            exitProcess(0)
        }
    }
}
