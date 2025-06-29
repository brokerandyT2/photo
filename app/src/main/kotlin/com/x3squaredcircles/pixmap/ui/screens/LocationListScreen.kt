package com.x3squaredcircles.pixmap.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

class LocationListScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = getScreenModel<LocationScreenModel>()
        val uiState by screenModel.uiState.collectAsState()

        Box(modifier = Modifier.fillMaxSize()) {
            LocationScreen(
                locations = uiState.locations,
                onLocationClick = { location ->
                    // Navigate to location detail screen
                    navigator.push(LocationDetailScreen(location.id))
                },
                onAddLocationClick = {
                    // Navigate to add location screen
                    navigator.push(AddLocationScreen())
                }
            )

            // Loading indicator
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // Error message
            uiState.error?.let { error ->
                LaunchedEffect(error) {
                    // Show error snackbar or dialog
                }
            }
        }
    }
}

// Placeholder screens for navigation
class LocationDetailScreen(private val locationId: Int) : Screen {
    @Composable
    override fun Content() {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Location Detail: $locationId")
        }
    }
}

class AddLocationScreen : Screen {
    @Composable
    override fun Content() {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Add Location Screen")
        }
    }
}