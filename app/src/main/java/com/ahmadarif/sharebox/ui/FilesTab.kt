package com.ahmadarif.sharebox.ui

import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
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
import com.ahmadarif.sharebox.files.Thumbs
import com.ahmadarif.sharebox.net.DirectClient
import com.ahmadarif.sharebox.net.JoinPayload
import com.ahmadarif.sharebox.net.Pairing
import com.ahmadarif.sharebox.net.PeerDiscovery
import com.ahmadarif.sharebox.net.PeerSender
import com.ahmadarif.sharebox.net.WifiJoiner
import com.ahmadarif.sharebox.net.toTarget
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
    /** Mode "pilih file untuk dikirim": tap = pilih/batal pilih, lalu kirim lewat bar bawah. */
    private var pickMode = false
    private var pendingItems: List<PeerSender.Item> = emptyList()
    private val appIcons = HashMap<String, Drawable?>()
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
        root.findViewById<View>(R.id.btn_send).setOnClickListener { chooseTargetAndSend(selectedItems()) }
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
        pickMode = false
        updateBanner()
        tvTarget.setOnClickListener { clearTarget() }
    }

    /**
     * Dari halaman Send: buka kategori (null = seluruh storage) dalam mode pilih file. Penerima
     * dipilih belakangan, setelah file dipilih — urutan yang sama dengan Share Me.
     */
    fun openForSend(cat: FileRepo.Cat?) {
        target = null
        pickMode = true
        clearSelection()
        updateBanner()
        tvTarget.setOnClickListener { clearTarget() }
        val index = chipCats.indexOf(cat).coerceAtLeast(0)
        selectChip(index)
    }

    private fun updateBanner() {
        val t = target
        when {
            t != null -> {
                tvTarget.visibility = View.VISIBLE
                tvTarget.text = act.getString(R.string.sending_to, t.name) + "   ✕"
            }
            pickMode -> {
                tvTarget.visibility = View.VISIBLE
                tvTarget.text = act.getString(R.string.pick_to_send) + "   ✕"
            }
            else -> tvTarget.visibility = View.GONE
        }
    }

    // ---------- chips ----------

    private fun buildChips() {
        cats.removeAllViews()
        chipViews.clear()
        chipLabels.forEachIndexed { index, labelRes ->
            val chip = TextView(act)
            chip.text = act.getString(labelRes)
            chip.textSize = 13f
            chip.setTypeface(chip.typeface, android.graphics.Typeface.BOLD)
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
        if (pickMode) {
            toggleSelect(entry)
            return
        }
        val t = target
        if (t != null) {
            doSend(listOf(PeerSender.Item(file, entry.sendName)), t)
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
        if (openInViewer(entry)) return
        Ui.openFile(act, file)
    }

    /** Gambar/video dibuka di viewer bawaan, dengan seluruh media di daftar ini bisa digeser. */
    private fun openInViewer(entry: FileRepo.Entry): Boolean {
        fun isMedia(e: FileRepo.Entry) =
            !e.dir && e.file != null && !e.key.startsWith("app:") && Thumbs.kindOf(e.name) != null
        if (!isMedia(entry)) return false
        val media = entries.filter { isMedia(it) }
        val index = media.indexOfFirst { it.key == entry.key }
        if (index < 0) return false
        ViewerActivity.open(act, media.map { it.file!!.absolutePath }, index)
        return true
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
        pickMode = false
        clearSelection()
        updateBanner()
    }

    private fun selectedEntries(): List<FileRepo.Entry> =
        entries.filter { selected.contains(it.key) }

    private fun selectedFiles(): List<File> = selectedEntries().mapNotNull { it.file }

    private fun selectedItems(): List<PeerSender.Item> =
        selectedEntries().mapNotNull { e -> e.file?.let { PeerSender.Item(it, e.sendName) } }

    private fun openSelected() {
        val entry = selectedEntries().firstOrNull() ?: return
        if (entry.key.startsWith("app:")) {
            launchApp(entry.key.removePrefix("app:"))
        } else if (!openInViewer(entry)) {
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

    /**
     * Tekan Send: kalau penerima sudah dipilih (dari Nearby) langsung kirim; kalau belum,
     * kamera terbuka otomatis untuk scan QR di layar penerima (ala Share Me).
     */
    private fun chooseTargetAndSend(files: List<PeerSender.Item>) {
        if (files.isEmpty()) return
        val t = target
        if (t != null) {
            doSend(files, t)
            return
        }
        pendingItems = files
        act.startScan()
    }

    /** Hasil layar scan: null = batal, "nearby" = pilih dari daftar, selain itu isi QR. */
    fun onScanResult(text: String?) {
        val items = pendingItems
        pendingItems = emptyList()
        if (text == null || items.isEmpty()) return
        if (text == ScanActivity.RESULT_NEARBY) {
            showNearbyDialog(items)
            return
        }
        val join = JoinPayload.parse(text)
        if (join == null) {
            Ui.toast(act, act.getString(R.string.scan_invalid))
            return
        }
        val ssid = join.ssid
        if (ssid == null) {
            // Satu jaringan Wi-Fi yang sama: tidak perlu pindah jaringan.
            sendTo(items, join.toTarget(), true)
            return
        }
        Ui.toast(act, act.getString(R.string.scan_connecting, join.name))
        WifiJoiner.join(act, ssid, join.pass) { ok, _ ->
            if (ok) {
                sendTo(items, join.toTarget(), true) { WifiJoiner.leave(act) }
            } else {
                WifiJoiner.leave(act)
                Ui.toast(act, act.getString(R.string.scan_join_failed, join.name))
            }
        }
    }

    private fun showNearbyDialog(files: List<PeerSender.Item>) {
        val peers = PeerDiscovery.list()
        if (peers.isEmpty()) {
            Ui.alert(act, act.getString(R.string.target_title), act.getString(R.string.target_none))
            return
        }
        val names = peers.map {
            var base = "${it.name} (${it.host})"
            if (!it.ready) base += act.getString(R.string.peer_server_off_suffix)
            if (Pairing.tokenFor(it.id) != null) base += act.getString(R.string.peer_paired_suffix)
            base
        }.toTypedArray()
        android.app.AlertDialog.Builder(act)
            .setTitle(R.string.target_title)
            .setItems(names) { _, which -> doSend(files, peers[which]) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun doSend(files: List<PeerSender.Item>, peer: PeerDiscovery.Peer) =
        sendTo(files, peer.toTarget(), peer.ready)

    private fun sendTo(
        files: List<PeerSender.Item>,
        target: DirectClient.Target,
        ready: Boolean,
        onFinished: () -> Unit = {},
    ) {
        if (files.isEmpty()) return
        clearSelection()
        if (pickMode) {
            pickMode = false
            updateBanner()
        }
        if (!ready) {
            // Device muncul di Nearby tapi penerimanya belum siap — beri penjelasan, bukan "no response".
            Ui.toast(act, act.getString(R.string.peer_not_ready, target.name))
        } else if (target.key == null && Pairing.tokenFor(target.id) == null) {
            Ui.toast(act, act.getString(R.string.pair_waiting, target.name))
        } else {
            Ui.toast(act, act.getString(R.string.sending, files.first().name))
        }
        val folder = files.firstOrNull()?.file?.parentFile?.let {
            runCatching { Fs.rel(Storage.root(act), it) }.getOrNull()
        }
        PeerSender.send(files, target, folder) { ok, failed, error ->
            onFinished()
            if (error != null) {
                Ui.toast(
                    act,
                    when (error) {
                        "declined" -> act.getString(R.string.pair_declined, target.name)
                        "server off" -> act.getString(R.string.peer_not_ready, target.name)
                        else -> act.getString(R.string.pair_no_response, target.name)
                    }
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

        /**
         * Ikon baris: aplikasi = ikon aslinya, gambar/video = thumbnail (dimuat di latar),
         * selain itu ikon vektor di lingkaran.
         */
        private fun bindIcon(icon: ImageView, entry: FileRepo.Entry) {
            icon.tag = entry.key
            val pkg = entry.key.takeIf { it.startsWith("app:") }?.removePrefix("app:")
            val real = pkg?.let {
                appIcons.getOrPut(it) { runCatching { act.packageManager.getApplicationIcon(it) }.getOrNull() }
            }
            val file = entry.file
            val kind = if (!entry.dir && pkg == null && file != null) Thumbs.kindOf(entry.name) else null
            when {
                real != null -> {
                    plainIcon(icon, 2f)
                    icon.setImageDrawable(real)
                }
                kind != null && file != null -> Ui.bindThumb(icon, entry.key, file, kind, entry.iconRes)
                else -> Ui.bindPlainIcon(
                    icon, entry.key, entry.iconRes, R.drawable.bg_icon_circle, act.getColor(R.color.accent)
                )
            }
        }

        private fun plainIcon(icon: ImageView, padDp: Float) {
            val pad = Ui.dp(act, padDp)
            icon.setPadding(pad, pad, pad, pad)
            icon.clipToOutline = false
            icon.scaleType = ImageView.ScaleType.CENTER_INSIDE
            icon.setBackgroundResource(0)
            icon.imageTintList = null
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView
                ?: LayoutInflater.from(act).inflate(R.layout.row_file, parent, false)
            val entry = entries[position]
            val icon = view.findViewById<ImageView>(R.id.img_icon)
            val name = view.findViewById<TextView>(R.id.tv_name)
            val meta = view.findViewById<TextView>(R.id.tv_meta)
            val check = view.findViewById<CheckBox>(R.id.check)

            bindIcon(icon, entry)
            name.text = entry.name
            meta.text = when {
                entry.dir -> "Folder"
                entry.note != null -> Ui.bytes(entry.size) + " • " + entry.note
                else -> Ui.bytes(entry.size) + " • " + Ui.date(entry.mtime)
            }
            check.isChecked = selected.contains(entry.key)
            check.setOnClickListener { toggleSelect(entry) }
            return view
        }
    }
}
