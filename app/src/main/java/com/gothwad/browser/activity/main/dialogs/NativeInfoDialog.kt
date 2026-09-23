package com.gothwad.browser.activity.main.dialogs

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.net.toUri
import com.gothwad.browser.R

class NativeInfoDialog(
    context: Context,
    private val title: String,
    private val subtitle: String,
    private val contentText: String,
    private val iconRes: Int = R.drawable.ic_gothwad_logo,
    private val actionButtonText: String? = null,
    private val actionUrl: String? = null,
    private val onActionClick: (() -> Unit)? = null
) : Dialog(context, R.style.SettingsDialog) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_info_reader)

        window?.setLayout(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        val ivIcon = findViewById<ImageView>(R.id.ivDialogIcon)
        val tvTitle = findViewById<TextView>(R.id.tvDialogTitle)
        val tvSubtitle = findViewById<TextView>(R.id.tvDialogSubtitle)
        val tvBody = findViewById<TextView>(R.id.tvDialogBody)
        val svContent = findViewById<ScrollView>(R.id.svDialogContent)
        val ibClose = findViewById<ImageButton>(R.id.ibCloseDialog)
        val btnClose = findViewById<Button>(R.id.btnDialogClose)
        val btnAction = findViewById<Button>(R.id.btnDialogAction)

        ivIcon.setImageResource(iconRes)
        tvTitle.text = title
        tvSubtitle.text = subtitle
        tvBody.text = contentText

        ibClose.setOnClickListener { dismiss() }
        btnClose.setOnClickListener { dismiss() }

        if (actionButtonText != null) {
            btnAction.visibility = View.VISIBLE
            btnAction.text = actionButtonText
            btnAction.setOnClickListener {
                if (onActionClick != null) {
                    onActionClick.invoke()
                } else if (actionUrl != null) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, actionUrl.toUri())
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                dismiss()
            }
        } else {
            btnAction.visibility = View.GONE
        }

        svContent.isFocusable = true
        svContent.requestFocus()
    }

    companion object {
        val PRIVACY_POLICY_TEXT = """
🔒 GOTHWAD BROWSER - PRIVACY POLICY
Effective Date: August 2026

1. ZERO TELEMETRY & ZERO DATA COLLECTION
Gothwad Browser is designed from the ground up with a 100% private, local-first architecture. We do NOT collect, transmit, store, or sell any of your personal information, browsing habits, IP address, device identifiers, or search history.

2. 100% ON-DEVICE LOCAL PERSISTENCE
All your data—including Favorites, Bookmarks, History, Download Logs, and Custom Preferences—is stored strictly and exclusively on your local Android TV device storage. 

3. NO BACKEND SERVERS OR USER ACCOUNTS
This browser operates without any proprietary server infrastructure. There is no user registration, no telemetry tracking, and no cloud synchronization service listening in the background.

4. NETWORK CONNECTIONS
Network activity occurs ONLY when you enter a URL or search query to load web content from the internet. When you browse the web, your connection is made directly between your device and the destination web server.

5. AD BLOCKING & SECURITY
Our built-in AdBlock and Tracker Blocker features run completely on-device, blocking intrusive ads, known third-party tracking scripts, and malicious popups locally.

Developed with ❤️ by Gothwad Tech (https://gothwadtech.com)
""".trimIndent()

        val TERMS_TEXT = """
📜 GOTHWAD BROWSER - TERMS AND CONDITIONS
Last Updated: August 2026

1. ACCEPTANCE OF TERMS
By installing and using Gothwad Browser, you agree to these Terms and Conditions. If you do not agree with any portion, please uninstall the application.

2. OPEN SOURCE & LICENSE
Gothwad Browser is open-source software provided free of charge under the permissive open-source license. The source code is publicly accessible on GitHub (github.com/gothwadtech/tvbrowser).

3. LOCAL USE & USER RESPONSIBILITY
Gothwad Browser provides standard web navigation tools for Android TV. You are solely responsible for the content you access, download, or interact with while browsing the internet.

4. NO WARRANTY
The software is provided "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT.

5. INTELLECTUAL PROPERTY & ATTRIBUTION
Created & Maintained by Gothwad Tech. All trademarks and brand names mentioned are the property of their respective owners.

Contact & Inquiries: gothwadtech@gmail.com | https://gothwadtech.com
""".trimIndent()

        val ABOUT_GOTHWAD_TECH_TEXT = """
🏢 ABOUT GOTHWAD TECH

Gothwad Tech is dedicated to building ultra-fast, clean, privacy-focused, and open-source software experiences tailored for modern smart screens, Android TVs, and connected devices.

OUR CORE PRINCIPLES:
• 🚀 Speed & Efficiency: Optimized for TV hardware with instant launch times and low memory footprint.
• 🔒 Privacy First: Zero tracking, no telemetry, no cloud logging, 100% on-device storage.
• 🔓 Open Source: Transparent, community-driven development with public source code.
• 📺 TV-First Ergonomics: Intuitive D-Pad navigation, remote control shortcuts, and crystal-clear display typography.

GET IN TOUCH & EXPLORE:
• Official Website: https://gothwadtech.com
• GitHub Organization: https://github.com/gothwadtech
• Project Repository: https://github.com/gothwadtech/tvbrowser
• Developer Contact: gothwadtech@gmail.com
""".trimIndent()

        val LICENSE_TEXT = """
MIT License

Copyright (c) 2026 Gothwad Tech & Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
""".trimIndent()
    }
}
