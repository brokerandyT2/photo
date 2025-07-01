// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/data/repositories/TipTypeRepository.kt

import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.domain.entities.TipType
import com.x3squaredcircles.pixmap.shared.domain.entities.Tip
import com.x3squaredcircles.pixmap.shared.infrastructure.data.IDatabaseContext
import com.x3squaredcircles.pixmap.shared.infrastructure.data.entities.TipTypeEntity
import com.x3squaredcircles.pixmap.shared.infrastructure.data.entities.TipEntity
import com.x3squaredcircles.pixmap.shared.infrastructure.services.IInfrastructureExceptionMappingService
import kotlinx.coroutines.logging.Logger
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class TipTypeRepository(
    private val context: IDatabaseContext,
    private val logger: Logger,
    private val exceptionMapper: IInfrastructureExceptionMappingService
) {

    // Query cache for frequently used queries
    private val queryCache = mapOf(
        "GetTipTypeById" to "SELECT * FROM TipTypeEntity WHERE Id = ? LIMIT 1",
        "GetAllTipTypes" to "SELECT * FROM TipTypeEntity ORDER BY Name",
        "GetTipTypeByName" to "SELECT * FROM TipTypeEntity WHERE Name = ? LIMIT 1",
        "GetTipsByTipType" to "SELECT * FROM TipEntity WHERE TipTypeId = ? ORDER BY Title",
        "CheckTipTypeExists" to "SELECT EXISTS(SELECT 1 FROM TipTypeEntity WHERE Id = ?)",
        "CheckTipTypeNameExists" to "SELECT EXISTS(SELECT 1 FROM TipTypeEntity WHERE Name = ?)",
        "CountTipsByTipType" to "SELECT COUNT(*) FROM TipEntity WHERE TipTypeId = ?"
    )

    suspend fun getByIdAsync(id: Int): TipType? {
        return try {
            val entity = context.getAsync(id) { primaryKey ->
                queryTipTypeById(primaryKey as Int)
            }
            entity?.let { mapEntityToDomain(it) }
        } catch (ex: Exception) {
            logger.error("Failed to get tip type by id $id", ex)
            throw exceptionMapper.mapToTipTypeDomainException(ex, "GetById")
        }
    }

    suspend fun getAllAsync(): List<TipType> {
        return try {
            val entities = context.queryAsync(
                queryCache["GetAllTipTypes"]!!,
                ::mapCursorToTipTypeEntity
            )
            entities.map { mapEntityToDomain(it) }
        } catch (ex: Exception) {
            logger.error("Failed to get all tip types", ex)
            throw exceptionMapper.mapToTipTypeDomainException(ex, "GetAll")
        }
    }

    suspend fun addAsync(tipType: TipType): TipType {
        return try {
            context.executeInTransactionAsync {
                // Check if name already exists
                val nameExists = context.executeScalarAsync<Long>(
                    queryCache["CheckTipTypeNameExists"]!!,
                    tipType.name
                ) ?: 0 > 0

                if (nameExists) {
                    throw IllegalArgumentException("TipType with name '${tipType.name}' already exists")
                }

                // Insert main tip type entity
                val entity = mapDomainToEntity(tipType)
                val tipTypeId = insertTipType(entity)

                // Update tip type with new ID
                val tipTypeWithId = tipType.copy(id = tipTypeId, timestamp = entity.timestamp)

                // Bulk insert associated tips if any
                if (tipType.tips.isNotEmpty()) {
                    val tipEntities = tipType.tips.map { tip ->
                        mapTipDomainToEntity(tip).copy(tipTypeId = tipTypeId)
                    }

                    tipEntities.chunked(50).forEach { batch ->
                        batch.forEach { tipEntity -> insertTip(tipEntity) }
                    }
                }

                logger.info("Created tip type with ID $tipTypeId")
                tipTypeWithId
            }
        } catch (ex: Exception) {
            logger.error("Failed to add tip type", ex)
            throw exceptionMapper.mapToTipTypeDomainException(ex, "Add")
        }
    }

    suspend fun updateAsync(tipType: TipType) {
        try {
            context.executeInTransactionAsync {
                // Update main tip type entity
                val entity = mapDomainToEntity(tipType)
                updateTipType(entity)

                // Handle tip updates efficiently
                updateAssociatedTipsAsync(tipType)

                logger.info("Updated tip type with ID ${tipType.id}")
            }
        } catch (ex: Exception) {
            logger.error("Failed to update tip type with id ${tipType.id}", ex)
            throw exceptionMapper.mapToTipTypeDomainException(ex, "Update")
        }
    }

    suspend fun deleteAsync(tipType: TipType) {
        try {
            context.executeInTransactionAsync {
                // Delete associated tips first (due to foreign key constraints)
                context.executeAsync("DELETE FROM TipEntity WHERE TipTypeId = ?", tipType.id)

                // Delete tip type
                context.executeAsync("DELETE FROM TipTypeEntity WHERE Id = ?", tipType.id)

                logger.info("Deleted tip type with ID ${tipType.id}")
            }
        } catch (ex: Exception) {
            logger.error("Failed to delete tip type with id ${tipType.id}", ex)
            throw exceptionMapper.mapToTipTypeDomainException(ex, "Delete")
        }
    }

    suspend fun deleteByIdAsync(id: Int) {
        try {
            context.executeInTransactionAsync {
                // Delete associated tips first (due to foreign key constraints)
                context.executeAsync("DELETE FROM TipEntity WHERE TipTypeId = ?", id)

                // Delete tip type
                val rowsAffected = context.executeAsync("DELETE FROM TipTypeEntity WHERE Id = ?", id)

                if (rowsAffected > 0) {
                    logger.info("Deleted tip type with ID $id")
                }
            }
        } catch (ex: Exception) {
            logger.error("Failed to delete tip type with id $id", ex)
            throw exceptionMapper.mapToTipTypeDomainException(ex, "DeleteById")
        }
    }

    suspend fun getByNameAsync(name: String): TipType? {
        return try {
            val entities = context.queryAsync(
                queryCache["GetTipTypeByName"]!!,
                ::mapCursorToTipTypeEntity,
                name
            )
            entities.firstOrNull()?.let { mapEntityToDomain(it) }
        } catch (ex: Exception) {
            logger.error("Failed to get tip type by name '$name'", ex)
            throw exceptionMapper.mapToTipTypeDomainException(ex, "GetByName")
        }
    }

    suspend fun getWithTipsAsync(id: Int): TipType? {
        return try {
            // Get tip type entity first
            val tipTypeEntity = context.getAsync(id) { primaryKey ->
                queryTipTypeById(primaryKey as Int)
            } ?: return null

            // Get associated tips
            val tipEntities = context.queryAsync(
                queryCache["GetTipsByTipType"]!!,
                ::mapCursorToTipEntity,
                id
            )

            val tipType = mapEntityToDomain(tipTypeEntity)
            val tips = tipEntities.map { mapTipEntityToDomain(it) }

            tipType.copy(tips = tips)
        } catch (ex: Exception) {
            logger.error("Failed to get tip type with tips for id $id", ex)
            throw exceptionMapper.mapToTipTypeDomainException(ex, "GetWithTips")
        }
    }

    suspend fun getAllWithTipsAsync(): List<TipType> {
        return try {
            // Get all tip types and all tips concurrently
            val tipTypeEntities = context.queryAsync(
                queryCache["GetAllTipTypes"]!!,
                ::mapCursorToTipTypeEntity
            )

            val allTipEntities = context.queryAsync(
                "SELECT * FROM TipEntity ORDER BY TipTypeId, Title",
                ::mapCursorToTipEntity
            )

            // Group tips by tip type ID
            val tipsByTipType = allTipEntities.groupBy { it.tipTypeId }

            tipTypeEntities.map { tipTypeEntity ->
                val tipType = mapEntityToDomain(tipTypeEntity)
                val tips = tipsByTipType[tipTypeEntity.id]?.map { mapTipEntityToDomain(it) } ?: emptyList()
                tipType.copy(tips = tips)
            }
        } catch (ex: Exception) {
            logger.error("Failed to get all tip types with tips", ex)
            throw exceptionMapper.mapToTipTypeDomainException(ex, "GetAllWithTips")
        }
    }

    suspend fun existsAsync(id: Int): Boolean {
        return try {
            val count = context.executeScalarAsync<Long>(
                queryCache["CheckTipTypeExists"]!!,
                id
            ) ?: 0
            count > 0
        } catch (ex: Exception) {
            logger.error("Failed to check if tip type exists with id $id", ex)
            throw exceptionMapper.mapToTipTypeDomainException(ex, "Exists")
        }
    }

    suspend fun nameExistsAsync(name: String, excludeId: Int? = null): Boolean {
        return try {
            val sql = if (excludeId != null) {
                "SELECT EXISTS(SELECT 1 FROM TipTypeEntity WHERE Name = ? AND Id != ?)"
            } else {
                queryCache["CheckTipTypeNameExists"]!!
            }

            val parameters = if (excludeId != null) arrayOf(name, excludeId) else arrayOf(name)

            val count = context.executeScalarAsync<Long>(sql, *parameters) ?: 0
            count > 0
        } catch (ex: Exception) {
            logger.error("Failed to check if tip type name exists: '$name'", ex)
            throw exceptionMapper.mapToTipTypeDomainException(ex, "NameExists")
        }
    }

    suspend fun countTipsAsync(tipTypeId: Int): Int {
        return try {
            val count = context.executeScalarAsync<Long>(
                queryCache["CountTipsByTipType"]!!,
                tipTypeId
            )?.toInt() ?: 0

            count
        } catch (ex: Exception) {
            logger.error("Failed to count tips for tip type $tipTypeId", ex)
            throw exceptionMapper.mapToTipTypeDomainException(ex, "CountTips")
        }
    }

    suspend fun createBulkAsync(tipTypes: List<TipType>): List<TipType> {
        return try {
            context.executeInTransactionAsync {
                val createdTipTypes = mutableListOf<TipType>()

                tipTypes.forEach { tipType ->
                    val createdTipType = addAsync(tipType)
                    createdTipTypes.add(createdTipType)
                }

                logger.info("Bulk created ${tipTypes.size} tip types")
                createdTipTypes
            }
        } catch (ex: Exception) {
            logger.error("Failed to bulk create tip types", ex)
            throw exceptionMapper.mapToTipTypeDomainException(ex, "CreateBulk")
        }
    }

    suspend fun updateBulkAsync(tipTypes: List<TipType>): Int {
        return try {
            context.executeInTransactionAsync {
                var totalUpdated = 0

                tipTypes.forEach { tipType ->
                    updateAsync(tipType)
                    totalUpdated++
                }

                logger.info("Bulk updated $totalUpdated tip types")
                totalUpdated
            }
        } catch (ex: Exception) {
            logger.error("Failed to bulk update tip types", ex)
            throw exceptionMapper.mapToTipTypeDomainException(ex, "UpdateBulk")
        }
    }

    // Helper methods for database operations
    private suspend fun queryTipTypeById(id: Int): TipTypeEntity? {
        val entities = context.queryAsync(
            queryCache["GetTipTypeById"]!!,
            ::mapCursorToTipTypeEntity,
            id
        )
        return entities.firstOrNull()
    }

    private suspend fun insertTipType(entity: TipTypeEntity): Int {
        return context.executeAsync(
            """INSERT INTO TipTypeEntity (Name, Description, I8n, Timestamp)
               VALUES (?, ?, ?, ?)""",
            entity.name,
            entity.description ?: "",
            entity.i8n ?: "",
            entity.timestamp.toString()
        )
    }

    private suspend fun updateTipType(entity: TipTypeEntity): Int {
        return context.executeAsync(
            """UPDATE TipTypeEntity 
               SET Name = ?, Description = ?, I8n = ?, Timestamp = ?
               WHERE Id = ?""",
            entity.name,
            entity.description ?: "",
            entity.i8n ?: "",
            entity.timestamp.toString(),
            entity.id
        )
    }

    private suspend fun insertTip(entity: TipEntity): Int {
        return context.executeAsync(
            """INSERT INTO TipEntity (TipTypeId, Title, Content, Fstop, ShutterSpeed, Iso, I8n, Timestamp)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
            entity.tipTypeId,
            entity.title,
            entity.content,
            entity.fstop ?: "",
            entity.shutterSpeed ?: "",
            entity.iso ?: "",
            entity.i8n ?: "",
            entity.timestamp.toString()
        )
    }

    private suspend fun updateAssociatedTipsAsync(tipType: TipType) {
        // Get existing tip IDs for this tip type
        val existingTipIds = context.queryAsync(
            "SELECT Id FROM TipEntity WHERE TipTypeId = ?",
            { cursor -> cursor.getInt(0) ?: 0 },
            tipType.id
        ).toSet()

        val currentTipIds = tipType.tips.filter { it.id > 0 }.map { it.id }.toSet()

        // Delete tips that are no longer in the collection
        val tipsToDelete = existingTipIds - currentTipIds
        if (tipsToDelete.isNotEmpty()) {
            val placeholders = tipsToDelete.joinToString(",") { "?" }
            context.executeAsync(
                "DELETE FROM TipEntity WHERE Id IN ($placeholders)",
                *tipsToDelete.toTypedArray()
            )
        }

        // Insert or update tips
        tipType.tips.forEach { tip ->
            val tipEntity = mapTipDomainToEntity(tip).copy(tipTypeId = tipType.id)

            if (tip.id > 0 && tip.id in existingTipIds) {
                // Update existing tip
                updateTip(tipEntity)
            } else {
                // Insert new tip
                insertTip(tipEntity)
            }
        }
    }

    private suspend fun updateTip(entity: TipEntity): Int {
        return context.executeAsync(
            """UPDATE TipEntity 
               SET TipTypeId = ?, Title = ?, Content = ?, Fstop = ?, ShutterSpeed = ?, Iso = ?, I8n = ?, Timestamp = ?
               WHERE Id = ?""",
            entity.tipTypeId,
            entity.title,
            entity.content,
            entity.fstop ?: "",
            entity.shutterSpeed ?: "",
            entity.iso ?: "",
            entity.i8n ?: "",
            entity.timestamp.toString(),
            entity.id
        )
    }

    // Mapping functions
    private fun mapEntityToDomain(entity: TipTypeEntity): TipType {
        return TipType(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            i8n = entity.i8n,
            timestamp = entity.timestamp,
            tips = emptyList() // Tips loaded separately when needed
        )
    }

    private fun mapDomainToEntity(tipType: TipType): TipTypeEntity {
        return TipTypeEntity(
            id = tipType.id,
            name = tipType.name,
            description = tipType.description,
            i8n = tipType.i8n,
            timestamp = tipType.timestamp
        )
    }

    private fun mapTipEntityToDomain(entity: TipEntity): Tip {
        return Tip(
            id = entity.id,
            tipTypeId = entity.tipTypeId,
            title = entity.title,
            content = entity.content,
            fstop = entity.fstop,
            shutterSpeed = entity.shutterSpeed,
            iso = entity.iso,
            i8n = entity.i8n,
            timestamp = entity.timestamp
        )
    }

    private fun mapTipDomainToEntity(tip: Tip): TipEntity {
        return TipEntity(
            id = tip.id,
            tipTypeId = tip.tipTypeId,
            title = tip.title,
            content = tip.content,
            fstop = tip.fstop,
            shutterSpeed = tip.shutterSpeed,
            iso = tip.iso,
            i8n = tip.i8n,
            timestamp = tip.timestamp
        )
    }

    private fun mapCursorToTipTypeEntity(cursor: SqlCursor): TipTypeEntity {
        return TipTypeEntity(
            id = cursor.getInt(0) ?: 0,
            name = cursor.getString(1) ?: "",
            description = cursor.getString(2),
            i8n = cursor.getString(3),
            timestamp = Instant.parse(cursor.getString(4) ?: Clock.System.now().toString())
        )
    }

    private fun mapCursorToTipEntity(cursor: SqlCursor): TipEntity {
        return TipEntity(
            id = cursor.getInt(0) ?: 0,
            tipTypeId = cursor.getInt(1) ?: 0,
            title = cursor.getString(2) ?: "",
            content = cursor.getString(3) ?: "",
            fstop = cursor.getString(4),
            shutterSpeed = cursor.getString(5),
            iso = cursor.getString(6),
            i8n = cursor.getString(7),
            timestamp = Instant.parse(cursor.getString(8) ?: Clock.System.now().toString())
        )
    }
}

// TipType entity data class
data class TipTypeEntity(
    val id: Int = 0,
    val name: String,
    val description: String? = null,
    val i8n: String? = null,
    val timestamp: Instant = Clock.System.now()
)


