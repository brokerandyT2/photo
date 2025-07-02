// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/presentation/viewmodels/INavigationAware.kt
package com.x3squaredcircles.pixmap.shared.presentation.viewmodels

/**
 * Interface for view models that need to be notified of navigation events
 */
interface INavigationAware {
    /**
     * Called when the view model's view is navigated to
     */
    suspend fun onNavigatedTo()

    /**
     * Called when the view model's view is navigated away from
     */
    suspend fun onNavigatedFrom()
}