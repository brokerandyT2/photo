//app/src/main/kotlin/com/x3squaredcircles/pixmap/di/AndroidSubscriptionModule.kt
package com.x3squaredcircles.pixmap.di

import com.x3squaredcircles.pixmap.androidapp.services.AndroidBillingService
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ISubscriptionStoreService
import com.x3squaredcircles.pixmap.shared.presentation.viewmodels.SubscriptionViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Android-specific dependency injection module for subscription functionality
 */
val androidSubscriptionModule = module {

    // Platform-specific billing service
    singleOf(::AndroidBillingService) { bind<ISubscriptionStoreService>() }

    // View Models
    viewModel { SubscriptionViewModel(get(), get(), "default-user-id") }
}