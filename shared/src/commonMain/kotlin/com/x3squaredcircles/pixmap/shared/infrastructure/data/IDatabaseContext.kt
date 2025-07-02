// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/data/IDatabaseContext.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.data

import kotlinx.coroutines.flow.Flow

/**
 * Database context interface for SQLite operations with KMM support
 */
interface IDatabaseContext {

    /**
     * Initializes the database connection and creates tables if needed
     */
    suspend fun initializeDatabaseAsync()

    /**
     * Closes the database connection
     */
    suspend fun closeAsync()

    // ===== BASIC CRUD OPERATIONS =====

    /**
     * Inserts an entity and returns the generated ID
     */
    suspend fun <T : Any> insertAsync(entity: T): Long

    /**
     * Updates an entity and returns the number of affected rows
     */
    suspend fun <T : Any> updateAsync(entity: T): Int

    /**
     * Deletes an entity and returns the number of affected rows
     */
    suspend fun <T : Any> deleteAsync(entity: T): Int

    /**
     * Gets an entity by primary key
     */
    suspend fun <T : Any> getAsync(primaryKey: Any, entityMapper: suspend (Any) -> T?): T?

    /**
     * Gets all entities of a type
     */
    suspend fun <T : Any> getAllAsync(entityMapper: suspend (Any) -> T): List<T>

    // ===== QUERY OPERATIONS =====

    /**
     * Executes a raw SQL query and returns results
     */
    suspend fun <T : Any> queryAsync(
        sql: String,
        resultMapper: suspend (Any) -> T,
        vararg parameters: Any?
    ): List<T>

    /**
     * Executes a raw SQL query and returns a single result
     */
    suspend fun <T : Any> querySingleAsync(
        sql: String,
        resultMapper: suspend (Any) -> T,
        vararg parameters: Any?
    ): T?

    /**
     * Executes a raw SQL query and returns the first result or null
     */
    suspend fun <T : Any> queryFirstOrNullAsync(
        sql: String,
        resultMapper: suspend (Any) -> T,
        vararg parameters: Any?
    ): T?

    /**
     * Executes a scalar query (returns single value)
     */
    suspend fun <T> queryScalarAsync(
        sql: String,
        vararg parameters: Any?
    ): T?

    /**
     * Executes a count query
     */
    suspend fun countAsync(
        sql: String,
        vararg parameters: Any?
    ): Long

    /**
     * Checks if any rows exist for the query
     */
    suspend fun existsAsync(
        sql: String,
        vararg parameters: Any?
    ): Boolean

    // ===== BULK OPERATIONS =====

    /**
     * Bulk insert multiple entities
     */
    suspend fun <T : Any> bulkInsertAsync(
        entities: List<T>,
        batchSize: Int = 100
    ): List<Long>

    /**
     * Bulk update multiple entities
     */
    suspend fun <T : Any> bulkUpdateAsync(
        entities: List<T>,
        batchSize: Int = 100
    ): Int

    /**
     * Bulk delete multiple entities
     */
    suspend fun <T : Any> bulkDeleteAsync(
        entities: List<T>,
        batchSize: Int = 100
    ): Int

    /**
     * Executes a non-query SQL statement (INSERT, UPDATE, DELETE)
     */
    suspend fun executeAsync(
        sql: String,
        vararg parameters: Any?
    ): Int

    // ===== TRANSACTION MANAGEMENT =====

    /**
     * Begins a new database transaction
     */
    suspend fun beginTransactionAsync()

    /**
     * Commits the current transaction
     */
    suspend fun commitAsync()

    /**
     * Rolls back the current transaction
     */
    suspend fun rollbackAsync()

    /**
     * Executes a block within a transaction
     */
    suspend fun <T> withTransactionAsync(block: suspend () -> T): T

    /**
     * Checks if currently in a transaction
     */
    fun isInTransaction(): Boolean

    // ===== CHANGE TRACKING =====

    /**
     * Saves all pending changes
     */
    suspend fun saveChangesAsync(): Int

    /**
     * Checks if there are pending changes
     */
    suspend fun hasPendingChangesAsync(): Boolean

    /**
     * Discards all pending changes
     */
    suspend fun discardChangesAsync()

    // ===== REACTIVE OPERATIONS =====

    /**
     * Gets a Flow that emits when the specified table changes
     */
    fun <T : Any> observeTable(tableName: String, mapper: suspend (Any) -> T): Flow<List<T>>

    /**
     * Gets a Flow that emits when any table changes
     */
    fun observeChanges(): Flow<DatabaseChange>

    // ===== SCHEMA AND METADATA =====

    /**
     * Gets the current database schema version
     */
    suspend fun getSchemaVersionAsync(): Int

    /**
     * Sets the database schema version
     */
    suspend fun setSchemaVersionAsync(version: Int)

    /**
     * Gets database metadata information
     */
    suspend fun getDatabaseInfoAsync(): DatabaseInfo

    /**
     * Checks if a table exists
     */
    suspend fun tableExistsAsync(tableName: String): Boolean

    /**
     * Gets table schema information
     */
    suspend fun getTableSchemaAsync(tableName: String): TableSchema?

    // ===== PERFORMANCE AND OPTIMIZATION =====

    /**
     * Analyzes database for query optimization
     */
    suspend fun analyzeAsync()

    /**
     * Vacuums the database to reclaim space
     */
    suspend fun vacuumAsync()

    /**
     * Gets database file size in bytes
     */
    suspend fun getDatabaseSizeAsync(): Long

    /**
     * Optimizes database performance
     */
    suspend fun optimizeAsync()

    // ===== BACKUP AND RESTORE =====

    /**
     * Creates a backup of the database
     */
    suspend fun backupAsync(backupPath: String): Boolean

    /**
     * Restores database from backup
     */
    suspend fun restoreAsync(backupPath: String): Boolean

    // ===== ERROR HANDLING AND LOGGING =====

    /**
     * Gets the last error message from the database
     */
    fun getLastError(): String?

    /**
     * Enables or disables SQL logging
     */
    fun setSqlLogging(enabled: Boolean)

    /**
     * Sets the log level for database operations
     */
    fun setLogLevel(level: DatabaseLogLevel)
}

/**
 * Database change notification
 */
data class DatabaseChange(
    val tableName: String,
    val changeType: ChangeType,
    val rowId: Long? = null,
    val timestamp: kotlinx.datetime.Instant = kotlinx.datetime.Clock.System.now()
)

/**
 * Types of database changes
 */
enum class ChangeType {
    INSERT,
    UPDATE,
    DELETE
}

/**
 * Database information
 */
data class DatabaseInfo(
    val version: Int,
    val path: String,
    val size: Long,
    val tableCount: Int,
    val lastModified: kotlinx.datetime.Instant,
    val isReadOnly: Boolean = false
)

/**
 * Table schema information
 */
data class TableSchema(
    val name: String,
    val columns: List<ColumnInfo>,
    val primaryKey: List<String>,
    val foreignKeys: List<ForeignKeyInfo> = emptyList(),
    val indexes: List<IndexInfo> = emptyList()
)

/**
 * Column information
 */
data class ColumnInfo(
    val name: String,
    val type: String,
    val isNullable: Boolean,
    val defaultValue: String? = null,
    val isPrimaryKey: Boolean = false,
    val isAutoIncrement: Boolean = false
)

/**
 * Foreign key information
 */
data class ForeignKeyInfo(
    val columnName: String,
    val referencedTable: String,
    val referencedColumn: String,
    val onDelete: ReferentialAction = ReferentialAction.NO_ACTION,
    val onUpdate: ReferentialAction = ReferentialAction.NO_ACTION
)

/**
 * Index information
 */
data class IndexInfo(
    val name: String,
    val columns: List<String>,
    val isUnique: Boolean = false
)

/**
 * Referential actions for foreign keys
 */
enum class ReferentialAction {
    NO_ACTION,
    RESTRICT,
    SET_NULL,
    SET_DEFAULT,
    CASCADE
}

/**
 * Database log levels
 */
enum class DatabaseLogLevel {
    NONE,
    ERROR,
    WARNING,
    INFO,
    DEBUG,
    VERBOSE
}

/**
 * Database connection state
 */
enum class ConnectionState {
    CLOSED,
    OPENING,
    OPEN,
    EXECUTING,
    CLOSING,
    ERROR
}

/**
 * Database exception for context-specific errors
 */
class DatabaseContextException(
    message: String,
    cause: Throwable? = null,
    val operation: String? = null,
    val sql: String? = null
) : Exception(message, cause)

/**
 * Extension functions for common database operations
 */
suspend inline fun <reified T : Any> IDatabaseContext.insertAndReturnAsync(entity: T): Pair<T, Long> {
    val id = insertAsync(entity)
    return entity to id
}

suspend inline fun <reified T : Any> IDatabaseContext.findByIdAsync(
    id: Any,
    noinline mapper: suspend (Any) -> T?
): T? {
    return getAsync(id, mapper)
}

suspend inline fun IDatabaseContext.executeCountAsync(sql: String, vararg parameters: Any?): Long {
    return countAsync(sql, *parameters)
}

suspend inline fun IDatabaseContext.executeExistsAsync(sql: String, vararg parameters: Any?): Boolean {
    return existsAsync(sql, *parameters)
}