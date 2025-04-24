package com.example.caramaxsample

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun CameraScreen(
    modifier: Modifier = Modifier,
    controller: CameraController = rememberCameraController(),
    isPermissionGranted: Boolean = false
) {
    Box(modifier = modifier) {
        AndroidView(
            modifier = modifier,
            factory = { controller.previewView }
        )
        Button(
            modifier = Modifier.align(Alignment.BottomCenter),
            onClick = controller::takePhoto
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
    }

    LaunchedEffect(isPermissionGranted) {
        if (!isPermissionGranted) return@LaunchedEffect
        controller.startCamera()
    }
}

