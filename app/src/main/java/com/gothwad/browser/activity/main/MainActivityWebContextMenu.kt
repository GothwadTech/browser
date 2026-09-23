package com.gothwad.browser.activity.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.Toast
import com.gothwad.browser.R
import com.gothwad.browser.model.WebTabState
import com.gothwad.browser.webengine.WebEngineWindowProviderCallback
import com.gothwad.browser.widgets.cursor.CursorDrawerDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object MainActivityWebContextMenuHelper {

    fun handleCopyTextToClipboard(activity: MainActivity, url: String) {
        val clipBoard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipData = ClipData.newPlainText("URL", url)
        clipBoard?.setPrimaryClip(clipData)
        Toast.makeText(activity, activity.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }

    fun handleShareUrl(activity: MainActivity, url: String) {
        val share = Intent(Intent.ACTION_SEND)
        share.type = "text/plain"
        share.putExtra(Intent.EXTRA_SUBJECT, R.string.share_url)
        share.putExtra(Intent.EXTRA_TEXT, url)
        try {
            activity.startActivity(share)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(activity, R.string.external_app_open_error, Toast.LENGTH_SHORT).show()
        }
    }

    fun handleOpenInExternalApp(activity: MainActivity, url: String) {
        val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        val activityComponent = intent.resolveActivity(activity.packageManager)
        if (activityComponent != null && activityComponent.packageName == activity.packageName) {
            Toast.makeText(activity, R.string.external_app_open_error, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            activity.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(activity, R.string.external_app_open_error, Toast.LENGTH_SHORT).show()
        }
    }

    fun suggestActionsForLink(
        activity: MainActivity,
        tab: WebTabState,
        callback: WebEngineWindowProviderCallback,
        baseUri: String?,
        linkUri: String?,
        srcUri: String?,
        title: String?,
        altText: String?,
        textContent: String?,
        x: Int,
        y: Int
    ) {
        var s = linkUri ?: srcUri
        if (s != null && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length - 1)
        }
        val url = s ?: tab.webEngine.url
        val isLinkTarget = linkUri != null || srcUri != null
        val isHTTPUrl = url != null && (url.startsWith("http://") || url.startsWith("https://"))
        val anchor = View(activity)
        val lp = FrameLayout.LayoutParams(1, 1)
        lp.setMargins(x, y, 0, 0)
        activity.vb.flWebViewContainer.addView(anchor, lp)
        activity.linkActionsMenu = PopupMenu(activity, anchor, Gravity.BOTTOM).also {
            it.inflate(R.menu.menu_link)
            it.menu.findItem(R.id.miOpenInNewTab).isVisible = isHTTPUrl && isLinkTarget
            it.menu.findItem(R.id.miOpenInExternalApp).isVisible = isHTTPUrl
            it.menu.findItem(R.id.miDownload).isVisible = isHTTPUrl && isLinkTarget
            it.menu.findItem(R.id.miCopyToClipboard).isVisible = url != null
            it.menu.findItem(R.id.miShare).isVisible = url != null
            it.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.miRefreshPage -> tab.webEngine.reload()
                    R.id.miOpenInNewTab -> callback.onOpenInNewTabRequested(url!!, true)
                    R.id.miOpenInExternalApp -> callback.onOpenInExternalAppRequested(url!!)
                    R.id.miDownload -> callback.onDownloadRequested(url!!)
                    R.id.miCopyToClipboard -> callback.onCopyTextToClipboardRequested(url!!)
                    R.id.miShare -> callback.onShareUrlRequested(url!!)
                }
                true
            }

            it.setOnDismissListener {
                activity.vb.flWebViewContainer.removeView(anchor)
                activity.linkActionsMenu = null
            }
            it.show()
        }
    }
}
