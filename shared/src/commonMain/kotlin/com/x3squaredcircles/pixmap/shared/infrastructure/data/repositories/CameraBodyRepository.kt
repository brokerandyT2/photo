//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/data/repositories/CameraBodyRepository.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.data.repositories

import com.x3squaredcircles.pixmap.shared.domain.entities.CameraBody
import com.x3squaredcircles.pixmap.shared.domain.entities.MountType
import com.x3squaredcircles.pixmap.shared.infrastructure.data.IDatabaseContext
import com.x3squaredcircles.pixmap.shared.infrastructure.data.entities.CameraBodyEntity


/**
 * Infrastructure repository implementation for CameraBody operations
 * This is the persistence layer - does not implement application interfaces directly
 */
class CameraBodyRepository(
    private val context: IDatabaseContext,
    private val logger: Logger
) {

    // Query cache for frequently used queries
    private val queryCache = mapOf(
        "GetCameraBodyById" to "SELECT * FROM CameraBodyEntity WHERE Id = ? LIMIT 1",
        "GetAllCameraBodies" to "SELECT * FROM CameraBodyEntity ORDER BY Name",
        "GetCameraBodyByName" to "SELECT * FROM CameraBodyEntity WHERE Name = ? LIMIT 1",
        "GetCameraBodiesByMountType" to "SELECT * FROM CameraBodyEntity WHERE MountType = ? ORDER BY Name",
        "GetCameraBodiesBySensorType" to "SELECT * FROM CameraBodyEntity WHERE SensorType = ? ORDER BY Name",
        "GetCameraBodiesByManufacturer" to "SELECT * FROM CameraBodyEntity WHERE Manufacturer = ? ORDER BY Name",
        "GetUserCreatedCameraBodies" to "SELECT * FROM CameraBodyEntity WHERE IsUserCreated = 1 ORDER BY Name",
        "GetSystemCreatedCameraBodies" to "SELECT * FROM CameraBodyEntity WHERE IsUserCreated = 0 ORDER BY Name",
        "CheckCameraBodyExists" to "SELECT EXISTS(SELECT 1 FROM CameraBodyEntity WHERE Id = ?)",
        "CheckCameraBodyNameExists" to "SELECT EXISTS(SELECT 1 FROM CameraBodyEntity WHERE Name = ?)"
    )

    suspend fun getByIdAsync(id: Int): CameraBody? {
        return try {
            logger.debug("Getting camera body by id: $id")

            val entity = context.getAsync(id) { primaryKey ->
                queryCameraBodyById(primaryKey as Int)
            }
            entity?.let { mapEntityToDomain(it) }
        } catch (ex: Exception) {
            logger.error("Failed to get camera body by id $id", ex)
            throw RuntimeException("Failed to get camera body by id: ${ex.message}", ex)
        }
    }

    suspend fun getAllAsync(): List<CameraBody> {
        return try {
            logger.debug("Getting all camera bodies")

            val entities = context.queryAsync(
                queryCache["GetAllCameraBodies"]!!,
                ::mapCursorToCameraBodyEntity
            )

            entities.map { mapEntityToDomain(it) }
        } catch (ex: Exception) {
            logger.error("Failed to get all camera bodies", ex)
            throw RuntimeException("Failed to get all camera bodies: ${ex.message}", ex)
        }
    }

    suspend fun getByNameAsync(name: String): CameraBody? {
        return try {
            logger.debug("Getting camera body by name: $name")

            val entities = context.queryAsync(
                queryCache["GetCameraBodyByName"]!!,
                ::mapCursorToCameraBodyEntity,
                name
            )

            entities.firstOrNull()?.let { mapEntityToDomain(it) }
        } catch (ex: Exception) {
            logger.error("Failed to get camera body by name $name", ex)
            throw RuntimeException("Failed to get camera body by name: ${ex.message}", ex)
        }
    }

    suspend fun getByMountTypeAsync(mountType: MountType): List<CameraBody> {
        return try {
            logger.debug("Getting camera bodies by mount type: $mountType")

            val entities = context.queryAsync(
                queryCache["GetCameraBodiesByMountType"]!!,
                ::mapCursorToCameraBodyEntity,
                mountType.name
            )

            entities.map { mapEntityToDomain(it) }
        } catch (ex: Exception) {
            logger.error("Failed to get camera bodies by mount type $mountType", ex)
            throw RuntimeException("Failed to get camera bodies by mount type: ${ex.message}", ex)
        }
    }

    suspend fun getBySensorTypeAsync(sensorType: String): List<CameraBody> {
        return try {
            logger.debug("Getting camera bodies by sensor type: $sensorType")

            val entities = context.queryAsync(
                queryCache["GetCameraBodiesBySensorType"]!!,
                ::mapCursorToCameraBodyEntity,
                sensorType
            )

            entities.map { mapEntityToDomain(it) }
        } catch (ex: Exception) {
            logger.error("Failed to get camera bodies by sensor type $sensorType", ex)
            throw RuntimeException("Failed to get camera bodies by sensor type: ${ex.message}", ex)
        }
    }

    suspend fun getByManufacturerAsync(manufacturer: String): List<CameraBody> {
        return try {
            logger.debug("Getting camera bodies by manufacturer: $manufacturer")

            val entities = context.queryAsync(
                queryCache["GetCameraBodiesByManufacturer"]!!,
                ::mapCursorToCameraBodyEntity,
                manufacturer
            )

            entities.map { mapEntityToDomain(it) }
        } catch (ex: Exception) {
            logger.error("Failed to get camera bodies by manufacturer $manufacturer", ex)
            throw RuntimeException("Failed to get camera bodies by manufacturer: ${ex.message}", ex)
        }
    }

    suspend fun getUserCreatedAsync(): List<CameraBody> {
        return try {
            logger.debug("Getting user created camera bodies")

            val entities = context.queryAsync(
                queryCache["GetUserCreatedCameraBodies"]!!,
                ::mapCursorToCameraBodyEntity
            )

            entities.map { mapEntityToDomain(it) }
        } catch (ex: Exception) {
            logger.error("Failed to get user created camera bodies", ex)
            throw RuntimeException("Failed to get user created camera bodies: ${ex.message}", ex)
        }
    }

    suspend fun getSystemCreatedAsync(): List<CameraBody> {
        return try {
            logger.debug("Getting system created camera bodies")

            val entities = context.queryAsync(
                queryCache["GetSystemCreatedCameraBodies"]!!,
                ::mapCursorToCameraBodyEntity
            )

            entities.map { mapEntityToDomain(it) }
        } catch (ex: Exception) {
            logger.error("Failed to get system created camera bodies", ex)
            throw RuntimeException("Failed to get system created camera bodies: ${ex.message}", ex)
        }
    }

    suspend fun addAsync(cameraBody: CameraBody): CameraBody {
        return try {
            logger.info("Creating camera body: ${cameraBody.name}")

            // Check if name already exists
            val nameExists = context.executeScalarAsync<Long>(
                queryCache["CheckCameraBodyNameExists"]!!,
                cameraBody.name
            ) ?: 0 > 0

            if (nameExists) {
                throw IllegalArgumentException("Camera body with name '${cameraBody.name}' already exists")
            }

            val entity = mapDomainToEntity(cameraBody)
            val id = context.insertAsync(entity) { cameraBodyEntity ->
                insertCameraBody(cameraBodyEntity)
            }

            val createdCameraBody = cameraBody.copy(id = id.toInt())
            logger.info("Created camera body with ID: $id")
            createdCameraBody
        } catch (ex: Exception) {
            logger.error("Failed to create camera body ${cameraBody.name}", ex)
            throw RuntimeException("Failed to create camera body: ${ex.message}", ex)
        }
    }

    suspend fun updateAsync(cameraBody: CameraBody): CameraBody {
        return try {
            logger.info("Updating camera body: ${cameraBody.name}")

            val entity = mapDomainToEntity(cameraBody)
            val rowsAffected = context.updateAsync(entity) { cameraBodyEntity ->
                updateCameraBody(cameraBodyEntity)
            }

            if (rowsAffected == 0) {
                throw IllegalArgumentException("Camera body with ID ${cameraBody.id} not found")
            }

            logger.info("Updated camera body with ID: ${cameraBody.id}")
            cameraBody
        } catch (ex: Exception) {
            logger.error("Failed to update camera body ${cameraBody.name}", ex)
            throw RuntimeException("Failed to update camera body: ${ex.message}", ex)
        }
    }

    suspend fun deleteAsync(cameraBody: CameraBody) {
        try {
            logger.info("Deleting camera body with ID: ${cameraBody.id}")

            val entity = mapDomainToEntity(cameraBody)
            context.deleteAsync(entity) { cameraBodyEntity ->
                deleteCameraBody(cameraBodyEntity)
            }

            logger.info("Deleted camera body with ID: ${cameraBody.id}")
        } catch (ex: Exception) {
            logger.error("Failed to delete camera body with ID ${cameraBody.id}", ex)
            throw RuntimeException("Failed to delete camera body: ${ex.message}", ex)
        }
    }

    suspend fun deleteByIdAsync(id: Int): Boolean {
        return try {
            logger.info("Deleting camera body with ID: $id")

            val rowsAffected = context.executeAsync(
                "DELETE FROM CameraBodyEntity WHERE Id = ?",
                id
            )

            val deleted = rowsAffected > 0
            logger.info("Camera body deletion result: $deleted")
            deleted
        } catch (ex: Exception) {
            logger.error("Failed to delete camera body with ID $id", ex)
            throw RuntimeException("Failed to delete camera body: ${ex.message}", ex)
        }
    }

    suspend fun existsByNameAsync(name: String): Boolean {
        return try {
            logger.debug("Checking if camera body exists by name: $name")

            context.executeScalarAsync<Long>(
                queryCache["CheckCameraBodyNameExists"]!!,
                name
            ) ?: 0 > 0
        } catch (ex: Exception) {
            logger.error("Failed to check camera body existence by name $name", ex)
            throw RuntimeException("Failed to check camera body existence: ${ex.message}", ex)
        }
    }

    suspend fun createBulkAsync(cameraBodies: List<CameraBody>): List<CameraBody> {
        return try {
            logger.info("Creating ${cameraBodies.size} camera bodies in bulk")

            context.executeInTransactionAsync {
                val createdCameraBodies = mutableListOf<CameraBody>()

                cameraBodies.chunked(50).forEach { batch ->
                    batch.forEach { cameraBody ->
                        val createdCameraBody = addAsync(cameraBody)
                        createdCameraBodies.add(createdCameraBody)
                    }
                }

                logger.info("Successfully created ${createdCameraBodies.size} camera bodies")
                createdCameraBodies
            }
        } catch (ex: Exception) {
            logger.error("Failed to create camera bodies in bulk", ex)
            throw RuntimeException("Failed to create camera bodies in bulk: ${ex.message}", ex)
        }
    }

    // Private helper methods
    private suspend fun queryCameraBodyById(id: Int): CameraBodyEntity? {
        val entities = context.queryAsync(
            queryCache["GetCameraBodyById"]!!,
            ::mapCursorToCameraBodyEntity,
            id
        )
        return entities.firstOrNull()
    }

    private suspend fun insertCameraBody(entity: CameraBodyEntity): Long {
        return context.executeAsync(
            """INSERT INTO CameraBodyEntity (Name, SensorType, SensorWidth, SensorHeight, MountType, 
               IsUserCreated, Manufacturer, Model, CropFactor)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            entity.name,
            entity.sensorType,
            entity.sensorWidth,
            entity.sensorHeight,
            entity.mountType,
            entity.isUserCreated,
            entity.manufacturer,
            entity.model,
            entity.cropFactor
        ).toLong()
    }

    private suspend fun updateCameraBody(entity: CameraBodyEntity): Int {
        return context.executeAsync(
            """UPDATE CameraBodyEntity 
               SET Name = ?, SensorType = ?, SensorWidth = ?, SensorHeight = ?, 
                   MountType = ?, IsUserCreated = ?, Manufacturer = ?, Model = ?, CropFactor = ?
               WHERE Id = ?""",
            entity.name,
            entity.sensorType,
            entity.sensorWidth,
            entity.sensorHeight,
            entity.mountType,
            entity.isUserCreated,
            entity.manufacturer,
            entity.model,
            entity.cropFactor,
            entity.id
        )
    }

    private suspend fun deleteCameraBody(entity: CameraBodyEntity): Int {
        return context.executeAsync(
            "DELETE FROM CameraBodyEntity WHERE Id = ?",
            entity.id
        )
    }

    // Mapping functions
    private fun mapEntityToDomain(entity: CameraBodyEntity): CameraBody {
        return CameraBody(
            id = entity.id,
            name = entity.name,
            sensorType = entity.sensorType,
            sensorWidth = entity.sensorWidth,
            sensorHeight = entity.sensorHeight,
            mountType = MountType.valueOf(entity.mountType),
            isUserCreated = entity.isUserCreated,
            manufacturer = entity.manufacturer,
            model = entity.model,
            cropFactor = entity.cropFactor
        )
    }

    private fun mapDomainToEntity(cameraBody: CameraBody): CameraBodyEntity {
        return CameraBodyEntity(
            id = cameraBody.id,
            name = cameraBody.name,
            sensorType = cameraBody.sensorType,
            sensorWidth = cameraBody.sensorWidth,
            sensorHeight = cameraBody.sensorHeight,
            mountType = cameraBody.mountType.name,
            isUserCreated = cameraBody.isUserCreated,
            manufacturer = cameraBody.manufacturer,
            model = cameraBody.model,
            cropFactor = cameraBody.cropFactor
        )
    }

    private fun mapCursorToCameraBodyEntity(cursor: SqlCursor): CameraBodyEntity {
        return CameraBodyEntity(
            id = cursor.getInt(0) ?: 0,
            name = cursor.getString(1) ?: "",
            sensorType = cursor.getString(2) ?: "",
            sensorWidth = cursor.getDouble(3) ?: 0.0,
            sensorHeight = cursor.getDouble(4) ?: 0.0,
            mountType = cursor.getString(5) ?: MountType.Other.name,
            isUserCreated = cursor.getBoolean(6) ?: false,
            manufacturer = cursor.getString(7) ?: "",
            model = cursor.getString(8) ?: "",
            cropFactor = cursor.getDouble(9) ?: 1.0
        )
    }
}

// SqlCursor interface for database queries
interface SqlCursor {
    fun getString(index: Int): String?
    fun getLong(index: Int): Long?
    fun getDouble(index: Int): Double?
    fun getBoolean(index: Int): Boolean?
    fun getInt(index: Int): Int?
}Body: CameraBody): CameraBodyEntity {
    return CameraBodyEntity(
        id = cameraBody.id,
        name = cameraBody.name,
        sensorType = cameraBody.sensorType,
        sensorWidth = cameraBody.sensorWidth,
        sensorHeight = cameraBody.sensorHeight,
        mountType = cameraBody.mountType.name,
        isUserCreated = cameraBody.isUserCreated,
        manufacturer = cameraBody.manufacturer,
        model = cameraBody.model,
        cropFactor = cameraBody.cropFactor
    )
}

private fun mapCursorToCameraBodyEntity(cursor: SqlCursor): CameraBodyEntity {
    return CameraBodyEntity(
        id = cursor.getInt(0) ?: 0,
        name = cursor.getString(1) ?: "",
        sensorType = cursor.getString(2) ?: "",
        sensorWidth = cursor.getDouble(3) ?: 0.0,
        sensorHeight = cursor.getDouble(4) ?: 0.0,
        mountType = cursor.getString(5) ?: MountType.Other.name,
        isUserCreated = cursor.getBoolean(6) ?: false,
        manufacturer = cursor.getString(7) ?: "",
        model = cursor.getString(8) ?: "",
        cropFactor = cursor.getDouble(9) ?: 1.0
    )
}
}