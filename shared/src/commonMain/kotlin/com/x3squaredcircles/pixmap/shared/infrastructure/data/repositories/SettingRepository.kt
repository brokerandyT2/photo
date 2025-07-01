// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/data/repositories/SettingRepository.kt

import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.domain.entities.Setting
import com.x3squaredcircles.pixmap.shared.infrastructure.data.IDatabaseContext
import com.x3squaredcircles.pixmap.shared.infrastructure.data.entities.SettingEntity
import com.x3squaredcircles.pixmap.shared.infrastructure.services.IInfrastructureExceptionMappingService
import kotlinx.coroutines.logging.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes

class SettingRepository(
    private val context: IDatabaseContext,
    private val logger: Logger,
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
            val entities = context.queryAsync(
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
            val entities = context.queryAsync(
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
            val existsResult = context.executeScalarAsync<Long>(
                queryCache["CheckSettingExists"]!!,
                setting.key
            ) ?: 0

            if (existsResult > 0) {
                throw IllegalArgumentException("Setting with key '${setting.key}' already exists")
            }

            val entity = mapDomainToEntity(setting)
            val id = context.insertAsync(entity) { settingEntity ->
                insertSetting(settingEntity)
            }

            // Update cache
            val createdSetting = setting.copy(id = id, timestamp = entity.timestamp)
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
            val rowsAffected = context.updateAsync(entity) { settingEntity ->
                updateSetting(settingEntity)
            }

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

            if (rowsAffected > 0) {
                // Remove from cache
                cacheMutex.withLock {
                    settingsCache.remove(key)
                }
                logger.info("Deleted setting with key $key")
            }

            rowsAffected > 0
        } catch (ex: Exception) {
            logger.error("Failed to delete setting with key '$key'", ex)
            throw exceptionMapper.mapToSettingDomainException(ex, "Delete")
        }
    }

    suspend fun upsertAsync(setting: Setting): Setting {
        return try {
            context.executeInTransactionAsync {
                val existingEntity = context.querySingleAsync(
                    queryCache["GetSettingByKey"]!!,
                    ::mapCursorToSettingEntity,
                    setting.key
                )

                if (existingEntity != null) {
                    // Update existing
                    val updatedEntity = existingEntity.copy(
                        value = setting.value,
                        description = setting.description,
                        timestamp = Clock.System.now()
                    )
                    updateSetting(updatedEntity)

                    val updatedSetting = mapEntityToDomain(updatedEntity)

                    // Update cache
                    cacheMutex.withLock {
                        settingsCache[setting.key] = CachedSetting(updatedSetting, Clock.System.now().plus(cacheExpiration))
                    }

                    logger.info("Updated setting with key ${setting.key} via upsert")
                    updatedSetting
                } else {
                    // Create new
                    val entity = mapDomainToEntity(setting)
                    val id = insertSetting(entity)

                    val createdSetting = setting.copy(id = id, timestamp = entity.timestamp)

                    // Update cache
                    cacheMutex.withLock {
                        settingsCache[setting.key] = CachedSetting(createdSetting, Clock.System.now().plus(cacheExpiration))
                    }

                    logger.info("Created setting with key ${setting.key} via upsert")
                    createdSetting
                }
            }
        } catch (ex: Exception) {
            logger.error("Failed to upsert setting with key '${setting.key}'", ex)
            throw exceptionMapper.mapToSettingDomainException(ex, "Upsert")
        }
    }

    suspend fun getAllAsDictionaryAsync(): Map<String, String> {
        return try {
            val entities = context.queryAsync(
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

                val entities = context.queryAsync(
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

    suspend fun addAsync(setting: Setting): Setting {
        return try {
            // Check if key already exists
            val existsResult = context.executeScalarAsync<Long>(
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
            val entities = context.queryAsync(
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
            val entities = context.queryAsync(
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

            context.executeInTransactionAsync {
                val result = mutableMapOf<String, String>()

                // Get existing settings
                val keys = keyValuePairs.keys.toList()
                val placeholders = keys.joinToString(",") { "?" }
                val sql = "SELECT * FROM SettingEntity WHERE Key IN ($placeholders)"

                val existingEntities = context.queryAsync(
                    sql,
                    ::mapCursorToSettingEntity,
                    *keys.toTypedArray()
                )
                val existingByKey = existingEntities.associateBy { it.key }

                val toUpdate = mutableListOf<SettingEntity>()
                val toInsert = mutableListOf<SettingEntity>()

                for ((key, value) in keyValuePairs) {
                    val existing = existingByKey[key]
                    if (existing != null) {
                        // Update existing
                        val updated = existing.copy(
                            value = value,
                            timestamp = Clock.System.now()
                        )
                        toUpdate.add(updated)
                    } else {
                        // Insert new
                        val newEntity = SettingEntity(
                            id = 0,
                            key = key,
                            value = value,
                            description = "",
                            timestamp = Clock.System.now()
                        )
                        toInsert.add(newEntity)
                    }
                }

                // Perform bulk operations
                toUpdate.forEach { updateSetting(it) }
                toInsert.forEach { insertSetting(it) }

                // Clear cache for affected keys (simpler than selective updates)
                cacheMutex.withLock {
                    keys.forEach { key -> settingsCache.remove(key) }
                }

                result.putAll(keyValuePairs)
                logger.info("Bulk upserted ${keyValuePairs.size} settings")
                result
            }
        } catch (ex: Exception) {
            logger.error("Failed to bulk upsert settings", ex)
            throw exceptionMapper.mapToSettingDomainException(ex, "UpsertBulk")
        }
    }

    // Helper methods for database operations
    private suspend fun insertSetting(entity: SettingEntity): Long {
        return context.executeAsync(
            """INSERT INTO SettingEntity (Key, Value, Description, Timestamp)
               VALUES (?, ?, ?, ?)""",
            entity.key,
            entity.value,
            entity.description ?: "",
            entity.timestamp.toString()
        ).toLong()
    }

    private suspend fun updateSetting(entity: SettingEntity): Int {
        return context.executeAsync(
            """UPDATE SettingEntity 
               SET Value = ?, Description = ?, Timestamp = ?
               WHERE Key = ?""",
            entity.value,
            entity.description ?: "",
            entity.timestamp.toString(),
            entity.key
        )
    }

    // Mapping functions
    private fun mapEntityToDomain(entity: SettingEntity): Setting {
        return Setting(
            id = entity.id,
            key = entity.key,
            value = entity.value,
            description = entity.description,
            timestamp = entity.timestamp
        )
    }

    private fun mapDomainToEntity(setting: Setting): SettingEntity {
        return SettingEntity(
            id = setting.id,
            key = setting.key,
            value = setting.value,
            description = setting.description,
            timestamp = setting.timestamp
        )
    }

    private fun mapCursorToSettingEntity(cursor: SqlCursor): SettingEntity {
        return SettingEntity(
            id = cursor.getInt(0) ?: 0,
            key = cursor.getString(1) ?: "",
            value = cursor.getString(2) ?: "",
            description = cursor.getString(3),
            timestamp = Instant.parse(cursor.getString(4) ?: Clock.System.now().toString())
        )
    }

    private fun mapCursorToKeyValuePair(cursor: SqlCursor): Pair<String, String> {
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

// Setting entity data class
data class SettingEntity(
    val id: Int = 0,
    val key: String,
    val value: String,
    val description: String? = null,
    val timestamp: Instant = Clock.System.now()
)

// SqlCursor interface for database queries
interface SqlCursor {
    fun getString(index: Int): String?
    fun getLong(index: Int): Long?
    fun getDouble(index: Int): Double?
    fun getBoolean(index: Int): Boolean?
    fun getInt(index: Int): Int?
}