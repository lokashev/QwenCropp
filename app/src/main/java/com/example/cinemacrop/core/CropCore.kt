// Чистая Kotlin-логика кадрирования (без Android/Compose) — для unit-тестов.
package com.example.cinemacrop.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class FRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class FSize(val width: Float, val height: Float)

data class CropModel(
    val imageWidthPx: Int,
    val imageHeightPx: Int,
    val rotationDegrees: Float = 0f,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val containerWidth: Float = 0f,
    val containerHeight: Float = 0f,
) {
    fun rotatedAspect(): Float {
        val a = imageWidthPx.toFloat() / max(1, imageHeightPx)
        val norm = ((rotationDegrees % 180f) + 180f) % 180f
        return if (abs(norm - 90f) < 1f) 1f / a else a
    }

    fun fittedDisplaySize(): FSize {
        if (containerWidth <= 0f || containerHeight <= 0f) return FSize(0f, 0f)
        val aspect = rotatedAspect()
        var w = containerWidth
        var h = w / aspect
        if (h > containerHeight) {
            h = containerHeight
            w = h * aspect
        }
        return FSize(w, h)
    }

    fun minZoomFor(frame: FSize): Float {
        val fit = fittedDisplaySize()
        if (fit.width <= 0f || frame.width <= 0f) return 1f
        return max(frame.width / fit.width, frame.height / fit.height)
    }
}

object CoreCropUtils {

    fun frameRect(model: CropModel, aspect: Float): FRect {
        if (model.containerWidth <= 0f || model.containerHeight <= 0f) return FRect(0f, 0f, 0f, 0f)
        val margin = 0.94f
        var w = model.containerWidth * margin
        var h = w / aspect
        if (h > model.containerHeight * margin) {
            h = model.containerHeight * margin
            w = h * aspect
        }
        val cx = model.containerWidth / 2f
        val cy = model.containerHeight / 2f
        return FRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    fun clampPan(model: CropModel, frame: FRect): Pair<Float, Float> {
        val fit = model.fittedDisplaySize()
        val dispW = fit.width * model.zoom
        val dispH = fit.height * model.zoom
        val maxPanX = max(dispW / 2f - frame.width / 2f, 0f)
        val maxPanY = max(dispH / 2f - frame.height / 2f, 0f)
        return model.panX.coerceIn(-maxPanX, maxPanX) to model.panY.coerceIn(-maxPanY, maxPanY)
    }

    fun cropRectRotated(model: CropModel, frame: FRect): FRect? {
        val fit = model.fittedDisplaySize()
        if (fit.width <= 0f || model.zoom <= 0f) return null
        val dispW = fit.width * model.zoom
        val dispH = fit.height * model.zoom

        val imgLeft = model.containerWidth / 2f - model.panX - dispW / 2f
        val imgTop = model.containerHeight / 2f - model.panY - dispH / 2f

        val uL = (frame.left - imgLeft) / dispW
        val uR = (frame.right - imgLeft) / dispW
        val vT = (frame.top - imgTop) / dispH
        val vB = (frame.bottom - imgTop) / dispH

        val norm = ((model.rotationDegrees % 180f) + 180f) % 180f
        val swapped = abs(norm - 90f) < 1f
        val rotW = if (swapped) model.imageHeightPx else model.imageWidthPx
        val rotH = if (swapped) model.imageWidthPx else model.imageHeightPx

        val left = uL.coerceIn(0f, 1f) * rotW
        val right = uR.coerceIn(0f, 1f) * rotW
        val top = vT.coerceIn(0f, 1f) * rotH
        val bottom = vB.coerceIn(0f, 1f) * rotH
        if (right - left < 2f || bottom - top < 2f) return null
        return FRect(left, top, right, bottom)
    }

    /** Прямоугольник выреза в пикселях исходного bitmap с учётом поворота. */
    fun cropRectInSource(imageW: Int, imageH: Int, rotationDegrees: Float, rectRotated: FRect): FRect {
        val norm = ((rotationDegrees % 360f) + 360f) % 360f
        return when {
            norm < 1f || norm > 359f -> rectRotated
            abs(norm - 90f) < 1f -> FRect(
                rectRotated.top,
                (imageW - rectRotated.right).coerceAtLeast(0f),
                rectRotated.bottom,
                (imageW - rectRotated.left).coerceAtLeast(0f)
            )
            abs(norm - 180f) < 1f -> FRect(
                imageW - rectRotated.right,
                imageH - rectRotated.bottom,
                imageW - rectRotated.left,
                imageH - rectRotated.top
            )
            else -> FRect( // 270
                (imageH - rectRotated.bottom).coerceAtLeast(0f),
                rectRotated.left,
                (imageH - rectRotated.top).coerceAtLeast(0f),
                rectRotated.right
            )
        }
    }

    fun centerCropRect(imgW: Int, imgH: Int, aspect: Float): FRect {
        require(aspect > 0f)
        val imgAspect = imgW.toFloat() / imgH
        var w = imgW
        var h = imgH
        if (imgAspect > aspect) w = (imgH * aspect).roundToInt() else h = (imgW / aspect).roundToInt()
        w = w.coerceIn(1, imgW)
        h = h.coerceIn(1, imgH)
        val x = (imgW - w) / 2
        val y = (imgH - h) / 2
        return FRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())
    }
}
