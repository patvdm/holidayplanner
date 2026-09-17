package com.pat.app.holidayplanner.ui.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.local.TripEntity
import com.pat.app.holidayplanner.domain.LocalTimes
import java.time.LocalDate

private const val TRIP_LIST_MAX_HEIGHT_DP = 360

/**
 * The two questions a day route needs before it exists: which trip, and which day.
 *
 * They are asked one after the other rather than in one form, because the trip decides which dates
 * are worth offering.
 */
@Composable
fun PlanDayDialog(
    trips: List<TripEntity>,
    onPlan: (tripId: String, date: LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosenTrip by remember { mutableStateOf<TripEntity?>(null) }
    val trip = chosenTrip

    if (trip == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.plan_day)) },
            text = {
                if (trips.isEmpty()) {
                    Text(stringResource(R.string.plan_day_no_trips))
                } else {
                    Column {
                        Text(stringResource(R.string.plan_day_pick_trip))
                        TripChoices(trips) { chosenTrip = it }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    } else {
        DatePickerSheet(
            initial = LocalTimes.parseDate(trip.startDate) ?: LocalDate.now(),
            onPick = { onPlan(trip.meta.id, it) },
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun TripChoices(trips: List<TripEntity>, onPick: (TripEntity) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = TRIP_LIST_MAX_HEIGHT_DP.dp),
    ) {
        items(trips, key = { it.meta.id }) { trip ->
            ListItem(
                headlineContent = { Text(trip.name) },
                supportingContent = {
                    val start = LocalTimes.parseDate(trip.startDate)?.let(LocalTimes::formatDay)
                    if (start != null) Text(start)
                },
                modifier = Modifier.clickable { onPick(trip) },
            )
        }
    }
}
