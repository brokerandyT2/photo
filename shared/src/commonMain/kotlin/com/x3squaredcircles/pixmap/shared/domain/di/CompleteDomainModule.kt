package com.x3squaredcircles.pixmap.shared.di

import com.x3squaredcircles.pixmap.shared.domain.di.domainServiceModule
import org.koin.dsl.module

/**
 * Complete domain module that includes all domain-related modules
 */
val completeDomainModule = module {
    includes(
        locationDomainModule,
        domainServiceModule
    )
}