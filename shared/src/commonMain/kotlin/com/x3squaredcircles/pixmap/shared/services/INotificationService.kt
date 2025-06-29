package com.x3squaredcircles.pixmap.shared.services

/**
 * Interface for notification services
 */
interface INotificationService {
    suspend fun showToast(message: String)
    suspend fun showNotification(title: String, message: String, id: Int = 0)
    suspend fun cancelNotification(id: Int)
    suspend fun hasPermission(): Boolean
    suspend fun requestPermission(): Boolean
}