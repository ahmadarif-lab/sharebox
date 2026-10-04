package id.my.bontot.sharebox.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Pager horizontal tanpa AndroidX: tiga halaman (kiri, tengah, kanan) yang mengikuti jari,
 * lalu meluncur mulus ke halaman berikut/sebelumnya (atau kembali) saat dilepas. View halaman
 * didaur-ulang: setelah pindah, halaman yang keluar layar dipakai lagi untuk tetangga baru.
 *
 * Isi halaman dimuat oleh pemanggil lewat [load]; pager hanya mengatur posisi & gestur.
 */
class MediaPager(
    context: Context,
    private val count: Int,
    start: Int,
    private val load: (index: Int, view: ZoomImageView) -> Unit,
    private val onSettled: (index: Int) -> Unit,
    private val onDragStart: () -> Unit,
    private val onTap: () -> Unit,
) : FrameLayout(context) {

    var index = start
        private set

    private var slots = Array(3) { ZoomImageView(context) }
    private var offset = 0f
    private var anim: ValueAnimator? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFling = ViewConfiguration.get(context).scaledMinimumFlingVelocity * 6

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var tracker: VelocityTracker? = null

    val current: ZoomImageView get() = slots[1]

    init {
        slots.forEach { s ->
            s.onTap = { onTap() }
            addView(s, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        // Tengah dulu supaya foto yang sedang dilihat dimuat paling awal.
        load(index, slots[1])
        load(index + 1, slots[2])
        load(index - 1, slots[0])
    }

    /** Pindah satu halaman dengan animasi (dipakai tombol panah). */
    fun go(dir: Int) {
        val target = index + dir
        if (target !in 0 until count) return
        onDragStart()
        animateTo(-dir * width.toFloat(), dir)
    }

    private fun position() {
        val w = width.toFloat()
        slots[0].translationX = -w + offset
        slots[1].translationX = offset
        slots[2].translationX = w + offset
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        position()
    }

    // ---------- gestur ----------

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                dragging = false
                if (anim?.isRunning == true) anim?.end()
            }
            MotionEvent.ACTION_MOVE -> if (!dragging && e.pointerCount == 1 && !current.zoomed) {
                val dx = e.x - downX
                val dy = e.y - downY
                if (abs(dx) > slop && abs(dx) > abs(dy) * 1.2f) {
                    dragging = true
                    tracker = VelocityTracker.obtain()
                    tracker?.addMovement(e)
                    downX = e.x
                    parent?.requestDisallowInterceptTouchEvent(true)
                    onDragStart()
                }
            }
        }
        return dragging
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!dragging) return false
        tracker?.addMovement(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                var dx = e.x - downX
                // Di ujung daftar: tarikan terasa berat, lalu kembali.
                if ((dx > 0 && index == 0) || (dx < 0 && index == count - 1)) dx *= 0.3f
                offset = dx
                position()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracker?.computeCurrentVelocity(1000)
                val vx = tracker?.xVelocity ?: 0f
                tracker?.recycle()
                tracker = null
                dragging = false
                finishDrag(vx, e.actionMasked == MotionEvent.ACTION_CANCEL)
            }
        }
        return true
    }

    private fun finishDrag(vx: Float, cancelled: Boolean) {
        val w = width.toFloat()
        var dir = 0
        if (!cancelled) {
            val farEnough = abs(offset) > w * 0.25f
            val fast = abs(vx) > 900f.coerceAtLeast(minFling.toFloat()) && vx * offset > 0
            if (farEnough || fast) dir = if (offset < 0) 1 else -1
        }
        if (index + dir !in 0 until count) dir = 0
        animateTo(if (dir == 0) 0f else -dir * w, dir)
    }

    private fun animateTo(target: Float, dir: Int) {
        anim?.cancel()
        val w = width.toFloat().coerceAtLeast(1f)
        val a = ValueAnimator.ofFloat(offset, target)
        a.duration = (260f * abs(target - offset) / w).toLong().coerceIn(140L, 280L)
        a.interpolator = DecelerateInterpolator(1.6f)
        a.addUpdateListener {
            offset = it.animatedValue as Float
            position()
        }
        a.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (cancelled) return
                settle(dir)
            }
        })
        anim = a
        a.start()
    }

    /** Animasi selesai: putar slot, muat tetangga baru, dan kabari pemanggil. */
    private fun settle(dir: Int) {
        if (dir == 1) {
            slots = arrayOf(slots[1], slots[2], slots[0])
            index++
            offset = 0f
            position()
            load(index + 1, slots[2])
        } else if (dir == -1) {
            slots = arrayOf(slots[2], slots[0], slots[1])
            index--
            offset = 0f
            position()
            load(index - 1, slots[0])
        } else {
            offset = 0f
            position()
        }
        onSettled(index)
    }
}
