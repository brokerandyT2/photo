//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/data/repositories/SettingRepository.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.data.repositories

import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.domain.entities.Setting
import com.x3squaredcircles.pixmap.shared.infrastructure.data.IDatabaseContext
import com.x3squaredcircles.pixmap.shared.infrastructure.data.entities.SettingEntity
import com.x3squaredcircles.pixmap.shared.infrastructure.services.IInfrastructureExceptionMappingService
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILoggingService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes

class SettingRepository(
    private val context: IDatabaseContext,
    private val logger: ILoggingService,
    private val exceptionMapper: IInfrastructureExceptionMappingService
) {

    // Cache for frequently accessed settings
    private val settingsCache = mutableMapOf<String, CachedSetting>()
    private val cacheMutex = Mutex()
    private val cacheExpiration = 15.minutes

    // Pre-compiled SQL queries for performance
    private val queryCache = mapOf(
        "GetSettingByKey" to "SELECT * FROM SettingEntity WHERE Key = ? LIMIT 1",
        "GetAllSettings" to "SELECT * FROM SettingEntity ORDER BY Key",
        "CheckSettingExists" to "SELECT EXISTS(SELECT 1 FROM SettingEntity WHERE Key = ?)",
        "GetAllSettingsKeyValue" to "SELECT Key, Value FROM SettingEntity ORDER BY Key",
        "GetSettingsByPrefix" to "SELECT * FROM SettingEntity WHERE Key LIKE ? ORDER BY Key",
        "GetRecentlyModifiedSettings" to "SELECT * FROM SettingEntity ORDER BY Timestamp DESC LIMIT ?"
    )

    suspend fun getByKeyAsync(key: String): Setting? {
        return try {
            // Check cache first for frequently accessed settings
            cacheMutex.withLock {
                val cachedSetting = settingsCache[key]
                if (cachedSetting != null && !cachedSetting.isExpired()) {
                    logger.debug("Retrieved setting $key from cache")
                    return cachedSetting.setting
                }
            }

            // Query database
            val entities = context.queryAsync<SettingEntity>(
                queryCache["GetSettingByKey"]!!,
                ::mapCursorToSettingEntity,
                key
            )
            val entity = entities.firstOrNull()

            if (entity == null) {
                // Cache null result to avoid repeated database hits
                cacheMutex.withLock {
                    settingsCache[key] = CachedSetting(null, Clock.System.now().plus(cacheExpiration))
                }
                return null
            }

            val setting = mapEntityToDomain(entity)

            // Cache the result
            cacheMutex.withLock {
                settingsCache[key] = CachedSetting(setting, Clock.System.now().plus(cacheExpiration))
            }

            setting
        } catch (ex: Exception) {
            logger.error("Failed to get setting by key '$key'", ex)
            throw exceptionMapper.mapToSettingDomainException(ex, "GetByKey")
        }
    }

    suspend fun getAllAsync(): List<Setting> {
        return try {
            val entities = context.queryAsync<SettingEntity>(
                queryCache["GetAllSettings"]!!,
                ::mapCursorToSettingEntity
            )
            entities.map { mapEntityToDomain(it) }
        } catch (ex: Exception) {
            logger.error("Failed to get all settings", ex)
            throw exceptionMapper.mapToSettingDomainException(ex, "GetAll")
        }
    }

    suspend fun createAsync(setting: Setting): Setting {
        return try {
            // Check if key already exists
            val existsResult = context.queryScalarAsync<Long>(
                queryCache["CheckSettingExists"]!!,
                setting.key
            ) ?: 0

            if (existsResult > 0) {
                throw IllegalArgumentException("Setting with key '${setting.key}' already exists")
            }

            val entity = mapDomainToEntity(setting)
            val id = context.insertAsync(entity)

            // Update cache
            val createdSetting = Setting.create(
                id = id.toInt(),
                key = setting.key,
                value = setting.value,
                description = setting.description
            ).copy(timestamp = Instant.fromEpochSeconds(entity.timestamp))
            cacheMutex.withLock {
                settingsCache[setting.key] = CachedSetting(createdSetting, Clock.System.now().plus(cacheExpiration))
            }

            logger.info("Created setting with key ${setting.key}")
            createdSetting
        } catch (ex: Exception) {
            logger.error("Failed to create setting with key '${setting.key}'", ex)
            throw exceptionMapper.mapToSettingDomainException(ex, "Create")
        }
    }

    suspend fun updateAsync(setting: Setting): Setting {
        return try {
            val entity = mapDomainToEntity(setting)
            val rowsAffected = context.executeAsync(
                """UPDATE SettingEntity 
                   SET Value = ?, Description = ?, Timestamp = ?
                   WHERE Key = ?""",
                entity.value,
                entity.description ?: "",
                entity.timestamp.toString(),
                entity.key
            )

            if (rowsAffected == 0) {
                throw IllegalArgumentException("Setting with key '${setting.key}' not found")
            }

            // Update cache
            cacheMutex.withLock {
                settingsCache[setting.key] = CachedSetting(setting, Clock.System.now().plus(cacheExpiration))
            }

            logger.info("Updated setting with key ${setting.key}")
            setting
        } catch (ex: Exception) {
            logger.error("Failed to update setting with key '${setting.key}'", ex)
            throw exceptionMapper.mapToSettingDomainException(ex, "Update")
        }
    }

    suspend fun deleteAsync(key: String): Boolean {
        return try {
            val rowsAffected = context.executeAsync(
                "DELETE FROM SettingEntity WHERE Key = ?",
                key
            )

            val deleted = rowsAffected > 0

            // Remove from cache
            if (deleted) {
                cacheMutex.withLock {
                    settingsCache.remove(key)
                }
            }

            logger.info("Setting deletion result for key '$key': $deleted")
            deleted
        } catch (ex: Exception) {
            logger.error("Failed to delete setting with key '$key'", ex)
            throw exceptionMapper.mapToSettingDomainException(ex, "Delete")
        }
    }

    suspend fun addAsync(setting: Setting): Setting {
        return try {
            // Check if key already exists
            val existsResult = context.queryScalarAsync<Long>(
                queryCache["CheckSettingExists"]!!,
                setting.key
            ) ?: 0

            if (existsResult > 0) {
                throw IllegalArgumentException("Setting with key '${setting.key}' already exists. Use updateAsync or upsertAsync instead.")
            }

            createAsync(setting)
        } catch (ex: Exception) {
            logger.error("Failed to add setting with key '${setting.key}'", ex)
            throw exceptionMapper.mapToSettingDomainException(ex, "Add")
        }
    }

    suspend fun getByPrefixAsync(prefix: String): List<Setting> {
        return try {
            val entities = context.queryAsync<SettingEntity>(
                queryCache["GetSettingsByPrefix"]!!,
                ::mapCursorToSettingEntity,
                "$prefix%"
            )
            entities.map { mapEntityToDomain(it) }
        } catch (ex: Exception) {
            logger.error("Failed to get settings by prefix '$prefix'", ex)
            throw exceptionMapper.mapToSettingDomainException(ex, "GetByPrefix")
        }
    }

    suspend fun getRecentlyModifiedAsync(limit: Int): List<Setting> {
        return try {
            val entities = context.queryAsync<SettingEntity>(
                queryCache["GetRecentlyModifiedSettings"]!!,
                ::mapCursorToSettingEntity,
                limit
            )
            entities.map { mapEntityToDomain(it) }
        } catch (ex: Exception) {
            logger.error("Failed to get recently modified settings", ex)
            throw exceptionMapper.mapToSettingDomainException(ex, "GetRecentlyModified")
        }
    }

    suspend fun deleteBulkAsync(keys: List<String>): Int {
        return try {
            if (keys.isEmpty()) return 0

            var totalDeleted = 0
            val batchSize = 100

            keys.chunked(batchSize).forEach { batch ->
                val placeholders = batch.joinToString(",") { "?" }
                val sql = "DELETE FROM SettingEntity WHERE Key IN ($placeholders)"

                val deleted = context.executeAsync(sql, *batch.toTypedArray())
                totalDeleted += deleted

                // Remove from cache
                cacheMutex.withLock {
                    batch.forEach { key -> settingsCache.remove(key) }
                }
            }

            logger.info("Bulk deleted $totalDeleted settings")
            totalDeleted
        } catch (ex: Exception) {
            logger.error("Failed to bulk delete settings", ex)
            throw exceptionMapper.mapToSettingDomainException(ex, "DeleteBulk")
        }
    }

    suspend fun upsertBulkAsync(keyValuePairs: Map<String, String>): Map<String, String> {
        return try {
            if (keyValuePairs.isEmpty()) return emptyMap()

            context.withTransactionAsync {
                val result = mutableMapOf<String, String>()

                // Get existing settings
                val keys = keyValuePairs.keys.toList()
                val placeholders = keys.joinToString(",") { "?" }
                val sql = "SELECT Key FROM SettingEntity WHERE Key IN ($placeholders)"

                val existingKeys = context.queryAsync<SettingEntity>(
                    sql,
                    ::mapCursorToSettingEntity,
                    *keys.toTypedArray()
                ).map { it.key }.toSet()

                // Update existing and insert new
                keyValuePairs.forEach { (key, value) ->
                    if (key in existingKeys) {
                        context.executeAsync(
                            "UPDATE SettingEntity SET Value = ?, Timestamp = ? WHERE Key = ?",
                            value,
                            Clock.System.now().epochSeconds.toString(),
                            key
                        )
                    } else {
                        context.executeAsync(
                            "INSERT INTO SettingEntity (Key, Value, Description, Timestamp) VALUES (?, ?, ?, ?)",
                            key,
                            value,
                            "",
                            Clock.System.now().epochSeconds.toString()
                        )
                    }
                    result[key] = value
                }

                // Clear cache for affected keys
                cacheMutex.withLock {
                    keys.forEach { key -> settingsCache.remove(key) }
                }

                logger.info("Upserted ${result.size} settings")
                result
            }
        } catch (ex: Exception) {
            logger.error("Failed to upsert settings in bulk", ex)
            throw exceptionMapper.mapToSettingDomainException(ex, "UpsertBulk")
        }
    }

    suspend fun getAllAsDictionaryAsync(): Map<String, String> {
        return try {
            val entities = context.queryAsync<Pair<String, String>>(
                queryCache["GetAllSettingsKeyValue"]!!,
                ::mapCursorToKeyValuePair
            )
            entities.toMap()
        } catch (ex: Exception) {
            logger.error("Failed to get all settings as dictionary", ex)
            throw exceptionMapper.mapToSettingDomainException(ex, "GetAllAsDictionary")
        }
    }

    suspend fun getByKeysAsync(keys: List<String>): List<Setting> {
        return try {
            if (keys.isEmpty()) return emptyList()

            val settings = mutableListOf<Setting>()

            // Check cache first
            val uncachedKeys = mutableListOf<String>()

            cacheMutex.withLock {
                for (key in keys) {
                    val cachedSetting = settingsCache[key]
                    if (cachedSetting != null && !cachedSetting.isExpired()) {
                        cachedSetting.setting?.let { settings.add(it) }
                    } else {
                        uncachedKeys.add(key)
                    }
                }
            }

            // Query uncached keys from database
            if (uncachedKeys.isNotEmpty()) {
                val placeholders = uncachedKeys.joinToString(",") { "?" }
                val sql = "SELECT * FROM SettingEntity WHERE Key IN ($placeholders) ORDER BY Key"

                val entities = context.queryAsync<SettingEntity>(
                    sql,
                    ::mapCursorToSettingEntity,
                    *uncachedKeys.toTypedArray()
                )
                val uncachedSettings = entities.map { mapEntityToDomain(it) }

                // Cache the results
                val foundKeys = uncachedSettings.map { it.key }.toSet()
                cacheMutex.withLock {
                    for (setting in uncachedSettings) {
                        settingsCache[setting.key] = CachedSetting(setting, Clock.System.now().plus(cacheExpiration))
                    }

                    // Cache null results for keys that weren't found
                    for (key in uncachedKeys.filter { it !in foundKeys }) {
                        settingsCache[key] = CachedSetting(null, Clock.System.now().plus(cacheExpiration))
                    }
                }

                settings.addAll(uncachedSettings)
            }

            settings
        } catch (ex: Exception) {
            logger.error("Failed to get settings by keys", ex)
            throw exceptionMapper.mapToSettingDomainException(ex, "GetByKeys")
        }
    }

    // Mapping functions
    private fun mapEntityToDomain(entity: SettingEntity): Setting {
        val setting = Setting.create(
            id = entity.id,
            key = entity.key,
            value = entity.value,
            description = entity.description ?: ""
        )
        // Set the timestamp from entity
        return setting.copy(timestamp = Instant.fromEpochSeconds(entity.timestamp))
    }

    private fun mapDomainToEntity(setting: Setting): SettingEntity {
        return SettingEntity(
            id = setting.id,
            key = setting.key,
            value = setting.value,
            description = setting.description,
            timestamp = setting.timestamp.toEpochMilliseconds()
        )
    }


    private fun mapCursorToSettingEntity(cursor: app.cash.sqldelight.db.SqlCursor): SettingEntity {
        return SettingEntity(
            id = cursor.getLong(0)?.toInt() ?: 0,
            key = cursor.getString(1) ?: "",
            value = cursor.getString(2) ?: "",
            description = cursor.getString(3)?:"",
            timestamp = Instant.parse(cursor.getString(4) ?: Clock.System.now().toString()).toEpochMilliseconds()
        )
    }

    private fun mapCursorToKeyValuePair(cursor: app.cash.sqldelight.db.SqlCursor): Pair<String, String> {
        return Pair(
            cursor.getString(0) ?: "",
            cursor.getString(1) ?: ""
        )
    }
}

// Cache helper class
private data class CachedSetting(
    val setting: Setting?,
    val expiresAt: Instant
) {
    fun isExpired(): Boolean = Clock.System.now() > expiresAt
}