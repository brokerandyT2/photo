//shared/src/androidMain/kotlin/com/x3squaredcircles/pixmap/android/services/AndroidLocationService.kt
package com.x3squaredcircles.pixmap.android.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.x3squaredcircles.pixmap.shared.common.Result
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate
import com.x3squaredcircles.pixmap.shared.services.ILocationService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Android implementation of location service using Google Play Services
 */
class AndroidLocationService(
    private val context: Context
) : ILocationService {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    override suspend fun getCurrentLocation(): Result<Coordinate> {
        return if (hasPermission() && isLocationEnabled()) {
            suspendCancellableCoroutine { continuation ->
                val cancellationToken = CancellationTokenSource()

                continuation.invokeOnCancellation {
                    cancellationToken.cancel()
                }

                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationToken.token
                ).addOnSuccessListener { location ->
                    if (location != null) {
                        try {
                            val coordinate = Coordinate.createValidated(location.latitude, location.longitude)
                            continuation.resume(Result.success(coordinate))
                        } catch (e: Exception) {
                            continuation.resume(Result.failure(e))
                        }
                    } else {
                        continuation.resume(Result.failure(Exception("Unable to get current location")))
                    }
                }.addOnFailureListener { exception ->
                    continuation.resume(Result.failure(exception))
                }
            }
        } else {
            when {
                !hasPermission() -> Result.failure(Exception("Location permission not granted"))
                !isLocationEnabled() -> Result.failure(Exception("Location services disabled"))
                else -> Result.failure(Exception("Unknown location error"))
            }
        }
    }

    override suspend fun getLastKnownLocation(): Result<Coordinate> {
        return if (hasPermission()) {
            suspendCancellableCoroutine { continuation ->
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            try {
                                val coordinate = Coordinate.createValidated(location.latitude, location.longitude)
                                continuation.resume(Result.success(coordinate))
                            } catch (e: Exception) {
                                continuation.resume(Result.failure(e))
                            }
                        } else {
                            continuation.resume(Result.failure(Exception("No last known location available")))
                        }
                    }
                    .addOnFailureListener { exception ->
                        continuation.resume(Result.failure(exception))
                    }
            }
        } else {
            Result.failure(Exception("Location permission not granted"))
        }
    }

    override fun hasPermission(): Boolean {
        val fineLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocationPermission || coarseLocationPermission
    }

    override suspend fun requestPermission(): Boolean {
        return hasPermission()
    }

    override fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}