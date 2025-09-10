package com.example.meydantestapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View

/**
 * DrawingView
 * لوحة رسم بسيطة تدعم: متعدد اللمس، تغيير اللون/السمك، تراجع/إعادة.
 *
 * استخدم setBrushColor / setBrushSize لضبط القلم.
 * استدعِ undo()/redo() للإرجاع/الإعادة.
 */
class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ضربة (مسار + إعدادات) واحدة
    private data class Stroke(val path: Path, val paint: Paint)

    private val strokes = mutableListOf<Stroke>()          // المنفذة (مرسومة)
    private val redoStack = mutableListOf<Stroke>()        // للـ Redo

    // ضربات نشطة أثناء اللمس المتعدد (pointerId -> Stroke)
    private val active = SparseArray<Stroke>()

    // إعدادات القلم الحالية
    private var currentColor: Int = Color.WHITE
    private var currentSize: Float = 16f // px

    init {
        setWillNotDraw(false)
        isClickable = true
    }

    // ===== API عامة =====

    fun setBrushColor(color: Int) {
        currentColor = color
    }

    fun setBrushSize(px: Float) {
        currentSize = px.coerceAtLeast(1f)
    }

    fun undo(): Boolean {
        if (strokes.isNotEmpty()) {
            redoStack.add(strokes.removeAt(strokes.lastIndex))
            invalidate()
            return true
        }
        return false
    }

    fun redo(): Boolean {
        if (redoStack.isNotEmpty()) {
            strokes.add(redoStack.removeAt(redoStack.lastIndex))
            invalidate()
            return true
        }
        return false
    }

    fun clearAll() {
        strokes.clear()
        redoStack.clear()
        active.clear()
        invalidate()
    }

    // ===== الرسم =====

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // ارسم جميع الضربات بالتسلسل
        for (s in strokes) {
            canvas.drawPath(s.path, s.paint)
        }
        // قد توجد ضربات نشطة لم تُغلق بعد
        for (i in 0 until active.size()) {
            val s = active.valueAt(i)
            canvas.drawPath(s.path, s.paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val pid = event.getPointerId(index)
                val stroke = makeStroke()
                stroke.path.moveTo(event.getX(index), event.getY(index))
                active.put(pid, stroke)
                strokes.add(stroke)
                // أي رسم جديد يلغي إمكانية إعادة الخطوات السابقة
                redoStack.clear()
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    val s = active.get(pid) ?: continue
                    s.path.lineTo(event.getX(i), event.getY(i))
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val index = event.actionIndex
                val pid = event.getPointerId(index)
                active.remove(pid)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun makeStroke(): Stroke {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = currentColor
            strokeWidth = currentSize
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isDither = true
        }
        return Stroke(Path(), p)
    }
}
