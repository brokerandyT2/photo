// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/di/SubscriptionModule.kt
package com.x3squaredcircles.pixmap.shared.di

import com.x3squaredcircles.pixmap.shared.application.handlers.commands.*
import com.x3squaredcircles.pixmap.shared.application.handlers.queries.*
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ISubscriptionRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ISubscriptionService
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ISubscriptionStoreService
import com.x3squaredcircles.pixmap.shared.infrastructure.data.repositories.SubscriptionRepositoryImpl
import com.x3squaredcircles.pixmap.shared.infrastructure.services.SubscriptionServiceImpl
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Dependency injection module for subscription domain
 */
val subscriptionModule = module {

    // Repository
    singleOf(::SubscriptionRepositoryImpl) { bind<ISubscriptionRepository>() }

    // Services
    singleOf(::SubscriptionServiceImpl) { bind<ISubscriptionService>() }

    // Note: ISubscriptionStoreService is platform-specific and provided by Android module
    // single<ISubscriptionStoreService> { AndroidBillingService(get()) }

    // Command Handlers
    factory { CreateSubscriptionCommandHandler(get(), get()) }
    factory { UpdateSubscriptionStatusCommandHandler(get(), get()) }
    factory { RenewSubscriptionCommandHandler(get(), get()) }
    factory { CancelSubscriptionCommandHandler(get(), get()) }
    factory { UpdatePurchaseTokenCommandHandler(get(), get()) }
    factory { UpdateSubscriptionExpirationCommandHandler(get(), get()) }

    // Query Handlers
    factory { GetActiveSubscriptionQueryHandler(get(), get()) }
    factory { GetSubscriptionByTransactionIdQueryHandler(get(), get()) }
    factory { GetSubscriptionByPurchaseTokenQueryHandler(get(), get()) }
    factory { GetSubscriptionByIdQueryHandler(get(), get()) }
    factory { GetSubscriptionsByUserIdQueryHandler(get(), get()) }
    factory { GetExpiredSubscriptionsQueryHandler(get(), get()) }
    factory { HasActiveSubscriptionQueryHandler(get(), get()) }
}