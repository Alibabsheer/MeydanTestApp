package com.example.meydantestapp

import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * MultiTouchListener
 * مستمع لمس يسمح بسحب/تكبير/تدوير أي View (نص/ملصق) على طبقة الملصقات.
 */
class MultiTouchListener : View.OnTouchListener {

    private enum class Mode { NONE, DRAG, ZOOM_ROTATE }

    private var mode = Mode.NONE

    private val startPoint = PointF()
    private val midPoint = PointF()

    private var startDist = 1f
    private var startAngle = 0f

    private var savedX = 0f
    private var savedY = 0f
    private var savedRotation = 0f
    private var savedScale = 1f

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mode = Mode.DRAG
                startPoint.set(event.x, event.y)
                savedX = v.translationX
                savedY = v.translationY
                savedRotation = v.rotation
                savedScale = v.scaleX
                v.parent.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    mode = Mode.ZOOM_ROTATE
                    startDist = spacing(event)
                    startAngle = rotation(event)
                    computeMidPoint(midPoint, event)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                when (mode) {
                    Mode.DRAG -> {
                        val dx = event.x - startPoint.x
                        val dy = event.y - startPoint.y
                        v.translationX = savedX + dx
                        v.translationY = savedY + dy
                    }
                    Mode.ZOOM_ROTATE -> {
                        if (event.pointerCount >= 2) {
                            val newDist = spacing(event)
                            val scale = (newDist / startDist).coerceIn(0.2f, 6f)
                            v.scaleX = savedScale * scale
                            v.scaleY = savedScale * scale

                            val newAngle = rotation(event)
                            v.rotation = savedRotation + (newAngle - startAngle)

                            // ثبّت المحور في مركز العنصر لتحقيق دوران/تكبير طبيعي
                            v.pivotX = v.width / 2f
                            v.pivotY = v.height / 2f
                        }
                    }
                    else -> Unit
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                mode = Mode.NONE
                v.parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return false
    }

    private fun spacing(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 1f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return hypot(dx, dy)
    }

    private fun rotation(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
    }

    private fun computeMidPoint(p: PointF, event: MotionEvent) {
        if (event.pointerCount < 2) return
        val x = (event.getX(0) + event.getX(1)) / 2f
        val y = (event.getY(0) + event.getY(1)) / 2f
        p.set(x, y)
    }
}
