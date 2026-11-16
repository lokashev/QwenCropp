package com.example.cinemacrop.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.cinemacrop.util.AspectRatioPreset
import com.example.cinemacrop.util.CropState
import com.example.cinemacrop.util.CropUtils
import kotlin.math.roundToInt

/**
 * Экран кадрирования фото в кинематографическом соотношении сторон.
 *
 * Жесты: щипок — зум, перетаскивание — панорамирование изображения.
 * Рамка фиксирована по центру, изображение под ней двигается.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    sourceBitmap: Bitmap?,
    onSave: (Bitmap) -> Unit,
    onPickPhoto: () -> Unit,
    loading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var selectedPreset by remember { mutableStateOf(AspectRatioPreset.SCOPE) }
    var rotation by remember(sourceBitmap) { mutableStateOf(0f) }
    var zoom by remember(sourceBitmap) { mutableStateOf(1f) }
    var pan by remember(sourceBitmap) { mutableStateOf(Offset.Zero) }
    var containerPx by remember(sourceBitmap) { mutableStateOf(IntSize.Zero) }
    val snackbarHostState = remember { SnackbarHostState() }

    val cropState = CropState(
        imageWidthPx = sourceBitmap?.width ?: 1,
        imageHeightPx = sourceBitmap?.height ?: 1,
        rotationDegrees = rotation,
        zoom = zoom,
        panOffset = pan,
        containerSize = Size(containerPx.width.toFloat(), containerPx.height.toFloat())
    )

    val aspect = when (selectedPreset) {
        AspectRatioPreset.ORIGINAL -> cropState.rotatedAspect()
        else -> selectedPreset.ratio
    }
    val frame = remember(cropState.containerSize, aspect) {
        CropUtils.frameRect(cropState, aspect)
    }
    val minZoom = remember(frame, cropState.containerSize, rotation, sourceBitmap) {
        cropState.minZoomFor(Size(frame.width, frame.height))
    }

    // Подгонка зума/пана при смене пресета, повороте или размера контейнера
    LaunchedEffect(minZoom, containerPx, sourceBitmap) {
        if (sourceBitmap == null || containerPx.width == 0) return@LaunchedEffect
        if (zoom < minZoom) zoom = minZoom
        pan = CropUtils.clampPan(cropState.copy(zoom = zoom), frame)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Кино-кадрирование", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onPickPhoto) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Выбрать фото")
                    }
                    IconButton(
                        onClick = { rotation = (rotation + 90f) % 360f },
                        enabled = sourceBitmap != null
                    ) {
                        Icon(Icons.Default.Rotate90DegreesCw, contentDescription = "Повернуть на 90°")
                    }
                    IconButton(
                        onClick = {
                            val bmp = sourceBitmap ?: return@IconButton
                            val rect = CropUtils.cropRectRotated(cropState, frame)
                            val result = if (rect != null) {
                                CropUtils.applyCrop(bmp, rotation, rect)
                            } else {
                                CropUtils.centerCropToAspect(bmp, aspect)
                            }
                            onSave(result)
                        },
                        enabled = sourceBitmap != null
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Сохранить")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
            ) {
                HorizontalDivider()
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Crop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${selectedPreset.description}  •  ${"%.2f".format(aspect)}:1",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(AspectRatioPreset.entries.toList()) { preset ->
                        PresetChip(
                            preset = preset,
                            selected = preset == selectedPreset,
                            onClick = { selectedPreset = preset }
                        )
                    }
                }
                if (sourceBitmap == null) {
                    Button(
                        onClick = onPickPhoto,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Выбрать фото из галереи")
                    }
                } else {
                    Row(
                        Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Slider(
                            value = zoom,
                            onValueChange = { z ->
                                zoom = z.coerceIn(minZoom, 6f)
                                pan = CropUtils.clampPan(cropState.copy(zoom = zoom), frame)
                            },
                            valueRange = minZoom..6f.coerceAtLeast(minZoom),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            zoom = minZoom
                            pan = Offset.Zero
                        }) { Text("Сброс") }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
                .onSizeChanged { containerPx = it },
            contentAlignment = Alignment.Center
        ) {
            val bmp = sourceBitmap
            if (bmp == null) {
                Text(
                    "Фото не выбрано\nНажмите «Выбрать фото»",
                    color = Color.White.copy(alpha = 0.7f)
                )
            } else {
                CropCanvas(
                    bitmap = bmp,
                    state = cropState,
                    frame = frame,
                    onGesture = { centroid, panChange, zoomChange ->
                        val centerX = containerPx.width / 2f
                        val centerY = containerPx.height / 2f
                        val newZoom = (zoom * zoomChange).coerceIn(minZoom, 6f)
                        val ratio = newZoom / zoom
                        // Компенсация смещения центра при зуме вокруг точки касания
                        val c = Offset(centroid.x - centerX, centroid.y - centerY)
                        val newPan = pan + panChange + c * (1f - ratio)
                        zoom = newZoom
                        pan = CropUtils.clampPan(
                            cropState.copy(zoom = newZoom, panOffset = newPan), frame
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun PresetChip(preset: AspectRatioPreset, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Box(
            Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                preset.title,
                color = fg,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

/**
 * Canvas: рисует изображение с трансформациями и рамку-оверлей с затемнением.
 */
@Composable
private fun CropCanvas(
    bitmap: Bitmap,
    state: CropState,
    frame: Rect,
    onGesture: (centroid: Offset, panChange: Offset, zoomChange: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageBmp = remember(bitmap) { bitmap.asImageBitmap() }

    Canvas(
        modifier = modifier.pointerInput(bitmap, state.rotationDegrees) {
            detectTransformGestures { centroid, panChange, zoomChange, _ ->
                onGesture(centroid, panChange, zoomChange)
            }
        }
    ) {
        val fit = state.fittedDisplaySize()
        if (fit.width <= 0f || fit.height <= 0f) return@Canvas

        withTransform({
            translate(state.containerSize.width / 2f + state.panOffset.x,
                      state.containerSize.height / 2f + state.panOffset.y)
            rotate(state.rotationDegrees)
            scale(state.zoom, state.zoom, pivot = Offset.Zero)
        }) {
            drawImage(
                image = imageBmp,
                dstSize = IntSize(fit.width.roundToInt().coerceAtLeast(1),
                                  fit.height.roundToInt().coerceAtLeast(1)),
                dstOffset = IntOffset((-fit.width / 2f).roundToInt(),
                                      (-fit.height / 2f).roundToInt())
            )
        }

        // Затемнение вне рамки (четыре прямоугольника)
        val scrim = Color.Black.copy(alpha = 0.55f)
        if (frame.top > 0f)
            drawRect(scrim, Offset.Zero, Size(size.width, frame.top))
        if (frame.bottom < size.height)
            drawRect(scrim, Offset(0f, frame.bottom), Size(size.width, size.height - frame.bottom))
        if (frame.left > 0f)
            drawRect(scrim, Offset(0f, frame.top), Size(frame.left, frame.height))
        if (frame.right < size.width)
            drawRect(scrim, Offset(frame.right, frame.top), Size(size.width - frame.right, frame.height))

        // Контур рамки
        drawRect(
            color = Color.White,
            topLeft = Offset(frame.left, frame.top),
            size = Size(frame.width, frame.height),
            style = Stroke(width = 2f)
        )
        // Сетка «правило третей»
        val grid = Color.White.copy(alpha = 0.35f)
        for (i in 1..2) {
            val x = frame.left + frame.width * i / 3f
            drawLine(grid, Offset(x, frame.top), Offset(x, frame.bottom), strokeWidth = 1f)
            val y = frame.top + frame.height * i / 3f
            drawLine(grid, Offset(frame.left, y), Offset(frame.right, y), strokeWidth = 1f)
        }
    }
}
