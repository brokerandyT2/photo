// shared/core-ui/src/commonMain/kotlin/com/x3squaredcircles/pixmap/core/ui/screens/AddLocationScreen.kt
package com.x3squaredcircles.pixmap.core.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.x3squaredcircles.pixmap.core.ui.screenmodels.AddLocationScreenModel
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ErrorDisplayEventArgs

/**
 * Core UI Screen for adding/editing locations
 * Complete implementation following MAUI AddLocation patterns
 */
class AddLocationScreen(
    private val locationId: Int = 0,
    private val isEditMode: Boolean = false
) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = getScreenModel<AddLocationScreenModel>()
        val uiState by screenModel.uiState.collectAsState()

        // Load location data if editing
        LaunchedEffect(locationId) {
            if (locationId > 0) {
                screenModel.loadLocation(locationId)
            } else {
                screenModel.startLocationTracking()
            }
        }

        // Handle navigation after save
        LaunchedEffect(uiState.saveCompleted) {
            if (uiState.saveCompleted) {
                if (isEditMode) {
                    navigator.pop()
                } else {
                    screenModel.resetForNewLocation()
                }
            }
        }

        // Handle error display events
        LaunchedEffect(uiState.errorEvent) {
            uiState.errorEvent?.let { event ->
                // Handle error display - could show snackbar or dialog
                // This would integrate with your IErrorDisplayService
                screenModel.clearErrorEvent()
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (isEditMode) "Edit Location" else "Add Location") },
                    navigationIcon = if (isEditMode) {
                        {
                            IconButton(onClick = { navigator.pop() }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                    } else null
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Main content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Photo Section
                    PhotoSection(
                        photoPath = uiState.photo,
                        onPhotoClick = { screenModel.takePhoto() }
                    )

                    // Title Field
                    OutlinedTextField(
                        value = uiState.title,
                        onValueChange = screenModel::updateTitle,
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = uiState.titleError.isNotBlank()
                    )
                    if (uiState.titleError.isNotBlank()) {
                        Text(
                            text = uiState.titleError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Coordinates Section
                    CoordinatesSection(
                        latitude = uiState.latitude,
                        longitude = uiState.longitude,
                        isLocationTracking = uiState.isLocationTracking
                    )

                    // Description Field
                    OutlinedTextField(
                        value = uiState.description,
                        onValueChange = screenModel::updateDescription,
                        label = { Text("Description") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        minLines = 4,
                        maxLines = 6
                    )

                    // Save Button
                    Button(
                        onClick = { screenModel.saveLocation() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = !uiState.isBusy && uiState.canSave
                    ) {
                        Text("Save", fontSize = 16.sp)
                    }

                    // Close Modal Button (only in edit mode)
                    if (isEditMode) {
                        OutlinedButton(
                            onClick = { navigator.pop() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text("Close Location Details", fontSize = 16.sp)
                        }
                    }
                }

                // Loading Overlay
                if (uiState.isBusy) {
                    LoadingOverlay(
                        message = when {
                            uiState.isLocationTracking -> "Getting your location..."
                            uiState.isCapturingPhoto -> "Processing photo..."
                            uiState.isSaving -> "Saving location..."
                            else -> "Processing, please wait..."
                        }
                    )
                }

                // Error Display
                if (uiState.isError && uiState.errorMessage.isNotBlank()) {
                    ErrorDisplay(
                        errorMessage = uiState.errorMessage,
                        onRetry = { screenModel.retryLastOperation() },
                        onDismiss = { screenModel.clearError() },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoSection(
    photoPath: String?,
    onPhotoClick: () -> Unit
) {
    Card(
        onClick = onPhotoClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (!photoPath.isNullOrBlank() && photoPath != "landscape.png") {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(photoPath)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Location photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Placeholder content
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "📷",
                        fontSize = 48.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap to add photo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CoordinatesSection(
    latitude: Double,
    longitude: Double,
    isLocationTracking: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Coordinates",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (isLocationTracking) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Getting location...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Latitude:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (latitude != 0.0) String.format("%.6f", latitude) else "Loading...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Longitude:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (longitude != 0.0) String.format("%.6f", longitude) else "Loading...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingOverlay(
    message: String = "Processing, please wait..."
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator()
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ErrorDisplay(
    errorMessage: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Dismiss",
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextButton(onClick = onRetry) {
                    Text(
                        text = "Retry",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}