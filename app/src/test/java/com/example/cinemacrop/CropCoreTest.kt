package com.example.cinemacrop

import com.example.cinemacrop.core.*
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class CropCoreTest {

    private val aspect = 2.39f

    @Test fun frameHasPresetRatio() {
        val m = CropModel(4000, 3000, containerWidth = 1000f, containerHeight = 800f)
        val f = CoreCropUtils.frameRect(m, aspect)
        assertTrue(abs(f.width / f.height - aspect) < 0.01f)
    }

    @Test fun minZoomCropCoversFrameWithImage() {
        val m = CropModel(4000, 3000, containerWidth = 1000f, containerHeight = 800f)
        val f = CoreCropUtils.frameRect(m, aspect)
        val z = m.minZoomFor(FSize(f.width, f.height))
        val r = CoreCropUtils.cropRectRotated(m.copy(zoom = z), f)!!
        // нормализованные координаты рамки должны быть в [0..1] без обрезки
        assertTrue(r.left >= 0f && r.top >= 0f && r.right <= 4000.01f && r.bottom <= 3000.01f)
        assertTrue(abs(r.width / r.height - aspect) < 0.02f)
    }

    @Test fun clampedPanKeepsRectInBounds() {
        val m = CropModel(4000, 3000, containerWidth = 1000f, containerHeight = 800f)
        val f = CoreCropUtils.frameRect(m, aspect)
        val z = m.minZoomFor(FSize(f.width, f.height))
        val panned = m.copy(zoom = z, panX = 5000f, panY = 5000f)
        val (px, py) = CoreCropUtils.clampPan(panned, f)
        val r = CoreCropUtils.cropRectRotated(panned.copy(panX = px, panY = py), f)!!
        assertTrue(r.left >= -0.01f && r.top >= -0.01f && r.right <= 4000.01f && r.bottom <= 3000.01f)
    }

    @Test fun rotated90AspectAndBounds() {
        val m = CropModel(4000, 3000, rotationDegrees = 90f, containerWidth = 1000f, containerHeight = 800f)
        assertTrue(abs(m.rotatedAspect() - 3000f / 4000f) < 0.001f)
        val f = CoreCropUtils.frameRect(m, aspect)
        val z = m.minZoomFor(FSize(f.width, f.height))
        val r = CoreCropUtils.cropRectRotated(m.copy(zoom = z), f)!!
        assertTrue(r.left >= 0f && r.top >= 0f && r.right <= 3000.01f && r.bottom <= 4000.01f)
    }

    @Test fun centerCropAspectAndPosition() {
        val cc = CoreCropUtils.centerCropRect(4000, 3000, aspect)
        assertTrue(abs(cc.width / cc.height - aspect) < 0.01f)
        assertTrue(abs((cc.left + cc.right) / 2 - 2000f) < 1f)
        assertTrue(abs((cc.top + cc.bottom) / 2 - 1500f) < 1f)
    }

    @Test fun sourceRectRotationMapping() {
        // вырез по всей ширине повёрнутого изображения (3000) и высоте 1256 ->
        // в исходнике (4000x3000) ширина=1256, высота=3000
        val src = CoreCropUtils.cropRectInSource(4000, 3000, 90f, FRect(0f, 500f, 3000f, 1756f))
        assertTrue(abs(src.width - 1256f) < 1f)
        assertTrue(abs(src.height - 3000f) < 1f)
    }
}
