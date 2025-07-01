// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/services/TimezoneService.kt

import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ITimezoneService
import kotlinx.datetime.*

/**
 * Timezone service implementation using kotlinx-datetime with proper UTC/local conversions
 */
class TimezoneService : ITimezoneService {

    override fun getCurrentTimeZone(): TimeZone {
        return TimeZone.currentSystemDefault()
    }

    override fun convertToLocalTime(utcTime: Instant): Instant {
        val localDateTime = utcTime.toLocalDateTime(getCurrentTimeZone())
        return localDateTime.toInstant(TimeZone.UTC)
    }

    override fun convertToUtc(localTime: Instant): Instant {
        val localDateTime = localTime.toLocalDateTime(TimeZone.UTC)
        return localDateTime.toInstant(getCurrentTimeZone())
    }

    override fun getTimeZoneOffset(): Int {
        val now = Clock.System.now()
        val offset = getCurrentTimeZone().offsetAt(now)
        return offset.totalSeconds / 60
    }

    override fun getTimeZoneDisplayName(): String {
        return getCurrentTimeZone().id
    }

    fun convertInstantToLocalDateTime(utcTime: Instant): LocalDateTime {
        return utcTime.toLocalDateTime(getCurrentTimeZone())
    }

    fun convertLocalDateTimeToInstant(localDateTime: LocalDateTime): Instant {
        return localDateTime.toInstant(getCurrentTimeZone())
    }

    fun formatInstantForDisplay(instant: Instant, includeTimezone: Boolean = true): String {
        val localDateTime = convertInstantToLocalDateTime(instant)
        val baseFormat = "${localDateTime.date} ${localDateTime.time}"

        return if (includeTimezone) {
            "$baseFormat ${getTimeZoneDisplayName()}"
        } else {
            baseFormat
        }
    }

    fun isDateInFuture(instant: Instant): Boolean {
        return instant > Clock.System.now()
    }

    fun isDateInPast(instant: Instant): Boolean {
        return instant < Clock.System.now()
    }

    fun getDurationUntil(targetInstant: Instant): kotlin.time.Duration {
        return targetInstant - Clock.System.now()
    }

    fun getDurationSince(pastInstant: Instant): kotlin.time.Duration {
        return Clock.System.now() - pastInstant
    }

    fun getStartOfDay(instant: Instant): Instant {
        val localDateTime = convertInstantToLocalDateTime(instant)
        val startOfDay = LocalDateTime(localDateTime.date, LocalTime(0, 0))
        return convertLocalDateTimeToInstant(startOfDay)
    }

    fun getEndOfDay(instant: Instant): Instant {
        val localDateTime = convertInstantToLocalDateTime(instant)
        val endOfDay = LocalDateTime(localDateTime.date, LocalTime(23, 59, 59, 999_999_999))
        return convertLocalDateTimeToInstant(endOfDay)
    }

    fun isSameDay(instant1: Instant, instant2: Instant): Boolean {
        val date1 = convertInstantToLocalDateTime(instant1).date
        val date2 = convertInstantToLocalDateTime(instant2).date
        return date1 == date2
    }

    fun addDays(instant: Instant, days: Int): Instant {
        val localDateTime = convertInstantToLocalDateTime(instant)
        val newLocalDateTime = localDateTime.date.plus(days, DateTimeUnit.DAY)
            .atTime(localDateTime.time)
        return convertLocalDateTimeToInstant(newLocalDateTime)
    }

    fun addHours(instant: Instant, hours: Int): Instant {
        return instant.plus(hours, DateTimeUnit.HOUR)
    }

    fun addMinutes(instant: Instant, minutes: Int): Instant {
        return instant.plus(minutes, DateTimeUnit.MINUTE)
    }

    fun getTimeZoneOffsetInHours(): Double {
        return getTimeZoneOffset() / 60.0
    }

    fun getTimeZoneOffsetString(): String {
        val offsetMinutes = getTimeZoneOffset()
        val hours = offsetMinutes / 60
        val minutes = offsetMinutes % 60

        val sign = if (offsetMinutes >= 0) "+" else "-"
        return String.format("%s%02d:%02d", sign, kotlin.math.abs(hours), kotlin.math.abs(minutes))
    }

    fun convertWeatherTimestamp(timestamp: Long): Instant {
        return Instant.fromEpochSeconds(timestamp)
    }

    fun formatWeatherTime(timestamp: Long): String {
        val instant = convertWeatherTimestamp(timestamp)
        return formatInstantForDisplay(instant, includeTimezone = false)
    }

    fun isWeatherDataStale(timestamp: Instant, maxAgeHours: Int = 2): Boolean {
        val age = getDurationSince(timestamp)
        return age.inWholeHours >= maxAgeHours
    }

    fun getLocationLocalTime(utcTime: Instant, timezoneId: String): LocalDateTime {
        val timezone = TimeZone.of(timezoneId)
        return utcTime.toLocalDateTime(timezone)
    }

    fun convertToLocationTimezone(utcTime: Instant, timezoneId: String): Instant {
        val timezone = TimeZone.of(timezoneId)
        val localDateTime = utcTime.toLocalDateTime(timezone)
        return localDateTime.toInstant(TimeZone.UTC)
    }
}