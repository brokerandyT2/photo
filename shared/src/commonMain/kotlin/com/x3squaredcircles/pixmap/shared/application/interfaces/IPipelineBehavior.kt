// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/interfaces/IPipelineBehavior.kt
package com.x3squaredcircles.pixmap.shared.application.interfaces

/**
 * Pipeline behavior interface for handling cross-cutting concerns in request processing
 */
interface IPipelineBehavior<TRequest : IRequest<TResponse>, TResponse> {

    /**
     * Handles the request by performing any cross-cutting concern logic
     * and then calling the next behavior or handler in the pipeline
     */
    suspend fun handle(
        request: TRequest,
        next: suspend () -> TResponse
    ): TResponse
}

/**
 * Base interface for notification pipeline behaviors
 */
interface INotificationPipelineBehavior<TNotification : INotification> {

    /**
     * Handles the notification by performing any cross-cutting concern logic
     * and then calling the next behavior or handler in the pipeline
     */
    suspend fun handle(
        notification: TNotification,
        next: suspend () -> Unit
    )
}

/**
 * Factory interface for creating pipeline behaviors
 */
interface IPipelineBehaviorFactory {

    /**
     * Creates a pipeline behavior for the specified request type
     */
    fun <TRequest : IRequest<TResponse>, TResponse> createBehavior(): IPipelineBehavior<TRequest, TResponse>?

    /**
     * Creates a notification pipeline behavior for the specified notification type
     */
    fun <TNotification : INotification> createNotificationBehavior(): INotificationPipelineBehavior<TNotification>?
}

/**
 * Pipeline behavior registration interface for dependency injection
 */
interface IPipelineBehaviorRegistry {

    /**
     * Registers a pipeline behavior for request handling
     */
    fun <TRequest : IRequest<TResponse>, TResponse> registerBehavior(
        behavior: IPipelineBehavior<TRequest, TResponse>
    )

    /**
     * Registers a notification pipeline behavior
     */
    fun <TNotification : INotification> registerNotificationBehavior(
        behavior: INotificationPipelineBehavior<TNotification>
    )

    /**
     * Gets all registered behaviors for a request type
     */
    fun <TRequest : IRequest<TResponse>, TResponse> getBehaviors(): List<IPipelineBehavior<TRequest, TResponse>>

    /**
     * Gets all registered notification behaviors for a notification type
     */
    fun <TNotification : INotification> getNotificationBehaviors(): List<INotificationPipelineBehavior<TNotification>>
}

/**
 * Extension functions for pipeline behavior composition
 */
suspend fun <TRequest : IRequest<TResponse>, TResponse> List<IPipelineBehavior<TRequest, TResponse>>.executePipeline(
    request: TRequest,
    finalHandler: suspend (TRequest) -> TResponse
): TResponse {
    var index = 0

    suspend fun next(): TResponse {
        return if (index < this@executePipeline.size) {
            val behavior = this@executePipeline[index++]
            behavior.handle(request) { next() }
        } else {
            finalHandler(request)
        }
    }

    return next()
}

suspend fun <TNotification : INotification> List<INotificationPipelineBehavior<TNotification>>.executeNotificationPipeline(
    notification: TNotification,
    finalHandler: suspend (TNotification) -> Unit
) {
    var index = 0

    suspend fun next() {
        if (index < this@executeNotificationPipeline.size) {
            val behavior = this@executeNotificationPipeline[index++]
            behavior.handle(notification) { next() }
        } else {
            finalHandler(notification)
        }
    }

    next()
}