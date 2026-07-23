package com.appsonar.homesonar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

/**
 * ImageView mit Pinch-Zoom und Verschieben; Doppeltipp zoomt hinein bzw.
 * setzt zurück. [visibleSourceRect] liefert den gerade sichtbaren Ausschnitt
 * in Pixelkoordinaten der Bitmap — Grundlage für "Ausschnitt speichern".
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatImageView(context, attrs) {

    /** Meldet Zoom-Änderungen (true = hineingezoomt). */
    var onZoomChanged: ((Boolean) -> Unit)? = null

    private val baseMatrix = Matrix() // passt das Bild eingemittet in die View
    private val userMatrix = Matrix() // Zoom + Verschiebung des Nutzers
    private val drawMatrix = Matrix()
    private var userScale = 1f

    init {
        scaleType = ScaleType.MATRIX
    }

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                applyScale(detector.scaleFactor, detector.focusX, detector.focusY)
                return true
            }
        },
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                if (userScale <= 1f) return false
                userMatrix.postTranslate(-distanceX, -distanceY)
                update()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (userScale > 1f) reset() else applyScale(2.5f, e.x, e.y)
                return true
            }
        },
    )

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Umgebende ScrollViews/Dialoge dürfen die Gesten nicht abfangen.
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        userScale = 1f
        userMatrix.reset()
        computeBase()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeBase()
    }

    fun isZoomed(): Boolean = userScale > 1.01f

    fun reset() {
        userScale = 1f
        userMatrix.reset()
        update()
    }

    /** Sichtbarer Ausschnitt in Bitmap-Pixeln, oder null ohne Bild. */
    fun visibleSourceRect(): Rect? {
        val d = drawable ?: return null
        val matrix = Matrix(baseMatrix).apply { postConcat(userMatrix) }
        val inverse = Matrix()
        if (!matrix.invert(inverse)) return null
        val viewRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        inverse.mapRect(viewRect)
        val rect = Rect(
            viewRect.left.toInt().coerceAtLeast(0),
            viewRect.top.toInt().coerceAtLeast(0),
            viewRect.right.toInt().coerceAtMost(d.intrinsicWidth),
            viewRect.bottom.toInt().coerceAtMost(d.intrinsicHeight),
        )
        return if (rect.width() > 0 && rect.height() > 0) rect else null
    }

    private fun computeBase() {
        val d = drawable ?: return
        if (width == 0 || height == 0) return
        baseMatrix.setRectToRect(
            RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat()),
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            Matrix.ScaleToFit.CENTER,
        )
        update()
    }

    private fun applyScale(factor: Float, focusX: Float, focusY: Float) {
        val newScale = (userScale * factor).coerceIn(1f, MAX_SCALE)
        val applied = newScale / userScale
        userScale = newScale
        userMatrix.postScale(applied, applied, focusX, focusY)
        update()
    }

    private fun update() {
        clampTranslation()
        drawMatrix.set(baseMatrix)
        drawMatrix.postConcat(userMatrix)
        imageMatrix = drawMatrix
        onZoomChanged?.invoke(isZoomed())
    }

    /** Hält das Bild im Rahmen: keine Lücken beim Verschieben, sonst mittig. */
    private fun clampTranslation() {
        val d = drawable ?: return
        val matrix = Matrix(baseMatrix).apply { postConcat(userMatrix) }
        val rect = RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        matrix.mapRect(rect)

        var dx = 0f
        var dy = 0f
        if (rect.width() <= width) {
            dx = width / 2f - rect.centerX()
        } else if (rect.left > 0) {
            dx = -rect.left
        } else if (rect.right < width) {
            dx = width - rect.right
        }
        if (rect.height() <= height) {
            dy = height / 2f - rect.centerY()
        } else if (rect.top > 0) {
            dy = -rect.top
        } else if (rect.bottom < height) {
            dy = height - rect.bottom
        }
        if (dx != 0f || dy != 0f) userMatrix.postTranslate(dx, dy)
    }

    companion object {
        private const val MAX_SCALE = 8f
    }
}
