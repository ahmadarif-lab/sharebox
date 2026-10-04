package com.ahmadarif.sharebox.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.TextView
import android.widget.VideoView
import com.ahmadarif.sharebox.R
import com.ahmadarif.sharebox.files.Thumbs
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Viewer gambar & video bawaan: zoom/geser gambar, putar video, geser/panah untuk pindah ke
 * file berikutnya — tanpa membuka aplikasi lain. Murni framework (ImageDecoder/BitmapFactory,
 * VideoView + MediaController).
 */
class ViewerActivity : Activity() {

    private lateinit var paths: List<String>
    private var index = 0
    private lateinit var pager: MediaPager
    private lateinit var video: VideoView
    private lateinit var title: TextView
    private lateinit var counter: TextView
    private lateinit var top: View
    private var arrows: List<View> = emptyList()
    private lateinit var errorText: TextView
    private var controller: MediaController? = null
    private var overlayVisible = true

    private val main = Handler(Looper.getMainLooper())
    private val decoder = Executors.newSingleThreadExecutor { r -> Thread(r, "sharebox-viewer").apply { isDaemon = true } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        paths = intent.getStringArrayListExtra(EXTRA_PATHS) ?: arrayListOf()
        index = (savedInstanceState?.getInt(STATE_INDEX) ?: intent.getIntExtra(EXTRA_INDEX, 0))
            .coerceIn(0, max(0, paths.size - 1))
        if (paths.isEmpty()) {
            finish()
            return
        }
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        buildUi()
        onSettled(index)
        flashArrows()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_INDEX, index)
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        pager = MediaPager(
            this, paths.size, index,
            load = { i, v -> loadInto(i, v) },
            onSettled = { i -> onSettled(i) },
            onDragStart = { stopVideo() },
            onTap = { toggleOverlay() },
        )
        root.addView(pager, FrameLayout.LayoutParams(-1, -1))

        // VideoView di dalam pager supaya geseran tetap bisa mengambil alih; hanya tampil saat berhenti di video.
        video = VideoView(this).apply { visibility = View.GONE }
        pager.addView(video, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))

        errorText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            text = getString(R.string.viewer_error)
            visibility = View.GONE
        }
        root.addView(errorText, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        // Bar atas: kembali + nama + posisi.
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0x99000000.toInt())
            setPadding(Ui.dp(this@ViewerActivity, 8f), Ui.dp(this@ViewerActivity, 36f), Ui.dp(this@ViewerActivity, 16f), Ui.dp(this@ViewerActivity, 8f))
        }
        val back = ImageView(this).apply {
            setImageResource(R.drawable.ic_back)
            setColorFilter(Color.WHITE)
            val p = Ui.dp(this@ViewerActivity, 12f)
            setPadding(p, p, p, p)
            isClickable = true
            contentDescription = getString(R.string.back)
            setOnClickListener { finish() }
        }
        bar.addView(back, LinearLayout.LayoutParams(Ui.dp(this, 48f), Ui.dp(this, 48f)))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        title = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        }
        counter = TextView(this).apply {
            setTextColor(0xCCFFFFFF.toInt())
            textSize = 12f
        }
        texts.addView(title)
        texts.addView(counter)
        bar.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        top = bar
        root.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // Panah kecil sebagai sorotan sesaat saat viewer dibuka; hilang sendiri.
        if (paths.size > 1) {
            val size = Ui.dp(this, 36f)
            val prev = arrow(false)
            val next = arrow(true)
            root.addView(prev, FrameLayout.LayoutParams(size, size, Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = Ui.dp(this@ViewerActivity, 10f) })
            root.addView(next, FrameLayout.LayoutParams(size, size, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = Ui.dp(this@ViewerActivity, 10f) })
            arrows = listOf(prev, next)
        }
        setContentView(root)
    }

    private fun arrow(forward: Boolean): View = ImageView(this).apply {
        setImageResource(R.drawable.ic_back)
        setColorFilter(Color.WHITE)
        rotation = if (forward) 180f else 0f
        val p = Ui.dp(this@ViewerActivity, 9f)
        setPadding(p, p, p, p)
        setBackgroundResource(R.drawable.bg_viewer_arrow)
        isClickable = true
        contentDescription = getString(if (forward) R.string.viewer_next else R.string.viewer_prev)
        setOnClickListener { pager.go(if (forward) 1 else -1) }
    }

    private fun toggleOverlay() {
        overlayVisible = !overlayVisible
        top.visibility = if (overlayVisible) View.VISIBLE else View.GONE
    }

    /** Muat foto/poster video ke satu halaman pager (async; hasil basi dibuang lewat tag indeks). */
    private fun loadInto(i: Int, v: ZoomImageView) {
        v.tag = i
        v.setImageDrawable(null)
        if (i !in paths.indices) return
        val file = File(paths[i])
        val isVideo = Thumbs.kindOf(file.name) == Thumbs.Kind.VIDEO
        decoder.execute {
            if (v.tag != i) return@execute
            val d: Drawable? = runCatching {
                if (isVideo) Thumbs.videoFrame(file, 1280)?.let { BitmapDrawable(resources, it) }
                else decodeImage(file)
            }.getOrNull()
            main.post {
                if (v.tag != i) return@post
                if (d == null) {
                    if (i == pager.index) errorText.visibility = View.VISIBLE
                } else {
                    v.setImageDrawable(d)
                    (d as? AnimatedImageDrawable)?.start()
                }
            }
        }
    }

    /** Berhenti di halaman [i]: perbarui judul, dan putar kalau itu video. */
    private fun onSettled(i: Int) {
        index = i
        val file = File(paths[i])
        title.text = file.name
        counter.text = getString(R.string.viewer_counter, i + 1, paths.size)
        errorText.visibility = View.GONE
        stopVideo()
        if (Thumbs.kindOf(file.name) == Thumbs.Kind.VIDEO) playVideo(file)
    }

    /** Panah menyorot sebentar (±1,6 dtk) lalu memudar; hanya saat viewer baru dibuka. */
    private fun flashArrows() {
        arrows.forEach { a ->
            a.alpha = 0.85f
            a.animate().alpha(0f).setStartDelay(1400L).setDuration(450L)
                .withEndAction { a.visibility = View.GONE }.start()
        }
    }

    /** Android 9+: ImageDecoder (orientasi, GIF/WebP animasi, HEIC); sebelumnya BitmapFactory + EXIF. */
    private fun decodeImage(file: File): Drawable? {
        val maxSide = 1800
        if (Build.VERSION.SDK_INT >= 28) {
            return ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { dec, info, _ ->
                val side = max(info.size.width, info.size.height)
                if (side > maxSide) dec.setTargetSampleSize((side + maxSide - 1) / maxSide)
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val deg = when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val out = if (deg == 0f) bmp else android.graphics.Bitmap.createBitmap(
            bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postRotate(deg) }, true
        )
        return BitmapDrawable(resources, out)
    }

    private fun playVideo(file: File) {
        video.visibility = View.VISIBLE
        val mc = controller ?: MediaController(this).also { controller = it }
        mc.setAnchorView(video)
        video.setMediaController(mc)
        video.setOnPreparedListener { it.isLooping = false; video.start() }
        video.setOnErrorListener { _, _, _ ->
            errorText.visibility = View.VISIBLE
            true
        }
        video.setVideoURI(Uri.fromFile(file))
    }

    private fun stopVideo() {
        runCatching { video.stopPlayback() }
        runCatching { controller?.hide() }
        video.visibility = View.GONE
    }

    override fun onPause() {
        runCatching { if (video.isPlaying) video.pause() }
        super.onPause()
    }

    override fun onDestroy() {
        stopVideo()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_PATHS = "paths"
        private const val EXTRA_INDEX = "index"
        private const val STATE_INDEX = "index"

        /** Buka viewer di [index] dari daftar [paths] (dibatasi ±200 file di sekitar posisi itu). */
        fun open(ctx: Context, paths: List<String>, index: Int) {
            if (paths.isEmpty()) return
            val from = max(0, index - 200)
            val to = minOf(paths.size, index + 201)
            ctx.startActivity(
                Intent(ctx, ViewerActivity::class.java)
                    .putStringArrayListExtra(EXTRA_PATHS, ArrayList(paths.subList(from, to)))
                    .putExtra(EXTRA_INDEX, index - from)
            )
        }
    }
}
