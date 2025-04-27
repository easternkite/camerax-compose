package com.example.caramaxsample

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * A controller class for managing camera operations using CameraX library.
 *
 * This class handles camera initialization, preview, image capture, and camera switching
 * between front and back cameras. It utilizes the CameraX API for efficient camera management.
 *
 * @property context The application context.
 * @property lifecycleOwner The lifecycle owner for the camera.
 */
class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    private val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    private val cameraProvider : ProcessCameraProvider get() = cameraProviderFuture.get()
    private val imageCapture = ImageCapture.Builder().build()
    private var isCameraFront = false
    private val preview get() = Preview.Builder().build()
        .also { it.surfaceProvider = previewView.surfaceProvider }

    private var _previewView = PreviewView(context)
    val previewView by lazy { _previewView }

    /**
     * Start the camera preview
     */
    fun startCamera() {
        cameraProviderFuture.addListener({
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, getSelector(), preview, imageCapture)
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Take a photo and save it to the external storage
     */
    fun takePhoto(onPhotoTaken: (Uri) -> Unit = {}) {
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.getDefault())
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            .build()

        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded: ${outputFileResults.savedUri}"
                    outputFileResults.savedUri?.also { onPhotoTaken(it) }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                }
            }
        )
    }

    /**
     * Switch the camera between front and back
     */
    fun switchCamera() {
        val selector = if (isCameraFront) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            isCameraFront = !isCameraFront
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Get the camera selector based on the current camera position
     */
    private fun getSelector() = if (isCameraFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

    /**
     * Retrieves the thumbnail of the latest image added to the device's external storage.
     *
     * This function queries the MediaStore for the most recently added image and then retrieves its thumbnail.
     * It handles different API levels for thumbnail retrieval.
     *
     * @return A [Bitmap] representing the thumbnail of the latest image, or null if no images are found or if an error occurs.
     *
     * The thumbnail is returned in 200x200 size for devices with Android Q and higher.
     * For devices below Android Q, it gets the MINI_KIND thumbnail.
     *
     * The operation is performed on the [Dispatchers.IO] thread to avoid blocking the main thread.
     */
    internal suspend fun getLatestImageThumbnail(): Bitmap? = withContext(Dispatchers.IO) {
        val projection = arrayOf(MediaStore.Images.Media._ID)

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val query = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        query?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val id = cursor.getLong(idColumn)


                return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentUri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )

                    return@withContext context.contentResolver.loadThumbnail(contentUri, Size(200, 200), null)
                } else {
                    MediaStore.Images.Thumbnails.getThumbnail(
                        context.contentResolver,
                        id,
                        MediaStore.Images.Thumbnails.MINI_KIND,
                        null
                    )
                }
            }
        }
        return@withContext null
    }

    /**
     * Retrieves a Bitmap from a given Uri.
     *
     * This function handles different Android API levels for optimal bitmap decoding.
     * On Android Pie (API level 28) and above, it uses `ImageDecoder` for efficient decoding.
     * On older versions, it falls back to `MediaStore.Images.Media.getBitmap`.
     *
     * @param uri The Uri of the image to decode.
     * @return The decoded Bitmap.
     * @throws IllegalArgumentException if the Uri is invalid or the image cannot be decoded.
     * @throws SecurityException If the app doesn't have permission to access the content at the uri.
     *
     * @sample
     *  val myImageUri = Uri.parse("content://media/external/images/media/123")
     *  try {
     *     val bitmap = getBitmapFromUri(myImageUri)
     *     // Use the bitmap
     *  } catch(e: Exception) {
     *      //Handle exception
     *  }
     */
    fun getBitmapFromUri(uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }

    companion object {
        private const val TAG = "CameraController"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private var cameraPreview: PreviewView? = null
        fun Saver(context: Context, owner: LifecycleOwner) = mapSaver(
            save = {
                cameraPreview = it.previewView
                mapOf("selector" to it.isCameraFront)
            },
            restore = { map ->
                CameraController(
                    context = context,
                    lifecycleOwner = owner,
                ).also {
                    it.isCameraFront = map["selector"] as Boolean
                    it._previewView = cameraPreview!!
                    cameraPreview = null
                }
            }
        )
    }
}

/**
 * Remember the camera controller
 */
@Composable
fun rememberCameraController(): CameraController {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    return rememberSaveable(
        saver = CameraController.Saver(context, lifecycleOwner)
    ) {
        CameraController(context, lifecycleOwner)
    }
}
