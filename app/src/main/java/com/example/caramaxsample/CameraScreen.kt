package com.example.caramaxsample

import android.media.MediaActionSound
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

@Composable
fun CameraScreen(
    modifier: Modifier = Modifier,
    controller: CameraController = rememberCameraController(),
    isPermissionGranted: Boolean = false
) {
    var imageThumbnail by remember { mutableStateOf<ImageBitmap?>(null) }
    var isFlashVisible by remember { mutableStateOf(false) }
    var pickedImage by remember { mutableStateOf<ImageBitmap?>(null) }
    val pickMedia = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            pickedImage = controller.getBitmapFromUri(uri).asImageBitmap()
            imageThumbnail = pickedImage
        }
    }

    LaunchedEffect(Unit) {
        imageThumbnail = controller.getLatestImageThumbnail()?.asImageBitmap()
    }

    LaunchedEffect(isFlashVisible) {
        if (!isFlashVisible) return@LaunchedEffect
        val sound = MediaActionSound()
        sound.play(MediaActionSound.SHUTTER_CLICK)
        delay(50L)
        isFlashVisible = false
    }

    Box(modifier = modifier.padding(horizontal = 16.dp)) {
        AndroidView(
            modifier = modifier,
            factory = { controller.previewView }
        )
        Button(
            modifier = Modifier.align(Alignment.BottomCenter),
            onClick = {
                isFlashVisible = true
                controller.takePhoto {
                    imageThumbnail = controller.getBitmapFromUri(it).asImageBitmap()
                }
            }
        ) {
            if (isPermissionGranted) {
                Text("Take Photo")
            } else {
                Text("Request Permission")
            }
        }

        Button(
            modifier = Modifier.align(Alignment.BottomEnd),
            onClick = controller::switchCamera
        ) {
            Text("Switch")
        }

        val imageModifier = Modifier
            .size(50.dp)
            .align(Alignment.BottomStart)
            .clip(CircleShape)
            .clickable(true) { pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
            .background(MaterialTheme.colorScheme.primary)
            .padding(2.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondary)

        if (imageThumbnail != null) {
            Image(
                modifier = imageModifier,
                bitmap = imageThumbnail!!,
                contentScale = ContentScale.Crop,
                contentDescription = "image thumbnail"
            )
        } else {
            Box(imageModifier)
        }
    }

    if (isFlashVisible) {
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.5f)))
    }

    LaunchedEffect(isPermissionGranted) {
        if (!isPermissionGranted) return@LaunchedEffect
        controller.startCamera()
    }
}

