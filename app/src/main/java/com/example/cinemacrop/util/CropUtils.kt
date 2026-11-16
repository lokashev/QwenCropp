package com.example.cinemacrop.util

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Состояние кадрирования.
 *
 * Схема отображения: bitmap (с учётом [rotationDegrees]) рисуется в контейнере
 * с масштабом [zoom] и смещением [panOffset] относительно режима ContentScale.Fit.
 * Рамка-оверлей фиксирована по центру; пользователь двигает/зумит изображение,
 * как в стандартном Crop-редакторе.
 */
data class CropState(
    val imageWidthPx: Int = 1,
    val imageHeightPx: Int = 1,
    val rotationDegrees: Float = 0f,
    val zoom: Float = 1f,
    val panOffset: Offset = Offset.Zero,
    val containerSize: Size = Size.Zero,
) {
    /** Соотношение сторон повёрнутого изображения. */
    fun rotatedAspect(): Float {
        val a = imageWidthPx.toFloat() / max(1, imageHeightPx)
        val norm = ((rotationDegrees % 180f) + 180f) % 180f
        return if (abs(norm - 90f) < 1f) 1f / a else a
    }

    /** Размер повёрнутого изображения при ContentScale.Fit в контейнере. */
    fun fittedDisplaySize(): Size {
        val c = containerSize
        if (c.width <= 0f || c.height <= 0f) return Size.Zero
        val aspect = rotatedAspect()
        var w = c.width
        var h = w / aspect
        if (h > c.height) {
            h = c.height
            w = h * aspect
        }
        return Size(w, h)
    }

    /** Минимальный zoom, при котором рамка полностью покрыта изображением. */
    fun minZoomFor(frame: Size): Float {
        val fit = fittedDisplaySize()
        if (fit.width <= 0f || frame.width <= 0f) return 1f
        return max(frame.width / fit.width, frame.height / fit.height)
    }
}

object CropUtils {

    /** Прямоугольник рамки (в координатах контейнера, центр — центр контейнера). */
    fun frameRect(state: CropState, aspect: Float): Rect {
        val c = state.containerSize
        if (c.width <= 0f || c.height <= 0f) return Rect.Zero
        // Максимальная рамка с нужным соотношением, вписанная в контейнер с отступом
        val margin = 0.94f
        var w = c.width * margin
        var h = w / aspect
        if (h > c.height * margin) {
            h = c.height * margin
            w = h * aspect
        }
        val cx = c.width / 2f
        val cy = c.height / 2f
        return Rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    /**
     * Пересчитывает pan так, чтобы изображение гарантированно покрывало рамку.
     */
    fun clampPan(state: CropState, frame: Rect): Offset {
        val fit = state.fittedDisplaySize()
        val dispW = fit.width * state.zoom
        val dispH = fit.height * state.zoom

        val halfImgW = dispW / 2f
        val halfImgH = dispH / 2f
        val halfFrameW = frame.width / 2f
        val halfFrameH = frame.height / 2f

        // Допустимое смещение центра изображения относительно центра контейнера
        val maxPanX = max(halfImgW - halfFrameW, 0f)
        val maxPanY = max(halfImgH - halfFrameH, 0f)

        return Offset(
            state.panOffset.x.coerceIn(-maxPanX, maxPanX),
            state.panOffset.y.coerceIn(-maxPanY, maxPanY)
        )
    }

    /**
     * Вычисляет прямоугольник выреза в пикселях ПОВЁРНУТОГО изображения.
     */
    fun cropRectRotated(state: CropState, frame: Rect): Rect? {
        val fit = state.fittedDisplaySize()
        if (fit.width <= 0f || state.zoom <= 0f) return null

        val dispW = fit.width * state.zoom
        val dispH = fit.height * state.zoom
        val c = state.containerSize

        // Центр контейнера в нормализованных координатах изображения
        val imgCenterX = c.width / 2f - state.panOffset.x
        val imgCenterY = c.height / 2f - state.panOffset.y

        fun toNorm(px: Float, py: Float): Offset = Offset(
            (px - (imgCenterX - dispW / 2f)) / dispW,
            (py - (imgCenterY - dispH / 2f)) / dispH
        )

        val tl = toNorm(frame.left, frame.top)
        val br = toNorm(frame.right, frame.bottom)

        val norm = ((state.rotationDegrees % 180f) + 180f) % 180f
        val swapped = abs(norm - 90f) < 1f
        val rotW = if (swapped) state.imageHeightPx else state.imageWidthPx
        val rotH = if (swapped) state.imageWidthPx else state.imageHeightPx

        val left = tl.x.coerceIn(0f, 1f) * rotW
        val top = tl.y.coerceIn(0f, 1f) * rotH
        val right = br.x.coerceIn(0f, 1f) * rotW
        val bottom = br.y.coerceIn(0f, 1f) * rotH

        if (right - left < 2f || bottom - top < 2f) return null
        return Rect(left, top, right, bottom)
    }

    /**
     * Применяет кадрирование к исходному bitmap:
     * поворот -> вырез -> (опционально) возврат к ориентации исходника не делается,
     * результат всегда в повёрнутой системе координат.
     */
    fun applyCrop(source: Bitmap, rotationDegrees: Float, rectRotated: Rect): Bitmap {
        val rotated = if (rotationDegrees % 360f == 0f) {
            source
        } else {
            val m = Matrix().apply { postRotate(rotationDegrees) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, m, true)
        }

        val x = rectRotated.left.roundToInt().coerceIn(0, max(0, rotated.width - 1))
        val y = rectRotated.top.roundToInt().coerceIn(0, max(0, rotated.height - 1))
        val w = rectRotated.width.roundToInt().coerceIn(1, rotated.width - x)
        val h = rectRotated.height.roundToInt().coerceIn(1, rotated.height - y)

        val result = Bitmap.createBitmap(rotated, x, y, w, h)
        if (rotated !== source && rotated !== result) rotated.recycle()
        return result
    }

    /** Кадрирование по центру под заданное соотношение (без UI). */
    fun centerCropToAspect(source: Bitmap, aspect: Float): Bitmap {
        require(aspect > 0f)
        val sw = source.width
        val sh = source.height
        val imgAspect = sw.toFloat() / sh
        var w = sw
        var h = sh
        if (imgAspect > aspect) w = (sh * aspect).roundToInt() else h = (sw / aspect).roundToInt()
        w = w.coerceIn(1, sw)
        h = h.coerceIn(1, sh)
        val x = (sw - w) / 2
        val y = (sh - h) / 2
        return Bitmap.createBitmap(source, x, y, w, h)
    }

    /** Эффективное итоговое соотношение сторон после кадрирования. */
    fun effectiveAspect(rect: Rect): Float =
        if (rect.height <= 0f) 1f else min(rect.width / rect.height, Float.MAX_VALUE)
}
