package com.x3squaredcircles.pixmap.shared.domain.repositories

import com.x3squaredcircles.pixmap.shared.domain.entities.Setting

/**
 * Repository interface for setting operations
 */
interface ISettingRepository {
    suspend fun getByKey(key: String): Setting?
    suspend fun getAll(): List<Setting>
    suspend fun save(setting: Setting): Setting
    suspend fun delete(key: String)
    suspend fun exists(key: String): Boolean
}