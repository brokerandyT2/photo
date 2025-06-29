package com.x3squaredcircles.pixmap.di

import com.x3squaredcircles.pixmap.ui.screens.LocationScreenModel
import org.koin.dsl.module

/**
 * Dependency injection module for navigation and screen models
 */
val navigationModule = module {
    factory { LocationScreenModel(get()) }
}