// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/entities/Subscription.kt
package com.x3squaredcircles.pixmap.shared.domain.entities

import com.x3squaredcircles.pixmap.shared.domain.common.Entity
import kotlinx.datetime.Instant

/**
 * Subscription entity for user subscriptions
 */
class Subscription private constructor() : Entity() {

    val userId: String
        get() = _userId

    private var _userId: String = ""

    val status: String
        get() = _status

    private var _status: String = ""

    val expirationDate: Instant
        get() = _expirationDate

    private var _expirationDate: Instant = Instant.DISTANT_PAST

    constructor(userId: String, status: String, expirationDate: Instant) : this() {
        require(userId.isNotBlank()) { "User ID cannot be empty" }
        require(status.isNotBlank()) { "Status cannot be empty" }
        _userId = userId
        _status = status
        _expirationDate = expirationDate
    }

    fun updateStatus(status: String) {
        require(status.isNotBlank()) { "Status cannot be empty" }
        _status = status
    }

    fun updateExpirationDate(expirationDate: Instant) {
        _expirationDate = expirationDate
    }

    fun isActive(): Boolean {
        return _status == "Active" && _expirationDate > kotlinx.datetime.Clock.System.now()
    }

    fun isExpired(): Boolean {
        return _expirationDate <= kotlinx.datetime.Clock.System.now()
    }
}