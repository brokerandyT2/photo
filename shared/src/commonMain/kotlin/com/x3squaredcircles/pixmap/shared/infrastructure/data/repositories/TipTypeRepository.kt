//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/data/repositories/TipTypeRepository.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.data.repositories

import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ITipTypeRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILoggingService
import com.x3squaredcircles.pixmap.shared.domain.entities.TipType
import com.x3squaredcircles.pixmap.shared.infrastructure.data.IDatabaseContext
import com.x3squaredcircles.pixmap.shared.infrastructure.data.entities.TipTypeEntity
import com.x3squaredcircles.pixmap.shared.infrastructure.services.IInfrastructureExceptionMappingService
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Repository implementation for tip type management
 */
class TipTypeRepository(
    private val context: IDatabaseContext,
    private val logger: ILoggingService,
    private val exceptionMapper: IInfrastructureExceptionMappingService
) : ITipTypeRepository {

    override suspend fun getByIdAsync(id: Int): Result<TipType?> {
        return try {
            logger.logInfo("Getting tip type by ID: $id")

            val entities = context.queryAsync<TipTypeEntity>(
                "SELECT * FROM TipTypeEntity WHERE id = ? LIMIT 1",
                ::mapCursorToTipTypeEntity,
                id
            )

            val result = entities.firstOrNull()?.let { mapEntityToDomain(it) }
            logger.logInfo("Found tip type: ${result?.id ?: "none"}")
            Result.success(result)
        } catch (ex: Exception) {
            logger.logError("Failed to get tip type by ID: $id", ex)
            Result.failure(exceptionMapper.mapToTipTypeDomainException(ex, "GetById").message ?: "TipType operation failed")
        }
    }

    override suspend fun getAllAsync(): Result<List<TipType>> {
        return try {
            logger.logInfo("Getting all tip types")

            val entities = context.queryAsync<TipTypeEntity>(
                "SELECT * FROM TipTypeEntity ORDER BY name",
                ::mapCursorToTipTypeEntity
            )

            val result = entities.map { mapEntityToDomain(it) }
            logger.logInfo("Found ${result.size} tip types")
            Result.success(result)
        } catch (ex: Exception) {
            logger.logError("Failed to get all tip types", ex)
            Result.failure(exceptionMapper.mapToTipTypeDomainException(ex, "GetAll").message ?: "TipType operation failed")
        }
    }

    override suspend fun addAsync(tipType: TipType): Result<TipType> {
        return try {
            logger.logInfo("Creating tip type: ${tipType.name}")

            val entity = mapDomainToEntity(tipType)
            val id = context.insertAsync(entity)

            val createdTipType = createTipTypeWithId(tipType, id.toInt())
            logger.logInfo("Successfully created tip type with ID: $id")
            Result.success(createdTipType)
        } catch (ex: Exception) {
            logger.logError("Failed to create tip type: ${tipType.name}", ex)
            Result.failure(exceptionMapper.mapToTipTypeDomainException(ex, "Add").message ?: "TipType operation failed")
        }
    }

    override suspend fun updateAsync(tipType: TipType): Result<Unit> {
        return try {
            logger.logInfo("Updating tip type: ${tipType.id}")

            val entity = mapDomainToEntity(tipType)
            val rowsAffected = context.executeAsync(
                """UPDATE TipTypeEntity 
                   SET name = ?, i8n = ?
                   WHERE id = ?""",
                entity.name,
                entity.i8n,
                entity.id
            )

            if (rowsAffected == 0) {
                return Result.failure("TipType not found")
            }

            logger.logInfo("Successfully updated tip type: ${tipType.id}")
            Result.success(Unit)
        } catch (ex: Exception) {
            logger.logError("Failed to update tip type: ${tipType.id}", ex)
            Result.failure(exceptionMapper.mapToTipTypeDomainException(ex, "Update").message ?: "TipType operation failed")
        }
    }

    override suspend fun deleteAsync(tipType: TipType): Result<Unit> {
        return try {
            logger.logInfo("Deleting tip type: ${tipType.id}")

            val rowsAffected = context.executeAsync(
                "DELETE FROM TipTypeEntity WHERE id = ?",
                tipType.id
            )

            if (rowsAffected == 0) {
                return Result.failure("TipType not found")
            }

            logger.logInfo("Successfully deleted tip type: ${tipType.id}")
            Result.success(Unit)
        } catch (ex: Exception) {
            logger.logError("Failed to delete tip type: ${tipType.id}", ex)
            Result.failure(exceptionMapper.mapToTipTypeDomainException(ex, "Delete").message ?: "TipType operation failed")
        }
    }

    private fun createTipTypeWithId(originalTipType: TipType, id: Int): TipType {
        val newTipType = TipType(
            name = originalTipType.name,
            i8n = originalTipType.i8n
        )
        setIdUsingReflection(newTipType, id)
        return newTipType
    }

    private fun mapEntityToDomain(entity: TipTypeEntity): TipType {
        val tipType = TipType(
            name = entity.name,
            i8n = entity.i8n
        )
        setIdUsingReflection(tipType, entity.id)
        return tipType
    }

    private fun mapDomainToEntity(tipType: TipType): TipTypeEntity {
        return TipTypeEntity(
            id = tipType.id,
            name = tipType.name,
            i8n = tipType.i8n
        )
    }

    private fun mapCursorToTipTypeEntity(cursor: app.cash.sqldelight.db.SqlCursor): TipTypeEntity {
        return TipTypeEntity(
            id = cursor.getLong(0)?.toInt() ?: 0,
            name = cursor.getString(1) ?: "",
            i8n = cursor.getString(2) ?: "en-US"
        )
    }

    private fun setIdUsingReflection(tipType: TipType, id: Int) {
        try {
            val idField = tipType::class.java.getDeclaredField("id")
            idField.isAccessible = true
            idField.setInt(tipType, id)
        } catch (e: Exception) {
            logger.logWarning("Could not set ID via reflection: ${e.message}")
        }
    }
}