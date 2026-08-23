package com.rescuemesh.app.identity

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.asDeferred

class LocationProvider(private val context: Context) {
    private val client: FusedLocationProviderClient? by lazy {
        if (isGooglePlayServicesAvailable()) {
            LocationServices.getFusedLocationProviderClient(context)
        } else {
            null
        }
    }

    private var lastLocation: android.location.Location? = null

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        val activeClient = client ?: return null
        return try {
            activeClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .asDeferred().await().also { lastLocation = it }
        } catch (e: Exception) {
            null
        }
    }

    fun getLastCachedLocation(): Location? = lastLocation

    private fun isGooglePlayServicesAvailable(): Boolean {
        val availability = GoogleApiAvailability.getInstance()
        val result = availability.isGooglePlayServicesAvailable(context)
        return result == ConnectionResult.SUCCESS
    }
}
