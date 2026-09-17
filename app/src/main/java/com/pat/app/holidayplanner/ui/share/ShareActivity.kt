package com.pat.app.holidayplanner.ui.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.net.toUri
import com.pat.app.holidayplanner.ui.theme.HolidayPlannerTheme

/**
 * Receives a link shared from another app.
 *
 * Its own activity with a dialog theme, so it pops over Instagram and closes straight back to it.
 * It never needs the main app to be running: Koin starts with the application, and everything this
 * writes goes to the local database, which sync drains later.
 */
class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = intent?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)

        setContent {
            HolidayPlannerTheme {
                ShareDialog(
                    sharedText = sharedText,
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
