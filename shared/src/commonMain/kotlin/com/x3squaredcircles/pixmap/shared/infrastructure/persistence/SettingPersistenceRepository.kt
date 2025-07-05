// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/persistence/SettingPersistenceRepository.kt

package com.x3squaredcircles.pixmap.shared.infrastructure.persistence

import app.cash.sqldelight.db.SqlCursor
import com.x3squaredcircles.pixmap.shared.domain.entities.Setting
import com.x3squaredcircles.pixmap.shared.infrastructure.data.IDatabaseContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class SettingPersistenceRepository(
    private val context: IDatabaseContext
) : ISettingPersistenceRepository {

    override suspend fun getByIdAsync(id: Int): Setting? {
        return context.querySingleAsync(
            "SELECT * FROM SettingEntity WHERE id = ?",
            ::mapToSetting,
            id
        )
    }

    override suspend fun getByKeyAsync(key: String): Setting? {
        return context.querySingleAsync(
            "SELECT * FROM SettingEntity WHERE key = ?",
            ::mapToSetting,
            key
        )
    }

    override suspend fun getAllAsync(): List<Setting> {
        return context.queryAsync(
            "SELECT * FROM SettingEntity ORDER BY key",
            ::mapToSetting
        )
    }

    override suspend fun getByKeysAsync(keys: List<String>): List<Setting> {
        if (keys.isEmpty()) return emptyList()

        val placeholders = keys.joinToString(",") { "?" }
        return context.queryAsync(
            "SELECT * FROM SettingEntity WHERE key IN ($placeholders) ORDER BY key",
            ::mapToSetting,
            *keys.toTypedArray()
        )
    }

    override suspend fun addAsync(setting: Setting): Setting {
        val existsResult = context.queryScalarAsync<Long>(
            "SELECT COUNT(*) FROM SettingEntity WHERE key = ?",
            setting.key
        ) ?: 0

        if (existsResult > 0) {
            throw IllegalStateException("Setting with key '${setting.key}' already exists")
        }

        val timestamp = Clock.System.now().toEpochMilliseconds()

        val id = context.executeAsync(
            """
               INSERT INTO SettingEntity (key, value, description, timestamp)
               VALUES (?, ?, ?, ?)
           """,
            setting.key,
            setting.value,
            setting.description,
            timestamp
        ).toLong()

        val createdSetting = Setting.create(
            id = id.toInt(),
            key = setting.key,
            value = setting.value,
            description = setting.description
        )

        setIdUsingReflection(createdSetting, id.toInt())
        return createdSetting
    }

    override suspend fun updateAsync(setting: Setting) {
        val timestamp = Clock.System.now().toEpochMilliseconds()

        val rowsAffected = context.executeAsync(
            """
               UPDATE SettingEntity 
               SET value = ?, description = ?, timestamp = ?
               WHERE key = ?
           """,
            setting.value,
            setting.description,
            timestamp,
            setting.key
        )

        if (rowsAffected == 0) {
            throw IllegalStateException("Setting with key '${setting.key}' not found for update")
        }
    }

    override suspend fun deleteAsync(setting: Setting) {
        val rowsAffected = context.executeAsync(
            "DELETE FROM SettingEntity WHERE key = ?",
            setting.key
        )

        if (rowsAffected == 0) {
            throw IllegalStateException("Setting with key '${setting.key}' not found for deletion")
        }
    }

    override suspend fun upsertAsync(key: String, value: String, description: String?): Setting {
        return context.withTransactionAsync {
            val existingSetting = getByKeyAsync(key)
            val timestamp = Clock.System.now().toEpochMilliseconds()

            if (existingSetting != null) {
                context.executeAsync(
                    """
                       UPDATE SettingEntity 
                       SET value = ?, description = ?, timestamp = ?
                       WHERE key = ?
                   """,
                    value,
                    description ?: existingSetting.description,
                    timestamp,
                    key
                )

                val updatedSetting = Setting.create(
                    id = existingSetting.id,
                    key = key,
                    value = value,
                    description = description ?: existingSetting.description
                )

                setIdUsingReflection(updatedSetting, existingSetting.id)
                updatedSetting
            } else {
                val id = context.executeAsync(
                    """
                       INSERT INTO SettingEntity (key, value, description, timestamp)
                       VALUES (?, ?, ?, ?)
                   """,
                    key,
                    value,
                    description ?: "",
                    timestamp
                ).toLong()

                val newSetting = Setting.create(
                    id = id.toInt(),
                    key = key,
                    value = value,
                    description = description ?: ""
                )

                setIdUsingReflection(newSetting, id.toInt())
                newSetting
            }
        }
    }

    override suspend fun getAllAsDictionaryAsync(): Map<String, String> {
        val keyValuePairs = context.queryAsync(
            "SELECT key, value FROM SettingEntity ORDER BY key",
            ::mapToKeyValuePair
        )

        return keyValuePairs.toMap()
    }

    override suspend fun getByPrefixAsync(keyPrefix: String): List<Setting> {
        val searchPattern = "$keyPrefix%"
        return context.queryAsync(
            "SELECT * FROM SettingEntity WHERE key LIKE ? ORDER BY key",
            ::mapToSetting,
            searchPattern
        )
    }

    override suspend fun getRecentlyModifiedAsync(count: Int): List<Setting> {
        return context.queryAsync(
            "SELECT * FROM SettingEntity ORDER BY timestamp DESC LIMIT ?",
            ::mapToSetting,
            count
        )
    }

    override suspend fun existsAsync(key: String): Boolean {
        val result = context.queryScalarAsync<Long>(
            "SELECT EXISTS(SELECT 1 FROM SettingEntity WHERE key = ?)",
            key
        ) ?: 0

        return result > 0
    }

    override suspend fun createBulkAsync(settings: List<Setting>): List<Setting> {
        if (settings.isEmpty()) return emptyList()

        return context.withTransactionAsync {
            settings.map { setting ->
                addAsync(setting)
            }
        }
    }

    override suspend fun updateBulkAsync(settings: List<Setting>): Int {
        if (settings.isEmpty()) return 0

        return context.withTransactionAsync {
            var updated = 0
            settings.forEach { setting ->
                updateAsync(setting)
                updated++
            }
            updated
        }
    }

    override suspend fun deleteBulkAsync(keys: List<String>): Int {
        if (keys.isEmpty()) return 0

        return context.withTransactionAsync {
            val placeholders = keys.joinToString(",") { "?" }
            context.executeAsync(
                "DELETE FROM SettingEntity WHERE key IN ($placeholders)",
                *keys.toTypedArray()
            )
        }
    }

    override suspend fun upsertBulkAsync(keyValuePairs: Map<String, String>): Map<String, String> {
        if (keyValuePairs.isEmpty()) return emptyMap()

        return context.withTransactionAsync {
            val results = mutableMapOf<String, String>()
            keyValuePairs.forEach { (key, value) ->
                val setting = upsertAsync(key, value, null)
                results[key] = setting.value
            }
            results
        }
    }

    private fun setIdUsingReflection(entity: Any, id: Int) {
        try {
            val idField = entity::class.java.getDeclaredField("id")
            idField.isAccessible = true
            idField.setInt(entity, id)
        } catch (e: Exception) {
            // Log warning if needed
        }
    }

    private fun mapToSetting(cursor: Any): Setting {
        val sqlCursor = cursor as SqlCursor
        val setting = Setting.create(
            key = sqlCursor.getString(1) ?: "",
            value = sqlCursor.getString(2) ?: "",
            description = sqlCursor.getString(3) ?: ""
        )

        setIdUsingReflection(setting, sqlCursor.getLong(0)?.toInt() ?: 0)
        return setting
    }

    private fun mapToKeyValuePair(cursor: Any): Pair<String, String> {
        val sqlCursor = cursor as SqlCursor
        return Pair(
            sqlCursor.getString(0) ?: "",
            sqlCursor.getString(1) ?: ""
        )
    }
}