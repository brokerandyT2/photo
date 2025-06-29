package com.x3squaredcircles.pixmap.shared.application.interfaces

/**
 * Marker interface for commands in CQRS pattern
 */
interface ICommand

/**
 * Interface for commands that return a result
 */
interface ICommand<out TResult> : ICommand