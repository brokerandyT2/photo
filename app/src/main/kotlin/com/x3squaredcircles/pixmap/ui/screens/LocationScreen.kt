package com.x3squaredcircles.pixmap.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.x3squaredcircles.pixmap.R
import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.ui.components.LocationCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationScreen(
    locations: List<LocationDto>,
    onLocationClick: (LocationDto) -> Unit,
    onAddLocationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(stringResource(R.string.app_name))
            },
            actions = {
                IconButton(onClick = onAddLocationClick) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_photo)
                    )
                }
            }
        )

        if (locations.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "No locations saved yet",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Button(onClick = onAddLocationClick) {
                        Text("Add Your First Location")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(locations) { location ->
                    LocationCard(
                        location = location,
                        onClick = { onLocationClick(location) }
                    )
                }
            }
        }
    }
}