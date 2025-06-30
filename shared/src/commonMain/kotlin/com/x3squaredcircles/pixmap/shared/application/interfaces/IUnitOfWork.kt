// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/interfaces/IUnitOfWork.kt
package com.x3squaredcircles.pixmap.shared.application.interfaces

import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ILocationRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ISettingRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ISubscriptionRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ITipRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ITipTypeRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.IWeatherRepository

/**
 * Unit of Work pattern interface for managing database transactions
 */
interface IUnitOfWork {

    /**
     * Repository for Location entities
     */
    val locations: ILocationRepository

    /**
     * Repository for Weather entities
     */
    val weather: IWeatherRepository

    /**
     * Repository for Tip entities
     */
    val tips: ITipRepository

    /**
     * Repository for TipType entities
     */
    val tipTypes: ITipTypeRepository

    /**
     * Repository for Setting entities
     */
    val settings: ISettingRepository

    /**
     * Repository for Subscription entities
     */
    val subscriptions: ISubscriptionRepository

    /**
     * Commits all changes made in this unit of work
     */
    suspend fun saveChangesAsync(): Int

    /**
     * Begins a new transaction
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
}