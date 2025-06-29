package com.x3squaredcircles.pixmap.shared.application.di

import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.mediator.Mediator
import org.koin.dsl.module

/**
 * Dependency injection module for mediator
 */
val mediatorModule = module {
    single<IMediator> { Mediator() }
}