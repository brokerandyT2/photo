package com.x3squaredcircles.pixmap.shared.application.interfaces

/**
 * Interface for command handlers in CQRS pattern
 */
interface ICommandHandler<in TCommand : ICommand> {
    suspend fun handle(command: TCommand)
}

/**
 * Interface for command handlers that return a result
 */
interface ICommandHandler<in TCommand : ICommand<TResult>, out TResult> {
    suspend fun handle(command: TCommand): TResult
}