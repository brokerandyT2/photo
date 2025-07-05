//app/src/main/kotlin/com/x3squaredcircles/pixmap/androidapp/services/AndroidServicePlaceholders.kt
package com.x3squaredcircles.pixmap.androidapp.services

import android.content.Context
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.*

/**
 * Placeholder Android service implementations
 * These would be fully implemented based on Android-specific requirements
 */

class AndroidCameraService(private val context: Context) : ICameraService {
    override suspend fun capturePhoto(): Result<String> {
        return Result.failure("Not implemented yet")
    }

    override suspend fun selectFromGallery(): Result<String> {
        return Result.failure("Not implemented yet")
    }

    override fun hasPermission(): Boolean = false

    override suspend fun requestPermission(): Boolean = false
}
