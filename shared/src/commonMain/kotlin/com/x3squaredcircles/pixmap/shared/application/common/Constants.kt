package com.x3squaredcircles.pixmap.shared.common

/**
 * Application constants
 */
object Constants {

    // Database
    const val DATABASE_NAME = "pixmap.db"
    const val DATABASE_VERSION = 1

    // File Storage
    const val PHOTOS_DIRECTORY = "photos"
    const val CACHE_DIRECTORY = "cache"

    // Settings Keys
    object Settings {
        const val FIRST_LAUNCH = "first_launch"
        const val THEME_MODE = "theme_mode"
        const val LOCATION_PERMISSION_REQUESTED = "location_permission_requested"
        const val CAMERA_PERMISSION_REQUESTED = "camera_permission_requested"
        const val WEATHER_UPDATE_INTERVAL = "weather_update_interval"
        const val DEFAULT_WEATHER_RADIUS = "default_weather_radius"
    }

    // Weather
    const val DEFAULT_WEATHER_RADIUS_KM = 50.0
    const val WEATHER_CACHE_DURATION_HOURS = 1

    // Location
    const val DEFAULT_LOCATION_ACCURACY = 100.0 // meters
    const val LOCATION_TIMEOUT_MS = 30000L // 30 seconds

    // Notifications
    const val WEATHER_UPDATE_NOTIFICATION_ID = 1001
    const val LOCATION_SAVED_NOTIFICATION_ID = 1002

    // API
    const val API_TIMEOUT_MS = 30000L
    const val API_RETRY_COUNT = 3
}