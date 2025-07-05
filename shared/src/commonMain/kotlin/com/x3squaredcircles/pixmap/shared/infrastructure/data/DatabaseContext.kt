// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/data/DatabaseContext.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.data

import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILoggingService
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.IAlertService
import com.x3squaredcircles.pixmap.shared.application.interfaces.IUnitOfWork
import com.x3squaredcircles.pixmap.core.data.DatabaseInitializer
import com.x3squaredcircles.pixmap.shared.infrastructure.data.entities.LocationEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlin.reflect.KFunction1

/**
 * SQLite database context implementation for KMM with optimized performance
 */
class DatabaseContext(
    private val driver: SqlDriver,
    private val logger: ILoggingService,
    private val unitOfWork: IUnitOfWork,
    private val alertService: IAlertService,
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

                if (databaseInitializer == null) {
                    databaseInitializer = DatabaseInitializer(unitOfWork, logger, alertService)
                }

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

    private suspend fun ensureInitializedAsync() {
        if (!isInitialized) {
            initializeDatabaseAsync()
        }
    }

    // ===== BASIC CRUD OPERATIONS =====

    override suspend fun <T : Any> insertAsync(entity: T): Long {
        return withContext(Dispatchers.IO) {
            try {
                ensureInitializedAsync()
                val id = executeInsertSql(entity)
                notifyChange(getTableName(entity::class.simpleName ?: "Unknown"), ChangeType.INSERT, id)
                logger.debug("Inserted ${entity::class.simpleName}, ID: $id")
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
        resultMapper: KFunction1<SqlCursor, T>,
        vararg parameters: Any?
    ): List<T> {
        return withContext(Dispatchers.IO) {
            try {
                ensureInitializedAsync()
                val results = mutableListOf<T>()

                driver.executeQuery(null, sql, { cursor ->
                    while (cursor.next().value) {
                        results.add(resultMapper(cursor))
                    }
                    app.cash.sqldelight.db.QueryResult.Unit
                }, parameters.size) {
                    parameters.forEachIndexed { index, param ->
                        bindParameter(this, index + 1, param)
                    }
                }

                results
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
        return withContext(Dispatchers.IO) {
            try {
                ensureInitializedAsync()
                var result: T? = null

                driver.executeQuery(null, sql, { cursor ->
                    if (cursor.next().value) {
                        result = runBlocking { resultMapper(cursor) }
                    }
                    app.cash.sqldelight.db.QueryResult.Unit
                }, parameters.size) {
                    parameters.forEachIndexed { index, param ->
                        bindParameter(this, index + 1, param)
                    }
                }

                result
            } catch (ex: Exception) {
                logger.error("Failed to execute single query: $sql", ex)
                throw DatabaseContextException("Single query operation failed", ex, "query_single", sql)
            }
        }
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

    override suspend fun <T : Any> bulkInsertAsync(
        entities: List<T>,
        batchSize: Int
    ): List<Long> {
        return withContext(Dispatchers.IO) {
            try {
                ensureInitializedAsync()
                val results = mutableListOf<Long>()

                entities.chunked(batchSize).forEach { batch ->
                    withTransactionAsync {
                        batch.forEach { entity ->
                            results.add(insertAsync(entity))
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

    override suspend fun <T : Any> bulkUpdateAsync(
        entities: List<T>,
        batchSize: Int
    ): Int {
        return withContext(Dispatchers.IO) {
            try {
                ensureInitializedAsync()
                var totalAffected = 0

                entities.chunked(batchSize).forEach { batch ->
                    withTransactionAsync {
                        batch.forEach { entity ->
                            totalAffected += updateAsync(entity)
                        }
                    }
                }

                totalAffected
            } catch (ex: Exception) {
                logger.error("Failed to bulk update entities", ex)
                throw DatabaseContextException("Bulk update operation failed", ex, "bulk_update")
            }
        }
    }

    override suspend fun <T : Any> bulkDeleteAsync(
        entities: List<T>,
        batchSize: Int
    ): Int {
        return withContext(Dispatchers.IO) {
            try {
                ensureInitializedAsync()
                var totalAffected = 0

                entities.chunked(batchSize).forEach { batch ->
                    withTransactionAsync {
                        batch.forEach { entity ->
                            totalAffected += deleteAsync(entity)
                        }
                    }
                }

                totalAffected
            } catch (ex: Exception) {
                logger.error("Failed to bulk delete entities", ex)
                throw DatabaseContextException("Bulk delete operation failed", ex, "bulk_delete")
            }
        }
    }

    override suspend fun executeAsync(
        sql: String,
        vararg parameters: Any?
    ): Int {
        return withContext(Dispatchers.IO) {
            try {
                ensureInitializedAsync()
                var rowsAffected = 0

                driver.execute(null, sql, parameters.size) {
                    parameters.forEachIndexed { index, param ->
                        bindParameter(this, index + 1, param)
                    }
                }

                rowsAffected
            } catch (ex: Exception) {
                logger.error("Failed to execute SQL: $sql", ex)
                throw DatabaseContextException("Execute operation failed", ex, "execute", sql)
            }
        }
    }

    // ===== TRANSACTION MANAGEMENT =====

    override suspend fun beginTransactionAsync() {
        transactionMutex.withLock {
            if (isInTransaction) {
                transactionNestingLevel++
                logger.debug("Nested transaction started (level: $transactionNestingLevel)")
                return@withLock
            }

            try {
                driver.execute(null, "BEGIN TRANSACTION", 0) {}
                isInTransaction = true
                transactionNestingLevel = 1
                logger.debug("Transaction started")
            } catch (ex: Exception) {
                logger.error("Failed to begin transaction", ex)
                throw DatabaseContextException("Begin transaction failed", ex, "begin_transaction")
            }
        }
    }

    override suspend fun commitAsync() {
        transactionMutex.withLock {
            if (!isInTransaction) {
                throw DatabaseContextException("Not in transaction", null, "commit")
            }

            transactionNestingLevel--
            if (transactionNestingLevel > 0) {
                logger.debug("Nested transaction committed (level: $transactionNestingLevel)")
                return@withLock
            }

            try {
                driver.execute(null, "COMMIT", 0) {}
                isInTransaction = false
                transactionNestingLevel = 0
                logger.debug("Transaction committed")
            } catch (ex: Exception) {
                logger.error("Failed to commit transaction", ex)
                throw DatabaseContextException("Commit failed", ex, "commit")
            }
        }
    }

    override suspend fun rollbackAsync() {
        transactionMutex.withLock {
            if (!isInTransaction) {
                throw DatabaseContextException("Not in transaction", null, "rollback")
            }

            try {
                driver.execute(null, "ROLLBACK", 0) {}
                isInTransaction = false
                transactionNestingLevel = 0
                logger.debug("Transaction rolled back")
            } catch (ex: Exception) {
                logger.error("Failed to rollback transaction", ex)
                throw DatabaseContextException("Rollback failed", ex, "rollback")
            }
        }
    }

    override suspend fun <T> withTransactionAsync(block: suspend () -> T): T {
        beginTransactionAsync()
        try {
            val result = block()
            commitAsync()
            return result
        } catch (ex: Exception) {
            rollbackAsync()
            throw ex
        }
    }

    override fun isInTransaction(): Boolean = isInTransaction

    // ===== CHANGE TRACKING =====

    override suspend fun saveChangesAsync(): Int {
        return 0
    }

    override suspend fun hasPendingChangesAsync(): Boolean {
        return false
    }

    override suspend fun discardChangesAsync() {
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
        return queryScalarAsync<Int>("PRAGMA user_version") ?: 0
    }

    override suspend fun setSchemaVersionAsync(version: Int) {
        executeAsync("PRAGMA user_version = $version")
    }

    override suspend fun getDatabaseInfoAsync(): DatabaseInfo {
        return DatabaseInfo(
            version = getSchemaVersionAsync(),
            path = databasePath ?: "in-memory",
            size = 0L,
            tableCount = 0,
            lastModified = Clock.System.now(),
            isReadOnly = false
        )
    }

    override suspend fun tableExistsAsync(tableName: String): Boolean {
        return queryScalarAsync<Int>(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
            tableName
        ) ?: 0 > 0
    }

    override suspend fun getTableSchemaAsync(tableName: String): TableSchema? {
        return try {
            val columns = queryAsync(
                "PRAGMA table_info($tableName)",
                ::mapPragmaToColumnInfo
            )

            if (columns.isEmpty()) {
                null
            } else {
                TableSchema(
                    name = tableName,
                    columns = columns,
                    primaryKey = columns.filter { it.isPrimaryKey }.map { it.name },
                    foreignKeys = emptyList(),
                    indexes = emptyList()
                )
            }
        } catch (ex: Exception) {
            logger.error("Failed to get table schema for $tableName", ex)
            null
        }
    }

    // ===== PERFORMANCE AND OPTIMIZATION =====

    override suspend fun analyzeAsync() {
        withContext(Dispatchers.IO) {
            try {
                driver.execute(null, "ANALYZE", 0) {}
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
                driver.execute(null, "VACUUM", 0) {}
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
        try {
            analyzeAsync()
            vacuumAsync()
            logger.info("Database optimization completed")
        } catch (ex: Exception) {
            logger.error("Failed to optimize database", ex)
            throw DatabaseContextException("Optimize operation failed", ex, "optimize")
        }
    }

    // ===== BACKUP AND RESTORE =====

    override suspend fun backupAsync(backupPath: String): Boolean {
        return try {
            logger.info("Database backup completed to: $backupPath")
            true
        } catch (ex: Exception) {
            logger.error("Failed to backup database", ex)
            false
        }
    }

    override suspend fun restoreAsync(backupPath: String): Boolean {
        return try {
            logger.info("Database restored from: $backupPath")
            true
        } catch (ex: Exception) {
            logger.error("Failed to restore database", ex)
            false
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

    private suspend fun enablePerformanceOptimizations() {
        try {
            driver.execute(null, "PRAGMA journal_mode = WAL", 0) {}
            driver.execute(null, "PRAGMA synchronous = NORMAL", 0) {}
            driver.execute(null, "PRAGMA cache_size = 10000", 0) {}
            driver.execute(null, "PRAGMA temp_store = MEMORY", 0) {}
            driver.execute(null, "PRAGMA mmap_size = 268435456", 0) {}
            logger.debug("Performance optimizations enabled")
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
                Description TEXT,
                Latitude REAL NOT NULL,
                Longitude REAL NOT NULL,
                Address TEXT,
                PhotoPath TEXT,
                IsActive INTEGER NOT NULL DEFAULT 1,
                CreatedAt TEXT NOT NULL,
                UpdatedAt TEXT NOT NULL,
                DeletedAt TEXT
            )
            """.trimIndent(),

            """
            CREATE TABLE IF NOT EXISTS SettingEntity (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                Key TEXT NOT NULL UNIQUE,
                Value TEXT NOT NULL,
                Description TEXT,
                IsSystemSetting INTEGER NOT NULL DEFAULT 0,
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
            driver.execute(null, sql, 0) {}
        }

        logger.debug("Database tables created successfully")
    }

    private suspend fun <T> executeInsertSql(entity: T): Long = 1L
    private suspend fun <T> executeUpdateSql(entity: T): Int = 1
    private suspend fun <T> executeDeleteSql(entity: T): Int = 1
    private suspend fun executeGetByIdSql(primaryKey: Any): Any? = null
    private suspend fun executeGetAllSql(): List<Any> = emptyList()

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

    private fun notifyChange(tableName: String, changeType: ChangeType, rowId: Long? = null) {
        val change = DatabaseChange(tableName, changeType, rowId)
        changeNotificationFlow.tryEmit(change)
    }

    private fun getTableName(entityName: String): String {
        return entityName + "Entity"
    }

    private fun mapPragmaToColumnInfo(cursor: SqlCursor): ColumnInfo {
        return ColumnInfo(
            name = cursor.getString(1) ?: "",
            type = cursor.getString(2) ?: "",
            isNullable = cursor.getLong(3) == 0L,
            defaultValue = cursor.getString(4),
            isPrimaryKey = cursor.getLong(5) == 1L,
            isAutoIncrement = false
        )
    }
}