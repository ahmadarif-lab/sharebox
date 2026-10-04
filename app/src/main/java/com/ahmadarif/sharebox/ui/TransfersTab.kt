package com.ahmadarif.sharebox.ui

import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import com.ahmadarif.sharebox.MainActivity
import com.ahmadarif.sharebox.R
import com.ahmadarif.sharebox.core.Storage
import com.ahmadarif.sharebox.core.TransferState
import com.ahmadarif.sharebox.core.TransferTracker
import java.io.File

class TransfersTab(activity: MainActivity) : BaseTab(activity) {

    override fun layoutId(): Int = R.layout.tab_transfers

    private val listView: ListView = root.findViewById(R.id.list)
    private val tvEmpty: TextView = root.findViewById(R.id.tv_empty)

    private val adapter = TransferAdapter()
    private val handler = Handler(Looper.getMainLooper())
    private var visible = false

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            if (visible) handler.postDelayed(this, 600L)
        }
    }

    init {
        listView.adapter = adapter
        // Empty state ikut bahasa web UI: ikon + teks, bukan teks polos.
        tvEmpty.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_transfer, 0, 0)
        tvEmpty.compoundDrawablePadding = Ui.dp(act, 10f)
        tvEmpty.setCompoundDrawableTintList(ColorStateList.valueOf(act.getColor(R.color.muted)))
        listView.setOnItemClickListener { _, _, position, _ ->
            val item = adapter.itemAt(position) ?: return@setOnItemClickListener
            val apk = item.path?.takeIf { Ui.isApk(item.name) && item.state == TransferState.DONE }
            if (Ui.viewablePath(item) != null) openViewer(item)
            else if (apk != null && File(apk).exists()) Ui.openFile(act, File(apk))
            else if (item.state == TransferState.DONE) openFolder(item.folder)
        }
        root.findViewById<View>(R.id.btn_clear_done).setOnClickListener {
            TransferTracker.clearFinished()
            refresh()
        }
        root.findViewById<View>(R.id.btn_inbox).setOnClickListener {
            openFolder("Inbox")
        }
    }

    /** Gambar/video hasil transfer dibuka di viewer; geser untuk pindah antar media transfer lain. */
    private fun openViewer(item: TransferTracker.Item) {
        val media = TransferTracker.items().mapNotNull { Ui.viewablePath(it) }.distinct()
        val index = media.indexOf(Ui.viewablePath(item))
        if (index >= 0) ViewerActivity.open(act, media, index)
    }

    private fun openFolder(folder: String?) {
        val dir = if (folder.isNullOrEmpty()) Storage.root(act) else File(Storage.root(act), folder)
        act.filesTab.openPath(dir)
        act.select(1)
    }

    override fun onShow() {
        visible = true
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onHide() {
        visible = false
        handler.removeCallbacks(ticker)
    }

    private fun refresh() {
        val items = TransferTracker.items()
        tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        adapter.setData(items)
    }

    private inner class TransferAdapter : BaseAdapter() {
        private var data: List<TransferTracker.Item> = emptyList()

        fun setData(items: List<TransferTracker.Item>) {
            data = items
            notifyDataSetChanged()
        }

        fun itemAt(position: Int): TransferTracker.Item? = data.getOrNull(position)

        override fun getCount(): Int = data.size

        override fun getItem(position: Int): Any = data[position]

        override fun getItemId(position: Int): Long = data[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView
                ?: LayoutInflater.from(act).inflate(R.layout.row_transfer, parent, false)
            Ui.bindTransfer(act, view, data[position])
            return view
        }
    }
}
