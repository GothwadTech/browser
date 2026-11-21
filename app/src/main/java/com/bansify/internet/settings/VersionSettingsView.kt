package com.bansify.internet.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ScrollView
import androidx.core.net.toUri
import com.bansify.internet.BuildConfig
import com.bansify.internet.R
import com.bansify.internet.activity.IncognitoModeMainActivity
import com.bansify.internet.activity.main.MainActivity
import com.bansify.internet.activity.main.dialogs.NativeInfoDialog
import com.bansify.internet.databinding.ViewSettingsVersionBinding
import com.bansify.internet.utils.activemodel.ActiveModelsRepository
import com.bansify.internet.utils.activity
import com.bansify.internet.webengine.WebEngineFactory

@SuppressLint("SetTextI18n")
class VersionSettingsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    companion object {
        const val URL_GITHUB_REPO = "https://github.com/gothwadtech/tvbrowser"
        const val URL_GOTHWAD_TECH = "https://gothwadtech.com"
    }

    private var vb = ViewSettingsVersionBinding.inflate(LayoutInflater.from(context), this, true)
    var settingsModel = ActiveModelsRepository.get(SettingsModel::class, activity!!)
    var callback: Callback? = null

    interface Callback {
        fun onNeedToCloseSettings()
    }

    init {
        vb.tvVersion.text = context.getString(R.string.version_s, BuildConfig.VERSION_NAME)

        vb.tvBuildFlavor.text = context.getString(
            R.string.build_flavor_s,
            BuildConfig.FLAVOR_appstore,
            BuildConfig.FLAVOR_webengine
        )

        val engineVersion = "Engine: " + WebEngineFactory.getWebEngineVersionString()
        vb.tvWebViewVersion.text = engineVersion

        vb.tvGithubLink.setOnClickListener {
            loadUrl(URL_GITHUB_REPO)
        }

        vb.tvWebsiteLink.setOnClickListener {
            loadUrl(URL_GOTHWAD_TECH)
        }

        vb.btnAboutGothwadTech.setOnClickListener {
            NativeInfoDialog(
                context = context,
                title = "About Gothwad Tech",
                subtitle = "Fastest · Lightest · Securest · Zero Tracking",
                contentText = NativeInfoDialog.ABOUT_GOTHWAD_TECH_TEXT,
                iconRes = R.drawable.ic_gothwad_logo,
                actionButtonText = "Visit Website",
                actionUrl = URL_GOTHWAD_TECH,
                onActionClick = {
                    loadUrl(URL_GOTHWAD_TECH)
                }
            ).show()
        }

        vb.btnPrivacyPolicy.setOnClickListener {
            NativeInfoDialog(
                context = context,
                title = "Privacy Policy",
                subtitle = "100% Local-Side · Zero Telemetry Guaranteed",
                contentText = NativeInfoDialog.PRIVACY_POLICY_TEXT,
                iconRes = R.drawable.ic_lock_security
            ).show()
        }

        vb.btnTermsConditions.setOnClickListener {
            NativeInfoDialog(
                context = context,
                title = "Terms & Conditions",
                subtitle = "Free & Open Source TV Browser License Terms",
                contentText = NativeInfoDialog.TERMS_TEXT,
                iconRes = R.drawable.ic_file_doc
            ).show()
        }

        vb.btnLicense.setOnClickListener {
            NativeInfoDialog(
                context = context,
                title = "Open Source License",
                subtitle = "MIT Permissive Software License",
                contentText = NativeInfoDialog.LICENSE_TEXT,
                iconRes = R.drawable.ic_file_doc
            ).show()
        }

        vb.btnSupportAuthor.setOnClickListener {
            loadUrl(URL_GOTHWAD_TECH)
        }

        vb.btnOpenWebsite.setOnClickListener {
            loadUrl(URL_GOTHWAD_TECH)
        }
    }

    private fun loadUrl(url: String) {
        callback?.onNeedToCloseSettings()
        val activityClass = if (settingsModel.config.incognitoMode)
            IncognitoModeMainActivity::class.java else MainActivity::class.java
        val intent = Intent(activity, activityClass)
        intent.data = url.toUri()
        activity?.startActivity(intent)
    }
}
