package com.gothwad.browser.activity.main.dialogs

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.browser.BuildConfig
import com.gothwad.browser.R
import com.gothwad.browser.activity.downloads.ActiveDownloadsModel
import com.gothwad.browser.activity.main.MainActivity
import com.gothwad.browser.model.Download
import com.gothwad.browser.utils.ListDiff
import com.gothwad.browser.singleton.AppDatabase
import com.gothwad.browser.utils.activemodel.ActiveModelsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DownloadsSidebarPopup(private val activity: MainActivity) : ActiveDownloadsModel.Listener {

    private val popupWindow: PopupWindow
    private val rootContainer: FrameLayout
    private val contentView: View

    private lateinit var btnDownloadsBack: ImageButton
    private lateinit var tvDownloadsTitle: TextView
    private lateinit var tvDownloadsCountBadge: TextView
    private lateinit var btnClearDownloads: Button
    private lateinit var btnFilterAll: Button
    private lateinit var btnFilterActive: Button
    private lateinit var btnFilterComplete: Button
    private lateinit var rvDownloads: RecyclerView
    private lateinit var llEmptyDownloads: LinearLayout

    private var activeDownloadsModel: ActiveDownloadsModel? = null
    private val downloadItems = mutableListOf<Download>()
    private var currentFilter = 0 // 0: All, 1: Active, 2: Complete
    private lateinit var adapter: DownloadsSidebarAdapter

    init {
        rootContainer = object : FrameLayout(activity) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_BACK,
                        KeyEvent.KEYCODE_ESCAPE,
                        KeyEvent.KEYCODE_BUTTON_B -> {
                            dismiss()
                            return true
                        }
                    }
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        contentView = LayoutInflater.from(activity).inflate(R.layout.dialog_sidebar_downloads, rootContainer, true)

        val popupWidth = SidebarHelper.calculateSidebarWidth(activity)

        popupWindow = PopupWindow(
            rootContainer,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 24f
            animationStyle = R.style.SideDrawerAnimation
        }

        bindViews()
        setupListeners()
        setupRecyclerView()
    }

    private fun bindViews() {
        btnDownloadsBack = contentView.findViewById(R.id.btnDownloadsBack)
        tvDownloadsTitle = contentView.findViewById(R.id.tvDownloadsTitle)
        tvDownloadsCountBadge = contentView.findViewById(R.id.tvDownloadsCountBadge)
        btnClearDownloads = contentView.findViewById(R.id.btnClearDownloads)
        btnFilterAll = contentView.findViewById(R.id.btnFilterAll)
        btnFilterActive = contentView.findViewById(R.id.btnFilterActive)
        btnFilterComplete = contentView.findViewById(R.id.btnFilterComplete)
        rvDownloads = contentView.findViewById(R.id.rvDownloads)
        llEmptyDownloads = contentView.findViewById(R.id.llEmptyDownloads)

        contentView.findViewById<View>(R.id.vDownloadsBackdrop).setOnClickListener {
            dismiss()
        }
    }

    private fun setupRecyclerView() {
        rvDownloads.layoutManager = LinearLayoutManager(activity)
        adapter = DownloadsSidebarAdapter(
            items = downloadItems,
            onItemClick = { download -> onDownloadClicked(download) },
            onDeleteClick = { download -> deleteDownload(download) }
        )
        rvDownloads.adapter = adapter
    }

    private fun setupListeners() {
        btnDownloadsBack.setOnClickListener { dismiss() }

        btnClearDownloads.setOnClickListener {
            clearCompletedDownloads()
        }

        btnFilterAll.setOnClickListener {
            currentFilter = 0
            updateFilterButtons()
            loadDownloads()
        }

        btnFilterActive.setOnClickListener {
            currentFilter = 1
            updateFilterButtons()
            loadDownloads()
        }

        btnFilterComplete.setOnClickListener {
            currentFilter = 2
            updateFilterButtons()
            loadDownloads()
        }
    }

    private fun updateFilterButtons() {
        btnFilterAll.isSelected = currentFilter == 0
        btnFilterActive.isSelected = currentFilter == 1
        btnFilterComplete.isSelected = currentFilter == 2

        btnFilterAll.setTextColor(if (currentFilter == 0) Color.WHITE else Color.parseColor("#94A3B8"))
        btnFilterActive.setTextColor(if (currentFilter == 1) Color.WHITE else Color.parseColor("#94A3B8"))
        btnFilterComplete.setTextColor(if (currentFilter == 2) Color.WHITE else Color.parseColor("#94A3B8"))
    }

    fun show(anchorView: View? = null) {
        try {
            activeDownloadsModel = ActiveModelsRepository.get(ActiveDownloadsModel::class, activity)
            activeDownloadsModel?.registerListener(this)
        } catch (e: Exception) {
            // Ignored if repository not ready
        }

        val decorView = activity.window.decorView
        val header = activity.findViewById<View>(R.id.rlActionBar) ?: anchorView ?: decorView

        val loc = IntArray(2)
        header.getLocationInWindow(loc)
        if (loc[1] == 0) {
            header.getLocationOnScreen(loc)
        }
        val headerBottom = loc[1] + header.height

        val screenWidth = if (decorView.width > 0) decorView.width else activity.resources.displayMetrics.widthPixels
        val screenHeight = if (decorView.height > 0) decorView.height else activity.resources.displayMetrics.heightPixels

        val popupWidth = SidebarHelper.calculateSidebarWidth(activity)
        val popupHeight = (screenHeight - headerBottom).coerceAtLeast(100)

        popupWindow.width = popupWidth
        popupWindow.height = popupHeight
        popupWindow.isClippingEnabled = false

        val xPos = screenWidth - popupWidth
        popupWindow.showAtLocation(decorView, Gravity.TOP or Gravity.START, xPos, headerBottom)

        loadDownloads()

        contentView.post {
            btnDownloadsBack.requestFocus()
        }
    }

    fun dismiss() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
        try {
            activeDownloadsModel?.unregisterListener(this)
        } catch (_: Exception) {}
    }

    private fun loadDownloads() {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.db
            val all = db.downloadDao().getAll()

            val activeTasks = activeDownloadsModel?.activeDownloads
            val activeList = activeTasks?.map { it.downloadInfo } ?: emptyList()

            val combined = mutableListOf<Download>()
            combined.addAll(activeList)

            for (d in all) {
                if (combined.none { it.id == d.id }) {
                    combined.add(d)
                }
            }

            combined.sortByDescending { it.time }

            val filtered = when (currentFilter) {
                1 -> combined.filter { it.size > 0 && it.bytesReceived < it.size }
                2 -> combined.filter { it.size > 0 && it.bytesReceived >= it.size }
                else -> combined
            }

            withContext(Dispatchers.Main) {
                val downloadsAdapter = adapter
                if (downloadsAdapter != null) {
                    ListDiff.submit(downloadsAdapter, downloadItems, filtered, idOf = { it.id },
                        contentOf = { "${it.filename}|${it.url}|${it.bytesReceived}|${it.size}" })
                } else {
                    downloadItems.clear()
                    downloadItems.addAll(filtered)
                }

                tvDownloadsCountBadge.text = "${downloadItems.size} files"
                llEmptyDownloads.visibility = if (downloadItems.isEmpty()) View.VISIBLE else View.GONE
                rvDownloads.visibility = if (downloadItems.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun onDownloadClicked(download: Download) {
        val file = File(download.filepath)
        if (!file.exists()) {
            Toast.makeText(activity, activity.getString(R.string.downloads_file_not_found_on), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val fileURI = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".provider", file)
            } else {
                Uri.fromFile(file)
            }

            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileURI, activity.contentResolver.getType(fileURI) ?: "*/*")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            activity.startActivity(openIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, activity.getString(R.string.downloads_no_app_found_to), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(activity, activity.getString(R.string.downloads_cannot_open, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteDownload(download: Download) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.db
                db.downloadDao().delete(download)

                val file = File(download.filepath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                // Ignore delete error
            }
            loadDownloads()
        }
    }

    private fun clearCompletedDownloads() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.db
                val all = db.downloadDao().getAll()
                for (d in all) {
                    if (d.bytesReceived >= d.size) {
                        db.downloadDao().delete(d)
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
            loadDownloads()
        }
    }

    override fun onDownloadUpdated(downloadInfo: Download) {
        activity.runOnUiThread {
            loadDownloads()
        }
    }

    override fun onDownloadError(downloadInfo: Download, responseCode: Int, responseMessage: String) {
        activity.runOnUiThread {
            loadDownloads()
        }
    }

    override fun onAllDownloadsComplete() {
        activity.runOnUiThread {
            loadDownloads()
        }
    }
}

class DownloadsSidebarAdapter(
    private val items: MutableList<Download>,
    private val onItemClick: (Download) -> Unit,
    private val onDeleteClick: (Download) -> Unit
) : RecyclerView.Adapter<DownloadsSidebarAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: LinearLayout = view.findViewById(R.id.llDownloadItemRoot)
        val ivIcon: ImageView = view.findViewById(R.id.ivDownloadIcon)
        val tvName: TextView = view.findViewById(R.id.tvDownloadFilename)
        val tvStatus: TextView = view.findViewById(R.id.tvDownloadStatus)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDownloadDelete)
        val pbProgress: ProgressBar = view.findViewById(R.id.pbDownloadProgress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sidebar_download, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.filename

        val isDownloading = item.size > 0 && item.bytesReceived < item.size
        if (isDownloading) {
            holder.pbProgress.visibility = View.VISIBLE
            val pct = ((item.bytesReceived.toDouble() / item.size) * 100).toInt()
            holder.pbProgress.progress = pct
            val currentMb = String.format(Locale.getDefault(), "%.1f", item.bytesReceived / (1024f * 1024f))
            val totalMb = String.format(Locale.getDefault(), "%.1f", item.size / (1024f * 1024f))
            holder.tvStatus.text = "$currentMb MB / $totalMb MB ($pct%)"
            holder.ivIcon.setImageResource(R.drawable.ic_file_download_grey_900)
        } else {
            holder.pbProgress.visibility = View.GONE
            val sizeMb = String.format(Locale.getDefault(), "%.1f MB", item.size / (1024f * 1024f))
            val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
            holder.tvStatus.text = "$sizeMb · ${sdf.format(Date(item.time))}"
            holder.ivIcon.setImageResource(R.drawable.ic_file_download_grey_900)
        }

        holder.root.setOnClickListener { onItemClick(item) }
        holder.btnDelete.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
