// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/persistence/SettingPersistenceRepository.kt

package com.x3squaredcircles.pixmap.shared.infrastructure.persistence

import com.x3squaredcircles.pixmap.shared.domain.entities.Setting
import com.x3squaredcircles.pixmap.shared.infrastructure.database.IDatabaseContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class SettingPersistenceRepository(
    private val context: IDatabaseContext
) : ISettingPersistenceRepository {

    override suspend fun getByIdAsync(id: Int): Setting? {
        return context.executeQuerySingle(
            sql = "SELECT * FROM SettingEntity WHERE id = ?",
            parameters = listOf(id)
        ) { cursor ->
            mapToSetting(cursor)
        }
    }

    override suspend fun getByKeyAsync(key: String): Setting? {
        return context.executeQuerySingle(
            sql = "SELECT * FROM SettingEntity WHERE key = ?",
            parameters = listOf(key)
        ) { cursor ->
            mapToSetting(cursor)
        }
    }

    override suspend fun getAllAsync(): List<Setting> {
        return context.executeQuery(
            sql = "SELECT * FROM SettingEntity ORDER BY key"
        ) { cursor ->
            mapToSetting(cursor)
        }
    }

    override suspend fun getByKeysAsync(keys: List<String>): List<Setting> {
        if (keys.isEmpty()) return emptyList()

        val placeholders = keys.joinToString(",") { "?" }
        return context.executeQuery(
            sql = "SELECT * FROM SettingEntity WHERE key IN ($placeholders) ORDER BY key",
            parameters = keys
        ) { cursor ->
            mapToSetting(cursor)
        }
    }

    override suspend fun addAsync(setting: Setting): Setting {
        val existsResult = context.executeScalar(
            sql = "SELECT COUNT(*) FROM SettingEntity WHERE key = ?",
            parameters = listOf(setting.key)
        ) { it.toString().toInt() }

        if (existsResult > 0) {
            throw IllegalStateException("Setting with key '${setting.key}' already exists")
        }

        val timestamp = Clock.System.now().toEpochMilliseconds()

        val id = context.executeNonQuery(
            sql = """
               INSERT INTO SettingEntity (key, value, description, timestamp)
               VALUES (?, ?, ?, ?)
           """,
            parameters = listOf(
                setting.key,
                setting.value,
                setting.description,
                timestamp
            )
        )

        return Setting.create(
            id = id.toInt(),
            key = setting.key,
            value = setting.value,
            description = setting.description,
            timestamp = Instant.fromEpochMilliseconds(timestamp)
        )
    }

    override suspend fun updateAsync(setting: Setting) {
        val timestamp = Clock.System.now().toEpochMilliseconds()

        val rowsAffected = context.executeNonQuery(
            sql = """
               UPDATE SettingEntity 
               SET value = ?, description = ?, timestamp = ?
               WHERE key = ?
           """,
            parameters = listOf(
                setting.value,
                setting.description,
                timestamp,
                setting.key
            )
        )

        if (rowsAffected == 0L) {
            throw IllegalStateException("Setting with key '${setting.key}' not found for update")
        }
    }

    override suspend fun deleteAsync(setting: Setting) {
        val rowsAffected = context.executeNonQuery(
            sql = "DELETE FROM SettingEntity WHERE key = ?",
            parameters = listOf(setting.key)
        )

        if (rowsAffected == 0L) {
            throw IllegalStateException("Setting with key '${setting.key}' not found for deletion")
        }
    }

    override suspend fun upsertAsync(key: String, value: String, description: String?): Setting {
        return context.executeInTransaction {
            val existingSetting = getByKeyAsync(key)
            val timestamp = Clock.System.now().toEpochMilliseconds()

            if (existingSetting != null) {
                context.executeNonQuery(
                    sql = """
                       UPDATE SettingEntity 
                       SET value = ?, description = ?, timestamp = ?
                       WHERE key = ?
                   """,
                    parameters = listOf(
                        value,
                        description ?: existingSetting.description,
                        timestamp,
                        key
                    )
                )

                Setting.create(
                    id = existingSetting.id,
                    key = key,
                    value = value,
                    description = description ?: existingSetting.description,
                    timestamp = Instant.fromEpochMilliseconds(timestamp)
                )
            } else {
                val id = context.executeNonQuery(
                    sql = """
                       INSERT INTO SettingEntity (key, value, description, timestamp)
                       VALUES (?, ?, ?, ?)
                   """,
                    parameters = listOf(
                        key,
                        value,
                        description ?: "",
                        timestamp
                    )
                )

                Setting.create(
                    id = id.toInt(),
                    key = key,
                    value = value,
                    description = description ?: "",
                    timestamp = Instant.fromEpochMilliseconds(timestamp)
                )
            }
        }
    }

    override suspend fun getAllAsDictionaryAsync(): Map<String, String> {
        val settings = context.executeQuery(
            sql = "SELECT key, value FROM SettingEntity ORDER BY key"
        ) { cursor ->
            Pair(
                cursor.getString(0) ?: "",
                cursor.getString(1) ?: ""
            )
        }

        return settings.toMap()
    }

    override suspend fun getByPrefixAsync(keyPrefix: String): List<Setting> {
        val searchPattern = "$keyPrefix%"
        return context.executeQuery(
            sql = "SELECT * FROM SettingEntity WHERE key LIKE ? ORDER BY key",
            parameters = listOf(searchPattern)
        ) { cursor ->
            mapToSetting(cursor)
        }
    }

    override suspend fun getRecentlyModifiedAsync(count: Int): List<Setting> {
        return context.executeQuery(
            sql = "SELECT * FROM SettingEntity ORDER BY timestamp DESC LIMIT ?",
            parameters = listOf(count)
        ) { cursor ->
            mapToSetting(cursor)
        }
    }

    override suspend fun existsAsync(key: String): Boolean {
        return context.executeScalar(
            sql = "SELECT EXISTS(SELECT 1 FROM SettingEntity WHERE key = ?)",
            parameters = listOf(key)
        ) { it.toString().toInt() > 0 }
    }

    override suspend fun createBulkAsync(settings: List<Setting>): List<Setting> {
        if (settings.isEmpty()) return settings

        val keys = settings.map { it.key }
        val duplicateKeys = keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        if (duplicateKeys.isNotEmpty()) {
            throw IllegalArgumentException("Duplicate keys in batch: ${duplicateKeys.joinToString(", ")}")
        }

        val placeholders = keys.joinToString(",") { "?" }
        val existingKeys = context.executeQuery(
            sql = "SELECT key FROM SettingEntity WHERE key IN ($placeholders)",
            parameters = keys
        ) { cursor ->
            cursor.getString(0) ?: ""
        }

        if (existingKeys.isNotEmpty()) {
            throw IllegalArgumentException("Settings with these keys already exist: ${existingKeys.joinToString(", ")}")
        }

        return context.executeInTransaction {
            val results = mutableListOf<Setting>()
            settings.chunked(100).forEach { batch ->
                batch.forEach { setting ->
                    val result = addAsync(setting)
                    results.add(result)
                }
            }
            results
        }
    }

    override suspend fun updateBulkAsync(settings: List<Setting>): Int {
        if (settings.isEmpty()) return 0

        return context.executeInTransaction {
            var updatedCount = 0
            settings.chunked(100).forEach { batch ->
                batch.forEach { setting ->
                    updateAsync(setting)
                    updatedCount++
                }
            }
            updatedCount
        }
    }

    override suspend fun deleteBulkAsync(keys: List<String>): Int {
        if (keys.isEmpty()) return 0

        return context.executeInTransaction {
            var totalDeleted = 0
            keys.chunked(100).forEach { batch ->
                val placeholders = batch.joinToString(",") { "?" }
                val deleted = context.executeNonQuery(
                    sql = "DELETE FROM SettingEntity WHERE key IN ($placeholders)",
                    parameters = batch
                )
                totalDeleted += deleted.toInt()
            }
            totalDeleted
        }
    }

    override suspend fun upsertBulkAsync(keyValuePairs: Map<String, String>): Map<String, String> {
        if (keyValuePairs.isEmpty()) return emptyMap()

        return context.executeInTransaction {
            val result = mutableMapOf<String, String>()

            val keys = keyValuePairs.keys.toList()
            val placeholders = keys.joinToString(",") { "?" }
            val existingSettings = context.executeQuery(
                sql = "SELECT key, value, description, id FROM SettingEntity WHERE key IN ($placeholders)",
                parameters = keys
            ) { cursor ->
                SettingDto(
                    id = cursor.getInt(3) ?: 0,
                    key = cursor.getString(0) ?: "",
                    value = cursor.getString(1) ?: "",
                    description = cursor.getString(2) ?: ""
                )
            }

            val existingByKey = existingSettings.associateBy { it.key }
            val timestamp = Clock.System.now().toEpochMilliseconds()

            keyValuePairs.forEach { (key, value) ->
                if (existingByKey.containsKey(key)) {
                    context.executeNonQuery(
                        sql = """
                           UPDATE SettingEntity 
                           SET value = ?, timestamp = ?
                           WHERE key = ?
                       """,
                        parameters = listOf(value, timestamp, key)
                    )
                } else {
                    context.executeNonQuery(
                        sql = """
                           INSERT INTO SettingEntity (key, value, description, timestamp)
                           VALUES (?, ?, ?, ?)
                       """,
                        parameters = listOf(key, value, "", timestamp)
                    )
                }
                result[key] = value
            }

            result
        }
    }

    private fun mapToSetting(cursor: com.x3squaredcircles.pixmap.shared.infrastructure.database.SqlCursor): Setting {
        return Setting.create(
            id = cursor.getInt(0) ?: 0,
            key = cursor.getString(1) ?: "",
            value = cursor.getString(2) ?: "",
            description = cursor.getString(3) ?: "",
            timestamp = Instant.fromEpochMilliseconds(cursor.getLong(4) ?: 0L)
        )
    }

    private data class SettingDto(
        val id: Int,
        val key: String,
        val value: String,
        val description: String
    )
}

fun Setting.toKeyValueDto() = mapOf(
    "key" to key,
    "value" to value
)

fun Setting.toSummaryDto() = mapOf(
    "id" to id,
    "key" to key,
    "value" to value,
    "timestamp" to timestamp.toEpochMilliseconds()
)

fun Setting.toDetailDto() = mapOf(
    "id" to id,
    "key" to key,
    "value" to value,
    "description" to description,
    "timestamp" to timestamp.toEpochMilliseconds()
)