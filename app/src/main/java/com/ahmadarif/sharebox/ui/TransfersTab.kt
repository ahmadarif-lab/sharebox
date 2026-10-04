package com.ahmadarif.sharebox.ui

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import com.ahmadarif.sharebox.MainActivity
import com.ahmadarif.sharebox.R
import com.ahmadarif.sharebox.core.Storage
import com.ahmadarif.sharebox.core.TransferDir
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
        listView.setOnItemClickListener { _, _, position, _ ->
            val item = adapter.itemAt(position) ?: return@setOnItemClickListener
            if (item.state == TransferState.DONE) openFolder(item.folder)
        }
        root.findViewById<View>(R.id.btn_clear_done).setOnClickListener {
            TransferTracker.clearFinished()
            refresh()
        }
        root.findViewById<View>(R.id.btn_inbox).setOnClickListener {
            openFolder("Inbox")
        }
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
            val item = data[position]
            val icon = view.findViewById<ImageView>(R.id.img_dir)
            val name = view.findViewById<TextView>(R.id.tv_name)
            val meta = view.findViewById<TextView>(R.id.tv_meta)
            val progress = view.findViewById<ProgressBar>(R.id.progress)

            val dest = item.folder?.takeIf { it.isNotEmpty() }?.let { " \u2192 $it" } ?: ""
            icon.setImageResource(
                if (item.dir == TransferDir.IN) R.drawable.ic_download else R.drawable.ic_upload
            )
            name.text = item.name
            meta.text = when (item.state) {
                TransferState.RUNNING -> {
                    val size = if (item.total > 0) {
                        "${Ui.bytes(item.done)} / ${Ui.bytes(item.total)}"
                    } else {
                        Ui.bytes(item.done)
                    }
                    if (item.paused) "Paused \u00B7 $size" else "Running\u2026 $size$dest"
                }
                TransferState.DONE ->
                    "Done \u00B7 ${Ui.bytes(if (item.total > 0) item.total else item.done)}$dest \u00B7 tap to open"
                TransferState.FAILED -> "Failed: ${item.error ?: "?"}"
            }
            when {
                item.state == TransferState.DONE -> {
                    progress.isIndeterminate = false
                    progress.progress = 1000
                }
                item.state == TransferState.FAILED -> {
                    progress.isIndeterminate = false
                    progress.progress = (item.fraction * 1000).toInt()
                }
                item.total > 0 -> {
                    progress.isIndeterminate = false
                    progress.progress = (item.fraction * 1000).toInt()
                }
                else -> {
                    progress.isIndeterminate = true
                }
            }
            return view
        }
    }
}
