//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/mediator/Mediator.kt
package com.x3squaredcircles.pixmap.shared.application.mediator

import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.INotification
import com.x3squaredcircles.pixmap.shared.application.interfaces.INotificationHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler

/**
 * Mediator implementation for handling queries and commands
 */
class Mediator(
    private val requestHandlers: Map<String, IRequestHandler<*, *>>,
    private val notificationHandlers: Map<String, List<INotificationHandler<*>>>
) : IMediator {

    override suspend fun <TResponse> send(request: IRequest<TResponse>): TResponse {
        val requestType = request::class.simpleName ?: throw IllegalArgumentException("Unknown request type")

        @Suppress("UNCHECKED_CAST")
        val handler = requestHandlers[requestType] as? IRequestHandler<IRequest<TResponse>, TResponse>
            ?: throw IllegalArgumentException("No handler registered for request type: $requestType")

        return handler.handle(request)
    }

    override suspend fun publish(notification: INotification) {
        val notificationType = notification::class.simpleName ?: return

        val handlers = notificationHandlers[notificationType] ?: return

        handlers.forEach { handler ->
            @Suppress("UNCHECKED_CAST")
            (handler as INotificationHandler<INotification>).handle(notification)
        }
    }
}