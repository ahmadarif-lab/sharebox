package com.ahmadarif.sharebox.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import com.ahmadarif.sharebox.MainActivity
import com.ahmadarif.sharebox.R
import com.ahmadarif.sharebox.core.Fs
import com.ahmadarif.sharebox.core.Storage
import com.ahmadarif.sharebox.files.FileRepo
import com.ahmadarif.sharebox.net.Pairing
import com.ahmadarif.sharebox.net.PeerDiscovery
import com.ahmadarif.sharebox.net.PeerSender
import java.io.File
import java.util.concurrent.Executors

class FilesTab(activity: MainActivity) : BaseTab(activity) {

    override fun layoutId(): Int = R.layout.tab_files

    private val tvTarget: TextView = root.findViewById(R.id.tv_target)
    private val cats: LinearLayout = root.findViewById(R.id.cats)
    private val btnUp: View = root.findViewById(R.id.btn_up)
    private val tvPath: TextView = root.findViewById(R.id.tv_path)
    private val btnRefresh: View = root.findViewById(R.id.btn_refresh_files)
    private val listView: ListView = root.findViewById(R.id.list)
    private val emptyState: LinearLayout = root.findViewById(R.id.empty_state)
    private val loadingSpinner: ProgressBar = root.findViewById(R.id.loading_spinner)
    private val tvEmpty: TextView = root.findViewById(R.id.tv_empty)
    private val selbar: LinearLayout = root.findViewById(R.id.selbar)
    private val tvSelCount: TextView = root.findViewById(R.id.tv_sel_count)

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "sharebox-files").apply { isDaemon = true }
    }
    private val adapter = FileAdapter()
    private val selected = LinkedHashSet<String>()
    private var entries: List<FileRepo.Entry> = emptyList()
    private var currentDir: File = Storage.root(act)
    private var activeCat: FileRepo.Cat? = null
    private var target: PeerDiscovery.Peer? = null
    private var loading = false
    private val chipViews = ArrayList<TextView>()
    private var activeChip = 0

    private val chipCats: List<FileRepo.Cat?> = listOf(
        null,
        FileRepo.Cat.IMAGES,
        FileRepo.Cat.VIDEOS,
        FileRepo.Cat.AUDIO,
        FileRepo.Cat.DOCS,
        FileRepo.Cat.APPS,
        FileRepo.Cat.DOWNLOADS,
    )

    private val chipLabels = listOf(
        R.string.tab_root,
        R.string.cat_images,
        R.string.cat_videos,
        R.string.cat_audio,
        R.string.cat_docs,
        R.string.cat_apps,
        R.string.cat_downloads,
    )

    init {
        listView.adapter = adapter
        buildChips()
        tvEmpty.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_folder, 0, 0)
        tvEmpty.compoundDrawablePadding = Ui.dp(act, 10f)
        tvEmpty.setCompoundDrawableTintList(ColorStateList.valueOf(act.getColor(R.color.muted)))
        btnUp.setOnClickListener { goUp() }
        btnRefresh.setOnClickListener { refresh() }
        listView.setOnItemClickListener { _, _, position, _ ->
            entries.getOrNull(position)?.let { onRowClick(it) }
        }
        listView.setOnItemLongClickListener { _, _, position, _ ->
            entries.getOrNull(position)?.let { toggleSelect(it) }
            true
        }
        root.findViewById<View>(R.id.btn_open).setOnClickListener { openSelected() }
        root.findViewById<View>(R.id.btn_send).setOnClickListener { chooseTargetAndSend(selectedFiles()) }
        root.findViewById<View>(R.id.btn_delete).setOnClickListener { deleteSelected() }
        root.findViewById<View>(R.id.btn_clear).setOnClickListener { clearSelection() }
        loadDir(currentDir)
    }

    // ---------- public ----------

    fun openPath(dir: File) {
        loadDir(dir)
    }

    fun setTarget(peer: PeerDiscovery.Peer) {
        target = peer
        tvTarget.visibility = View.VISIBLE
        tvTarget.text = act.getString(R.string.open_target_hint, peer.name)
        tvTarget.setOnClickListener { clearTarget() }
    }

    // ---------- chips ----------

    private fun buildChips() {
        cats.removeAllViews()
        chipViews.clear()
        chipLabels.forEachIndexed { index, labelRes ->
            val chip = TextView(act)
            chip.text = act.getString(labelRes)
            chip.textSize = 13f
            chip.setPadding(Ui.dp(act, 14f), Ui.dp(act, 8f), Ui.dp(act, 14f), Ui.dp(act, 8f))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = Ui.dp(act, 8f)
            chip.layoutParams = lp
            chip.setOnClickListener { selectChip(index) }
            chipViews.add(chip)
            cats.addView(chip)
        }
        paintChips()
    }

    private fun selectChip(index: Int) {
        activeChip = index
        paintChips()
        val cat = chipCats[index]
        if (cat == null) {
            loadDir(Storage.root(act))
        } else {
            loadCategory(cat)
        }
    }

    private fun paintChips() {
        chipViews.forEachIndexed { index, chip ->
            val on = index == activeChip
            chip.setBackgroundResource(if (on) R.drawable.bg_chip_on else R.drawable.bg_chip)
            chip.setTextColor(act.getColor(if (on) R.color.on_accent else R.color.fg))
        }
    }

    // ---------- loading ----------

    private fun loadDir(dir: File) {
        currentDir = dir
        activeCat = null
        activeChip = 0
        paintChips()
        updatePath()
        load { FileRepo.listDir(dir) }
    }

    private fun loadCategory(cat: FileRepo.Cat) {
        activeCat = cat
        updatePath()
        load { FileRepo.category(act, cat) }
    }

    private fun load(block: () -> List<FileRepo.Entry>) {
        loading = true
        showLoading()
        executor.execute {
            val result = runCatching { block() }.getOrDefault(emptyList())
            act.runOnUiThread {
                loading = false
                entries = result
                selected.clear()
                adapter.notifyDataSetChanged()
                updateEmpty()
                updateSelbar()
            }
        }
    }

    /**
     * Sedang memuat: kosongkan list dulu supaya nama file dari folder sebelumnya tidak
     * bertumpuk dengan teks "Loading…", lalu tampilkan spinner.
     */
    private fun showLoading() {
        entries = emptyList()
        adapter.notifyDataSetChanged()
        emptyState.visibility = View.VISIBLE
        loadingSpinner.visibility = View.VISIBLE
        tvEmpty.setText(R.string.loading)
        tvEmpty.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
    }

    fun refresh() {
        val cat = activeCat
        if (cat != null) loadCategory(cat) else loadDir(currentDir)
    }

    private fun updatePath() {
        val cat = activeCat
        if (cat != null) {
            tvPath.text = act.getString(cat.labelRes)
            return
        }
        val rel = runCatching { Fs.rel(Storage.root(act), currentDir) }.getOrDefault("")
        tvPath.text = if (rel.isEmpty()) act.getString(R.string.tab_root) else rel
    }

    private fun updateEmpty() {
        loadingSpinner.visibility = View.GONE
        emptyState.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        tvEmpty.setText(
            if (activeCat != null && !Storage.hasAllFiles(act)) R.string.empty_perm
            else R.string.empty_folder
        )
        tvEmpty.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_folder, 0, 0)
    }

    private fun updateSelbar() {
        selbar.visibility = if (selected.isEmpty()) View.GONE else View.VISIBLE
        tvSelCount.text = act.getString(R.string.selected_n, selected.size)
    }

    // ---------- rows ----------

    private fun onRowClick(entry: FileRepo.Entry) {
        if (entry.dir) {
            entry.file?.let { loadDir(it) }
            return
        }
        val file = entry.file ?: return
        val t = target
        if (t != null) {
            doSend(listOf(file), t)
            return
        }
        if (selected.isNotEmpty()) {
            toggleSelect(entry)
            return
        }
        if (entry.key.startsWith("app:")) {
            launchApp(entry.key.removePrefix("app:"))
            return
        }
        Ui.openFile(act, file)
    }

    private fun launchApp(pkg: String) {
        val intent = act.packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            act.startActivity(intent)
        } else {
            Ui.toast(act, act.getString(R.string.no_app_open))
        }
    }

    private fun toggleSelect(entry: FileRepo.Entry) {
        if (!selected.remove(entry.key)) selected.add(entry.key)
        adapter.notifyDataSetChanged()
        updateSelbar()
    }

    private fun clearSelection() {
        selected.clear()
        adapter.notifyDataSetChanged()
        updateSelbar()
    }

    private fun clearTarget() {
        target = null
        tvTarget.visibility = View.GONE
    }

    private fun selectedEntries(): List<FileRepo.Entry> =
        entries.filter { selected.contains(it.key) }

    private fun selectedFiles(): List<File> = selectedEntries().mapNotNull { it.file }

    private fun openSelected() {
        val entry = selectedEntries().firstOrNull() ?: return
        if (entry.key.startsWith("app:")) {
            launchApp(entry.key.removePrefix("app:"))
        } else {
            entry.file?.let { Ui.openFile(act, it) }
        }
    }

    private fun deleteSelected() {
        val files = selectedFiles()
        if (files.isEmpty()) return
        Ui.confirm(act, act.getString(R.string.act_delete), act.getString(R.string.delete_confirm, files.size)) {
            executor.execute {
                var deleted = 0
                for (f in files) {
                    if (runCatching { f.deleteRecursively() }.getOrDefault(false)) deleted++
                }
                act.runOnUiThread {
                    Ui.toast(act, "$deleted item(s) deleted")
                    clearSelection()
                    refresh()
                }
            }
        }
    }

    // ---------- sending ----------

    private fun chooseTargetAndSend(files: List<File>) {
        if (files.isEmpty()) return
        val t = target
        if (t != null) {
            doSend(files, t)
            return
        }
        val peers = PeerDiscovery.list()
        if (peers.isEmpty()) {
            Ui.alert(act, act.getString(R.string.target_title), act.getString(R.string.target_none))
            return
        }
        val names = peers.map { "${it.name} (${it.host})" }.toTypedArray()
        android.app.AlertDialog.Builder(act)
            .setTitle(R.string.target_title)
            .setItems(names) { _, which -> doSend(files, peers[which]) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun doSend(files: List<File>, peer: PeerDiscovery.Peer) {
        if (files.isEmpty()) return
        clearSelection()
        if (Pairing.tokenFor(peer.id) == null) {
            Ui.toast(act, act.getString(R.string.pair_waiting, peer.name))
        } else {
            Ui.toast(act, act.getString(R.string.sending, files.first().name))
        }
        val folder = files.firstOrNull()?.parentFile?.let {
            runCatching { Fs.rel(Storage.root(act), it) }.getOrNull()
        }
        PeerSender.send(files, peer, folder) { ok, failed, error ->
            if (error != null) {
                Ui.toast(
                    act,
                    if (error == "declined") act.getString(R.string.pair_declined, peer.name)
                    else act.getString(R.string.pair_no_response, peer.name)
                )
            } else {
                Ui.toast(
                    act,
                    if (failed == 0) act.getString(R.string.sent_ok, ok)
                    else act.getString(R.string.sent_partial, ok, failed)
                )
            }
            act.select(2)
        }
    }

    // ---------- navigation ----------

    private fun goUp(): Boolean {
        if (activeCat != null) {
            selectChip(0)
            return true
        }
        val rootDir = Storage.root(act)
        val parent = currentDir.parentFile
        if (currentDir != rootDir && parent != null) {
            loadDir(parent)
            return true
        }
        return false
    }

    override fun onBack(): Boolean = goUp()

    override fun onShow() {
        if (entries.isEmpty() && !loading) refresh()
    }

    // ---------- adapter ----------

    private inner class FileAdapter : BaseAdapter() {
        override fun getCount(): Int = entries.size
        override fun getItem(position: Int): Any = entries[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView
                ?: LayoutInflater.from(act).inflate(R.layout.row_file, parent, false)
            val entry = entries[position]
            val icon = view.findViewById<ImageView>(R.id.img_icon)
            val name = view.findViewById<TextView>(R.id.tv_name)
            val meta = view.findViewById<TextView>(R.id.tv_meta)
            val check = view.findViewById<CheckBox>(R.id.check)

            icon.setImageResource(entry.iconRes)
            name.text = entry.name
            meta.text = if (entry.dir) "Folder" else Ui.bytes(entry.size) + " • " + Ui.date(entry.mtime)
            check.isChecked = selected.contains(entry.key)
            check.setOnClickListener { toggleSelect(entry) }
            return view
        }
    }
}
