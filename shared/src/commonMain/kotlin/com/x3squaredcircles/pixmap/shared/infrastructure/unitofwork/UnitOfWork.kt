// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/unitofwork/UnitOfWork.kt

package com.x3squaredcircles.pixmap.shared.infrastructure.unitofwork

import com.x3squaredcircles.pixmap.shared.application.interfaces.ILocationRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.ISettingRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.ITipRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.ITipTypeRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.IWeatherRepository
import com.x3squaredcircles.pixmap.shared.infrastructure.database.IDatabaseContext

interface IUnitOfWork {
    val locations: ILocationRepository
    val subscriptions: ISubscriptionRepository
    val weather: IWeatherRepository
    val tips: ITipRepository
    val tipTypes: ITipTypeRepository
    val settings: ISettingRepository

    suspend fun saveChangesAsync(): Int
    fun getDatabaseContext(): IDatabaseContext
    suspend fun beginTransactionAsync()
    suspend fun commitAsync()
    suspend fun rollbackAsync()
}

interface ISubscriptionRepository {
    // Placeholder for subscription repository interface
    // Will be implemented when subscription functionality is added
}

class UnitOfWork(
    private val context: IDatabaseContext,
    override val locations: ILocationRepository,
    override val subscriptions: ISubscriptionRepository,
    override val weather: IWeatherRepository,
    override val tips: ITipRepository,
    override val tipTypes: ITipTypeRepository,
    override val settings: ISettingRepository
) : IUnitOfWork {

    private var inTransaction: Boolean = false
    private val transactionLock = Any()

    override suspend fun saveChangesAsync(): Int {
        // In SQLDelight, saves are immediate, so this is a no-op
        // But we return 1 to indicate success for compatibility
        return 1
    }

    override fun getDatabaseContext(): IDatabaseContext {
        return context
    }

    override suspend fun beginTransactionAsync() {
        synchronized(transactionLock) {
            if (inTransaction) {
                throw IllegalStateException("Transaction already in progress")
            }
        }

        try {
            context.beginTransaction()
            synchronized(transactionLock) {
                inTransaction = true
            }
        } catch (e: Exception) {
            throw UnitOfWorkException("Failed to begin transaction", e)
        }
    }

    override suspend fun commitAsync() {
        synchronized(transactionLock) {
            if (!inTransaction) {
                return
            }
        }

        try {
            context.commitTransaction()
        } finally {
            synchronized(transactionLock) {
                inTransaction = false
            }
        }
    }

    override suspend fun rollbackAsync() {
        synchronized(transactionLock) {
            if (!inTransaction) {
                throw IllegalStateException("No transaction in progress")
            }
        }

        try {
            context.rollbackTransaction()
        } finally {
            synchronized(transactionLock) {
                inTransaction = false
            }
        }
    }
}

class UnitOfWorkException(message: String, cause: Throwable? = null) : Exception(message, cause)