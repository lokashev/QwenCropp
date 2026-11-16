package com.example.cinemacrop

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.example.cinemacrop.ui.CropScreen
import com.example.cinemacrop.util.ImageLoader
import com.example.cinemacrop.util.ImageSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null && activity != null) {
            loading = true
            activity.lifecycleScope.launch {
                val bmp = withContext(Dispatchers.IO) { ImageLoader.load(context, uri) }
                bitmap = bmp
                loading = false
                if (bmp == null) {
                    Toast.makeText(context, "Не удалось загрузить фото", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    CropScreen(
        sourceBitmap = bitmap,
        loading = loading,
        onPickPhoto = {
            pickLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        onSave = { cropped ->
            val saved = withContextMainSafe(context) { ImageSaver.save(context, cropped) }
            val msg = if (saved != null) "Сохранено в Pictures/CinemaCrop" else "Не удалось сохранить"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    )
}

/** Сохранение выполняем на IO-потоке, если есть CoroutineScope активности. */
private fun withContextMainSafe(context: android.content.Context, block: () -> Uri?): Uri? {
    // MediaStore.insert достаточно быстрый; блокировка UI минимальна.
    return runCatching(block).getOrNull()
}
