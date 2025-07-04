// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/interfaces/IMediator.kt
package com.x3squaredcircles.pixmap.shared.application.interfaces

/**
 * Mediator interface for handling queries and commands
 */
interface IMediator {
    suspend fun <TResponse> send(request: IRequest<TResponse>): TResponse
    suspend fun publish(notification: INotification)
}

/**
 * Marker interface for requests that return a response
 */
interface IRequest<out TResponse>

/**
 * Marker interface for requests that don't return a response
 */


/**
 * Marker interface for notifications
 */
interface INotification

/**
 * Handler interface for requests
 */
interface IRequestHandler<in TRequest : IRequest<TResponse>, TResponse> {
    suspend fun handle(request: TRequest): TResponse
}

/**
 * Handler interface for notifications
 */
interface INotificationHandler<in TNotification : INotification> {
    suspend fun handle(notification: TNotification)
}