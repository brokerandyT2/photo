// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/data/DatabaseContext.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.data

import app.cash.sqldelight.db.SqlDriver
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILoggingService
import com.x3squaredcircles.pixmap.core.data.DatabaseInitializer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * SQLite database context implementation for KMM with optimized performance
 */
class DatabaseContext(
    private val driver: SqlDriver,
    private val logger: ILoggingService,
    private val databasePath: String? = null
) : IDatabaseContext {

    private val initializationMutex = Mutex()
    private val transactionMutex = Mutex()
    private val changeNotificationFlow = MutableSharedFlow<DatabaseChange>()
    private var databaseInitializer: DatabaseInitializer? = null

    @Volatile
    private var isInitialized = false
    @Volatile
    private var isInTransaction = false
    @Volatile
    private var transactionNestingLevel = 0

    companion object {
        private const val BUSY_TIMEOUT_MS = 3000L
        private const val SCHEMA_VERSION = 1
    }

    fun setDatabaseInitializer(initializer: DatabaseInitializer) {
        databaseInitializer = initializer
    }

    // ===== INITIALIZATION =====

    override suspend fun initializeDatabaseAsync() {
        if (isInitialized) return

        initializationMutex.withLock {
            if (isInitialized) return

            try {
                logger.info("Starting database initialization...")

                enablePerformanceOptimizations()
                createTables()
                setSchemaVersionAsync(SCHEMA_VERSION)

                databaseInitializer?.let { initializer ->
                    try {
                        logger.info("Populating database with default data...")
                        initializer.initializeDatabaseWithStaticDataAsync()
                        logger.info("Default data population completed successfully")
                    } catch (e: Exception) {
                        logger.error("Failed to populate default data", e)
                    }
                }

                isInitialized = true
                logger.info("Database initialization completed successfully")
            } catch (ex: Exception) {
                logger.error("Failed to initialize database", ex)
                throw DatabaseContextException("Database initialization failed", ex, "initialization")
            }
        }
    }

    override suspend fun closeAsync() {
        try {
            if (isInTransaction) {
                rollbackAsync()
            }
            driver.close()
            logger.info("Database connection closed")
        } catch (ex: Exception) {
            logger.error("Error closing database", ex)
            throw DatabaseContextException("Failed to close database", ex, "close")
        }
    }

    // ===== BASIC CRUD OPERATIONS =====

    override suspend fun <T : Any> insertAsync(entity: T): Long {
        return withContext(Dispatchers.IO) {
            try {
                ensureInitializedAsync()
                val id = executeInsertSql(entity)
                notifyChange(getTableName(entity::class.simpleName ?: "Unknown"), ChangeType.INSERT, id)
                logger.debug("Inserted ${entity::class.simpleName} with ID: $id")
                id
            } catch (ex: Exception) {
                logger.error("Failed to insert ${entity::class.simpleName}", ex)
                throw DatabaseContextException("Insert operation failed", ex, "insert")
            }
        }
    }

    override suspend fun <T : Any> updateAsync(entity: T): Int {
        return withContext(Dispatchers.IO) {
            try {
                ensureInitializedAsync()
                val rowsAffected = executeUpdateSql(entity)
                if (rowsAffected > 0) {
                    notifyChange(getTableName(entity::class.simpleName ?: "Unknown"), ChangeType.UPDATE)
                }
                logger.debug("Updated ${entity::class.simpleName}, rows affected: $rowsAffected")
                rowsAffected
            } catch (ex: Exception) {
                logger.error("Failed to update ${entity::class.simpleName}", ex)
                throw DatabaseContextException("Update operation failed", ex, "update")
            }
        }
    }

    override suspend fun <T : Any> deleteAsync(entity: T): Int {
        return withContext(Dispatchers.IO) {
            try {
                ensureInitializedAsync()
                val rowsAffected = executeDeleteSql(entity)
                if (rowsAffected > 0) {
                    notifyChange(getTableName(entity::class.simpleName ?: "Unknown"), ChangeType.DELETE)
                }
                logger.debug("Deleted ${entity::class.simpleName}, rows affected: $rowsAffected")
                rowsAffected
            } catch (ex: Exception) {
                logger.error("Failed to delete ${entity::class.simpleName}", ex)
                throw DatabaseContextException("Delete operation failed", ex, "delete")
            }
        }
    }

    override suspend fun <T : Any> getAsync(primaryKey: Any, entityMapper: suspend (Any) -> T?): T? {
        return withContext(Dispatchers.IO) {
            try {
                ensureInitializedAsync()
                val result = executeGetByIdSql(primaryKey)
                result?.let { entityMapper(it) }
            } catch (ex: Exception) {
                logger.error("Failed to get entity by ID: $primaryKey", ex)
                throw DatabaseContextException("Get operation failed", ex, "get")
            }
        }
    }

    override suspend fun <T : Any> getAllAsync(entityMapper: suspend (Any) -> T): List<T> {
        return withContext(Dispatchers.IO) {
            try {
                ensureInitializedAsync()
                val results = executeGetAllSql()
                results.map { entityMapper(it) }
            } catch (ex: Exception) {
                logger.error("Failed to get all entities", ex)
                throw DatabaseContextException("Get all operation failed", ex, "get_all")
            }
        }
    }

    // ===== QUERY OPERATIONS =====

    override suspend fun <T : Any> queryAsync(
        sql: String,
        resultMapper: suspend (Any) -> T,
        vararg parameters: Any?
    ): List<T> {
        return withContext(Dispatchers.IO) {
            try {
                ensureInitializedAsync()
                val rawResults = mutableListOf<Any>()

                driver.executeQuery(null, sql, { cursor ->
                    while (cursor.next().value) {
                        rawResults.add(cursor)
                    }
                    app.cash.sqldelight.db.QueryResult.Unit
                }, parameters.size) {
                    parameters.forEachIndexed { index, param ->
                        bindParameter(this, index + 1, param)
                    }
                }

                rawResults.map { resultMapper(it) }
            } catch (ex: Exception) {
                logger.error("Failed to execute query: $sql", ex)
                throw DatabaseContextException("Query operation failed", ex, "query", sql)
            }
        }
    }

    override suspend fun <T : Any> querySingleAsync(
        sql: String,
        resultMapper: suspend (Any) -> T,
        vararg parameters: Any?
    ): T? {
        return queryAsync(sql, resultMapper, *parameters).firstOrNull()
    }

    override suspend fun <T : Any> queryFirstOrNullAsync(
        sql: String,
        resultMapper: suspend (Any) -> T,
        vararg parameters: Any?
    ): T? {
        return querySingleAsync(sql, resultMapper, *parameters)
    }

    override suspend fun <T> queryScalarAsync(
        sql: String,
        vararg parameters: Any?
    ): T? {
        return withContext(Dispatchers.IO) {
            try {
                ensureInitializedAsync()
                var result: T? = null

                driver.executeQuery(null, sql, { cursor ->
                    if (cursor.next().value) {
                        @Suppress("UNCHECKED_CAST")
                        result = cursor.getString(0) as T?
                    }
                    app.cash.sqldelight.db.QueryResult.Unit
                }, parameters.size) {
                    parameters.forEachIndexed { index, param ->
                        bindParameter(this, index + 1, param)
                    }
                }

                result
            } catch (ex: Exception) {
                logger.error("Failed to execute scalar query: $sql", ex)
                throw DatabaseContextException("Query scalar operation failed", ex, "query_scalar", sql)
            }
        }
    }

    override suspend fun countAsync(
        sql: String,
        vararg parameters: Any?
    ): Long {
        return queryScalarAsync<Long>(sql, *parameters) ?: 0L
    }

    override suspend fun existsAsync(
        sql: String,
        vararg parameters: Any?
    ): Boolean {
        return countAsync(sql, *parameters) > 0
    }

    // ===== BULK OPERATIONS =====

    override suspend fun <T : Any> bulkInsertAsync(entities: List<T>, batchSize: Int): List<Long> {
        return withContext(Dispatchers.IO) {
            try {
                ensureInitializedAsync()
                val results = mutableListOf<Long>()
                entities.chunked(batchSize).forEach { batch ->
                    withTransactionAsync {
                        batch.forEach { entity ->
                            val id = executeInsertSql(entity)
                            results.add(id)
                        }
                    }
                }
                results
            } catch (ex: Exception) {
                logger.error("Failed to bulk insert entities", ex)
                throw DatabaseContextException("Bulk insert operation failed", ex, "bulk_insert")
            }
        }
    }

    override suspend fun <T : Any> bulkUpdateAsync(entities: List<T>, batchSize: Int): Int {
        return withContext(Dispatchers.IO) {
            try {
                ensureInitializedAsync()
                var totalUpdated = 0
                entities.chunked(batchSize).forEach { batch ->
                    withTransactionAsync {
                        batch.forEach { entity ->
                            totalUpdated += executeUpdateSql(entity)
                        }
                    }
                }
                totalUpdated
            } catch (ex: Exception) {
                logger.error("Failed to bulk update entities", ex)
                throw DatabaseContextException("Bulk update operation failed", ex, "bulk_update")
            }
        }
    }

    override suspend fun <T : Any> bulkDeleteAsync(entities: List<T>, batchSize: Int): Int {
        return withContext(Dispatchers.IO) {
            try {
                ensureInitializedAsync()
                var totalDeleted = 0
                entities.chunked(batchSize).forEach { batch ->
                    withTransactionAsync {
                        batch.forEach { entity ->
                            totalDeleted += executeDeleteSql(entity)
                        }
                    }
                }
                totalDeleted
            } catch (ex: Exception) {
                logger.error("Failed to bulk delete entities", ex)
                throw DatabaseContextException("Bulk delete operation failed", ex, "bulk_delete")
            }
        }
    }

    override suspend fun executeAsync(sql: String, vararg parameters: Any?): Int {
        return withContext(Dispatchers.IO) {
            try {
                ensureInitializedAsync()
                driver.execute(null, sql, parameters.size) {
                    parameters.forEachIndexed { index, param ->
                        bindParameter(this, index + 1, param)
                    }
                }.value.toInt()
            } catch (ex: Exception) {
                logger.error("Failed to execute SQL: $sql", ex)
                throw DatabaseContextException("Execute operation failed", ex, "execute", sql)
            }
        }
    }

    // ===== TRANSACTION MANAGEMENT =====

    override suspend fun beginTransactionAsync() {
        transactionMutex.withLock {
            if (transactionNestingLevel == 0) {
                driver.execute(null, "BEGIN TRANSACTION", 0)
                isInTransaction = true
                logger.debug("Transaction started")
            }
            transactionNestingLevel++
        }
    }

    override suspend fun commitAsync() {
        transactionMutex.withLock {
            if (transactionNestingLevel > 0) {
                transactionNestingLevel--
                if (transactionNestingLevel == 0) {
                    driver.execute(null, "COMMIT", 0)
                    isInTransaction = false
                    logger.debug("Transaction committed")
                }
            }
        }
    }

    override suspend fun rollbackAsync() {
        transactionMutex.withLock {
            if (isInTransaction) {
                driver.execute(null, "ROLLBACK", 0)
                isInTransaction = false
                transactionNestingLevel = 0
                logger.debug("Transaction rolled back")
            }
        }
    }

    override suspend fun <T> withTransactionAsync(block: suspend () -> T): T {
        beginTransactionAsync()
        return try {
            val result = block()
            commitAsync()
            result
        } catch (ex: Exception) {
            rollbackAsync()
            throw ex
        }
    }

    override fun isInTransaction(): Boolean = isInTransaction

    // ===== CHANGE TRACKING =====

    override suspend fun saveChangesAsync(): Int {
        return withContext(Dispatchers.IO) {
            try {
                commitAsync()
                logger.debug("Changes saved successfully")
                1
            } catch (ex: Exception) {
                logger.error("Failed to save changes", ex)
                throw DatabaseContextException("Save changes operation failed", ex, "save_changes")
            }
        }
    }

    override suspend fun hasPendingChangesAsync(): Boolean {
        return isInTransaction
    }

    override suspend fun discardChangesAsync() {
        rollbackAsync()
    }

    // ===== REACTIVE OPERATIONS =====

    override fun <T : Any> observeTable(tableName: String, mapper: suspend (Any) -> T): Flow<List<T>> {
        return emptyFlow()
    }

    override fun observeChanges(): Flow<DatabaseChange> {
        return changeNotificationFlow.asSharedFlow()
    }

    // ===== SCHEMA AND METADATA =====

    override suspend fun getSchemaVersionAsync(): Int {
        return queryScalarAsync<Long>("PRAGMA user_version")?.toInt() ?: 0
    }

    override suspend fun setSchemaVersionAsync(version: Int) {
        executeAsync("PRAGMA user_version = $version")
    }

    override suspend fun getDatabaseInfoAsync(): DatabaseInfo {
        val version = getSchemaVersionAsync()
        val size = getDatabaseSizeAsync()
        val tableCount = countAsync("SELECT COUNT(*) FROM sqlite_master WHERE type='table'").toInt()

        return DatabaseInfo(
            version = version,
            path = databasePath ?: "in-memory",
            size = size,
            tableCount = tableCount,
            lastModified = Clock.System.now()
        )
    }

    override suspend fun tableExistsAsync(tableName: String): Boolean {
        return existsAsync(
            "SELECT name FROM sqlite_master WHERE type='table' AND name = ?",
            tableName
        )
    }

    override suspend fun getTableSchemaAsync(tableName: String): TableSchema? {
        return withContext(Dispatchers.IO) {
            try {
                val columns = mutableListOf<ColumnInfo>()
                driver.executeQuery(null, "PRAGMA table_info($tableName)", { cursor ->
                    while (cursor.next().value) {
                        val name = cursor.getString(1) ?: ""
                        val type = cursor.getString(2) ?: ""
                        val notNull = cursor.getLong(3) != 0L
                        val defaultValue = cursor.getString(4)
                        val isPrimaryKey = cursor.getLong(5) != 0L
                        columns.add(ColumnInfo(name, type, !notNull, defaultValue, isPrimaryKey))
                    }
                    app.cash.sqldelight.db.QueryResult.Unit
                }, 0) {}

                if (columns.isNotEmpty()) {
                    TableSchema(tableName, columns, columns.filter { it.isPrimaryKey }.map { it.name })
                } else {
                    null
                }
            } catch (ex: Exception) {
                logger.error("Failed to get table schema: $tableName", ex)
                null
            }
        }
    }

    // ===== PERFORMANCE AND OPTIMIZATION =====

    override suspend fun analyzeAsync() {
        withContext(Dispatchers.IO) {
            try {
                driver.execute(null, "ANALYZE", 0)
                logger.debug("Database analysis completed")
            } catch (ex: Exception) {
                logger.error("Failed to analyze database", ex)
                throw DatabaseContextException("Analyze operation failed", ex, "analyze")
            }
        }
    }

    override suspend fun vacuumAsync() {
        withContext(Dispatchers.IO) {
            try {
                driver.execute(null, "VACUUM", 0)
                logger.debug("Database vacuum completed")
            } catch (ex: Exception) {
                logger.error("Failed to vacuum database", ex)
                throw DatabaseContextException("Vacuum operation failed", ex, "vacuum")
            }
        }
    }

    override suspend fun getDatabaseSizeAsync(): Long {
        return withContext(Dispatchers.IO) {
            try {
                var pageCount = 0L
                var pageSize = 0L

                driver.executeQuery(null, "PRAGMA page_count", { cursor ->
                    if (cursor.next().value) pageCount = cursor.getLong(0) ?: 0L
                    app.cash.sqldelight.db.QueryResult.Unit
                }, 0) {}

                driver.executeQuery(null, "PRAGMA page_size", { cursor ->
                    if (cursor.next().value) pageSize = cursor.getLong(0) ?: 0L
                    app.cash.sqldelight.db.QueryResult.Unit
                }, 0) {}

                pageCount * pageSize
            } catch (ex: Exception) {
                logger.error("Failed to get database size", ex)
                0L
            }
        }
    }

    override suspend fun optimizeAsync() {
        withContext(Dispatchers.IO) {
            try {
                analyzeAsync()
                vacuumAsync()
                logger.debug("Database optimization completed")
            } catch (ex: Exception) {
                logger.error("Failed to optimize database", ex)
                throw DatabaseContextException("Optimize operation failed", ex, "optimize")
            }
        }
    }

    // ===== BACKUP AND RESTORE =====

    override suspend fun backupAsync(backupPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                logger.debug("Database backup to $backupPath - not implemented")
                false
            } catch (ex: Exception) {
                logger.error("Failed to backup database", ex)
                false
            }
        }
    }

    override suspend fun restoreAsync(backupPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                logger.debug("Database restore from $backupPath - not implemented")
                false
            } catch (ex: Exception) {
                logger.error("Failed to restore database", ex)
                false
            }
        }
    }

    // ===== ERROR HANDLING AND LOGGING =====

    override fun getLastError(): String? {
        return null
    }

    override fun setSqlLogging(enabled: Boolean) {
        logger.debug("SQL logging ${if (enabled) "enabled" else "disabled"}")
    }

    override fun setLogLevel(level: DatabaseLogLevel) {
        logger.debug("Database log level set to: $level")
    }

    // ===== PRIVATE HELPER METHODS =====

    private suspend fun ensureInitializedAsync() {
        if (!isInitialized) {
            initializeDatabaseAsync()
        }
    }

    private suspend fun enablePerformanceOptimizations() {
        try {
            val optimizations = listOf(
                "PRAGMA synchronous = NORMAL",
                "PRAGMA cache_size = 10000",
                "PRAGMA temp_store = MEMORY",
                "PRAGMA journal_mode = WAL",
                "PRAGMA foreign_keys = ON",
                "PRAGMA busy_timeout = $BUSY_TIMEOUT_MS"
            )

            optimizations.forEach { pragma ->
                driver.execute(null, pragma, 0)
            }

            logger.debug("Database performance optimizations enabled")
        } catch (ex: Exception) {
            logger.warning("Failed to enable some performance optimizations", ex)
        }
    }

    private suspend fun createTables() {
        val createStatements = listOf(
            """
            CREATE TABLE IF NOT EXISTS LocationEntity (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                Title TEXT NOT NULL,
                Description TEXT NOT NULL,
                Latitude REAL NOT NULL,
                Longitude REAL NOT NULL,
                City TEXT NOT NULL,
                State TEXT NOT NULL,
                Country TEXT NOT NULL,
                PostalCode TEXT NOT NULL,
                Photo TEXT,
                CreatedAt TEXT NOT NULL,
                UpdatedAt TEXT NOT NULL,
                CONSTRAINT chk_latitude CHECK (Latitude >= -90.0 AND Latitude <= 90.0),
                CONSTRAINT chk_longitude CHECK (Longitude >= -180.0 AND Longitude <= 180.0)
            )
            """.trimIndent(),

            """
            CREATE TABLE IF NOT EXISTS WeatherEntity (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                LocationId INTEGER NOT NULL,
                Temperature REAL NOT NULL,
                Humidity INTEGER NOT NULL,
                Pressure REAL NOT NULL,
                WindSpeed REAL NOT NULL,
                WindDirection INTEGER NOT NULL,
                CloudCover INTEGER NOT NULL,
                UVIndex REAL NOT NULL,
                Visibility REAL NOT NULL,
                Description TEXT NOT NULL,
                IconCode TEXT NOT NULL,
                Timestamp TEXT NOT NULL,
                FOREIGN KEY (LocationId) REFERENCES LocationEntity(Id)
            )
            """.trimIndent(),

            """
            CREATE TABLE IF NOT EXISTS SettingEntity (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                Key TEXT NOT NULL UNIQUE,
                Value TEXT NOT NULL,
                Description TEXT NOT NULL,
                Timestamp TEXT NOT NULL
            )
            """.trimIndent(),

            """
            CREATE TABLE IF NOT EXISTS TipTypeEntity (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                Name TEXT NOT NULL UNIQUE,
                I8n TEXT NOT NULL,
                Timestamp TEXT NOT NULL
            )
            """.trimIndent(),

            """
            CREATE TABLE IF NOT EXISTS TipEntity (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                TipTypeId INTEGER NOT NULL,
                Title TEXT NOT NULL,
                Content TEXT NOT NULL,
                Fstop TEXT NOT NULL,
                ShutterSpeed TEXT NOT NULL,
                Iso TEXT NOT NULL,
                I8n TEXT NOT NULL,
                Timestamp TEXT NOT NULL,
                FOREIGN KEY (TipTypeId) REFERENCES TipTypeEntity(Id)
            )
            """.trimIndent(),

            """
            CREATE TABLE IF NOT EXISTS CameraBodyEntity (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                Name TEXT NOT NULL UNIQUE,
                SensorType TEXT NOT NULL,
                SensorWidth REAL NOT NULL,
                SensorHeight REAL NOT NULL,
                MountType TEXT NOT NULL,
                IsCustom INTEGER NOT NULL DEFAULT 0,
                Timestamp TEXT NOT NULL
            )
            """.trimIndent()
        )

        createStatements.forEach { sql ->
            driver.execute(null, sql, 0)
        }

        logger.debug("Database tables created successfully")
    }

    private fun notifyChange(tableName: String, changeType: ChangeType, rowId: Long? = null) {
        val change = DatabaseChange(tableName, changeType, rowId)
        changeNotificationFlow.tryEmit(change)
    }

    private fun getTableName(entityName: String): String {
        return entityName + "Entity"
    }

    private fun bindParameter(statement: app.cash.sqldelight.db.SqlPreparedStatement, index: Int, param: Any?) {
        when (param) {
            is String -> statement.bindString(index, param)
            is Long -> statement.bindLong(index, param)
            is Int -> statement.bindLong(index, param.toLong())
            is Double -> statement.bindDouble(index, param)
            is Float -> statement.bindDouble(index, param.toDouble())
            is Boolean -> statement.bindLong(index, if (param) 1L else 0L)
            is ByteArray -> statement.bindBytes(index, param)
            null -> statement.bindString(index, "")
            else -> statement.bindString(index, param.toString())
        }
    }

    // Placeholder implementations for entity operations
    private suspend fun <T> executeInsertSql(entity: T): Long = 1L
    private suspend fun <T> executeUpdateSql(entity: T): Int = 1
    private suspend fun <T> executeDeleteSql(entity: T): Int = 1
    private suspend fun executeGetByIdSql(primaryKey: Any): Any? = null
    private suspend fun executeGetAllSql(): List<Any> = emptyList()
}