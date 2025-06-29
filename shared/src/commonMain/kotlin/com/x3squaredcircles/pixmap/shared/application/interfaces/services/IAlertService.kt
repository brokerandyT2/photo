// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/interfaces/services/IAlertService.kt
package com.x3squaredcircles.pixmap.shared.application.interfaces.services

/**
 * Service interface for showing alerts and notifications
 */
interface IAlertService {
    suspend fun showInfoAlertAsync(message: String, title: String = "Information")
    suspend fun showSuccessAlertAsync(message: String, title: String = "Success")
    suspend fun showWarningAlertAsync(message: String, title: String = "Warning")
    suspend fun showErrorAlertAsync(message: String, title: String = "Error")
}