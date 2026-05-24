package com.offlinebot.utils

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class LatLng(val latitude: Double, val longitude: Double)

@Singleton
class LocationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("location_prefs", Context.MODE_PRIVATE)

    private val locationManager: LocationManager?
        get() = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    val lastKnownLocation: LatLng?
        get() {
            val lat = prefs.getFloat("last_lat", Float.NaN)
            val lng = prefs.getFloat("last_lng", Float.NaN)
            return if (lat.isNaN() || lng.isNaN()) null else LatLng(lat.toDouble(), lng.toDouble())
        }

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Try to get a fresh GPS location (timeout 5s), then fall back to last known cached location,
     * then fall back to SharedPreferences stored location.
     */
    suspend fun getCurrentLocation(): LatLng? {
        // Try LocationManager last known location (works even without GPS permission on some devices)
        try {
            val lm = locationManager
            if (lm != null) {
                for (provider in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
                    try {
                        val loc = lm.getLastKnownLocation(provider)
                        if (loc != null) {
                            saveLastLocation(loc.latitude, loc.longitude)
                            return LatLng(loc.latitude, loc.longitude)
                        }
                    } catch (_: SecurityException) {}
                }
            }
        } catch (_: Exception) {}

        // Try fresh GPS if permission granted
        if (hasPermission()) {
            try {
                val fresh = requestSingleUpdate()
                if (fresh != null) return fresh
            } catch (_: Exception) {}
        }

        // Absolute last resort: SharedPreferences stored location
        return lastKnownLocation
    }

    private suspend fun requestSingleUpdate(): LatLng? =
        suspendCancellableCoroutine { cont ->
            val lm = locationManager
            if (lm == null) {
                cont.resume(lastKnownLocation)
                return@suspendCancellableCoroutine
            }

            val listener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    saveLastLocation(loc.latitude, loc.longitude)
                    if (cont.isActive) cont.resume(LatLng(loc.latitude, loc.longitude))
                    try { lm.removeUpdates(this) } catch (_: Exception) {}
                }
                override fun onProviderDisabled(provider: String) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) {}
            }

            try {
                lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, Looper.getMainLooper())
            } catch (_: Exception) {
                try {
                    lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, Looper.getMainLooper())
                } catch (_: Exception) {
                    cont.resume(lastKnownLocation)
                }
            }

            // Timeout fallback after 5 seconds
            android.os.Handler(Looper.getMainLooper()).postDelayed({
                if (cont.isActive) {
                    try { lm.removeUpdates(listener) } catch (_: Exception) {}
                    cont.resume(lastKnownLocation)
                }
            }, 5000)
        }

    private fun saveLastLocation(lat: Double, lng: Double) {
        prefs.edit()
            .putFloat("last_lat", lat.toFloat())
            .putFloat("last_lng", lng.toFloat())
            .apply()
    }

    private fun isRecent(location: Location, maxAgeMs: Long): Boolean {
        return System.currentTimeMillis() - location.time < maxAgeMs
    }

    companion object {
        private const val TAG = "LocationHelper"
    }
}
