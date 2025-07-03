// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/unitofwork/UnitOfWork.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.unitofwork

import com.x3squaredcircles.pixmap.shared.application.interfaces.IUnitOfWork
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.*
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILoggingService
import com.x3squaredcircles.pixmap.shared.infrastructure.data.IDatabaseContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Unit of Work pattern implementation for managing database transactions
 * Provides coordination across multiple repositories with transaction support
 */
class UnitOfWork(
    private val context: IDatabaseContext,
    private val logging: ILoggingService,
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
                logging.debug("Saved $changeCount changes to database")
                changeCount
            } catch (e: Exception) {
                logging.error("Failed to save changes", e)
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
                    logging.debug("Started new transaction")
                } else {
                    transactionNestingLevel++
                    logging.debug("Nested transaction started (level: $transactionNestingLevel)")
                }
            } catch (e: Exception) {
                logging.error("Failed to begin transaction", e)
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
                        logging.debug("Transaction committed")
                    } else {
                        logging.debug("Nested transaction committed (level: $transactionNestingLevel)")
                    }
                } else {
                    logging.warning("Attempted to commit transaction when none is active")
                }
            } catch (e: Exception) {
                logging.error("Failed to commit transaction", e)
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
                    logging.debug("Transaction rolled back")
                } else {
                    logging.warning("Attempted to rollback transaction when none is active")
                }
            } catch (e: Exception) {
                logging.error("Failed to rollback transaction", e)
                throw UnitOfWorkException("Failed to rollback transaction: ${e.message}", e)
            }
        }
    }

    /**
     * Executes a block of code within a transaction
     */
    suspend fun <T> withTransactionAsync(block: suspend () -> T): T {
        return transactionMutex.withLock {
            val wasInTransaction = isInTransaction

            try {
                if (!wasInTransaction) {
                    beginTransactionAsync()
                }

                val result = block()

                if (!wasInTransaction) {
                    commitAsync()
                }

                result
            } catch (e: Exception) {
                if (!wasInTransaction && isInTransaction) {
                    try {
                        rollbackAsync()
                    } catch (rollbackException: Exception) {
                        logging.error("Failed to rollback transaction after error", rollbackException)
                    }
                }
                throw e
            }
        }
    }

    /**
     * Checks if currently in a transaction
     */
    fun isInTransaction(): Boolean = isInTransaction

    /**
     * Checks if there are pending changes
     */
    suspend fun hasPendingChangesAsync(): Boolean {
        return try {
            context.hasPendingChangesAsync()
        } catch (e: Exception) {
            logging.error("Failed to check pending changes", e)
            false
        }
    }

    /**
     * Discards all pending changes
     */
    suspend fun discardChangesAsync() {
        try {
            context.discardChangesAsync()
            logging.debug("Discarded pending changes")
        } catch (e: Exception) {
            logging.error("Failed to discard changes", e)
            throw UnitOfWorkException("Failed to discard changes: ${e.message}", e)
        }
    }

    /**
     * Disposes the unit of work and cleans up resources
     */
    fun dispose() {
        try {
            if (isInTransaction) {
                logging.debug("Disposing unit of work with active transaction - rolling back")
                // Note: This is a blocking call, consider making dispose suspend if needed
                // For now, we'll just log and reset the state
                isInTransaction = false
                transactionNestingLevel = 0
            }
        } catch (e: Exception) {
            logging.error("Error during unit of work disposal", e)
        }
    }
}

/**
 * Exception thrown when unit of work operations fail
 */
class UnitOfWorkException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)