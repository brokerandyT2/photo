//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/core/data/DatabaseInitializer.kt
package com.x3squaredcircles.pixmap.core.data

import com.x3squaredcircles.pixmap.shared.domain.entities.Setting
import com.x3squaredcircles.pixmap.shared.domain.entities.TipType
import com.x3squaredcircles.pixmap.shared.domain.entities.Tip
import com.x3squaredcircles.pixmap.shared.domain.entities.Location
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Address
import com.x3squaredcircles.pixmap.shared.domain.entities.CameraBody
import com.x3squaredcircles.pixmap.shared.domain.entities.MountType
import com.x3squaredcircles.pixmap.shared.application.interfaces.IUnitOfWork
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILoggingService
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.IAlertService

import com.x3squaredcircles.pixmap.core.constants.MagicStrings
import com.x3squaredcircles.pixmap.shared.infrastructure.data.IDatabaseContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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
            // Wait for lock with timeout
            var attempts = 0
            while (!initializationLock.tryLock() && attempts < 30) {
                delay(1.seconds)
                attempts++
            }
            if (attempts >= 30) {
                throw Exception("Failed to acquire database initialization lock within timeout period")
            }
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

                // Wait for the other initialization to complete
                var waitTime = 0
                while (isInitializing && waitTime < 180) {
                    delay(1.seconds)
                    waitTime++
                }

                if (isInitialized) {
                    logger.debug("Database initialization completed by another process")
                    return
                }

                if (isInitializing) {
                    throw Exception("Database initialization by another process did not complete within expected time")
                }
            }

            // Final check using database query
            if (isDatabaseInitializedAsync()) {
                logger.debug("Database already initialized (database check) - skipping")
                return
            }

            logger.info("Starting database initialization with static data")
            isInitializing = true
            initializationTimestamp = Clock.System.now()

            // Initialize database structure
            val databaseContext = (unitOfWork as? IUnitOfWorkWithContext)?.getDatabaseContext()
            if (databaseContext != null) {
                databaseContext.initializeDatabaseAsync()
            } else {
                logger.warning("DatabaseContext not available through UnitOfWork")
            }

            // Initialize data in parallel
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
            alertService.showErrorAlertAsync("Database initialization failed: ${e.message}", "Database Error")
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
                "Landscape", "Silhouette", "Building", "Person", "Baby", "Animals",
                "Blurry Water", "Night", "Blue Hour", "Golden Hour", "Sunset",
                "Portrait", "Macro", "Street Photography", "Architecture", "Wildlife",
                "Wedding", "Event", "Travel", "Food", "Product", "Fashion",
                "Sports", "Nature", "Urban", "Documentary", "Fine Art", "Abstract"
            )

            tipTypeNames.forEach { name ->
                val tipType = TipType.create(name)
                val tipTypeWithLocalization = tipType.setLocalization("en-US")

                val typeResult = unitOfWork.tipTypes.addAsync(tipTypeWithLocalization)
                if (!typeResult.isSuccess || typeResult.data == null) {
                    logger.warning("Failed to create tip type: $name")
                    return@forEach
                }

                // Create a sample tip for each type
                val tip = Tip.create(
                    typeResult.data!!.id,
                    "How to Take Great $name Photos",
                    "Photography tips and techniques for capturing stunning $name images. Consider lighting, composition, and camera settings for the best results.",
                    fstop = "f/1.6",
                    iso = "100",
                    shutterSpeed = "1/125",
                    i8n = "en-US")

                val tipWithSettings = tip.updatePhotographySettings("f/1.8", "1/125", "100")
                val tipWithLocalization = tipWithSettings.setLocalization("en-US")

                val tipResult = unitOfWork.tips.createAsync(tipWithLocalization)
                if (!tipResult.isSuccess) {
                    logger.warning("Failed to create tip for type $name: ${tipResult.errorMessage}")
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
                SampleLocation(
                    "Soldiers and Sailors Monument",
                    "Located in the heart of downtown in Monument Circle, it was originally designed to honor Indiana's Civil War veterans. It now commemorates the valor of Hoosier veterans who served in all wars prior to WWI, including the Revolutionary War, the War of 1812, the Mexican War, the Civil War, the Frontier Wars and the Spanish-American War. One of the most popular parts of the monument is the observation deck with a 360-degree view of the city skyline from 275 feet up.",
                    39.7685, -86.1580,
                    "s_and_sm_new.jpg"
                ),
                SampleLocation(
                    "The Bean",
                    "What is The Bean?\r\nThe Bean is a work of public art in the heart of Chicago. The sculpture, which is officially titled Cloud Gate, is one of the world's largest permanent outdoor art installations. The monumental work was unveiled in 2004 and quickly became of the Chicago's most iconic sights.",
                    41.8827, -87.6233,
                    "chicagobean.jpg"
                ),
                SampleLocation(
                    "Golden Gate Bridge",
                    "The Golden Gate Bridge is a suspension bridge spanning the Golden Gate strait, the one-mile-wide (1.6 km) channel between San Francisco Bay and the Pacific Ocean. The strait is the entrance to San Francisco Bay from the Pacific Ocean. The bridge connects the city of San Francisco, California, to Marin County, carrying both U.S. Route 101 and California State Route 1 across the strait.",
                    37.8199, -122.4783,
                    "ggbridge.jpg"
                ),
                SampleLocation(
                    "Gateway Arch",
                    "The Gateway Arch is a 630-foot (192 m) monument in St. Louis, Missouri, that commemorates Thomas Jefferson and the westward expansion of the United States. The arch is the centerpiece of the Gateway Arch National Park and is the tallest arch in the world.",
                    38.6247, -90.1848,
                    "stlarch.jpg"
                ),
                SampleLocation(
                    "Space Needle",
                    "The Space Needle is an observation tower in Seattle, Washington, United States. It was built in the Seattle Center for the 1962 World's Fair, which drew over 2.3 million visitors. The Space Needle is 605 ft high, 138 ft wide, and weighs 9,550 tons.",
                    47.6205, -122.3493,
                    "spaceneedle.jpg"
                ),
                SampleLocation(
                    "Hollywood Sign",
                    "The Hollywood Sign is an American landmark and cultural icon overlooking Hollywood, Los Angeles, California. Originally the Hollywoodland Sign, it is situated on Mount Lee, in the Hollywood Hills area of the Santa Monica Mountains.",
                    34.1341, -118.3215,
                    "hollywoodsign.jpg"
                ),
                SampleLocation(
                    "Mount Rushmore",
                    "Mount Rushmore National Memorial is centered around a colossal sculpture carved into the granite face of Mount Rushmore in the Black Hills in Keystone, South Dakota. The sculpture features the 60-foot heads of Presidents George Washington, Thomas Jefferson, Theodore Roosevelt and Abraham Lincoln.",
                    43.8791, -103.4591,
                    "mountrushmore.jpg"
                ),
                SampleLocation(
                    "Statue of Liberty",
                    "The Statue of Liberty is a colossal neoclassical sculpture on Liberty Island in New York Harbor in New York City, in the United States. The copper statue, a gift from France to the United States, was designed by French sculptor Frédéric Auguste Bartholdi.",
                    40.6892, -74.0445,
                    "statueofliberty.jpg"
                )
            )

            sampleLocations.forEach { locationData ->
                val location = Location(
                    locationData.title,
                    locationData.description,
                    Coordinate(locationData.latitude, locationData.longitude),
                    Address("", "")
                )

                if (locationData.photo.isNotEmpty()) {
                    location.attachPhoto(locationData.photo)
                }

                val result = unitOfWork.locations.createAsync(location)
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
            val baseSettings = mutableListOf(
                Triple(MagicStrings.LastBulkWeatherUpdate, Clock.System.now().minus(2.days).toString(), "Timestamp of last bulk weather update"),
                Triple(MagicStrings.DefaultLanguage, "en-US", "Default language setting"),
                Triple(MagicStrings.CameraRefresh, "500", "Camera refresh rate in milliseconds"),
                Triple(MagicStrings.AppOpenCounter, "1", "Number of times the app has been opened"),
                Triple(MagicStrings.WeatherURL, "https://api.openweathermap.org/data/3.0/onecall", "Weather API URL"),
                Triple(MagicStrings.Weather_API_Key, "aa24f449cced50c0491032b2f955d610", "Weather API key"),
                Triple(MagicStrings.FreePremiumAdSupported, "false", "Whether the app is running in ad-supported mode")
            )

            // Add build-specific settings
            val buildSettings = if (isDebugBuild()) {
                listOf(
                    Triple(MagicStrings.SettingsViewed, MagicStrings.True_string, "Whether the settings page has been viewed"),
                    Triple(MagicStrings.HomePageViewed, MagicStrings.True_string, "Whether the home page has been viewed"),
                    Triple(MagicStrings.LocationListViewed, MagicStrings.True_string, "Whether the location list has been viewed"),
                    Triple(MagicStrings.TipsViewed, MagicStrings.True_string, "Whether the tips page has been viewed"),
                    Triple(MagicStrings.ExposureCalcViewed, MagicStrings.True_string, "Whether the exposure calculator has been viewed"),
                    Triple(MagicStrings.LightMeterViewed, MagicStrings.True_string, "Whether the light meter has been viewed"),
                    Triple(MagicStrings.SceneEvaluationViewed, MagicStrings.True_string, "Whether the scene evaluation has been viewed"),
                    Triple(MagicStrings.AddLocationViewed, MagicStrings.True_string, "Whether the add location page has been viewed"),
                    Triple(MagicStrings.WeatherDisplayViewed, MagicStrings.True_string, "Whether the weather display has been viewed"),
                    Triple(MagicStrings.SunCalculatorViewed, MagicStrings.True_string, "Whether the sun calculator has been viewed"),
                    Triple(MagicStrings.SunLocationViewed, MagicStrings.True_string, "Whether the SunLocation Page has been viewed"),
                    Triple(MagicStrings.ExposureCalcAdViewed_TimeStamp, Clock.System.now().toString(), "Timestamp of last exposure calculator ad view"),
                    Triple(MagicStrings.LightMeterAdViewed_TimeStamp, Clock.System.now().toString(), "Timestamp of last light meter ad view"),
                    Triple(MagicStrings.SceneEvaluationAdViewed_TimeStamp, Clock.System.now().toString(), "Timestamp of last scene evaluation ad view"),
                    Triple(MagicStrings.SunCalculatorViewed_TimeStamp, Clock.System.now().toString(), "Timestamp of last sun calculator ad view"),
                    Triple(MagicStrings.SunLocationAdViewed_TimeStamp, Clock.System.now().toString(), "Timestamp of last sun location ad view"),
                    Triple(MagicStrings.WeatherDisplayAdViewed_TimeStamp, Clock.System.now().toString(), "Timestamp of last weather display ad view"),
                    Triple(MagicStrings.SubscriptionType, MagicStrings.Premium, "Subscription type (Free/Premium)"),
                    Triple(MagicStrings.SubscriptionExpiration, Clock.System.now().plus(365.days).toString(), "Subscription expiration date"),
                    Triple(MagicStrings.SubscriptionProductId, "premium_yearly_subscription", "Subscription product ID"),
                    Triple(MagicStrings.SubscriptionPurchaseDate, Clock.System.now().toString(), "Subscription purchase date"),
                    Triple(MagicStrings.SubscriptionTransactionId, "debug_transaction_${kotlin.random.Random.nextInt()}", "Subscription transaction ID"),
                    Triple(MagicStrings.AdGivesHours, "24", "Hours of premium access granted per ad view"),
                    Triple(MagicStrings.LastUploadTimeStamp, Clock.System.now().toString(), "Last Time that data was backed up to cloud")
                )
            } else {
                listOf(
                    Triple(MagicStrings.SettingsViewed, MagicStrings.False_string, "Whether the settings page has been viewed"),
                    Triple(MagicStrings.HomePageViewed, MagicStrings.False_string, "Whether the home page has been viewed"),
                    Triple(MagicStrings.LocationListViewed, MagicStrings.False_string, "Whether the location list has been viewed"),
                    Triple(MagicStrings.TipsViewed, MagicStrings.False_string, "Whether the tips page has been viewed"),
                    Triple(MagicStrings.ExposureCalcViewed, MagicStrings.False_string, "Whether the exposure calculator has been viewed"),
                    Triple(MagicStrings.LightMeterViewed, MagicStrings.False_string, "Whether the light meter has been viewed"),
                    Triple(MagicStrings.SceneEvaluationViewed, MagicStrings.False_string, "Whether the scene evaluation has been viewed"),
                    Triple(MagicStrings.AddLocationViewed, MagicStrings.False_string, "Whether the add location page has been viewed"),
                    Triple(MagicStrings.WeatherDisplayViewed, MagicStrings.False_string, "Whether the weather display has been viewed"),
                    Triple(MagicStrings.SunCalculatorViewed, MagicStrings.False_string, "Whether the sun calculator has been viewed"),
                    Triple(MagicStrings.SunLocationViewed, MagicStrings.False_string, "Whether the SunLocation Page has been viewed"),
                    Triple(MagicStrings.ExposureCalcAdViewed_TimeStamp, Clock.System.now().minus(1.days).toString(), "Timestamp of last exposure calculator ad view"),
                    Triple(MagicStrings.LightMeterAdViewed_TimeStamp, Clock.System.now().minus(1.days).toString(), "Timestamp of last light meter ad view"),
                    Triple(MagicStrings.SceneEvaluationAdViewed_TimeStamp, Clock.System.now().minus(1.days).toString(), "Timestamp of last scene evaluation ad view"),
                    Triple(MagicStrings.SunCalculatorViewed_TimeStamp, Clock.System.now().minus(1.days).toString(), "Timestamp of last sun calculator ad view"),
                    Triple(MagicStrings.SunLocationAdViewed_TimeStamp, Clock.System.now().minus(1.days).toString(), "Timestamp of last sun location ad view"),
                    Triple(MagicStrings.WeatherDisplayAdViewed_TimeStamp, Clock.System.now().minus(1.days).toString(), "Timestamp of last weather display ad view"),
                    Triple(MagicStrings.SubscriptionType, MagicStrings.Free, "Subscription type (Free/Premium)"),
                    Triple(MagicStrings.SubscriptionExpiration, Clock.System.now().minus(3.days).toString(), "Subscription expiration date"),
                    Triple("SubscriptionProductId", "", "Subscription product ID"),
                    Triple("SubscriptionPurchaseDate", "", "Subscription purchase date"),
                    Triple("SubscriptionTransactionId", "", "Subscription transaction ID"),
                    Triple(MagicStrings.AdGivesHours, "12", "Hours of premium access granted per ad view"),
                    Triple(MagicStrings.LastUploadTimeStamp, Clock.System.now().minus(1.days).toString(), "Last Time that data was backed up to cloud")
                )
            }

            baseSettings.addAll(buildSettings)

            // Process settings in batches
            val batchSize = 10
            baseSettings.chunked(batchSize).forEach { batch ->
                batch.forEach { (key, value, description) ->
                    val setting = Setting.create(key, value, description)
                    val result = unitOfWork.settings.createAsync(setting)
                    if (!result.isSuccess) {
                        logger.warning("Failed to create base setting $key: ${result.errorMessage}")
                    }
                }
            }

            logger.info("Created ${baseSettings.size} base settings")
        } catch (e: Exception) {
            logger.error("Error creating base settings", e)
            throw e
        }
    }

    private suspend fun createCameraSensorProfilesAsync() {
        try {
            val cameraProfiles = getCameraProfiles()

            // Process camera profiles in batches
            val batchSize = 10
            cameraProfiles.chunked(batchSize).forEach { batch ->
                batch.forEach { profile ->
                    val mountType = determineMountType(profile.brand, profile.name)
                    val cameraBody = CameraBody.create(
                        name = profile.name,
                        sensorType = profile.sensorType,
                        sensorWidth = profile.sensorWidth,
                        sensorHeight = profile.sensorHeight,
                        mountType = mountType,
                        isUserCreated = false
                    )

                    // Insert camera body through database context
                    val databaseContext = (unitOfWork as? IUnitOfWorkWithContext)?.getDatabaseContext()
                    if (databaseContext != null) {
                        databaseContext.insertAsync(cameraBody)

                    } else {
                        logger.warning("Failed to create camera profile ${profile.name}: DatabaseContext not available")
                    }
                }
            }

            logger.info("Created ${cameraProfiles.size} camera sensor profiles")
        } catch (e: Exception) {
            logger.error("Error creating camera sensor profiles", e)
            throw e
        }
    }

    private fun determineMountType(brand: String, cameraName: String): MountType {
        val brandLower = brand.lowercase()
        val cameraNameLower = cameraName.lowercase()

        return when {
            brandLower == "canon" && cameraNameLower.contains("eos r") -> MountType.CanonRF
            brandLower == "canon" && cameraNameLower.contains("eos m") -> MountType.CanonEFM
            brandLower == "canon" -> MountType.CanonEF
            brandLower == "nikon" && cameraNameLower.contains(" z") -> MountType.NikonZ
            brandLower == "nikon" -> MountType.NikonF
            brandLower == "sony" && (cameraNameLower.contains("fx") || cameraNameLower.contains("a7")) -> MountType.SonyFE
            brandLower == "sony" -> MountType.SonyE
            brandLower == "pentax" -> MountType.PentaxK
            brandLower == "panasonic" || brandLower == "olympus" || brandLower == "om system" -> MountType.MicroFourThirds
            brandLower == "fujifilm" -> MountType.FujifilmX
            else -> MountType.Other
        }
    }

    private fun getCameraProfiles(): List<CameraProfile> {
        return listOf(
            // 2010-2024 Cameras (extensive list)
            CameraProfile("Canon EOS 550D", "Canon", "Crop", 22.3, 14.9),
            CameraProfile("Nikon D3100", "Nikon", "Crop", 23.1, 15.4),
            CameraProfile("Canon EOS 7D", "Canon", "Crop", 22.3, 14.9),
            CameraProfile("Nikon D7000", "Nikon", "Crop", 23.6, 15.6),
            CameraProfile("Sony Alpha A500", "Sony", "Crop", 23.5, 15.6),
            CameraProfile("Pentax K-x", "Pentax", "Crop", 23.6, 15.8),
            CameraProfile("Canon EOS 5D Mark II", "Canon", "Full Frame", 36.0, 24.0),
            CameraProfile("Nikon D3s", "Nikon", "Full Frame", 36.0, 23.9),
            CameraProfile("Sony Alpha A850", "Sony", "Full Frame", 35.9, 24.0),
            CameraProfile("Canon EOS 600D", "Canon", "Crop", 22.3, 14.9),
            CameraProfile("Nikon D5100", "Nikon", "Crop", 23.6, 15.6),
            CameraProfile("Sony Alpha A35", "Sony", "Crop", 23.5, 15.6),
            CameraProfile("Pentax K-5", "Pentax", "Crop", 23.7, 15.7),
            CameraProfile("Canon EOS 650D", "Canon", "Crop", 22.3, 14.9),
            CameraProfile("Nikon D5200", "Nikon", "Crop", 23.5, 15.6),
            CameraProfile("Canon EOS 6D", "Canon", "Full Frame", 35.8, 23.9),
            CameraProfile("Nikon D600", "Nikon", "Full Frame", 35.9, 24.0),
            CameraProfile("Nikon D800", "Nikon", "Full Frame", 35.9, 24.0),
            CameraProfile("Canon EOS 5D Mark III", "Canon", "Full Frame", 36.0, 24.0),
            CameraProfile("Nikon D3200", "Nikon", "Crop", 23.2, 15.4),
            CameraProfile("Canon EOS 60D", "Canon", "Crop", 22.3, 14.9),
            CameraProfile("Pentax K-30", "Pentax", "Crop", 23.7, 15.7),
            CameraProfile("Sony Alpha A57", "Sony", "Crop", 23.5, 15.6),
            CameraProfile("Canon EOS 700D", "Canon", "Crop", 22.3, 14.9),
            CameraProfile("Nikon D7100", "Nikon", "Crop", 23.5, 15.6),
            CameraProfile("Canon EOS 100D", "Canon", "Crop", 22.3, 14.9),
            CameraProfile("Nikon D5300", "Nikon", "Crop", 23.5, 15.6),
            CameraProfile("Sony Alpha A58", "Sony", "Crop", 23.5, 15.6),
            CameraProfile("Pentax K-3", "Pentax", "Crop", 23.5, 15.6),
            CameraProfile("Nikon D610", "Nikon", "Full Frame", 35.9, 24.0),
            CameraProfile("Canon EOS 70D", "Canon", "Crop", 22.3, 14.9),
            CameraProfile("Sony Alpha A7", "Sony", "Full Frame", 35.8, 23.9),
            CameraProfile("Sony Alpha A7R", "Sony", "Full Frame", 35.9, 24.0),
            CameraProfile("Canon EOS 750D", "Canon", "Crop", 22.3, 14.9),
            CameraProfile("Nikon D750", "Nikon", "Full Frame", 35.9, 24.0),
            CameraProfile("Canon EOS 760D", "Canon", "Crop", 22.3, 14.9),
            CameraProfile("Nikon D810", "Nikon", "Full Frame", 35.9, 24.0),
            CameraProfile("Canon EOS 7D Mark II", "Canon", "Crop", 22.4, 15.0),
            CameraProfile("Sony Alpha A7S", "Sony", "Full Frame", 35.6, 23.8),
            CameraProfile("Sony Alpha A7 II", "Sony", "Full Frame", 35.9, 24.0),
            CameraProfile("Canon EOS 5DS", "Canon", "Full Frame", 36.0, 24.0),
            CameraProfile("Canon EOS 5DS R", "Canon", "Full Frame", 36.0, 24.0),
            CameraProfile("Nikon D7200", "Nikon", "Crop", 23.5, 15.6),
            CameraProfile("Pentax K-3 II", "Pentax", "Crop", 23.5, 15.6),
            CameraProfile("Sony Alpha A7R II", "Sony", "Full Frame", 35.9, 24.0),
            CameraProfile("Sony Alpha A7S II", "Sony", "Full Frame", 35.6, 23.8),
            CameraProfile("Fujifilm X-T1", "Fujifilm", "Crop", 23.6, 15.6),
            CameraProfile("Fujifilm X-T10", "Fujifilm", "Crop", 23.6, 15.6),
            CameraProfile("Panasonic Lumix DMC-GH4", "Panasonic", "Micro Four Thirds", 17.3, 13.0),
            CameraProfile("Olympus OM-D E-M1", "Olympus", "Micro Four Thirds", 17.3, 13.0),
            CameraProfile("Canon EOS 80D", "Canon", "Crop", 22.3, 14.9),
            CameraProfile("Canon EOS 5D Mark IV", "Canon", "Full Frame", 36.0, 24.0),
            CameraProfile("Nikon D500", "Nikon", "Crop", 23.5, 15.6),
            CameraProfile("Pentax K-1", "Pentax", "Full Frame", 35.9, 24.0),
            CameraProfile("Canon EOS 1D X Mark II", "Canon", "Full Frame", 36.0, 24.0),
            CameraProfile("Sony Alpha A6300", "Sony", "Crop", 23.5, 15.6),
            CameraProfile("Fujifilm X-Pro2", "Fujifilm", "Crop", 23.6, 15.6),
            CameraProfile("Fujifilm X-T2", "Fujifilm", "Crop", 23.6, 15.6),
            CameraProfile("Nikon D850", "Nikon", "Full Frame", 35.9, 23.9),
            CameraProfile("Canon EOS 77D", "Canon", "Crop", 22.3, 14.9),
            CameraProfile("Canon EOS 800D", "Canon", "Crop", 22.3, 14.9),
            CameraProfile("Nikon D7500", "Nikon", "Crop", 23.5, 15.6),
            CameraProfile("Canon EOS 6D Mark II", "Canon", "Full Frame", 35.9, 24.0),
            CameraProfile("Sony Alpha A9", "Sony", "Full Frame", 35.6, 23.8),
            CameraProfile("Sony Alpha A7R III", "Sony", "Full Frame", 35.9, 24.0),
            CameraProfile("Sony Alpha A6500", "Sony", "Crop", 23.5, 15.6),
            CameraProfile("Fujifilm X-T20", "Fujifilm", "Crop", 23.6, 15.6),
            CameraProfile("Sony Alpha A7 III", "Sony", "Full Frame", 35.9, 24.0),
            CameraProfile("Fujifilm X-T3", "Fujifilm", "Crop", 23.5, 15.6),
            CameraProfile("Fujifilm X-H1", "Fujifilm", "Crop", 23.5, 15.6),
            CameraProfile("Sony Alpha A6400", "Sony", "Crop", 23.5, 15.6),
            CameraProfile("Canon EOS R", "Canon", "Full Frame", 36.0, 24.0),
            CameraProfile("Nikon Z7", "Nikon", "Full Frame", 35.9, 24.0),
            CameraProfile("Nikon Z6", "Nikon", "Full Frame", 35.9, 24.0),
            CameraProfile("Panasonic Lumix DC-S1R", "Panasonic", "Full Frame", 36.0, 24.0),
            CameraProfile("Panasonic Lumix DC-S1", "Panasonic", "Full Frame", 36.0, 24.0),
            CameraProfile("Canon EOS 90D", "Canon", "Crop", 22.3, 14.8),
            CameraProfile("Canon EOS 250D", "Canon", "Crop", 22.3, 14.9),
            CameraProfile("Sony Alpha A7R IV", "Sony", "Full Frame", 35.9, 24.0),
            CameraProfile("Sony Alpha A6600", "Sony", "Crop", 23.5, 15.6),
            CameraProfile("Sony Alpha A6100", "Sony", "Crop", 23.5, 15.6),
            CameraProfile("Fujifilm X-T30", "Fujifilm", "Crop", 23.5, 15.6),
            CameraProfile("Canon EOS RP", "Canon", "Full Frame", 35.9, 24.0),
            CameraProfile("Nikon Z50", "Nikon", "Crop", 23.5, 15.6),
            CameraProfile("Canon EOS R5", "Canon", "Full Frame", 36.0, 24.0),
            CameraProfile("Canon EOS R6", "Canon", "Full Frame", 35.9, 24.0),
            CameraProfile("Sony Alpha A7S III", "Sony", "Full Frame", 35.6, 23.8),
            CameraProfile("Fujifilm X-T4", "Fujifilm", "Crop", 23.5, 15.6),
            CameraProfile("Fujifilm X-S10", "Fujifilm", "Crop", 23.5, 15.6),
            CameraProfile("Nikon Z5", "Nikon", "Full Frame", 35.9, 24.0),
            CameraProfile("Panasonic Lumix DC-S5", "Panasonic", "Full Frame", 36.0, 24.0),
            CameraProfile("Sony Alpha A7C", "Sony", "Full Frame", 35.9, 24.0),
            CameraProfile("Sony Alpha A7 IV", "Sony", "Full Frame", 35.9, 23.9),
            CameraProfile("Sony Alpha A1", "Sony", "Full Frame", 35.9, 24.0),
            CameraProfile("Canon EOS R3", "Canon", "Full Frame", 36.0, 24.0),
            CameraProfile("Nikon Z9", "Nikon", "Full Frame", 35.9, 24.0),
            CameraProfile("Fujifilm X-E4", "Fujifilm", "Crop", 23.5, 15.6),
            CameraProfile("Canon EOS R7", "Canon", "Crop", 22.3, 14.9),
            CameraProfile("Canon EOS R10", "Canon", "Crop", 22.3, 14.9),
            CameraProfile("Sony Alpha A7R V", "Sony", "Full Frame", 35.9, 24.0),
            CameraProfile("Fujifilm X-H2S", "Fujifilm", "Crop", 23.5, 15.6),
            CameraProfile("Fujifilm X-H2", "Fujifilm", "Crop", 23.5, 15.6),
            CameraProfile("Canon EOS R6 Mark II", "Canon", "Full Frame", 35.9, 24.0),
            CameraProfile("Nikon Z30", "Nikon", "Crop", 23.5, 15.6),
            CameraProfile("OM System OM-1", "OM System", "Micro Four Thirds", 17.3, 13.0),
            CameraProfile("Fujifilm X-T5", "Fujifilm", "Crop", 23.5, 15.6),
            CameraProfile("Nikon Z6 II", "Nikon", "Full Frame", 35.9, 24.0),
            CameraProfile("Nikon Z7 II", "Nikon", "Full Frame", 35.9, 24.0),
            CameraProfile("Sony Alpha A7C II", "Sony", "Full Frame", 35.9, 24.0),
            CameraProfile("Sony Alpha A7C R", "Sony", "Full Frame", 35.9, 24.0),
            CameraProfile("Canon EOS R8", "Canon", "Full Frame", 35.9, 24.0),
            CameraProfile("Canon EOS R50", "Canon", "Crop", 22.3, 14.9),
            CameraProfile("Fujifilm X-S20", "Fujifilm", "Crop", 23.5, 15.6),
            CameraProfile("Nikon Z8", "Nikon", "Full Frame", 35.9, 24.0),
            CameraProfile("Sony Alpha A6700", "Sony", "Crop", 23.5, 15.6),
            CameraProfile("Canon EOS R100", "Canon", "Crop", 22.3, 14.9),
            CameraProfile("Nikon Zf", "Nikon", "Full Frame", 35.9, 24.0),
            CameraProfile("Canon EOS R5 Mark II", "Canon", "Full Frame", 36.0, 24.0),
            CameraProfile("Sony Alpha A9 III", "Sony", "Full Frame", 35.6, 23.8),
            CameraProfile("Nikon Z6 III", "Nikon", "Full Frame", 35.9, 24.0),
            CameraProfile("Fujifilm X-T50", "Fujifilm", "Crop", 23.5, 15.6),
            CameraProfile("Canon EOS R1", "Canon", "Full Frame", 36.0, 24.0),
            CameraProfile("Leica Q", "Leica", "Full Frame", 36.0, 24.0),
            CameraProfile("Leica Q2", "Leica", "Full Frame", 36.0, 24.0),
            CameraProfile("Leica Q3", "Leica", "Full Frame", 36.0, 24.0),
            CameraProfile("Leica SL", "Leica", "Full Frame", 36.0, 24.0),
            CameraProfile("Leica SL2", "Leica", "Full Frame", 36.0, 24.0),
            CameraProfile("Leica SL3", "Leica", "Full Frame", 36.0, 24.0),
            CameraProfile("Leica M11", "Leica", "Full Frame", 36.0, 24.0),
            CameraProfile("Hasselblad X1D", "Hasselblad", "Medium Format", 43.8, 32.9),
            CameraProfile("Hasselblad X1D II 50C", "Hasselblad", "Medium Format", 43.8, 32.9),
            CameraProfile("Hasselblad X2D 100C", "Hasselblad", "Medium Format", 43.8, 32.9),
            CameraProfile("Pentax 645D", "Pentax", "Medium Format", 44.0, 33.0),
            CameraProfile("Pentax K-70", "Pentax", "Crop", 23.5, 15.6),
            CameraProfile("Pentax KP", "Pentax", "Crop", 23.5, 15.6),
            CameraProfile("Pentax K-1 Mark II", "Pentax", "Full Frame", 35.9, 24.0),
            CameraProfile("Pentax K-3 Mark III", "Pentax", "Crop", 23.5, 15.6),
            CameraProfile("Sigma fp", "Sigma", "Full Frame", 35.9, 23.9),
            CameraProfile("Sigma fp L", "Sigma", "Full Frame", 35.9, 23.9),
            CameraProfile("Sigma fp II", "Sigma", "Full Frame", 35.9, 23.9)
        )
    }

    // Platform-specific method stub - to be implemented per platform
    private fun checkDatabaseFileExists(): Boolean {
        // This would be implemented differently on each platform
        return true
    }

    // Platform-specific method stub - to be implemented per platform
    private fun isDebugBuild(): Boolean {
        // This would be implemented differently on each platform
        return false
    }

    // Reset method for testing
    fun resetInitializationState() {
        isInitialized = false
        isInitializing = false
        initializationTimestamp = null
    }

    // Data classes for sample data
    private data class SampleLocation(
        val title: String,
        val description: String,
        val latitude: Double,
        val longitude: Double,
        val photo: String
    )

    private data class CameraProfile(
        val name: String,
        val brand: String,
        val sensorType: String,
        val sensorWidth: Double,
        val sensorHeight: Double
    )
}

// Interface extension for accessing database context
interface IUnitOfWorkWithContext : IUnitOfWork {
    fun getDatabaseContext(): IDatabaseContext?
}