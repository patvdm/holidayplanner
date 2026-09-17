package com.pat.app.holidayplanner.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.pat.app.holidayplanner.domain.GeoPoint
import kotlinx.coroutines.tasks.await

private const val TAG = "DeviceLocation"

/** Where the phone is, when it will say. Null whenever it will not, for any reason. */
interface DeviceLocation {
    fun hasPermission(): Boolean

    suspend fun current(): GeoPoint?
}

/**
 * Fused location, asked for a fresh low-power fix.
 *
 * Coarse accuracy is deliberate: Near me sorts spots by distance in kilometres, which a city-block
 * fix answers perfectly well, and asking for precise location to do it would be a poor trade.
 */
class FusedDeviceLocation(context: Context) : DeviceLocation {

    private val appContext = context.applicationContext
    private val client by lazy { LocationServices.getFusedLocationProviderClient(appContext) }

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun current(): GeoPoint? {
        if (!hasPermission()) return null
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .build()
        return try {
            client.getCurrentLocation(request, null).await()
                ?.let { GeoPoint(it.latitude, it.longitude) }
        } catch (e: SecurityException) {
            // The permission can be revoked between the check above and the call.
            Log.w(TAG, "location refused: ${e.message}")
            null
        }
    }
}
