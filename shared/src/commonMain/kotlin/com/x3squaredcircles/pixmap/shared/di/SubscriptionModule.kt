package com.x3squaredcircles.pixmap.shared.di

import com.x3squaredcircles.pixmap.shared.application.handlers.commands.*
import com.x3squaredcircles.pixmap.shared.application.handlers.queries.*
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ISubscriptionRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ISubscriptionService
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ISubscriptionStoreService
import com.x3squaredcircles.pixmap.shared.infrastructure.data.repositories.SubscriptionRepositoryImpl
import com.x3squaredcircles.pixmap.shared.infrastructure.services.SubscriptionServiceImpl
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
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
    factoryOf(::CreateSubscriptionCommandHandler)
    factoryOf(::UpdateSubscriptionStatusCommandHandler)
    factoryOf(::RenewSubscriptionCommandHandler)
    factoryOf(::CancelSubscriptionCommandHandler)
    factoryOf(::UpdatePurchaseTokenCommandHandler)
    factoryOf(::VerifySubscriptionCommandHandler)
    factoryOf(::UpdateSubscriptionExpirationCommandHandler)

    // Query Handlers
    factoryOf(::GetActiveSubscriptionQueryHandler)
    factoryOf(::GetSubscriptionByTransactionIdQueryHandler)
    factoryOf(::GetSubscriptionByPurchaseTokenQueryHandler)
    factoryOf(::GetSubscriptionByIdQueryHandler)
    factoryOf(::GetSubscriptionsByUserIdQueryHandler)
    factoryOf(::GetExpiredSubscriptionsQueryHandler)
    factoryOf(::GetSubscriptionsNeedingVerificationQueryHandler)
    factoryOf(::HasActiveSubscriptionQueryHandler)
}