package com.x3squaredcircles.pixmap.shared.application.interfaces

/**
 * Interface for mediator pattern implementation
 */
interface IMediator {
    suspend fun send(command: ICommand)
    suspend fun <TResult> send(command: ICommand<TResult>): TResult
    suspend fun <TResult> send(query: IQuery<TResult>): TResult
}