package com.bansify.internet.settings

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import com.bansify.internet.R
import com.bansify.internet.activity.main.dialogs.ShortcutDialog
import com.bansify.internet.databinding.ViewShortcutBinding
import com.bansify.internet.singleton.shortcuts.Shortcut
import com.bansify.internet.singleton.shortcuts.ShortcutMgr

class ShortcutsSettingsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), AdapterView.OnItemClickListener {

    private val lvShortcuts: ListView
    private val btnResetAllShortcuts: Button
    private val adapter: ShortcutItemAdapter

    init {
        LayoutInflater.from(context).inflate(R.layout.view_settings_shortcuts, this, true)
        lvShortcuts = findViewById(R.id.lvShortcuts)
        btnResetAllShortcuts = findViewById(R.id.btnResetAllShortcuts)

        lvShortcuts.selector = ResourcesCompat.getDrawable(context.resources, android.R.color.transparent, null)
        adapter = ShortcutItemAdapter()
        lvShortcuts.adapter = adapter
        lvShortcuts.onItemClickListener = this

        btnResetAllShortcuts.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle(R.string.shortcut_reset_all)
                .setMessage(R.string.shortcut_reset_all_confirm)
                .setPositiveButton(R.string.yes) { _, _ ->
                    ShortcutMgr.getInstance().resetAllToDefaults()
                    adapter.notifyDataSetChanged()
                    Toast.makeText(context, R.string.shortcut_reset_all, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.no, null)
                .show()
        }
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val shortcut = Shortcut.entries[position]
        val dialog = ShortcutDialog(context, shortcut)
        dialog.setOnDismissListener {
            adapter.notifyDataSetChanged()
        }
        dialog.show()
    }

    inner class ShortcutItemAdapter : BaseAdapter() {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = if (convertView != null) {
                convertView as ShortcutItemView
            } else {
                ShortcutItemView(context)
            }
            view.bind(Shortcut.entries[position])
            return view
        }

        override fun getItem(position: Int): Any = Shortcut.entries[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getCount(): Int = Shortcut.entries.size
    }

    inner class ShortcutItemView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
    ) : RelativeLayout(context, attrs, defStyleAttr) {
        private val vb: ViewShortcutBinding =
            ViewShortcutBinding.inflate(LayoutInflater.from(context), this)

        init {
            layoutParams = AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            background = ResourcesCompat.getDrawable(resources, R.drawable.bg_tv_setting_item, null)
            isFocusable = true
            isClickable = true
        }

        fun bind(shortcut: Shortcut) {
            vb.tvTitle.setText(shortcut.titleResId)
            vb.tvKey.text = if (shortcut.keyCode == 0) {
                context.getString(R.string.not_set)
            } else {
                Shortcut.shortcutKeysToString(shortcut, context)
            }
        }
    }
}
