package com.ahmadarif.sharebox.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.ahmadarif.sharebox.R

/**
 * Tiga cincin yang memancar keluar — tanda visual "sedang siaga / sedang mencari".
 * Saat tidak aktif, cincin diam dan redup. Sengaja digambar sendiri supaya tidak ada aset tambahan.
 */
class RadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2f
    }
    private var phase = 0f
    private var anim: ValueAnimator? = null
    private var wantActive = false

    var ringColor: Int = context.getColor(R.color.border)
        set(v) {
            field = v
            invalidate()
        }

    var active: Boolean
        get() = wantActive
        set(v) {
            if (v == wantActive) return
            wantActive = v
            if (v) start() else stop()
            invalidate()
        }

    private fun start() {
        if (anim != null || !isAttachedToWindow) return
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2400L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stop() {
        anim?.cancel()
        anim = null
        phase = 0f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (wantActive) start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val max = minOf(width, height) / 2f - paint.strokeWidth
        paint.color = ringColor
        if (!wantActive) {
            paint.alpha = 70
            canvas.drawCircle(cx, cy, max * 0.62f, paint)
            paint.alpha = 40
            canvas.drawCircle(cx, cy, max, paint)
            return
        }
        for (i in 0 until 3) {
            val t = (phase + i / 3f) % 1f
            paint.alpha = ((1f - t) * 255).toInt()
            canvas.drawCircle(cx, cy, max * (0.3f + 0.7f * t), paint)
        }
    }
}
