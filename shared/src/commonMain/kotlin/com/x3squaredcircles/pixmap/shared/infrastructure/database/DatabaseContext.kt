// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/database/DatabaseContext.kt

package com.x3squaredcircles.pixmap.shared.infrastructure.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

interface IDatabaseContext {
    suspend fun initializeDatabase()
    suspend fun <T> executeInTransaction(operation: suspend () -> T): T
    suspend fun beginTransaction()
    suspend fun commitTransaction()
    suspend fun rollbackTransaction()
    suspend fun executeNonQuery(sql: String, parameters: List<Any> = emptyList()): Long
    suspend fun <T> executeScalar(sql: String, parameters: List<Any> = emptyList(), mapper: (Any?) -> T): T
    suspend fun <T> executeQuery(sql: String, parameters: List<Any> = emptyList(), mapper: (SqlCursor) -> T): List<T>
    suspend fun <T> executeQuerySingle(sql: String, parameters: List<Any> = emptyList(), mapper: (SqlCursor) -> T): T?
    suspend fun <T> insert(entity: T, insertFunction: (T) -> Unit): Long
    suspend fun <T> update(entity: T, updateFunction: (T) -> Unit): Long
    suspend fun <T> delete(entity: T, deleteFunction: (T) -> Unit): Long
    suspend fun <T> bulkInsert(entities: List<T>, insertFunction: (T) -> Unit, batchSize: Int = 100): Int
    suspend fun <T> bulkUpdate(entities: List<T>, updateFunction: (T) -> Unit, batchSize: Int = 100): Int
    fun getConnection(): SqlDriver
}

interface SqlCursor {
    fun getString(index: Int): String?
    fun getLong(index: Int): Long?
    fun getDouble(index: Int): Double?
    fun getBoolean(index: Int): Boolean?
    fun getInt(index: Int): Int?
}

class DatabaseContext(
    private val driver: SqlDriver,
    private val database: PixMapDatabase
) : IDatabaseContext {

    private var isInTransaction = false
    private val transactionLock = Any()
    private var isInitialized = false

    override suspend fun initializeDatabase() {
        if (isInitialized) return

        withContext(Dispatchers.IO) {
            try {
                // Enable foreign keys
                driver.execute(null, "PRAGMA foreign_keys = ON", 0)

                // Set performance optimizations
                driver.execute(null, "PRAGMA journal_mode = WAL", 0)
                driver.execute(null, "PRAGMA synchronous = NORMAL", 0)
                driver.execute(null, "PRAGMA temp_store = MEMORY", 0)
                driver.execute(null, "PRAGMA mmap_size = 268435456", 0) // 256MB

                // Analyze tables for query optimization
                driver.execute(null, "ANALYZE", 0)

                isInitialized = true
            } catch (e: Exception) {
                throw DatabaseException("Failed to initialize database", e)
            }
        }
    }

    override suspend fun <T> executeInTransaction(operation: suspend () -> T): T {
        val wasInTransaction = synchronized(transactionLock) { isInTransaction }

        if (wasInTransaction) {
            // Already in transaction, just execute the operation
            return operation()
        }

        // Start new transaction
        beginTransaction()
        return try {
            val result = operation()
            commitTransaction()
            result
        } catch (e: Exception) {
            rollbackTransaction()
            throw e
        }
    }

    override suspend fun beginTransaction() {
        synchronized(transactionLock) {
            if (isInTransaction) {
                throw IllegalStateException("Transaction already in progress")
            }
            isInTransaction = true
        }

        withContext(Dispatchers.IO) {
            try {
                driver.execute(null, "BEGIN TRANSACTION", 0)
            } catch (e: Exception) {
                synchronized(transactionLock) {
                    isInTransaction = false
                }
                throw DatabaseException("Failed to begin transaction", e)
            }
        }
    }

    override suspend fun commitTransaction() {
        synchronized(transactionLock) {
            if (!isInTransaction) {
                throw IllegalStateException("No transaction in progress")
            }
        }

        withContext(Dispatchers.IO) {
            try {
                driver.execute(null, "COMMIT", 0)
            } finally {
                synchronized(transactionLock) {
                    isInTransaction = false
                }
            }
        }
    }

    override suspend fun rollbackTransaction() {
        synchronized(transactionLock) {
            if (!isInTransaction) {
                throw IllegalStateException("No transaction in progress")
            }
        }

        withContext(Dispatchers.IO) {
            try {
                driver.execute(null, "ROLLBACK", 0)
            } finally {
                synchronized(transactionLock) {
                    isInTransaction = false
                }
            }
        }
    }

    override suspend fun executeNonQuery(sql: String, parameters: List<Any>): Long {
        return withContext(Dispatchers.IO) {
            try {
                driver.execute(null, sql, parameters.size) {
                    parameters.forEachIndexed { index, param ->
                        bindParameter(it, index, param)
                    }
                }.value
            } catch (e: Exception) {
                throw DatabaseException("Failed to execute non-query: $sql", e)
            }
        }
    }

    override suspend fun <T> executeScalar(sql: String, parameters: List<Any>, mapper: (Any?) -> T): T {
        return withContext(Dispatchers.IO) {
            try {
                driver.executeQuery(null, sql, parameters.size) { cursor ->
                    parameters.forEachIndexed { index, param ->
                        bindParameter(cursor, index, param)
                    }
                }.use { cursor ->
                    if (cursor.next()) {
                        mapper(cursor.getString(0))
                    } else {
                        throw DatabaseException("No result returned from scalar query")
                    }
                }
            } catch (e: Exception) {
                throw DatabaseException("Failed to execute scalar query: $sql", e)
            }
        }
    }

    override suspend fun <T> executeQuery(sql: String, parameters: List<Any>, mapper: (SqlCursor) -> T): List<T> {
        return withContext(Dispatchers.IO) {
            try {
                val results = mutableListOf<T>()
                driver.executeQuery(null, sql, parameters.size) { cursor ->
                    parameters.forEachIndexed { index, param ->
                        bindParameter(cursor, index, param)
                    }
                }.use { cursor ->
                    while (cursor.next()) {
                        results.add(mapper(SqliteDriverCursor(cursor)))
                    }
                }
                results
            } catch (e: Exception) {
                throw DatabaseException("Failed to execute query: $sql", e)
            }
        }
    }

    override suspend fun <T> executeQuerySingle(sql: String, parameters: List<Any>, mapper: (SqlCursor) -> T): T? {
        return withContext(Dispatchers.IO) {
            try {
                driver.executeQuery(null, sql, parameters.size) { cursor ->
                    parameters.forEachIndexed { index, param ->
                        bindParameter(cursor, index, param)
                    }
                }.use { cursor ->
                    if (cursor.next()) {
                        mapper(SqliteDriverCursor(cursor))
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                throw DatabaseException("Failed to execute single query: $sql", e)
            }
        }
    }

    override suspend fun <T> insert(entity: T, insertFunction: (T) -> Unit): Long {
        return withContext(Dispatchers.IO) {
            try {
                insertFunction(entity)
                // Return the last insert row id
                driver.executeQuery(null, "SELECT last_insert_rowid()", 0).use { cursor ->
                    if (cursor.next()) {
                        cursor.getLong(0) ?: 0L
                    } else {
                        0L
                    }
                }
            } catch (e: Exception) {
                throw DatabaseException("Failed to insert entity", e)
            }
        }
    }

    override suspend fun <T> update(entity: T, updateFunction: (T) -> Unit): Long {
        return withContext(Dispatchers.IO) {
            try {
                updateFunction(entity)
                // Return affected rows count
                driver.executeQuery(null, "SELECT changes()", 0).use { cursor ->
                    if (cursor.next()) {
                        cursor.getLong(0) ?: 0L
                    } else {
                        0L
                    }
                }
            } catch (e: Exception) {
                throw DatabaseException("Failed to update entity", e)
            }
        }
    }

    override suspend fun <T> delete(entity: T, deleteFunction: (T) -> Unit): Long {
        return withContext(Dispatchers.IO) {
            try {
                deleteFunction(entity)
                // Return affected rows count
                driver.executeQuery(null, "SELECT changes()", 0).use { cursor ->
                    if (cursor.next()) {
                        cursor.getLong(0) ?: 0L
                    } else {
                        0L
                    }
                }
            } catch (e: Exception) {
                throw DatabaseException("Failed to delete entity", e)
            }
        }
    }

    override suspend fun <T> bulkInsert(entities: List<T>, insertFunction: (T) -> Unit, batchSize: Int): Int {
        if (entities.isEmpty()) return 0

        return executeInTransaction {
            var totalInserted = 0
            entities.chunked(batchSize).forEach { batch ->
                batch.forEach { entity ->
                    insertFunction(entity)
                    totalInserted++
                }
            }
            totalInserted
        }
    }

    override suspend fun <T> bulkUpdate(entities: List<T>, updateFunction: (T) -> Unit, batchSize: Int): Int {
        if (entities.isEmpty()) return 0

        return executeInTransaction {
            var totalUpdated = 0
            entities.chunked(batchSize).forEach { batch ->
                batch.forEach { entity ->
                    updateFunction(entity)
                    totalUpdated++
                }
            }
            totalUpdated
        }
    }

    override fun getConnection(): SqlDriver = driver

    private fun bindParameter(binder: Any, index: Int, parameter: Any) {
        when (parameter) {
            is String -> (binder as? app.cash.sqldelight.db.SqlPreparedStatement)?.bindString(index + 1, parameter)
            is Int -> (binder as? app.cash.sqldelight.db.SqlPreparedStatement)?.bindLong(index + 1, parameter.toLong())
            is Long -> (binder as? app.cash.sqldelight.db.SqlPreparedStatement)?.bindLong(index + 1, parameter)
            is Double -> (binder as? app.cash.sqldelight.db.SqlPreparedStatement)?.bindDouble(index + 1, parameter)
            is Float -> (binder as? app.cash.sqldelight.db.SqlPreparedStatement)?.bindDouble(index + 1, parameter.toDouble())
            is Boolean -> (binder as? app.cash.sqldelight.db.SqlPreparedStatement)?.bindLong(index + 1, if (parameter) 1L else 0L)
            else -> (binder as? app.cash.sqldelight.db.SqlPreparedStatement)?.bindString(index + 1, parameter.toString())
        }
    }
}

private class SqliteDriverCursor(private val cursor: app.cash.sqldelight.db.SqlCursor) : SqlCursor {
    override fun getString(index: Int): String? = cursor.getString(index)
    override fun getLong(index: Int): Long? = cursor.getLong(index)
    override fun getDouble(index: Int): Double? = cursor.getDouble(index)
    override fun getBoolean(index: Int): Boolean? = cursor.getLong(index)?.let { it != 0L }
    override fun getInt(index: Int): Int? = cursor.getLong(index)?.toInt()
}

class DatabaseException(message: String, cause: Throwable? = null) : Exception(message, cause)