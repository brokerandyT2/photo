//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/interfaces/repositories/ICameraBodyRepository.kt
package com.x3squaredcircles.pixmap.shared.application.interfaces.repositories

import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.domain.entities.CameraBody
import com.x3squaredcircles.pixmap.shared.domain.entities.MountType

/**
 * Repository interface for CameraBody operations
 */
interface ICameraBodyRepository {
    suspend fun getByIdAsync(id: Int): Result<CameraBody?>
    suspend fun getAllAsync(): Result<List<CameraBody>>
    suspend fun getByNameAsync(name: String): Result<CameraBody?>
    suspend fun getByMountTypeAsync(mountType: MountType): Result<List<CameraBody>>
    suspend fun getBySensorTypeAsync(sensorType: String): Result<List<CameraBody>>
    suspend fun getByManufacturerAsync(manufacturer: String): Result<List<CameraBody>>
    suspend fun getUserCreatedAsync(): Result<List<CameraBody>>
    suspend fun getSystemCreatedAsync(): Result<List<CameraBody>>
    suspend fun createAsync(cameraBody: CameraBody): Result<CameraBody>
    suspend fun updateAsync(cameraBody: CameraBody): Result<CameraBody>
    suspend fun deleteAsync(id: Int): Result<Boolean>
    suspend fun existsByNameAsync(name: String): Result<Boolean>
    suspend fun createBulkAsync(cameraBodies: List<CameraBody>): Result<List<CameraBody>>
}