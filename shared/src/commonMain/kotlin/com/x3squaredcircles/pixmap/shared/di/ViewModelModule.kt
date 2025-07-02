// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/presentation/di/ViewModelModule.kt
package com.x3squaredcircles.pixmap.shared.presentation.di

import com.x3squaredcircles.pixmap.shared.presentation.viewmodels.*
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/**
 * Dependency injection module for view models
 * Provides all view model instances with their required dependencies
 */
val viewModelModule = module {

    // Base view model components
    factoryOf(::BaseViewModel)

    // Core view models for main application features
    factoryOf(::LocationViewModel)

    factoryOf(::LocationsViewModel)
    factoryOf(::WeatherViewModel)
    factoryOf(::TipsViewModel)
    factoryOf(::SettingsViewModel)

    // Additional view models can be added here as the application grows
    // Examples of future view models that might be needed:
    // factoryOf(::MainViewModel)
    // factoryOf(::NavigationViewModel)
    // factoryOf(::PhotographyViewModel)
    // factoryOf(::MapViewModel)
    // factoryOf(::ProfileViewModel)
    // factoryOf(::SyncViewModel)
    // factoryOf(::OnboardingViewModel)
    // factoryOf(::TutorialViewModel)
}