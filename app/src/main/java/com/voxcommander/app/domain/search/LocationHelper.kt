package com.voxcommander.app.domain.search

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.voxcommander.app.utils.Logger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Simple location helper using Android's built-in LocationManager.
 * Returns last known location from GPS or network provider.
 * No Google Play Services dependency needed.
 */
object LocationHelper {

    private const val TAG = "LocationHelper"

    /**
     * Returns the last known location, or null if unavailable or no permission.
     * Tries GPS first, then network provider.
     */
    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) {
            Logger.log("No location permission granted", TAG)
            return null
        }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Try GPS first (more accurate)
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (loc != null) {
                Logger.log("Got location from GPS: lat=${loc.latitude}, lon=${loc.longitude}", TAG)
                return loc
            }
        }

        // Fallback to network provider
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            val loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (loc != null) {
                Logger.log("Got location from network: lat=${loc.latitude}, lon=${loc.longitude}", TAG)
                return loc
            }
        }

        Logger.log("No last known location available", TAG)
        return null
    }

    /**
     * Tries last known location first. If null, requests a single fresh update.
     * Use this from coroutines for weather search.
     */
    @SuppressLint("MissingPermission")
    suspend fun getLocation(context: Context): Location? {
        val lastKnown = getLastKnownLocation(context)
        if (lastKnown != null) return lastKnown

        if (!hasLocationPermission(context)) return null

        Logger.log("Requesting fresh location update...", TAG)
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> {
                Logger.log("No location provider enabled", TAG)
                return null
            }
        }

        return suspendCancellableCoroutine { cont ->
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: Location) {
                    Logger.log("Fresh location: lat=${location.latitude}, lon=${location.longitude}", TAG)
                    lm.removeUpdates(this)
                    if (cont.isActive) cont.resume(location)
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {
                    lm.removeUpdates(this)
                    if (cont.isActive) cont.resume(null)
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            }

            try {
                lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            } catch (e: Exception) {
                Logger.log("Failed to request location updates: ${e.message}", TAG)
                if (cont.isActive) cont.resume(null)
            }

            cont.invokeOnCancellation {
                try { lm.removeUpdates(listener) } catch (_: Exception) {}
            }
        }
    }

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
