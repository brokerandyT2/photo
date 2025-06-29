package com.x3squaredcircles.pixmap.shared.application.interfaces

/**
 * Interface for query handlers in CQRS pattern
 */
interface IQueryHandler<in TQuery : IQuery<TResult>, out TResult> {
    suspend fun handle(query: TQuery): TResult
}