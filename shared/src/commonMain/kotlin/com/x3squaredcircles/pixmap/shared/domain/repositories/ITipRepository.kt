package com.x3squaredcircles.pixmap.shared.domain.repositories

import com.x3squaredcircles.pixmap.shared.domain.entities.Tip
import com.x3squaredcircles.pixmap.shared.domain.entities.TipType

/**
 * Repository interface for tip operations
 */
interface ITipRepository {
    suspend fun getById(id: Int): Tip?
    suspend fun getAll(): List<Tip>
    suspend fun getByTipType(tipTypeId: Int): List<Tip>
    suspend fun save(tip: Tip): Tip
    suspend fun delete(id: Int)
    suspend fun exists(id: Int): Boolean
}

/**
 * Repository interface for tip type operations
 */
interface ITipTypeRepository {
    suspend fun getById(id: Int): TipType?
    suspend fun getAll(): List<TipType>
    suspend fun save(tipType: TipType): TipType
    suspend fun delete(id: Int)
    suspend fun exists(id: Int): Boolean
}