package com.x3squaredcircles.pixmap.android.services

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.x3squaredcircles.pixmap.shared.common.Result
import com.x3squaredcircles.pixmap.shared.services.ICameraService
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume

/**
 * Android implementation of camera service using native camera APIs
 */
class AndroidCameraService(
    private val context: Context
) : ICameraService {

    private var pendingPhotoUri: Uri? = null
    private var photoResultCallback: ((Result<String>) -> Unit)? = null

    override suspend fun capturePhoto(): Result<String> {
        return if (hasPermission()) {
            suspendCancellableCoroutine { continuation ->
                try {
                    val photoFile = createImageFile()
                    val photoUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        photoFile
                    )

                    pendingPhotoUri = photoUri
                    photoResultCallback = { result ->
                        continuation.resume(result)
                    }

                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                    }

                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    } else {
                        continuation.resume(Result.error("No camera app available"))
                    }
                } catch (e: Exception) {
                    continuation.resume(Result.error(e))
                }
            }
        } else {
            Result.error("Camera permission not granted")
        }
    }

    override suspend fun selectFromGallery(): Result<String> {
        return if (hasPermission()) {
            suspendCancellableCoroutine { continuation ->
                try {
                    photoResultCallback = { result ->
                        continuation.resume(result)
                    }

                    val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    } else {
                        continuation.resume(Result.error("No gallery app available"))
                    }
                } catch (e: Exception) {
                    continuation.resume(Result.error(e))
                }
            }
        } else {
            Result.error("Storage permission not granted")
        }
    }

    override fun hasPermission(): Boolean {
        val cameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val storagePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        return cameraPermission && storagePermission
    }

    override suspend fun requestPermission(): Boolean {
        return hasPermission()
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    fun onCameraResult(success: Boolean) {
        val callback = photoResultCallback
        photoResultCallback = null

        if (success && pendingPhotoUri != null) {
            val path = pendingPhotoUri.toString()
            pendingPhotoUri = null
            callback?.invoke(Result.success(path))
        } else {
            pendingPhotoUri = null
            callback?.invoke(Result.error("Camera capture failed"))
        }
    }

    fun onGalleryResult(uri: Uri?) {
        val callback = photoResultCallback
        photoResultCallback = null

        if (uri != null) {
            callback?.invoke(Result.success(uri.toString()))
        } else {
            callback?.invoke(Result.error("Gallery selection cancelled"))
        }
    }
}