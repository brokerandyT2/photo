//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/interfaces/services/ICameraService.kt
package com.x3squaredcircles.pixmap.shared.application.interfaces.services

import com.x3squaredcircles.pixmap.shared.common.Result

/**
 * Camera service interface for photo capture and gallery selection
 */
interface ICameraService {
    suspend fun capturePhoto(): Result<String>
    suspend fun selectFromGallery(): Result<String>
    fun hasPermission(): Boolean
    suspend fun requestPermission(): Boolean
}