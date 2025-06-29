// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/interfaces/repositories/ISettingRepository.kt
package com.x3squaredcircles.pixmap.shared.application.interfaces.repositories

import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.domain.entities.Setting

/**
 * Repository interface for Setting operations
 */
interface ISettingRepository {
    suspend fun getByKeyAsync(key: String): Result<Setting?>
    suspend fun getAllAsync(): Result<List<Setting>>
    suspend fun createAsync(setting: Setting): Result<Setting>
    suspend fun updateAsync(setting: Setting): Result<Setting>
    suspend fun deleteAsync(key: String): Result<Boolean>
    suspend fun upsertAsync(setting: Setting): Result<Setting>
    suspend fun getAllAsDictionaryAsync(): Result<Map<String, String>>
}
