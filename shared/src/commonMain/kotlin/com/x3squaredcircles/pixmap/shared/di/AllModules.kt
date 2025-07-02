//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/di/AllModules.kt
package com.x3squaredcircles.pixmap.shared.di

import com.x3squaredcircles.pixmap.shared.application.di.applicationServicesModule
import com.x3squaredcircles.pixmap.shared.application.di.mediatorModule
import com.x3squaredcircles.pixmap.shared.domain.di.domainServiceModule
import com.x3squaredcircles.pixmap.shared.infrastructure.di.infrastructureModule
import com.x3squaredcircles.pixmap.shared.infrastructure.di.sharedModule

/**
 * Combines all shared modules for easy registration
 */
val allSharedModules = listOf(
    // Domain layer
    domainServiceModule,

    // Application layer
    applicationServicesModule,
    mediatorModule,

    // Infrastructure layer
    infrastructureModule,
    sharedModule,

    // Feature modules
    subscriptionModule,
    locationDomainModule,
    servicesModule
)