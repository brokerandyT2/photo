// shared/core-ui/src/commonMain/kotlin/com/x3squaredcircles/pixmap/core/ui/screens/EditLocationTabScreen.kt
package com.x3squaredcircles.pixmap.core.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.x3squaredcircles.pixmap.core.ui.components.CoreTabRow
import com.x3squaredcircles.pixmap.core.ui.components.CoreTabs
import kotlinx.coroutines.launch

/**
 * Core screen for editing/viewing locations with Edit and Weather tabs
 * Part of the reusable Core UI module
 */
class EditLocationTabScreen(
    private val locationId: Int
) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = getScreenModel<EditLocationTabScreenModel>()
        val uiState by screenModel.uiState.collectAsState()

        val scope = rememberCoroutineScope()
        val pagerState = rememberPagerState(pageCount = { 2 })

        // Load location data when screen appears
        LaunchedEffect(locationId) {
            screenModel.loadLocation(locationId)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = uiState.locationTitle.takeIf { it.isNotBlank() } ?: "Edit Location"
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Core Tab Row - Simple Edit + Weather tabs
                CoreTabRow(
                    tabs = CoreTabs.editLocationTabs(),
                    pagerState = pagerState,
                    scope = scope
                )

                // Tab Content
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> {
                            // Edit Tab Content
                            EditTabContent(
                                locationId = locationId,
                                isLoading = uiState.isLoading,
                                onLocationSaved = { navigator.pop() }
                            )
                        }
                        1 -> {
                            // Weather Tab Content
                            WeatherTabContent(
                                locationId = locationId,
                                isLoading = uiState.isLoading
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditTabContent(
    locationId: Int,
    isLoading: Boolean,
    onLocationSaved: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Here you would embed the AddLocationScreen content in edit mode
            // For now, placeholder content:
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Edit Location Content",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Location ID: $locationId",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onLocationSaved) {
                    Text("Save & Close")
                }
            }
        }
    }
}

@Composable
private fun WeatherTabContent(
    locationId: Int,
    isLoading: Boolean
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Weather content placeholder
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Weather Forecast",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Location ID: $locationId",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "☀️ Sunny, 75°F",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

/**
 * UI State for EditLocationTabScreen
 */
data class EditLocationTabUiState(
    val locationTitle: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)