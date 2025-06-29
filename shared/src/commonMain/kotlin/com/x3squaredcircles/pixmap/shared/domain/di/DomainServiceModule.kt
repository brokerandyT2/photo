package com.x3squaredcircles.pixmap.shared.domain.di

import com.x3squaredcircles.pixmap.shared.domain.interfaces.IDomainEventDispatcher
import com.x3squaredcircles.pixmap.shared.domain.services.DomainEventDispatcher
import org.koin.dsl.module

/**
 * Dependency injection module for domain services
 */
val domainServiceModule = module {
    single<IDomainEventDispatcher> { DomainEventDispatcher() }
}