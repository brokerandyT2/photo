// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/data/repositories/SettingRepositoryAdapter.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.data.repositories

import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ISettingRepository
import com.x3squaredcircles.pixmap.shared.domain.entities.Setting

/**
 * Adapter that bridges Application ISettingRepository to Infrastructure SettingRepository
 */
class SettingRepositoryAdapter(
    private val innerRepository: SettingRepository
) : ISettingRepository {

    override suspend fun getByKeyAsync(key: String): Result<Setting?> {
        return try {
            val setting = innerRepository.getByKeyAsync(key)
            Result.success(setting)
        } catch (ex: Exception) {
            Result.failure("Failed to retrieve setting: ${ex.message}")
        }
    }

    override suspend fun getAllAsync(): Result<List<Setting>> {
        return try {
            val settings = innerRepository.getAllAsync()
            Result.success(settings)
        } catch (ex: Exception) {
            Result.failure("Failed to retrieve settings: ${ex.message}")
        }
    }

    override suspend fun createAsync(setting: Setting): Result<Setting> {
        return try {
            val created = innerRepository.createAsync(setting)
            Result.success(created)
        } catch (ex: Exception) {
            Result.failure("Failed to create setting: ${ex.message}")
        }
    }

    override suspend fun updateAsync(setting: Setting): Result<Setting> {
        return try {
            val updated = innerRepository.updateAsync(setting)
            Result.success(updated)
        } catch (ex: Exception) {
            Result.failure("Failed to update setting: ${ex.message}")
        }
    }

    override suspend fun deleteAsync(key: String): Result<Boolean> {
        return try {
            val deleted = innerRepository.deleteAsync(key)
            Result.success(deleted)
        } catch (ex: Exception) {
            Result.failure("Failed to delete setting: ${ex.message}")
        }
    }

    override suspend fun upsertAsync(setting: Setting): Result<Setting> {
        return try {
            // Check if setting exists, then either update or create
            val existing = innerRepository.getByKeyAsync(setting.key)
            val upserted = if (existing != null) {
                innerRepository.updateAsync(setting)
            } else {
                innerRepository.createAsync(setting)
            }
            Result.success(upserted)
        } catch (ex: Exception) {
            Result.failure("Failed to upsert setting: ${ex.message}")
        }
    }

    override suspend fun getAllAsDictionaryAsync(): Result<Map<String, String>> {
        return try {
            val dictionary = innerRepository.getAllAsDictionaryAsync()
            Result.success(dictionary)
        } catch (ex: Exception) {
            Result.failure("Failed to retrieve settings as dictionary: ${ex.message}")
        }
    }
}