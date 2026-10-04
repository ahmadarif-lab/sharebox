package com.ahmadarif.sharebox.ui

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView

/**
 * ImageView dengan pinch-zoom, geser (pan), dan double-tap — murni matrix, tanpa library.
 * Perpindahan antar-foto diurus [MediaPager], yang hanya menggeser saat gambar belum di-zoom.
 */
class ZoomImageView(context: Context) : ImageView(context) {

    var onTap: (() -> Unit)? = null

    private val m = Matrix()
    private val v = FloatArray(9)
    private var baseScale = 1f
    private val maxZoom = 6f

    val zoomed: Boolean get() = scaleNow() > baseScale * 1.02f

    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val target = (scaleNow() * d.scaleFactor).coerceIn(baseScale * 0.9f, baseScale * maxZoom)
            val f = target / scaleNow()
            m.postScale(f, f, d.focusX, d.focusY)
            clamp()
            imageMatrix = m
            return true
        }

        override fun onScaleEnd(d: ScaleGestureDetector) {
            if (scaleNow() < baseScale) fit()
        }
    })

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onTap?.invoke()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (zoomed) {
                fit()
            } else {
                val f = (baseScale * 2.5f) / scaleNow()
                m.postScale(f, f, e.x, e.y)
                clamp()
                imageMatrix = m
            }
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (zoomed && !scaler.isInProgress) {
                m.postTranslate(-dx, -dy)
                clamp()
                imageMatrix = m
            }
            return true
        }
    })

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        fit()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        fit()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaler.onTouchEvent(event)
        gestures.onTouchEvent(event)
        return true
    }

    private fun scaleNow(): Float {
        m.getValues(v)
        return v[Matrix.MSCALE_X]
    }

    /** Pas ke layar: seluruh gambar terlihat, di tengah. */
    private fun fit() {
        val d = drawable ?: return
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        if (dw <= 0 || dh <= 0 || width == 0 || height == 0) return
        baseScale = minOf(width / dw, height / dh)
        m.reset()
        m.postScale(baseScale, baseScale)
        m.postTranslate((width - dw * baseScale) / 2f, (height - dh * baseScale) / 2f)
        imageMatrix = m
    }

    /** Jaga gambar tidak keluar dari layar; kalau lebih kecil dari layar, taruh di tengah. */
    private fun clamp() {
        val d = drawable ?: return
        val r = RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        m.mapRect(r)
        var dx = 0f
        var dy = 0f
        dx = if (r.width() <= width) (width - r.width()) / 2f - r.left
        else if (r.left > 0) -r.left else if (r.right < width) width - r.right else 0f
        dy = if (r.height() <= height) (height - r.height()) / 2f - r.top
        else if (r.top > 0) -r.top else if (r.bottom < height) height - r.bottom else 0f
        m.postTranslate(dx, dy)
    }
}
