//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/data/repositories/TipRepository.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.data.repositories

import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ITipRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILoggingService
import com.x3squaredcircles.pixmap.shared.domain.entities.Tip
import com.x3squaredcircles.pixmap.shared.infrastructure.data.IDatabaseContext
import com.x3squaredcircles.pixmap.shared.infrastructure.data.entities.TipEntity
import com.x3squaredcircles.pixmap.shared.infrastructure.services.IInfrastructureExceptionMappingService
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Repository implementation for tip management
 */
class TipRepository(
    private val context: IDatabaseContext,
    private val logger: ILoggingService,
    private val exceptionMapper: IInfrastructureExceptionMappingService
) : ITipRepository {

    override suspend fun getByIdAsync(id: Int): Result<Tip?> {
        return try {
            logger.logInfo("Getting tip by ID: $id")

            val entities = context.queryAsync<TipEntity>(
                "SELECT * FROM TipEntity WHERE id = ? LIMIT 1",
                ::mapCursorToTipEntity,
                id
            )

            val result = entities.firstOrNull()?.let { mapEntityToDomain(it) }
            logger.logInfo("Found tip: ${result?.id ?: "none"}")
            Result.success(result)
        } catch (ex: Exception) {
            logger.logError("Failed to get tip by ID: $id", ex)
            Result.failure(exceptionMapper.mapToTipDomainException(ex, "GetById").message ?: "Tip operation failed")
        }
    }

    override suspend fun getAllAsync(): Result<List<Tip>> {
        return try {
            logger.logInfo("Getting all tips")

            val entities = context.queryAsync<TipEntity>(
                "SELECT * FROM TipEntity ORDER BY title",
                ::mapCursorToTipEntity
            )

            val result = entities.map { mapEntityToDomain(it) }
            logger.logInfo("Found ${result.size} tips")
            Result.success(result)
        } catch (ex: Exception) {
            logger.logError("Failed to get all tips", ex)
            Result.failure(exceptionMapper.mapToTipDomainException(ex, "GetAll").message ?: "Tip operation failed")
        }
    }

    override suspend fun getByTypeAsync(tipTypeId: Int): Result<List<Tip>> {
        return try {
            logger.logInfo("Getting tips by type: $tipTypeId")

            val entities = context.queryAsync<TipEntity>(
                "SELECT * FROM TipEntity WHERE tipTypeId = ? ORDER BY title",
                ::mapCursorToTipEntity,
                tipTypeId
            )

            val result = entities.map { mapEntityToDomain(it) }
            logger.logInfo("Found ${result.size} tips for type: $tipTypeId")
            Result.success(result)
        } catch (ex: Exception) {
            logger.logError("Failed to get tips by type: $tipTypeId", ex)
            Result.failure(exceptionMapper.mapToTipDomainException(ex, "GetByType").message ?: "Tip operation failed")
        }
    }

    override suspend fun createAsync(tip: Tip): Result<Tip> {
        return try {
            logger.logInfo("Creating tip: ${tip.title}")

            val entity = mapDomainToEntity(tip)
            val id = context.insertAsync(entity)

            val createdTip = createTipWithId(tip, id.toInt())
            logger.logInfo("Successfully created tip with ID: $id")
            Result.success(createdTip)
        } catch (ex: Exception) {
            logger.logError("Failed to create tip: ${tip.title}", ex)
            Result.failure(exceptionMapper.mapToTipDomainException(ex, "Create").message ?: "Tip operation failed")
        }
    }

    override suspend fun updateAsync(tip: Tip): Result<Tip> {
        return try {
            logger.logInfo("Updating tip: ${tip.id}")

            val entity = mapDomainToEntity(tip)
            val rowsAffected = context.executeAsync(
                """UPDATE TipEntity 
                   SET tipTypeId = ?, title = ?, content = ?, fstop = ?, 
                       shutterSpeed = ?, iso = ?, i8n = ?
                   WHERE id = ?""",
                entity.tipTypeId,
                entity.title,
                entity.content,
                entity.fstop ?: "",
                entity.shutterSpeed ?: "",
                entity.iso ?: "",
                entity.i8n ?: "en-US",
                entity.id
            )

            if (rowsAffected == 0) {
                return Result.failure("Tip not found")
            }

            logger.logInfo("Successfully updated tip: ${tip.id}")
            Result.success(tip)
        } catch (ex: Exception) {
            logger.logError("Failed to update tip: ${tip.id}", ex)
            Result.failure(exceptionMapper.mapToTipDomainException(ex, "Update").message ?: "Tip operation failed")
        }
    }

    override suspend fun deleteAsync(id: Int): Result<Boolean> {
        return try {
            logger.logInfo("Deleting tip: $id")

            val rowsAffected = context.executeAsync(
                "DELETE FROM TipEntity WHERE id = ?",
                id
            )

            val deleted = rowsAffected > 0
            logger.logInfo("Tip deletion result for ID $id: $deleted")
            Result.success(deleted)
        } catch (ex: Exception) {
            logger.logError("Failed to delete tip: $id", ex)
            Result.failure(exceptionMapper.mapToTipDomainException(ex, "Delete").message ?: "Tip operation failed")
        }
    }

    override suspend fun getRandomByTypeAsync(tipTypeId: Int): Result<Tip?> {
        return try {
            logger.logInfo("Getting random tip by type: $tipTypeId")

            val entities = context.queryAsync<TipEntity>(
                "SELECT * FROM TipEntity WHERE tipTypeId = ? ORDER BY RANDOM() LIMIT 1",
                ::mapCursorToTipEntity,
                tipTypeId
            )

            val result = entities.firstOrNull()?.let { mapEntityToDomain(it) }
            logger.logInfo("Found random tip: ${result?.id ?: "none"} for type: $tipTypeId")
            Result.success(result)
        } catch (ex: Exception) {
            logger.logError("Failed to get random tip by type: $tipTypeId", ex)
            Result.failure(exceptionMapper.mapToTipDomainException(ex, "GetRandomByType").message ?: "Tip operation failed")
        }
    }

    private fun createTipWithId(originalTip: Tip, id: Int): Tip {
        val newTip = Tip(
            tipTypeId = originalTip.tipTypeId,
            title = originalTip.title,
            content = originalTip.content,
            fstop = originalTip.fstop,
            shutterSpeed = originalTip.shutterSpeed,
            iso = originalTip.iso,
            i8n = originalTip.i8n
        )
        setIdUsingReflection(newTip, originalTip.id)

        return newTip
    }

    private fun mapEntityToDomain(entity: TipEntity): Tip {
        val tip = Tip(
            tipTypeId = entity.tipTypeId,
            title = entity.title,
            content = entity.content,
            fstop = entity.fstop ?: "",
            shutterSpeed = entity.shutterSpeed ?: "",
            iso = entity.iso ?: "",
            i8n = entity.i8n ?: "en-US"
        )
        setIdUsingReflection(tip, entity.id)
        return tip
    }

    private fun mapDomainToEntity(tip: Tip): TipEntity {
        return TipEntity(
            id = tip.id,
            tipTypeId = tip.tipTypeId,
            title = tip.title,
            content = tip.content,
            fstop = tip.fstop.ifBlank { null }?: "",
            shutterSpeed = tip.shutterSpeed.ifBlank { null }?: "",
            iso = tip.iso.ifBlank { null }?: "",
            i8n = tip.i8n.ifBlank { "en-US" }
        )
    }

    private fun mapCursorToTipEntity(cursor: app.cash.sqldelight.db.SqlCursor): TipEntity {
        return TipEntity(
            id = cursor.getLong(0)?.toInt() ?: 0,
            tipTypeId = cursor.getLong(1)?.toInt() ?: 0,
            title = cursor.getString(2) ?: "",
            content = cursor.getString(3) ?: "",
            fstop = cursor.getString(4)?: "",
            shutterSpeed = cursor.getString(5)?: "",
            iso = cursor.getString(6)?: "",
            i8n = cursor.getString(7)?: ""
        )
    }
    private fun setIdUsingReflection(tip: Tip, id: Int) {
        try {
            val idField = tip::class.java.getDeclaredField("id")
            idField.isAccessible = true
            idField.setInt(tip, id)
        } catch (e: Exception) {
            logger.logWarning("Could not set ID via reflection: ${e.message}")
        }
    }
}