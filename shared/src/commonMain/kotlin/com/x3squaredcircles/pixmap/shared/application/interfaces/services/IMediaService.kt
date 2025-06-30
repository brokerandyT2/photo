// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/interfaces/services/IMediaService.kt
package com.x3squaredcircles.pixmap.shared.application.interfaces.services

import com.x3squaredcircles.pixmap.shared.application.common.models.Result

/**
 * Interface for media services (photo capture/selection)
 */
interface IMediaService {

    /**
     * Captures a photo using the device camera
     */
    suspend fun capturePhotoAsync(): Result<String>

    /**
     * Picks a photo from the device gallery
     */
    suspend fun pickPhotoAsync(): Result<String>

    /**
     * Checks if the device has camera support
     */
    suspend fun isCaptureSupported(): Result<Boolean>

    /**
     * Deletes a photo file
     */
    suspend fun deletePhotoAsync(filePath: String): Result<Boolean>

    /**
     * Gets the app's photo storage directory
     */
    fun getPhotoStorageDirectory(): String

    /**
     * Compresses an image file
     */
    suspend fun compressImageAsync(filePath: String, quality: Int = 80): Result<String>

    /**
     * Gets image metadata (dimensions, file size, etc.)
     */
    suspend fun getImageMetadataAsync(filePath: String): Result<ImageMetadata>

    /**
     * Checks if the file path is a valid image
     */
    fun isValidImagePath(filePath: String): Boolean

    /**
     * Gets supported image formats
     */
    fun getSupportedImageFormats(): List<String>
}

/**
 * Image metadata information
 */
data class ImageMetadata(
    val width: Int,
    val height: Int,
    val fileSize: Long,
    val format: String,
    val orientation: Int = 0
)

/**
 * Image compression quality levels
 */
object ImageQuality {
    const val LOW = 50
    const val MEDIUM = 75
    const val HIGH = 90
    const val MAXIMUM = 100
}

/**
 * Supported image formats
 */
object ImageFormats {
    const val JPEG = "jpeg"
    const val JPG = "jpg"
    const val PNG = "png"
    const val WEBP = "webp"

    val ALL = listOf(JPEG, JPG, PNG, WEBP)
}