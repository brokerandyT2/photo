// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/data/DatabaseContext.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlCursor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * SQLite database context implementation for KMM with optimized performance
 */
class DatabaseContext(
    private val driver: SqlDriver,
    private val logger: kotlinx.coroutines.logging.Logger,
    private val databasePath: String? = null
) : IDatabaseContext {

    private val initializationMutex = Mutex()
    private val transactionMutex = Mutex()
    private val preparedStatementCache = mutableMapOf<String, Any>()
    private val changeNotificationFlow = MutableSharedFlow<DatabaseChange>()

    @Volatile
    private var isInitialized = false
    @Volatile
    private var isInTransaction = false
    @Volatile
    private var transactionNestingLevel = 0

    companion object {
        private const val DATABASE_NAME = "locations.db"
        private const val BUSY_TIMEOUT_MS = 3000L
        private const val DEFAULT_BATCH_SIZE = 100
        private const val MAX_CACHED_STATEMENTS = 50
        private const val SCHEMA_VERSION = 1
    }

    // ===== INITIALIZATION =====

    override suspend fun initializeDatabaseAsync() {
        if (isInitialized) return

        initializationMutex.withLock {
            if (isInitialized) return

            try {
                logger.info("Starting database initialization...")

                // Enable performance optimizations
                enablePerformanceOptimizations()

                // Create tables
                createTables()

                // Set schema version
                setSchemaVersionAsync(SCHEMA_VERSION)

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
                // This would be implemented with actual SQL generation based on entity type
                val id = executeInsertSql(entity)
                notifyChange(getTableName<T>(), ChangeType.INSERT, id)
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
                    notifyChange(getTableName<T>(), ChangeType.UPDATE)
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
                    notifyChange(getTableName<T>(), ChangeType.DELETE)
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
                // This would execute a SELECT WHERE primary_key = ? query
                val rawResult = executeGetByIdSql<T>(primaryKey)
                rawResult?.let { entityMapper(it) }
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
                val rawResults = executeGetAllSql<T>()
                rawResults.map { entityMapper(it) }
            } catch (ex: Exception) {
                logger.error("Failed to get all entities", ex)
                throw DatabaseContextException("GetAll operation failed", ex, "getAll")
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
                val results = mutableListOf<T>()

                driver.executeQuery(null, sql, { cursor ->
                    while (cursor.next().value) {
                        results.add(runBlocking { resultMapper(cursor) })
                    }
                    app.cash.sqldelight.db.QueryResult.Unit
                }, parameters.size) {
                    parameters.forEachIndexed { index, param ->
                        when (param) {
                            is String -> it.bindString(index + 1, param)
                            is Long -> it.bindLong(index + 1, param)
                            is Double -> it.bindDouble(index + 1, param)
                            is ByteArray -> it.bindBytes(index + 1, param)
                            null -> it.bindNull(index + 1)
                            else -> it.bindString(index + 1, param.toString())
                        }
                    }
                }

                logger.debug("Query returned ${results.size} results")
                results
            } catch (ex: Exception) {
                logger.error("Query execution failed: $sql", ex)
                throw DatabaseContextException("Query failed", ex, "query", sql)
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

    override suspend fun <T> queryScalarAsync(sql: String, vararg parameters: Any?): T? {
        return withContext(Dispatchers.IO) {
            try {
                ensureInitializedAsync()
                var result: T? = null

                driver.executeQuery(null, sql, { cursor ->
                    if (cursor.next().value) {
                        @Suppress("UNCHECKED_CAST")
                        result = when (T::class) {
                            String::class -> cursor.getString(0) as T?
                            Long::class -> cursor.getLong(0) as T?
                            Double::class -> cursor.getDouble(0) as T?
                            Boolean::class -> (cursor.getLong(0) != 0L) as T?
                            else -> cursor.getString(0) as T?
                        }
                    }
                    app.cash.sqldelight.db.QueryResult.Unit
                }, parameters.size) {
                    parameters.forEachIndexed { index, param ->
                        bindParameter(it, index + 1, param)
                    }
                }

                result
            } catch (ex: Exception) {
                logger.error("Scalar query failed: $sql", ex)
                throw DatabaseContextException("Scalar query failed", ex, "scalar", sql)
            }
        }
    }

    override suspend fun countAsync(sql: String, vararg parameters: Any?): Long {
        return queryScalarAsync<Long>(sql, *parameters) ?: 0L
    }

    override suspend fun existsAsync(sql: String, vararg parameters: Any?): Boolean {
        return queryScalarAsync<Long>(sql, *parameters)?.let { it > 0 } ?: false
    }

    // ===== BULK OPERATIONS =====

    override suspend fun <T : Any> bulkInsertAsync(entities: List<T>, batchSize: Int): List<Long> {
        return withContext(Dispatchers.IO) {
            withTransactionAsync {
                val ids = mutableListOf<Long>()
                entities.chunked(batchSize).forEach { batch ->
                    batch.forEach { entity ->
                        ids.add(insertAsync(entity))
                    }
                }
                logger.info("Bulk inserted ${entities.size} entities in batches of $batchSize")
                ids
            }
        }
    }

    override suspend fun <T : Any> bulkUpdateAsync(entities: List<T>, batchSize: Int): Int {
        return withContext(Dispatchers.IO) {
            withTransactionAsync {
                var totalUpdated = 0
                entities.chunked(batchSize).forEach { batch ->
                    batch.forEach { entity ->
                        totalUpdated += updateAsync(entity)
                    }
                }
                logger.info("Bulk updated $totalUpdated entities in batches of $batchSize")
                totalUpdated
            }
        }
    }

    override suspend fun <T : Any> bulkDeleteAsync(entities: List<T>, batchSize: Int): Int {
        return withContext(Dispatchers.IO) {
            withTransactionAsync {
                var totalDeleted = 0
                entities.chunked(batchSize).forEach { batch ->
                    batch.forEach { entity ->
                        totalDeleted += deleteAsync(entity)
                    }
                }
                logger.info("Bulk deleted $totalDeleted entities in batches of $batchSize")
                totalDeleted
            }
        }
    }

    override suspend fun executeAsync(sql: String, vararg parameters: Any?): Int {
        return withContext(Dispatchers.IO) {
            try {
                ensureInitializedAsync()

                var rowsAffected = 0
                driver.execute(null, sql, parameters.size) {
                    parameters.forEachIndexed { index, param ->
                        bindParameter(it, index + 1, param)
                    }
                }.also {
                    rowsAffected = it.value.toInt()
                }

                logger.debug("Execute SQL affected $rowsAffected rows")
                rowsAffected
            } catch (ex: Exception) {
                logger.error("Execute failed: $sql", ex)
                throw DatabaseContextException("Execute failed", ex, "execute", sql)
            }
        }
    }

    // ===== TRANSACTION MANAGEMENT =====

    override suspend fun beginTransactionAsync() {
        transactionMutex.withLock {
            try {
                if (!isInTransaction) {
                    driver.execute(null, "BEGIN TRANSACTION", 0)
                    isInTransaction = true
                    transactionNestingLevel = 1
                    logger.debug("Started database transaction")
                } else {
                    transactionNestingLevel++
                    logger.debug("Incremented transaction nesting level to $transactionNestingLevel")
                }
            } catch (ex: Exception) {
                logger.error("Failed to begin transaction", ex)
                throw DatabaseContextException("Begin transaction failed", ex, "beginTransaction")
            }
        }
    }

    override suspend fun commitAsync() {
        transactionMutex.withLock {
            try {
                if (isInTransaction) {
                    transactionNestingLevel--

                    if (transactionNestingLevel <= 0) {
                        driver.execute(null, "COMMIT", 0)
                        isInTransaction = false
                        transactionNestingLevel = 0
                        logger.debug("Committed database transaction")
                    } else {
                        logger.debug("Decremented transaction nesting level to $transactionNestingLevel")
                    }
                } else {
                    logger.warning("Attempted to commit when no transaction is active")
                }
            } catch (ex: Exception) {
                logger.error("Failed to commit transaction", ex)
                isInTransaction = false
                transactionNestingLevel = 0
                throw DatabaseContextException("Commit transaction failed", ex, "commit")
            }
        }
    }

    override suspend fun rollbackAsync() {
        transactionMutex.withLock {
            try {
                if (isInTransaction) {
                    driver.execute(null, "ROLLBACK", 0)
                    isInTransaction = false
                    transactionNestingLevel = 0
                    logger.debug("Rolled back database transaction")
                } else {
                    logger.warning("Attempted to rollback when no transaction is active")
                }
            } catch (ex: Exception) {
                logger.error("Failed to rollback transaction", ex)
                isInTransaction = false
                transactionNestingLevel = 0
                throw DatabaseContextException("Rollback transaction failed", ex, "rollback")
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
            try {
                rollbackAsync()
            } catch (rollbackEx: Exception) {
                logger.error("Failed to rollback after error", rollbackEx)
                ex.addSuppressed(rollbackEx)
            }
            throw ex
        }
    }

    override fun isInTransaction(): Boolean = isInTransaction

    // ===== CHANGE TRACKING =====

    override suspend fun saveChangesAsync(): Int {
        // In SQLite, changes are immediately persisted, so this returns 0
        // In other contexts, this would flush pending changes
        return 0
    }

    override suspend fun hasPendingChangesAsync(): Boolean {
        // SQLite doesn't have pending changes like Entity Framework
        return false
    }

    override suspend fun discardChangesAsync() {
        // No-op for SQLite since changes are immediate
    }

    // ===== REACTIVE OPERATIONS =====

    override fun <T : Any> observeTable(tableName: String, mapper: suspend (Any) -> T): Flow<List<T>> {
        // Implementation would use SQLite triggers or polling
        // For now, return empty flow
        return kotlinx.coroutines.flow.emptyFlow()
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
        val tableCount = queryScalarAsync<Long>("SELECT COUNT(*) FROM sqlite_master WHERE type='table'")?.toInt() ?: 0

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
        // Implementation would query PRAGMA table_info
        return null // Simplified for this migration
    }

    // ===== PERFORMANCE AND OPTIMIZATION =====

    override suspend fun analyzeAsync() {
        executeAsync("ANALYZE")
        logger.info("Database analysis completed")
    }

    override suspend fun vacuumAsync() {
        executeAsync("VACUUM")
        logger.info("Database vacuum completed")
    }

    override suspend fun getDatabaseSizeAsync(): Long {
        return queryScalarAsync<Long>("PRAGMA page_count") ?: 0L
    }

    override suspend fun optimizeAsync() {
        analyzeAsync()
        executeAsync("PRAGMA optimize")
        logger.info("Database optimization completed")
    }

    // ===== BACKUP AND RESTORE =====

    override suspend fun backupAsync(backupPath: String): Boolean {
        return try {
            // Platform-specific backup implementation would go here
            logger.info("Database backup to $backupPath")
            true
        } catch (ex: Exception) {
            logger.error("Backup failed", ex)
            false
        }
    }

    override suspend fun restoreAsync(backupPath: String): Boolean {
        return try {
            // Platform-specific restore implementation would go here
            logger.info("Database restore from $backupPath")
            true
        } catch (ex: Exception) {
            logger.error("Restore failed", ex)
            false
        }
    }

    // ===== ERROR HANDLING AND LOGGING =====

    override fun getLastError(): String? {
        // Would return last SQLite error
        return null
    }

    override fun setSqlLogging(enabled: Boolean) {
        // Configure SQL logging
    }

    override fun setLogLevel(level: DatabaseLogLevel) {
        // Configure log level
    }

    // ===== PRIVATE HELPER METHODS =====

    private suspend fun ensureInitializedAsync() {
        if (!isInitialized) {
            initializeDatabaseAsync()
        }
    }

    private fun enablePerformanceOptimizations() {
        try {
            driver.execute(null, "PRAGMA journal_mode=WAL", 0)
            driver.execute(null, "PRAGMA synchronous=NORMAL", 0)
            driver.execute(null, "PRAGMA cache_size=10000", 0)
            driver.execute(null, "PRAGMA temp_store=MEMORY", 0)
            driver.execute(null, "PRAGMA mmap_size=268435456", 0) // 256MB
            logger.debug("Performance optimizations enabled")
        } catch (ex: Exception) {
            logger.warning("Some performance optimizations could not be enabled", ex)
        }
    }

    private fun createTables() {
        // This would contain all CREATE TABLE statements
        // For the migration, these would be generated from the entity definitions
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
                PhotoPath TEXT,
                Timestamp TEXT NOT NULL,
                IsDeleted INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),

            """
            CREATE TABLE IF NOT EXISTS WeatherEntity (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                LocationId INTEGER NOT NULL,
                Temperature REAL NOT NULL,
                Description TEXT NOT NULL,
                Icon TEXT NOT NULL,
                WindSpeed REAL NOT NULL,
                WindDirection REAL NOT NULL,
                Humidity INTEGER NOT NULL,
                Pressure INTEGER NOT NULL,
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

    private inline fun <reified T> getTableName(): String {
        return T::class.simpleName + "Entity"
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
            null -> statement.bindNull(index)
            else -> statement.bindString(index, param.toString())
        }
    }

    // Simplified implementations for entity operations - these would be generated
    private suspend fun <T> executeInsertSql(entity: T): Long {
        // This would be generated based on entity type
        return 1L // Placeholder
    }

    private suspend fun <T> executeUpdateSql(entity: T): Int {
        // This would be generated based on entity type
        return 1 // Placeholder
    }

    private suspend fun <T> executeDeleteSql(entity: T): Int {
        // This would be generated based on entity type
        return 1 // Placeholder
    }

    private suspend fun <T> executeGetByIdSql(primaryKey: Any): Any? {
        // This would be generated based on entity type
        return null // Placeholder
    }

    private suspend fun <T> executeGetAllSql(): List<Any> {
        // This would be generated based on entity type
        return emptyList() // Placeholder
    }
}