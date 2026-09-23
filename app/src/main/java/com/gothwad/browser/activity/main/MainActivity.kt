package com.gothwad.browser.activity.main

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Process
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gothwad.browser.AppContext
import com.gothwad.browser.BrowserApp
import com.gothwad.browser.R
import com.gothwad.browser.activity.IncognitoModeMainActivity
import com.gothwad.browser.activity.history.HistoryActivity
import com.gothwad.browser.activity.main.dialogs.SearchEngineMenuPopup
import com.gothwad.browser.activity.main.dialogs.WebsiteMenuPopup
import com.gothwad.browser.activity.main.view.ActionBar
import com.gothwad.browser.browser.tabs.TabsRowDialog
import com.gothwad.browser.databinding.ActivityMainBinding
import com.gothwad.browser.model.Download
import com.gothwad.browser.utils.HardwareMousePointerManager
import com.gothwad.browser.service.downloads.DownloadService
import com.gothwad.browser.settings.SettingsModel
import com.gothwad.browser.singleton.AppLockManager
import com.gothwad.browser.singleton.FaviconsPool
import com.gothwad.browser.utils.BackNavigationEventsAdapter
import com.gothwad.browser.utils.BaseAnimationListener
import com.gothwad.browser.utils.VoiceSearchHelper
import com.gothwad.browser.utils.activemodel.ActiveModelsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

open class MainActivity : AppCompatActivity(), ActionBar.Callback {
    companion object {
        private val TAG = MainActivity::class.java.simpleName
        const val VOICE_SEARCH_REQUEST_CODE = 10001
        const val MY_PERMISSIONS_REQUEST_POST_NOTIFICATIONS_ACCESS = 10003
        const val MY_PERMISSIONS_REQUEST_EXTERNAL_STORAGE_ACCESS = 10004
        const val PICK_FILE_REQUEST_CODE = 10005
        const val REQUEST_CODE_HISTORY_ACTIVITY = 10006
        const val REQUEST_CODE_UNKNOWN_APP_SOURCES = 10007
        const val KEY_PROCESS_ID_TO_KILL = "proc_id_to_kill"
        const val MY_PERMISSIONS_REQUEST_VOICE_SEARCH_PERMISSIONS = 10008
        private const val COMMON_REQUESTS_START_CODE = 10100
    }

    internal lateinit var vb: ActivityMainBinding
    internal lateinit var viewModel: MainActivityViewModel
    internal lateinit var tabsModel: TabsModel
    internal lateinit var settingsModel: SettingsModel
    internal lateinit var adblockModel: AdblockModel
    internal lateinit var autoUpdateModel: AutoUpdateModel
    internal lateinit var uiHandler: Handler
    internal var isFullscreen: Boolean = false
    internal lateinit var prefs: SharedPreferences
    internal val config = AppContext.provideConfig()
    internal val voiceSearchHelper = VoiceSearchHelper(this, VOICE_SEARCH_REQUEST_CODE, MY_PERMISSIONS_REQUEST_VOICE_SEARCH_PERMISSIONS)
    internal var lastCommonRequestsCode = COMMON_REQUESTS_START_CODE
    internal var downloadService: DownloadService? = null
    internal var downloadIntent: Download? = null
    var openUrlInExternalAppDialog: AlertDialog? = null
    internal var linkActionsMenu: PopupMenu? = null
    var currentTabsDialog: TabsRowDialog? = null
    internal var topTabsAdapter: com.gothwad.browser.browser.tabs.TopTabsAdapter? = null

    internal val progressBarHideRunnable = Runnable {
        val anim = AnimationUtils.loadAnimation(this@MainActivity, android.R.anim.fade_out)
        anim.setAnimationListener(object : BaseAnimationListener() {
            override fun onAnimationEnd(animation: Animation) {
                vb.progressBar.visibility = View.GONE
                vb.progressBarGeneric.visibility = View.GONE
            }
        })
        vb.progressBar.startAnimation(anim)
        vb.progressBarGeneric.visibility = View.GONE
    }

    private val mConnectivityChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetworkInfo
            val isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting
            val tab = tabsModel.currentTab.value ?: return
            tab.webEngine.setNetworkAvailable(isConnected)
        }
    }

    internal val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            backNavigationEventsAdapter.dispatchSystemBackNavigationEvent()
        }
    }

    var lastBackPressTime: Long = 0L

    internal val backNavigationEventsAdapter = BackNavigationEventsAdapter(
        onEmulatedBackEvent = {
            if (!hideSoftwareKeyboardIfVisible()) {
                handleBackNavigation()
            }
        }
    )

    internal val downloadServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as? DownloadService.Binder
            if (binder == null) {
                Log.e(TAG, "Download service connection failed")
                uiHandler.postDelayed({
                    bindService(Intent(this@MainActivity, DownloadService::class.java), this, Context.BIND_AUTO_CREATE)
                }, 1000)
                return
            }
            downloadService = binder.service
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            downloadService = null
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val incognitoMode = config.incognitoMode
        if (incognitoMode xor (this is IncognitoModeMainActivity)) {
            switchProcess(incognitoMode, intent?.extras)
            finish()
            return
        }
        val pidToKill = intent?.getIntExtra(KEY_PROCESS_ID_TO_KILL, -1) ?: -1
        if (pidToKill != -1) {
            Process.killProcess(pidToKill)
        }

        if (AppLockManager.requiresUnlock(this)) {
            val lockIntent = Intent(this, com.gothwad.browser.activity.lock.AppLockActivity::class.java)
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivity(lockIntent)
        }

        viewModel = ActiveModelsRepository.get(MainActivityViewModel::class, this)
        if (incognitoMode) {
            viewModel.prepareSwitchToIncognito()
        }
        settingsModel = ActiveModelsRepository.get(SettingsModel::class, this)
        adblockModel = ActiveModelsRepository.get(AdblockModel::class, this)
        tabsModel = ActiveModelsRepository.get(TabsModel::class, this)
        autoUpdateModel = ActiveModelsRepository.get(AutoUpdateModel::class, this)
        uiHandler = Handler()
        prefs = getSharedPreferences(BrowserApp.MAIN_PREFS_NAME, Context.MODE_PRIVATE)
        applyScreenOrientation()
        vb = ActivityMainBinding.inflate(layoutInflater)
        setContentView(vb.root)

        vb.ivMiniatures.visibility = View.INVISIBLE
        vb.llBottomPanel.visibility = View.GONE
        vb.rlActionBar.visibility = View.VISIBLE
        vb.rlActionBar.alpha = 1f
        vb.rlActionBar.translationY = 0f
        vb.flWebViewContainer.translationY = 0f
        vb.vNativeHome.translationY = 0f
        vb.vNativeHome.onNavigateUrl = { url -> navigate(url) }
        vb.vNativeHome.updateIncognitoState(incognitoMode, this)
        vb.progressBar.visibility = View.GONE

        setupHeaderClickListeners(incognitoMode)
        setupTopTabBar()
        setupDragAndDropListener()
        setupSettingsSubscriptions()

        tabsModel.currentTab.subscribe(this) {
            vb.vActionBar.setAddressBoxText(it?.url ?: "")
            it?.let { onWebViewUpdated(it) }
        }

        tabsModel.tabsStates.subscribe(this, false) {
            updateTabCountBadge()
            currentTabsDialog?.refreshData()
            if (it.isEmpty()) {
                vb.flWebViewContainer.removeAllViews()
            }
        }
        updateTabCountBadge()

        onBackPressedDispatcher.addCallback(onBackPressedCallback)
        applySoftInputMode()
        HardwareMousePointerManager.applyToWindow(window)

        window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            if (config.disableVirtualKeyboard && newFocus != null) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(newFocus.windowToken, 0)
            }
        }

        loadState()
    }

    internal fun applySoftInputMode() {
        if (config.disableVirtualKeyboard) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
            val currentFocusView = currentFocus ?: vb.flWebViewContainer
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(currentFocusView.windowToken, 0)
            imm?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
            vb.vActionBar.updateVirtualKeyboardPolicy()
        } else {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED)
            vb.vActionBar.updateVirtualKeyboardPolicy()
        }
    }

    override fun initiateVoiceSearch() = initiateVoiceSearchInternal()

    override fun closeWindow() {
        lifecycleScope.launch {
            if (config.incognitoMode) {
                toggleIncognitoMode(false).join()
            }
            finish()
        }
    }

    override fun showDownloads() = showDownloadsActivity()
    override fun showHistory() = showHistoryActivity()
    override fun showFavorites() = showFavoritesDialog()
    override fun showSettings() = showSettingsDialog()
    override fun onExtendedAddressBarMode() { vb.llBottomPanel.visibility = View.INVISIBLE }
    override fun onUrlInputDone() {}
    override fun toggleHeader() = toggleMenu()
    override fun onSearchEngineIconClicked(anchorView: View) {
        SearchEngineMenuPopup(this).show(anchorView)
    }
    override fun onSecurityIconClicked(anchorView: View) {
        WebsiteMenuPopup(this).show(anchorView)
    }

    override fun onDestroy() {
        voiceSearchHelper.destroy()
        if (::tabsModel.isInitialized) {
            tabsModel.onDetachActivity()
        }
        super.onDestroy()
    }

    @SuppressLint("MissingSuperCall")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.data != null) {
            handleIntent(intent)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::tabsModel.isInitialized) {
            tabsModel.onTrimMemory()
        }
        if (level >= TRIM_MEMORY_RUNNING_LOW || level >= TRIM_MEMORY_MODERATE) {
            FaviconsPool.clear()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (::tabsModel.isInitialized) {
            tabsModel.onTrimMemory()
        }
        FaviconsPool.clear()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (voiceSearchHelper.processPermissionsResult(requestCode, permissions, grantResults)) return
        if (tabsModel.currentTab.value?.webEngine?.onPermissionsResult(requestCode, permissions, grantResults) == true) return
        if (grantResults.isEmpty()) return
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_EXTERNAL_STORAGE_ACCESS,
            MY_PERMISSIONS_REQUEST_POST_NOTIFICATIONS_ACCESS -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startDownload()
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (voiceSearchHelper.processActivityResult(requestCode, resultCode, data)) return
        when (requestCode) {
            PICK_FILE_REQUEST_CODE -> {
                tabsModel.currentTab.value?.webEngine?.onFilePicked(resultCode, data)
            }
            REQUEST_CODE_HISTORY_ACTIVITY -> if (resultCode == Activity.RESULT_OK) {
                val url = data?.getStringExtra(HistoryActivity.KEY_URL)
                val inNewTab = data?.getBooleanExtra(HistoryActivity.KEY_OPEN_IN_NEW_TAB, false) ?: false
                if (url != null) {
                    if (inNewTab) {
                        openInNewTab(url, tabsModel.tabsStates.size, needToHideMenuOverlay = true, navigateImmediately = true)
                    } else {
                        navigate(url)
                    }
                }
                hideMenuOverlay()
            }
            REQUEST_CODE_UNKNOWN_APP_SOURCES -> if (autoUpdateModel.needToShowUpdateDlgAgain) {
                autoUpdateModel.showUpdateDialogIfNeeded(this)
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onStart() {
        super.onStart()
        if (AppLockManager.requiresUnlock(this)) {
            startActivity(Intent(this, com.gothwad.browser.activity.lock.AppLockActivity::class.java))
        }
        bindService(Intent(this, DownloadService::class.java), downloadServiceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        unbindService(downloadServiceConnection)
        downloadService = null
    }

    override fun onResume() {
        super.onResume()
        applySoftInputMode()
        HardwareMousePointerManager.applyToWindow(window)
        val intentFilter = IntentFilter("android.net.conn.CONNECTIVITY_CHANGE")
        registerReceiver(mConnectivityChangeReceiver, intentFilter)
        tabsModel.currentTab.value?.webEngine?.onResume()
    }

    override fun onPause() {
        unregisterReceiver(mConnectivityChangeReceiver)
        tabsModel.currentTab.value?.let { tab ->
            tab.webEngine.onPause()
            tab.onPause()
            lifecycleScope.launch(Dispatchers.IO) {
                tabsModel.saveTab(tab)
            }
        }
        super.onPause()
    }

    fun navigate(url: String) = navigateInternal(url)
    fun navigateBack(goHomeIfNoHistory: Boolean = false) = navigateBackInternal(goHomeIfNoHistory)
    fun refresh() = refreshInternal()
    fun applyScreenOrientation() = applyScreenOrientationInternal()
    suspend fun showPopupBlockOptions() = showPopupBlockOptionsInternal()

    override fun attachBaseContext(newBase: Context?) {
        if (newBase != null) {
            val cfg = AppContext.provideConfig()
            val scale = cfg.getUiScaleDensityMultiplier()
            if (kotlin.math.abs(scale - 1.0f) > 0.001f) {
                val overrideConfig = android.content.res.Configuration(newBase.resources.configuration)
                val targetDpi = (newBase.resources.displayMetrics.densityDpi * scale).toInt().coerceAtLeast(1)
                overrideConfig.densityDpi = targetDpi
                val scaledContext = newBase.createConfigurationContext(overrideConfig)
                super.attachBaseContext(scaledContext)
                return
            }
        }
        super.attachBaseContext(newBase)
    }

    fun focusDefaultNavigationElement() { vb.ibMenu.requestFocus() }
    fun showHome() { showHomeScreen() }
    fun handleBack() { handleBackNavigation() }
    fun handleMenu() { showBrowserSidebar() }
    fun applyUiScale() { recreate() }

    override fun search(aText: String) { handleSearch(aText) }
    override fun toggleIncognitoMode() { toggleIncognitoMode(true) }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setupWindowCallbacks()
    }
}
