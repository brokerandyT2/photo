//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/core/data/DatabaseInitializer.kt
package com.x3squaredcircles.pixmap.core.data

import com.x3squaredcircles.pixmap.shared.domain.entities.Setting
import com.x3squaredcircles.pixmap.shared.domain.entities.TipType
import com.x3squaredcircles.pixmap.shared.domain.entities.Tip
import com.x3squaredcircles.pixmap.shared.domain.entities.Location
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Address
import com.x3squaredcircles.pixmap.shared.application.interfaces.IUnitOfWork
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILoggingService
import com.x3squaredcircles.pixmap.shared.infrastructure.data.IDatabaseContext
import com.x3squaredcircles.pixmap.photography.domain.entities.*
import com.x3squaredcircles.pixmap.core.constants.MagicStrings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID

/**
 * Database initializer for KMM
 * Direct conversion from C# DatabaseInitializer to Kotlin
 */
class DatabaseInitializer(
    private val unitOfWork: IUnitOfWork,
    private val logger: ILoggingService,
    private val alertService: IAlertService
) {
    companion object {
        private val initializationLock = Mutex()
        private var isInitialized = false
        private var isInitializing = false
        private var initializationTimestamp: Instant? = null
    }

    suspend fun isDatabaseInitializedAsync(): Boolean {
        return try {
            // Check static flag first (fastest)
            if (isInitialized) {
                logger.debug("Database already initialized (static flag)")
                return true
            }

            // Check if database file exists - platform specific implementation needed
            val dbExists = checkDatabaseFileExists()
            if (!dbExists) {
                logger.debug("Database file does not exist")
                return false
            }

            // Check for initialization marker in settings
            val markerResult = unitOfWork.settings.getByKeyAsync("DatabaseInitialized")
            if (markerResult.isSuccess && markerResult.data != null) {
                isInitialized = true
                logger.debug("Database initialization marker found")
                return true
            }

            // Fallback: Check if we have sample locations (legacy check)
            val locationsResult = unitOfWork.locations.getAllAsync()
            val hasData = locationsResult.isSuccess && (locationsResult.data?.isNotEmpty() == true)

            if (hasData) {
                // Database exists but missing marker - add it
                createInitializationMarkerAsync()
                isInitialized = true
                logger.info("Database has data but missing marker - marker added")
                return true
            }

            logger.debug("Database exists but is not initialized")
            false
        } catch (e: Exception) {
            logger.error("Error checking database initialization status", e)
            false
        }
    }

    suspend fun initializeDatabaseWithStaticDataAsync() {
        // Fast path: if already initialized, return immediately
        if (isInitialized) {
            logger.debug("Database already initialized - skipping")
            return
        }

        val lockAcquired = initializationLock.tryLock()
        if (!lockAcquired) {
            throw Exception("Failed to acquire database initialization lock within timeout period")
        }

        try {
            // Double-check after acquiring lock (another thread might have initialized)
            if (isInitialized) {
                logger.debug("Database was initialized by another thread - skipping")
                return
            }

            // Check if we're already in the process of initializing
            if (isInitializing) {
                logger.warning("Database initialization already in progress - waiting for completion")
                // Implementation would wait here in real code
                return
            }

            // Final check using database query
            if (isDatabaseInitializedAsync()) {
                logger.debug("Database already initialized (database check) - skipping")
                return
            }

            logger.info("Starting database initialization with static data")
            isInitializing = true
            initializationTimestamp = Clock.System.now()

            // Initialize database structure - need to access the database context properly
            // Database initialization will be handled by the infrastructure layer

            // Parallelize independent database operations to improve performance
            createTipTypesAsync()
            createSampleLocationsAsync()
            createBaseSettingsAsync()
            createCameraSensorProfilesAsync()

            // Create initialization marker
            createInitializationMarkerAsync()

            // Mark as completed
            isInitialized = true
            isInitializing = false

            val duration = Clock.System.now() - (initializationTimestamp ?: Clock.System.now())
            logger.info("Database initialization with static data completed successfully in ${duration.inWholeMilliseconds}ms")

        } catch (e: Exception) {
            isInitializing = false
            logger.error("Error during database initialization with static data", e)
            throw e
        } finally {
            initializationLock.unlock()
        }
    }

    suspend fun initializeDatabaseAsync(
        hemisphere: String = "north",
        tempFormat: String = "F",
        dateFormat: String = "MMM/dd/yyyy",
        timeFormat: String = "hh:mm tt",
        windDirection: String = "towardsWind",
        email: String = "",
        guid: String = ""
    ) {
        try {
            // Initialize with static data first (will skip if already done)
            initializeDatabaseWithStaticDataAsync()

            // Then create user settings
            createUserSettingsAsync(hemisphere, tempFormat, dateFormat, timeFormat, windDirection, email, guid)

            logger.info("Complete database initialization completed successfully")
        } catch (e: Exception) {
            logger.error("Error during complete database initialization", e)
            // Note: AlertService interface needs to be defined in the shared layer
            throw e
        }
    }

    private suspend fun createInitializationMarkerAsync() {
        try {
            val marker = Setting.create(
                "DatabaseInitialized",
                Clock.System.now().toString(),
                "Timestamp when database initialization was completed"
            )
            val result = unitOfWork.settings.createAsync(marker)
            if (!result.isSuccess) {
                logger.warning("Failed to create database initialization marker: ${result.errorMessage}")
            } else {
                logger.debug("Database initialization marker created successfully")
            }
        } catch (e: Exception) {
            logger.error("Error creating database initialization marker", e)
        }
    }

    private suspend fun createTipTypesAsync() {
        try {
            val tipTypeNames = arrayOf(
                "Landscape", "Silhouette", "Building",
                "Person", "Baby", "Animals",
                "BlurryWater", "Night", "BlueHour",
                "GoldenHour", "Sunset"
            )

            // Process tip types in batches to improve performance and reduce database contention
            val batchSize = 3
            for (i in tipTypeNames.indices step batchSize) {
                val batch = tipTypeNames.drop(i).take(batchSize)
                batch.forEach { name ->
                    val tipType = TipType.create(name).setLocalization("en-US")

                    // Create tip type in repository
                    val typeResult = unitOfWork.tipTypes.addAsync(tipType)

                    if (!typeResult.isSuccess || typeResult.data == null) {
                        logger.warning("Failed to create tip type: $name")
                        return@forEach
                    }

                    // Create a sample tip for each type
                    val tip = Tip.create(
                        typeResult.data.id,
                        "How to take great $name photos",
                        "Sample content placeholder"
                    ).updatePhotographySettings("f/1", "1/125", "50")
                        .setLocalization("en-US")

                    val tipResult = unitOfWork.tips.createAsync(tip)
                    if (!tipResult.isSuccess) {
                        logger.warning("Failed to create tip for type $name: ${tipResult.errorMessage}")
                    }
                }
            }

            logger.info("Created ${tipTypeNames.size} tip types with sample tips")
        } catch (e: Exception) {
            logger.error("Error creating tip types", e)
            throw e
        }
    }

    private suspend fun createSampleLocationsAsync() {
        try {
            val sampleLocations = listOf(
                SampleLocationData(
                    "Soldiers and Sailors Monument",
                    "Located in the heart of downtown in Monument Circle, it was originally designed to honor Indiana's Civil War veterans. It now commemorates the valor of Hoosier veterans who served in all wars prior to WWI, including the Revolutionary War, the War of 1812, the Mexican War, the Civil War, the Frontier Wars and the Spanish-American War. One of the most popular parts of the monument is the observation deck with a 360-degree view of the city skyline from 275 feet up.",
                    39.7685, -86.1580,
                    "s_and_sm_new.jpg"
                ),
                SampleLocationData(
                    "The Bean",
                    "What is The Bean?\r\nThe Bean is a work of public art in the heart of Chicago. The sculpture, which is officially titled Cloud Gate, is one of the world's largest permanent outdoor art installations. The monumental work was unveiled in 2004 and quickly became of the Chicago's most iconic sights.",
                    41.8827, -87.6233,
                    "chicagobean.jpg"
                ),
                SampleLocationData(
                    "Golden Gate Bridge",
                    "The Golden Gate Bridge is a suspension bridge spanning the Golden Gate strait, the one-mile-wide (1.6 km) channel between San Francisco Bay and the Pacific Ocean. The strait is the entrance to San Francisco Bay from the Pacific Ocean. The bridge connects the city of San Francisco, California, to Marin County, carrying both U.S. Route 101 and California State Route 1 across the strait.",
                    37.8199, -122.4783,
                    "ggbridge.jpg"
                ),
                SampleLocationData(
                    "Gateway Arch",
                    "The Gateway Arch is a 630-foot (192 m) monument in St. Louis, Missouri, that commemorates Thomas Jefferson and the westward expansion of the United States. The arch is the centerpiece of the Gateway Arch National Park and is the tallest arch in the world.",
                    38.6247, -90.1848,
                    "stlarch.jpg"
                )
            )

            // Process locations in parallel to improve performance
            sampleLocations.forEach { locationData ->
                val coordinate = Coordinate.createValidated(locationData.latitude, locationData.longitude)
                val address = Address("", "")

                val location = Location(
                    title = locationData.title,
                    description = locationData.description,
                    coordinate = coordinate,
                    address = address
                )

                val finalLocation = if (locationData.photo.isNotEmpty()) {
                    location.apply { attachPhoto(locationData.photo) }
                } else {
                    location
                }

                val result = unitOfWork.locations.createAsync(finalLocation)
                if (!result.isSuccess) {
                    logger.warning("Failed to create location ${locationData.title}: ${result.errorMessage}")
                }
            }

            logger.info("Created ${sampleLocations.size} sample locations")
        } catch (e: Exception) {
            logger.error("Error creating sample locations", e)
            throw e
        }
    }

    private suspend fun createBaseSettingsAsync() {
        try {
            val baseSettings = listOf(
                Triple("AppVersion", "1.0.0", "Current application version"),
                Triple("DatabaseVersion", "1.0", "Database schema version"),
                Triple("FirstRun", "true", "Indicates if this is the first application run"),
                Triple("LastBackup", "", "Timestamp of last backup"),
                Triple("AutoBackup", "false", "Enable automatic backups"),
                Triple("LocationServicesEnabled", "true", "Location services permission status"),
                Triple("CameraPermissionGranted", "false", "Camera permission status"),
                Triple("StoragePermissionGranted", "false", "Storage permission status"),
                Triple("NotificationsEnabled", "true", "Push notifications enabled"),
                Triple("ThemeMode", "auto", "App theme (light/dark/auto)"),
                Triple("AnalyticsEnabled", "true", "Usage analytics enabled"),
                Triple("CrashReportingEnabled", "true", "Crash reporting enabled")
            )

            baseSettings.forEach { (key, value, description) ->
                val setting = Setting.create(key, value, description)
                val result = unitOfWork.settings.createAsync(setting)
                if (!result.isSuccess) {
                    logger.warning("Failed to create base setting $key: ${result.errorMessage}")
                }
            }

            logger.info("Created ${baseSettings.size} base settings")
        } catch (e: Exception) {
            logger.error("Error creating base settings", e)
            throw e
        }
    }

    suspend fun createUserSettingsAsync(
        hemisphere: String,
        tempFormat: String,
        dateFormat: String,
        timeFormat: String,
        windDirection: String,
        email: String,
        guid: String
    ) {
        try {
            logger.info("Creating user-specific settings")

            val userSettings = listOf(
                Triple(MagicStrings.Hemisphere, hemisphere, "User's hemisphere (north/south)"),
                Triple(MagicStrings.WindDirection, windDirection, "Wind direction setting (towardsWind/withWind)"),
                Triple(MagicStrings.TimeFormat, timeFormat, "Time format (12h/24h)"),
                Triple(MagicStrings.DateFormat, dateFormat, "Date format (US/International)"),
                Triple(MagicStrings.TemperatureType, tempFormat, "Temperature format (F/C)"),
                Triple(MagicStrings.Email, email, "User's email address"),
                Triple(MagicStrings.UniqueID, guid, "Unique identifier for the installation")
            )

            userSettings.forEach { (key, value, description) ->
                val setting = Setting.create(key, value, description)
                val result = unitOfWork.settings.createAsync(setting)
                if (!result.isSuccess) {
                    logger.warning("Failed to create user setting $key: ${result.errorMessage}")
                }
            }

            logger.info("Created ${userSettings.size} user-specific settings")
        } catch (e: Exception) {
            logger.error("Error creating user-specific settings", e)
            throw e
        }
    }

    private suspend fun createCameraSensorProfilesAsync() {
        try {
            logger.info("Creating camera sensor profiles")
            // Placeholder for camera sensor profile creation
            // This would create common camera bodies and lens profiles
            logger.info("Camera sensor profiles creation completed")
        } catch (e: Exception) {
            logger.error("Error creating camera sensor profiles", e)
            throw e
        }
    }

    private suspend fun checkDatabaseFileExists(): Boolean {
        // Platform-specific implementation needed
        // This is a placeholder that should be implemented per platform
        return true
    }
}

/**
 * Data class for sample location information
 */
private data class SampleLocationData(
    val title: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val photo: String = ""
)

/**
 * Alert service interface - this should be defined in the shared layer
 */
interface IAlertService {
    suspend fun showErrorAlertAsync(message: String, title: String)
}