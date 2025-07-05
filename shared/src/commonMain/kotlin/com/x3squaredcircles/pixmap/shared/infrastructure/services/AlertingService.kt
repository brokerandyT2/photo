// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/services/AlertingService.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.services

import com.x3squaredcircles.pixmap.shared.application.interfaces.services.IAlertService
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILoggingService

/**
 * Cross-platform alerting service implementation
 * Provides basic alert functionality that can be extended by platform-specific implementations
 */
class AlertingService(
    private val logger: ILoggingService
) : IAlertService {

    override suspend fun showInfoAlertAsync(message: String, title: String) {
        logger.logInfo("Info Alert - $title: $message", "ALERT")
        // Platform-specific implementation would show actual alert dialog
        // For now, we just log the alert
    }

    override suspend fun showSuccessAlertAsync(message: String, title: String) {
        logger.logInfo("Success Alert - $title: $message", "ALERT")
        // Platform-specific implementation would show actual success alert dialog
        // For now, we just log the alert
    }

    override suspend fun showWarningAlertAsync(message: String, title: String) {
        logger.logWarning("Warning Alert - $title: $message", "ALERT")
        // Platform-specific implementation would show actual warning alert dialog
        // For now, we just log the alert
    }

    override suspend fun showErrorAlertAsync(message: String, title: String) {
        logger.logError("Error Alert - $title: $message", null, "ALERT")
        // Platform-specific implementation would show actual error alert dialog
        // For now, we just log the alert
    }
}