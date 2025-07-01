// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/data/repositories/TipRepository.kt

import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.domain.entities.Tip
import com.x3squaredcircles.pixmap.shared.infrastructure.data.IDatabaseContext
import com.x3squaredcircles.pixmap.shared.infrastructure.data.entities.TipEntity
import com.x3squaredcircles.pixmap.shared.infrastructure.services.IInfrastructureExceptionMappingService
import kotlinx.coroutines.logging.Logger
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class TipRepository(
    private val context: IDatabaseContext,
    private val logger: Logger,
    private val exceptionMapper: IInfrastructureExceptionMappingService
) {

    // Query cache for frequently used queries
    private val queryCache = mapOf(
        "GetTipById" to "SELECT * FROM TipEntity WHERE Id = ? LIMIT 1",
        "GetAllTips" to "SELECT * FROM TipEntity ORDER BY Title",
        "GetTipsByTipType" to "SELECT * FROM TipEntity WHERE TipTypeId = ? ORDER BY Title",
        "GetTipByTitle" to "SELECT * FROM TipEntity WHERE Title = ? LIMIT 1",
        "GetRandomTipByType" to "SELECT * FROM TipEntity WHERE TipTypeId = ? ORDER BY RANDOM() LIMIT 1",
        "CheckTipExists" to "SELECT EXISTS(SELECT 1 FROM TipEntity WHERE Id = ?)",
        "GetTipsByFstop" to "SELECT * FROM TipEntity WHERE Fstop = ? ORDER BY Title",
        "GetTipsByIso" to "SELECT * FROM TipEntity WHERE Iso = ? ORDER BY Title"
    )

    suspend fun getByIdAsync(id: Int): Tip? {
        return try {
            val entity = context.getAsync(id) { primaryKey ->
                queryTipById(primaryKey as Int)
            }
            entity?.let { mapEntityToDomain(it) }
        } catch (ex: Exception) {
            logger.error("Failed to get tip by id $id", ex)
            throw exceptionMapper.mapToTipDomainException(ex, "GetById")
        }
    }

    suspend fun getAllAsync(): List<Tip> {
        return try {
            val entities = context.queryAsync(
                queryCache["GetAllTips"]!!,
                ::mapCursorToTipEntity
            )
            entities.map { mapEntityToDomain(it) }
        } catch (ex: Exception) {
            logger.error("Failed to get all tips", ex)
            throw exceptionMapper.mapToTipDomainException(ex, "GetAll")
        }
    }

    suspend fun getByTipTypeIdAsync(tipTypeId: Int): List<Tip> {
        return try {
            val entities = context.queryAsync(
                queryCache["GetTipsByTipType"]!!,
                ::mapCursorToTipEntity,
                tipTypeId
            )
            entities.map { mapEntityToDomain(it) }
        } catch (ex: Exception) {
            logger.error("Failed to get tips by tip type id $tipTypeId", ex)
            throw exceptionMapper.mapToTipDomainException(ex, "GetByTipTypeId")
        }
    }

    suspend fun addAsync(tip: Tip): Tip {
        return try {
            val entity = mapDomainToEntity(tip)
            val id = context.insertAsync(entity) { tipEntity ->
                insertTip(tipEntity)
            }

            val createdTip = tip.copy(id = id, timestamp = entity.timestamp)
            logger.info("Created tip with ID $id")
            createdTip
        } catch (ex: Exception) {
            logger.error("Failed to add tip", ex)
            throw exceptionMapper.mapToTipDomainException(ex, "Add")
        }
    }

    suspend fun updateAsync(tip: Tip): Tip {
        return try {
            val entity = mapDomainToEntity(tip)
            val rowsAffected = context.updateAsync(entity) { tipEntity ->
                updateTip(tipEntity)
            }

            if (rowsAffected == 0) {
                throw IllegalArgumentException("Tip with ID ${tip.id} not found")
            }

            logger.info("Updated tip with ID ${tip.id}")
            tip
        } catch (ex: Exception) {
            logger.error("Failed to update tip with id ${tip.id}", ex)
            throw exceptionMapper.mapToTipDomainException(ex, "Update")
        }
    }

    suspend fun deleteAsync(tip: Tip) {
        try {
            val entity = mapDomainToEntity(tip)
            context.deleteAsync(entity) { tipEntity ->
                deleteTip(tipEntity)
            }
            logger.info("Deleted tip with ID ${tip.id}")
        } catch (ex: Exception) {
            logger.error("Failed to delete tip with id ${tip.id}", ex)
            throw exceptionMapper.mapToTipDomainException(ex, "Delete")
        }
    }

    suspend fun deleteByIdAsync(id: Int): Boolean {
        return try {
            val rowsAffected = context.executeAsync(
                "DELETE FROM TipEntity WHERE Id = ?",
                id
            )

            if (rowsAffected > 0) {
                logger.info("Deleted tip with ID $id")
            }

            rowsAffected > 0
        } catch (ex: Exception) {
            logger.error("Failed to delete tip with id $id", ex)
            throw exceptionMapper.mapToTipDomainException(ex, "DeleteById")
        }
    }

    suspend fun getByTitleAsync(title: String): Tip? {
        return try {
            val entities = context.queryAsync(
                queryCache["GetTipByTitle"]!!,
                ::mapCursorToTipEntity,
                title
            )
            entities.firstOrNull()?.let { mapEntityToDomain(it) }
        } catch (ex: Exception) {
            logger.error("Failed to get tip by title '$title'", ex)
            throw exceptionMapper.mapToTipDomainException(ex, "GetByTitle")
        }
    }

    suspend fun getRandomByTypeAsync(tipTypeId: Int): Tip? {
        return try {
            val entities = context.queryAsync(
                queryCache["GetRandomTipByType"]!!,
                ::mapCursorToTipEntity,
                tipTypeId
            )
            entities.firstOrNull()?.let { mapEntityToDomain(it) }
        } catch (ex: Exception) {
            logger.error("Failed to get random tip by type $tipTypeId", ex)
            throw exceptionMapper.mapToTipDomainException(ex, "GetRandomByType")
        }
    }

    suspend fun existsAsync(id: Int): Boolean {
        return try {
            val count = context.executeScalarAsync<Long>(
                queryCache["CheckTipExists"]!!,
                id
            ) ?: 0
            count > 0
        } catch (ex: Exception) {
            logger.error("Failed to check if tip exists with id $id", ex)
            throw exceptionMapper.mapToTipDomainException(ex, "Exists")
        }
    }

    suspend fun getByFstopAsync(fstop: String): List<Tip> {
        return try {
            val entities = context.queryAsync(
                queryCache["GetTipsByFstop"]!!,
                ::mapCursorToTipEntity,
                fstop
            )
            entities.map { mapEntityToDomain(it) }
        } catch (ex: Exception) {
            logger.error("Failed to get tips by f-stop '$fstop'", ex)
            throw exceptionMapper.mapToTipDomainException(ex, "GetByFstop")
        }
    }

    suspend fun getByIsoAsync(iso: String): List<Tip> {
        return try {
            val entities = context.queryAsync(
                queryCache["GetTipsByIso"]!!,
                ::mapCursorToTipEntity,
                iso
            )
            entities.map { mapEntityToDomain(it) }
        } catch (ex: Exception) {
            logger.error("Failed to get tips by ISO '$iso'", ex)
            throw exceptionMapper.mapToTipDomainException(ex, "GetByIso")
        }
    }

    suspend fun createBulkAsync(tips: List<Tip>): List<Tip> {
        return try {
            context.executeInTransactionAsync {
                val createdTips = mutableListOf<Tip>()

                tips.chunked(50).forEach { batch ->
                    batch.forEach { tip ->
                        val createdTip = addAsync(tip)
                        createdTips.add(createdTip)
                    }
                }

                logger.info("Bulk created ${tips.size} tips")
                createdTips
            }
        } catch (ex: Exception) {
            logger.error("Failed to bulk create tips", ex)
            throw exceptionMapper.mapToTipDomainException(ex, "CreateBulk")
        }
    }

    suspend fun updateBulkAsync(tips: List<Tip>): Int {
        return try {
            context.executeInTransactionAsync {
                var totalUpdated = 0

                tips.chunked(50).forEach { batch ->
                    batch.forEach { tip ->
                        val entity = mapDomainToEntity(tip)
                        val rowsAffected = updateTip(entity)
                        totalUpdated += rowsAffected
                    }
                }

                logger.info("Bulk updated $totalUpdated tips")
                totalUpdated
            }
        } catch (ex: Exception) {
            logger.error("Failed to bulk update tips", ex)
            throw exceptionMapper.mapToTipDomainException(ex, "UpdateBulk")
        }
    }

    suspend fun deleteBulkAsync(tipIds: List<Int>): Int {
        return try {
            if (tipIds.isEmpty()) return 0

            var totalDeleted = 0
            val batchSize = 100

            tipIds.chunked(batchSize).forEach { batch ->
                val placeholders = batch.joinToString(",") { "?" }
                val sql = "DELETE FROM TipEntity WHERE Id IN ($placeholders)"

                val deleted = context.executeAsync(sql, *batch.toTypedArray())
                totalDeleted += deleted
            }

            logger.info("Bulk deleted $totalDeleted tips")
            totalDeleted
        } catch (ex: Exception) {
            logger.error("Failed to bulk delete tips", ex)
            throw exceptionMapper.mapToTipDomainException(ex, "DeleteBulk")
        }
    }

    suspend fun countByTipTypeAsync(tipTypeId: Int): Int {
        return try {
            val count = context.executeScalarAsync<Long>(
                "SELECT COUNT(*) FROM TipEntity WHERE TipTypeId = ?",
                tipTypeId
            )?.toInt() ?: 0

            count
        } catch (ex: Exception) {
            logger.error("Failed to count tips by tip type $tipTypeId", ex)
            throw exceptionMapper.mapToTipDomainException(ex, "CountByTipType")
        }
    }

    suspend fun searchByContentAsync(searchTerm: String): List<Tip> {
        return try {
            val searchPattern = "%$searchTerm%"
            val entities = context.queryAsync(
                "SELECT * FROM TipEntity WHERE Title LIKE ? OR Content LIKE ? ORDER BY Title",
                ::mapCursorToTipEntity,
                searchPattern,
                searchPattern
            )
            entities.map { mapEntityToDomain(it) }
        } catch (ex: Exception) {
            logger.error("Failed to search tips by content '$searchTerm'", ex)
            throw exceptionMapper.mapToTipDomainException(ex, "SearchByContent")
        }
    }

    // Helper methods for database operations
    private suspend fun queryTipById(id: Int): TipEntity? {
        val entities = context.queryAsync(
            queryCache["GetTipById"]!!,
            ::mapCursorToTipEntity,
            id
        )
        return entities.firstOrNull()
    }

    private suspend fun insertTip(entity: TipEntity): Long {
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
        ).toLong()
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

    private suspend fun deleteTip(entity: TipEntity): Int {
        return context.executeAsync(
            "DELETE FROM TipEntity WHERE Id = ?",
            entity.id
        )
    }

    // Mapping functions
    private fun mapEntityToDomain(entity: TipEntity): Tip {
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

    private fun mapDomainToEntity(tip: Tip): TipEntity {
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

// Tip entity data class
data class TipEntity(
    val id: Int = 0,
    val tipTypeId: Int,
    val title: String,
    val content: String,
    val fstop: String? = null,
    val shutterSpeed: String? = null,
    val iso: String? = null,
    val i8n: String? = null,
    val timestamp: Instant = Clock.System.now()
)

// SqlCursor interface for database queries