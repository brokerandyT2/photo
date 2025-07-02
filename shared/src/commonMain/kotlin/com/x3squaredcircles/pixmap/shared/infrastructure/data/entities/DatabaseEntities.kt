//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/data/entities/DatabaseEntities.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.data.entities

import kotlinx.serialization.Serializable

/**
 * Database entity for Location table
 */
@Serializable
data class LocationEntity(
    val id: Int = 0,
    val title: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val city: String,
    val state: String,
    val photoPath: String? = null,
    val isDeleted: Boolean = false,
    var timestamp: Long = 0
)

/**
 * Database entity for Weather table
 */
@Serializable
data class WeatherEntity(
    val id: Int = 0,
    val locationId: Int,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val timezoneOffset: Int,
    val lastUpdate: Long,
    var timestamp: Long = 0
)

/**
 * Database entity for WeatherForecast table
 */
@Serializable
data class WeatherForecastEntity(
    val id: Int = 0,
    val weatherId: Int,
    val date: Long,
    val sunrise: Long,
    val sunset: Long,
    val temperature: Double,
    val minTemperature: Double,
    val maxTemperature: Double,
    val description: String,
    val icon: String,
    val windSpeed: Double,
    val windDirection: Double,
    val windGust: Double? = null,
    val humidity: Int,
    val pressure: Int,
    val clouds: Int,
    val uvIndex: Double,
    val precipitation: Double? = null,
    val moonRise: Long? = null,
    val moonSet: Long? = null,
    val moonPhase: Double,
    var timestamp: Long = 0
)

/**
 * Database entity for HourlyForecast table
 */
@Serializable
data class HourlyForecastEntity(
    val id: Int = 0,
    val weatherId: Int,
    val dateTime: Long,
    val temperature: Double,
    val feelsLike: Double,
    val description: String,
    val icon: String,
    val windSpeed: Double,
    val windDirection: Double,
    val windGust: Double? = null,
    val humidity: Int,
    val pressure: Int,
    val clouds: Int,
    val uvIndex: Double,
    val probabilityOfPrecipitation: Double,
    val visibility: Int,
    val dewPoint: Double,
    var timestamp: Long = 0
)

/**
 * Database entity for TipType table
 */
@Serializable
data class TipTypeEntity(
    val id: Int = 0,
    val name: String,
    val i8n: String = "en-US"
)

/**
 * Database entity for Tip table
 */
@Serializable
data class TipEntity(
    val id: Int = 0,
    val tipTypeId: Int,
    val title: String,
    val content: String,
    val fstop: String,
    val shutterSpeed: String,
    val iso: String,
    val i8n: String = "en-US"
)

/**
 * Database entity for Setting table
 */
@Serializable
data class SettingEntity(
    val id: Int = 0,
    val key: String,
    val value: String,
    val description: String,
    var timestamp: Long = 0
)

/**
 * Database entity for Log table
 */
@Serializable
data class LogEntity(
    val id: Int = 0,
    val timestamp: Long,
    val level: String,
    val message: String,
    val exception: String
)

/**
 * Database entity for Subscription table
 */
@Serializable
data class SubscriptionEntity(
    val id: Int = 0,
    val userId: String,
    val productId: String,
    val transactionId: String,
    val purchaseToken: String,
    val status: String,
    val startDate: Long,
    val expirationDate: Long,
    val autoRenewing: Boolean,
    val lastVerified: Long? = null,
    val cancelledAt: Long? = null,
    val renewalCount: Int = 0,
    var timestamp: Long = 0
)

/**
 * Database entity for CameraBodies table
 */
@Serializable
data class CameraBodyEntity(
    val id: Int = 0,
    val name: String,
    val sensorType: String,
    val sensorWidth: Double,
    val sensorHeight: Double,
    val mountType: String,
    val isUserCreated: Boolean = false,
    val manufacturer: String = "",
    val model: String = "",
    val cropFactor: Double = 1.0
)

/**
 * Database entity for Lenses table
 */
@Serializable
data class LensEntity(
    val id: Int = 0,
    val name: String,
    val minMM: Int,
    val maxMM: Int,
    val maxAperture: Double,
    val mountType: String,
    val lensType: String,
    val isUserCreated: Boolean = false,
    val manufacturer: String = "",
    val model: String = ""
)

/**
 * Database entity for LensCameraCompatibility table
 */
@Serializable
data class LensCameraCompatibilityEntity(
    val id: Int = 0,
    val lensId: Int,
    val cameraBodyId: Int,
    val isNative: Boolean = true,
    val requiresAdapter: Boolean = false,
    val adapterName: String? = null,
    val cropFactorMultiplier: Double = 1.0,
    val autofocusSupported: Boolean = true,
    val imageStabilizationSupported: Boolean = true,
    val notes: String? = null
)

/**
 * Database entity for UserCameraBodies table
 */
@Serializable
data class UserCameraBodyEntity(
    val id: Int = 0,
    val userId: String,
    val cameraBodyId: Int,
    val isFavorite: Boolean = false,
    val nickname: String? = null,
    val dateSaved: Long,
    val notes: String? = null
)

/**
 * Database metadata classes
 */
@Serializable
data class DatabaseInfo(
    val version: Int,
    val path: String,
    val size: Long,
    val tableCount: Int,
    val lastModified: kotlinx.datetime.Instant
)

@Serializable
data class TableSchema(
    val tableName: String,
    val columns: List<ColumnInfo>,
    val indexes: List<IndexInfo>,
    val constraints: List<ConstraintInfo>
)

@Serializable
data class ColumnInfo(
    val name: String,
    val type: String,
    val nullable: Boolean,
    val defaultValue: String? = null,
    val isPrimaryKey: Boolean = false,
    val isForeignKey: Boolean = false,
    val foreignKeyTable: String? = null,
    val foreignKeyColumn: String? = null
)

@Serializable
data class IndexInfo(
    val name: String,
    val columns: List<String>,
    val isUnique: Boolean = false,
    val isClustered: Boolean = false
)

@Serializable
data class ConstraintInfo(
    val name: String,
    val type: ConstraintType,
    val columns: List<String>,
    val referencedTable: String? = null,
    val referencedColumns: List<String> = emptyList()
)

@Serializable
enum class ConstraintType {
    PRIMARY_KEY,
    FOREIGN_KEY,
    UNIQUE,
    CHECK,
    NOT_NULL
}

/**
 * Database change notification
 */
@Serializable
data class DatabaseChange(
    val tableName: String,
    val operation: DatabaseOperation,
    val recordId: Any? = null,
    val timestamp: kotlinx.datetime.Instant = kotlinx.datetime.Clock.System.now()
)

@Serializable
enum class DatabaseOperation {
    INSERT,
    UPDATE,
    DELETE,
    BULK_INSERT,
    BULK_UPDATE,
    BULK_DELETE
}

/**
 * Bulk operation result
 */
@Serializable
data class BulkOperationResult(
    val totalRecords: Int,
    val successfulRecords: Int,
    val failedRecords: Int,
    val errors: List<String> = emptyList(),
    val executionTimeMs: Long
)

/**
 * Query execution statistics
 */
@Serializable
data class QueryStats(
    val sql: String,
    val executionTimeMs: Long,
    val recordCount: Int,
    val cacheHit: Boolean = false,
    val timestamp: kotlinx.datetime.Instant = kotlinx.datetime.Clock.System.now()
)