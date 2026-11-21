package com.bansify.internet.activity.downloads

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.format.Formatter
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.bansify.internet.R

class DownloadPromptDialog(
    context: Context,
    private val fileName: String,
    private val fileSize: Long,
    private val onConfirm: () -> Unit,
    private val onCancel: () -> Unit = {}
) : Dialog(context, R.style.TvFullScreenDialog) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_download_prompt)

        window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.CENTER)
        }

        val ivPromptFileIcon: ImageView = findViewById(R.id.ivPromptFileIcon)
        val tvPromptFileName: TextView = findViewById(R.id.tvPromptFileName)
        val tvPromptFileSize: TextView = findViewById(R.id.tvPromptFileSize)
        val tvPromptDestination: TextView = findViewById(R.id.tvPromptDestination)
        val btnPromptCancel: Button = findViewById(R.id.btnPromptCancel)
        val btnPromptDownload: Button = findViewById(R.id.btnPromptDownload)

        tvPromptFileName.text = fileName
        tvPromptDestination.text = "Download/$fileName"

        if (fileSize > 0) {
            tvPromptFileSize.text = Formatter.formatFileSize(context, fileSize)
        } else {
            tvPromptFileSize.text = "Unknown size"
        }

        val lower = fileName.lowercase()
        when {
            lower.endsWith(".apk") -> ivPromptFileIcon.setImageResource(R.drawable.ic_file_apk)
            lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") || lower.endsWith(".tar") || lower.endsWith(".gz") ->
                ivPromptFileIcon.setImageResource(R.drawable.ic_file_zip)
            lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi") || lower.endsWith(".webm") || lower.endsWith(".mov") ->
                ivPromptFileIcon.setImageResource(R.drawable.ic_file_video)
            lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg") || lower.endsWith(".m4a") || lower.endsWith(".flac") ->
                ivPromptFileIcon.setImageResource(R.drawable.ic_file_audio)
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".gif") ->
                ivPromptFileIcon.setImageResource(R.drawable.ic_file_image)
            lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".txt") ->
                ivPromptFileIcon.setImageResource(R.drawable.ic_file_doc)
            else -> ivPromptFileIcon.setImageResource(R.drawable.ic_file_generic)
        }

        btnPromptDownload.isSelected = true
        btnPromptDownload.requestFocus()

        btnPromptCancel.setOnClickListener {
            dismiss()
            onCancel()
        }

        btnPromptDownload.setOnClickListener {
            dismiss()
            onConfirm()
        }
    }
}
