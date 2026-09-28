package com.pat.app.holidayplanner.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.core.content.IntentCompat

private const val TAG = "UpdateStatusReceiver"
private const val NO_STATUS = -999

/**
 * Hears back from Android's installer. When Android wants the person to confirm, that screen is
 * shown only if they tapped Install themselves; a background attempt leaves it for later rather
 * than popping a dialog over whatever they are doing.
 */
class UpdateStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, NO_STATUS)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null && intent.getBooleanExtra(EXTRA_INTERACTIVE, false)) {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    Log.i(TAG, "update needs a tap; offered next time the app opens")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Log.i(TAG, "update installed")
            else -> Log.w(
                TAG,
                "update not installed ($status): ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}",
            )
        }
    }

    companion object {
        const val EXTRA_INTERACTIVE = "interactive"
    }
}
