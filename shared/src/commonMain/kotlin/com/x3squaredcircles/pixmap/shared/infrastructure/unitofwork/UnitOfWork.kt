// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/unitofwork/UnitOfWork.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.unitofwork

import com.x3squaredcircles.pixmap.shared.application.interfaces.IUnitOfWork
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.*
import com.x3squaredcircles.pixmap.shared.infrastructure.data.IDatabaseContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.logging.Logger

/**
 * Unit of Work pattern implementation for managing database transactions
 * Provides coordination across multiple repositories with transaction support
 */
class UnitOfWork(
    private val context: IDatabaseContext,
    private val logger: Logger,
    override val locations: ILocationRepository,
    override val weather: IWeatherRepository,
    override val tips: ITipRepository,
    override val tipTypes: ITipTypeRepository,
    override val settings: ISettingRepository,
    override val subscriptions: ISubscriptionRepository
) : IUnitOfWork {

    private val transactionMutex = Mutex()
    private var isInTransaction = false
    private var transactionNestingLevel = 0

    /**
     * Commits all changes made in this unit of work
     */
    override suspend fun saveChangesAsync(): Int {
        return transactionMutex.withLock {
            try {
                val changeCount = context.saveChangesAsync()
                logger.debug("Saved $changeCount changes to database")
                changeCount
            } catch (e: Exception) {
                logger.error("Failed to save changes", e)
                throw UnitOfWorkException("Failed to save changes: ${e.message}", e)
            }
        }
    }

    /**
     * Begins a new transaction
     */
    override suspend fun beginTransactionAsync() {
        transactionMutex.withLock {
            try {
                if (!isInTransaction) {
                    context.beginTransactionAsync()
                    isInTransaction = true
                    transactionNestingLevel = 1
                    logger.debug("Started new database transaction")
                } else {
                    // Support nested transactions by incrementing level
                    transactionNestingLevel++
                    logger.debug("Incremented transaction nesting level to $transactionNestingLevel")
                }
            } catch (e: Exception) {
                logger.error("Failed to begin transaction", e)
                throw UnitOfWorkException("Failed to begin transaction: ${e.message}", e)
            }
        }
    }

    /**
     * Commits the current transaction
     */
    override suspend fun commitAsync() {
        transactionMutex.withLock {
            try {
                if (isInTransaction) {
                    transactionNestingLevel--

                    if (transactionNestingLevel <= 0) {
                        context.commitAsync()
                        isInTransaction = false
                        transactionNestingLevel = 0
                        logger.debug("Committed database transaction")
                    } else {
                        logger.debug("Decremented transaction nesting level to $transactionNestingLevel")
                    }
                } else {
                    logger.warning("Attempted to commit transaction when none is active")
                }
            } catch (e: Exception) {
                logger.error("Failed to commit transaction", e)
                // Reset transaction state on error
                isInTransaction = false
                transactionNestingLevel = 0
                throw UnitOfWorkException("Failed to commit transaction: ${e.message}", e)
            }
        }
    }

    /**
     * Rolls back the current transaction
     */
    override suspend fun rollbackAsync() {
        transactionMutex.withLock {
            try {
                if (isInTransaction) {
                    context.rollbackAsync()
                    isInTransaction = false
                    transactionNestingLevel = 0
                    logger.debug("Rolled back database transaction")
                } else {
                    logger.warning("Attempted to rollback transaction when none is active")
                }
            } catch (e: Exception) {
                logger.error("Failed to rollback transaction", e)
                // Reset transaction state regardless
                isInTransaction = false
                transactionNestingLevel = 0
                throw UnitOfWorkException("Failed to rollback transaction: ${e.message}", e)
            }
        }
    }

    /**
     * Executes a block of code within a transaction
     */
    suspend fun <T> executeInTransactionAsync(block: suspend () -> T): T {
        beginTransactionAsync()
        return try {
            val result = block()
            commitAsync()
            result
        } catch (e: Exception) {
            try {
                rollbackAsync()
            } catch (rollbackException: Exception) {
                logger.error("Failed to rollback after error", rollbackException)
                // Add rollback exception as suppressed
                e.addSuppressed(rollbackException)
            }
            throw e
        }
    }

    /**
     * Executes a block of code within a transaction and saves changes
     */
    suspend fun <T> executeWithSaveAsync(block: suspend () -> T): T {
        return executeInTransactionAsync {
            val result = block()
            saveChangesAsync()
            result
        }
    }

    /**
     * Gets transaction status information
     */
    fun getTransactionStatus(): TransactionStatus {
        return TransactionStatus(
            isInTransaction = isInTransaction,
            nestingLevel = transactionNestingLevel
        )
    }

    /**
     * Checks if there are any pending changes
     */
    suspend fun hasPendingChanges(): Boolean {
        return try {
            context.hasPendingChangesAsync()
        } catch (e: Exception) {
            logger.error("Failed to check for pending changes", e)
            false
        }
    }

    /**
     * Discards all pending changes without saving
     */
    suspend fun discardChanges() {
        try {
            context.discardChangesAsync()
            logger.debug("Discarded all pending changes")
        } catch (e: Exception) {
            logger.error("Failed to discard changes", e)
            throw UnitOfWorkException("Failed to discard changes: ${e.message}", e)
        }
    }

    /**
     * Gets database context for advanced operations
     */
    fun getDatabaseContext(): IDatabaseContext = context
}

/**
 * Transaction status information
 */
data class TransactionStatus(
    val isInTransaction: Boolean,
    val nestingLevel: Int
) {
    val isNestedTransaction: Boolean
        get() = nestingLevel > 1
}

/**
 * Exception thrown by UnitOfWork operations
 */
class UnitOfWorkException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Extension functions for common UnitOfWork patterns
 */
suspend inline fun <T> IUnitOfWork.withTransaction(crossinline block: suspend () -> T): T {
    if (this is UnitOfWork) {
        return executeInTransactionAsync { block() }
    } else {
        // Fallback implementation for other UnitOfWork implementations
        beginTransactionAsync()
        return try {
            val result = block()
            commitAsync()
            result
        } catch (e: Exception) {
            rollbackAsync()
            throw e
        }
    }
}

suspend inline fun <T> IUnitOfWork.withSave(crossinline block: suspend () -> T): T {
    val result = block()
    saveChangesAsync()
    return result
}

suspend inline fun <T> IUnitOfWork.withTransactionAndSave(crossinline block: suspend () -> T): T {
    return withTransaction {
        val result = block()
        saveChangesAsync()
        result
    }
}

/**
 * Batch operation support
 */
suspend fun <T> IUnitOfWork.batchOperation(
    items: List<T>,
    batchSize: Int = 100,
    operation: suspend (List<T>) -> Unit
) {
    withTransaction {
        items.chunked(batchSize).forEach { batch ->
            operation(batch)
        }
        saveChangesAsync()
    }
}

/**
 * Safe operation with automatic rollback on exception
 */
suspend inline fun <T> IUnitOfWork.safely(crossinline operation: suspend () -> T): Result<T> {
    return try {
        withTransaction {
            val result = operation()
            saveChangesAsync()
            result
        }.let { Result.success(it) }
    } catch (e: Exception) {
        Result.failure(e)
    }
}