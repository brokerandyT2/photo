// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/interfaces/services/ITimezoneService.kt
package com.x3squaredcircles.pixmap.shared.application.interfaces.services

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

/**
 * Service interface for timezone operations
 */
interface ITimezoneService {
    fun getCurrentTimeZone(): TimeZone
    fun convertToLocalTime(utcTime: Instant): Instant
    fun convertToUtc(localTime: Instant): Instant
    fun getTimeZoneOffset(): Int
    fun getTimeZoneDisplayName(): String
}