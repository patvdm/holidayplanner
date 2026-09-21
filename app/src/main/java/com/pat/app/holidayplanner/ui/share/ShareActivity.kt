package com.pat.app.holidayplanner.ui.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import com.pat.app.holidayplanner.ui.theme.HolidayPlannerTheme

/**
 * Receives a link or a photo shared from another app.
 *
 * Its own activity with a dialog theme, so it pops over Instagram and closes straight back to it.
 * It never needs the main app to be running: Koin starts with the application, and everything this
 * writes goes to the local database, which sync drains later.
 */
class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val send = intent?.takeIf { it.action == Intent.ACTION_SEND }
        val sharedText = send?.getStringExtra(Intent.EXTRA_TEXT)
        val sharedImage = send
            ?.takeIf { it.type?.startsWith("image/") == true }
            ?.let { IntentCompat.getParcelableExtra(it, Intent.EXTRA_STREAM, Uri::class.java) }

        setContent {
            HolidayPlannerTheme {
                ShareDialog(
                    sharedText = sharedText,
                    sharedImage = sharedImage,
                    onClose = ::finish,
                    onOpenLink = ::openExternally,
                )
            }
        }
    }

    private fun openExternally(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
        finish()
    }
}
