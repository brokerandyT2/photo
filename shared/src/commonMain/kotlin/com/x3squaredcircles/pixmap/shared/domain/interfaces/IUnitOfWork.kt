package com.x3squaredcircles.pixmap.shared.domain.interfaces

/**
 * Interface for Unit of Work pattern
 */
interface IUnitOfWork {
    suspend fun saveChanges(): Int
    suspend fun beginTransaction()
    suspend fun commitTransaction()
    suspend fun rollbackTransaction()
}