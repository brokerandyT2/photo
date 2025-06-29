package com.x3squaredcircles.pixmap.shared.application.mediator

import com.x3squaredcircles.pixmap.shared.application.interfaces.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.reflect.KClass

/**
 * Mediator implementation using Koin for dependency resolution
 */
class Mediator : IMediator, KoinComponent {

    override suspend fun send(command: ICommand) {
        val handler = getCommandHandler(command::class)
        handler.handle(command)
    }

    override suspend fun <TResult> send(command: ICommand<TResult>): TResult {
        val handler = getCommandHandlerWithResult(command::class)
        return handler.handle(command)
    }

    override suspend fun <TResult> send(query: IQuery<TResult>): TResult {
        val handler = getQueryHandler(query::class)
        return handler.handle(query)
    }

    @Suppress("UNCHECKED_CAST")
    private fun getCommandHandler(commandClass: KClass<out ICommand>): ICommandHandler<ICommand> {
        return when (commandClass.simpleName) {
            "UpdateLocationCommand" -> get<com.x3squaredcircles.pixmap.shared.application.handlers.UpdateLocationCommandHandler>() as ICommandHandler<ICommand>
            "DeleteLocationCommand" -> get<com.x3squaredcircles.pixmap.shared.application.handlers.DeleteLocationCommandHandler>() as ICommandHandler<ICommand>
            "AttachPhotoCommand" -> get<com.x3squaredcircles.pixmap.shared.application.handlers.AttachPhotoCommandHandler>() as ICommandHandler<ICommand>
            else -> throw IllegalArgumentException("No handler found for command: ${commandClass.simpleName}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <TResult> getCommandHandlerWithResult(commandClass: KClass<out ICommand<TResult>>): ICommandHandler<ICommand<TResult>, TResult> {
        return when (commandClass.simpleName) {
            "CreateLocationCommand" -> get<com.x3squaredcircles.pixmap.shared.application.handlers.CreateLocationCommandHandler>() as ICommandHandler<ICommand<TResult>, TResult>
            else -> throw IllegalArgumentException("No handler found for command: ${commandClass.simpleName}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <TResult> getQueryHandler(queryClass: KClass<out IQuery<TResult>>): IQueryHandler<IQuery<TResult>, TResult> {
        return when (queryClass.simpleName) {
            "GetLocationByIdQuery" -> get<com.x3squaredcircles.pixmap.shared.application.handlers.GetLocationByIdQueryHandler>() as IQueryHandler<IQuery<TResult>, TResult>
            "GetAllLocationsQuery" -> get<com.x3squaredcircles.pixmap.shared.application.handlers.GetAllLocationsQueryHandler>() as IQueryHandler<IQuery<TResult>, TResult>
            "GetLocationsByCoordinateQuery" -> get<com.x3squaredcircles.pixmap.shared.application.handlers.GetLocationsByCoordinateQueryHandler>() as IQueryHandler<IQuery<TResult>, TResult>
            else -> throw IllegalArgumentException("No handler found for query: ${queryClass.simpleName}")
        }
    }
}