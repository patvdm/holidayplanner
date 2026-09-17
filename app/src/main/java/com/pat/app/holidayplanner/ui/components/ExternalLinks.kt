package com.pat.app.holidayplanner.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri

private const val TAG = "ExternalLinks"

/**
 * Hands a URL to whichever app claims it: Google Maps for a maps link, Instagram for a reel, the
 * browser otherwise. Returns false when nothing on the device can open it.
 */
fun Context.openExternalUrl(url: String): Boolean {
    if (url.isBlank()) return false
    return try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "nothing can open $url: ${e.message}")
        false
    }
}
