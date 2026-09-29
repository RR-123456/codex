package com.pragon.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/**
 * A joystick-styled circular pad that acts as a relative trackpad for the
 * PC's mouse: drag moves the cursor by the finger's motion (like a laptop
 * trackpad, not an absolute joystick position), and a quick tap sends a
 * left click. The knob is just visual feedback - it always springs back to
 * center, since cursor movement is relative, not "how far the stick is
 * pushed".
 */
class TrackpadView(context: Context) : View(context) {

    var onMove: ((dx: Float, dy: Float) -> Unit)? = null
    var onTap: (() -> Unit)? = null

    private val baseColor = Color.parseColor("#12161f")
    private val ringColor = Color.parseColor("#00D4FF")
    private val knobColor = Color.parseColor("#00D4FF")

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = baseColor; style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ringColor; style = Paint.Style.STROKE; strokeWidth = 3f; alpha = 140
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = knobColor; style = Paint.Style.FILL }

    private var knobX = 0f
    private var knobY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var moved = false

    // Higher = faster cursor for the same finger travel.
    var sensitivity = 2.2f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        knobX = w / 2f; knobY = h / 2f
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = min(width, height) / 2f - 8f
        canvas.drawCircle(cx, cy, r, basePaint)
        canvas.drawCircle(cx, cy, r, ringPaint)
        canvas.drawCircle(knobX, knobY, r * 0.28f, knobPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cx = width / 2f; val cy = height / 2f
        val maxR = min(width, height) / 2f - 8f
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x; lastY = event.y
                downX = event.x; downY = event.y
                downTime = System.currentTimeMillis()
                moved = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                if (hypot((event.x - downX).toDouble(), (event.y - downY).toDouble()) > 8) moved = true
                if (dx != 0f || dy != 0f) onMove?.invoke(dx * sensitivity, dy * sensitivity)
                lastX = event.x; lastY = event.y

                // knob: visual only, clamped to the ring, follows finger offset from center-of-gesture
                var ox = knobX + dx; var oy = knobY + dy
                val ddx = ox - cx; val ddy = oy - cy
                val dist = hypot(ddx.toDouble(), ddy.toDouble()).toFloat()
                if (dist > maxR && dist > 0f) {
                    val scale = maxR / dist
                    ox = cx + ddx * scale; oy = cy + ddy * scale
                }
                knobX = ox; knobY = oy
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val quick = System.currentTimeMillis() - downTime < 220
                knobX = cx; knobY = cy
                invalidate()
                if (!moved && quick) onTap?.invoke()
            }
        }
        return true
    }
}
