package com.pat.app.holidayplanner.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.domain.WalkEstimate
import java.util.Locale

private const val METRES_PER_KM = 1000.0
private const val MINUTES_PER_HOUR = 60

/** "About 5.8 km, roughly 1 h 15 min on foot". */
@Composable
fun walkEstimateText(estimate: WalkEstimate): String {
    val distance = if (estimate.metres < METRES_PER_KM) {
        stringResource(R.string.walk_metres, estimate.metres)
    } else {
        stringResource(R.string.walk_km, String.format(Locale.getDefault(), "%.1f", estimate.metres / METRES_PER_KM))
    }
    val hours = estimate.minutes / MINUTES_PER_HOUR
    val minutes = estimate.minutes % MINUTES_PER_HOUR
    val time = when {
        hours == 0 -> stringResource(R.string.walk_minutes, minutes)
        minutes == 0 -> stringResource(R.string.walk_hours, hours)
        else -> stringResource(R.string.walk_hours_minutes, hours, minutes)
    }
    return stringResource(R.string.walk_estimate, distance, time)
}
