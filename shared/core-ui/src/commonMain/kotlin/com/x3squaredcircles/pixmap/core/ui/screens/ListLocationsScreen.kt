// shared/core-ui/src/commonMain/kotlin/com/x3squaredcircles/pixmap/core/ui/screens/ListLocationsScreen.kt
package com.x3squaredcircles.pixmap.core.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.x3squaredcircles.pixmap.core.ui.screenmodels.ListLocationsScreenModel
import com.x3squaredcircles.pixmap.core.ui.screenmodels.LocationListItem

/**
 * Core UI Screen for listing locations
 * Complete implementation following MAUI LocationsPage patterns
 */
class ListLocationsScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = getScreenModel<ListLocationsScreenModel>()
        val uiState by screenModel.uiState.collectAsState()

        // Handle error display events
        LaunchedEffect(uiState.errorEvent) {
            uiState.errorEvent?.let { event ->
                // Handle error display - could show snackbar or dialog
                screenModel.clearErrorEvent()
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Locations") },
                    actions = {
                        IconButton(onClick = { screenModel.refreshLocations() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        navigator.push(AddLocationScreen())
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Location")
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when {
                    uiState.isLoading -> {
                        LoadingContent()
                    }
                    uiState.isEmpty -> {
                        EmptyContent(
                            onAddLocationClick = {
                                navigator.push(AddLocationScreen())
                            }
                        )
                    }
                    uiState.isError -> {
                        ErrorContent(
                            errorMessage = uiState.errorMessage,
                            onRetryClick = { screenModel.retryLastOperation() },
                            canRetry = uiState.canRetry
                        )
                    }
                    else -> {
                        LocationsList(
                            locations = uiState.locations,
                            onLocationClick = { location ->
                                screenModel.selectLocation(location.id)
                                navigator.push(AddLocationScreen(location.id, isEditMode = true))
                            },
                            onMapClick = { location ->
                                screenModel.openLocationInMap(location)
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun LoadingContent() {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }

    @Composable
    private fun EmptyContent(
        onAddLocationClick: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "No locations found",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Add a new location by tapping the + button below",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onAddLocationClick) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Location")
            }
        }
    }

    @Composable
    private fun ErrorContent(
        errorMessage: String,
        onRetryClick: () -> Unit,
        canRetry: Boolean
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (canRetry) {
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onRetryClick) {
                    Text("Retry")
                }
            }
        }
    }

    @Composable
    private fun LocationsList(
        locations: List<LocationListItem>,
        onLocationClick: (LocationListItem) -> Unit,
        onMapClick: (LocationListItem) -> Unit
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(locations) { location ->
                LocationListItemCard(
                    location = location,
                    onClick = { onLocationClick(location) },
                    onMapClick = { onMapClick(location) }
                )
            }
        }
    }

    @Composable
    private fun LocationListItemCard(
        location: LocationListItem,
        onClick: () -> Unit,
        onMapClick: () -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Photo
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(location.photo ?: "landscape.png")
                        .crossfade(true)
                        .build(),
                    contentDescription = "Location photo",
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Title and coordinates
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = location.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = location.formattedCoordinates,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Map button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(onClick = onMapClick) {
                        Icon(
                            Icons.Default.Map,
                            contentDescription = "Open in map",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Go to Location",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}