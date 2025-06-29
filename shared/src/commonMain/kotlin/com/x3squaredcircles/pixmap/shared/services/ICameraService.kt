package com.x3squaredcircles.pixmap.shared.services

import com.x3squaredcircles.pixmap.shared.common.Result

/**
 * Interface for camera operations
 */
interface ICameraService {
    suspend fun capturePhoto(): Result<String>
    suspend fun selectFromGallery(): Result<String>
    fun hasPermission(): Boolean
    suspend fun requestPermission(): Boolean
}