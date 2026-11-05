package com.pat.app.holidayplanner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.local.SpotEntity
import com.pat.app.holidayplanner.data.repo.HOURS_BULK_SKIP_DAYS
import com.pat.app.holidayplanner.data.repo.HoursRun
import com.pat.app.holidayplanner.data.repo.OpeningHoursLookup
import com.pat.app.holidayplanner.data.repo.canLookUpHours
import com.pat.app.holidayplanner.domain.LocalTimes
import com.pat.app.holidayplanner.domain.OpeningHours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * A spot's opening hours on its own screen, with the one way to fetch them: hours are only ever
 * looked up because somebody asked. The button asks again whatever is there, to refresh hours that
 * have changed or to try once more where Google had none.
 */
@Composable
fun SpotHoursSection(spot: SpotEntity, lookup: OpeningHoursLookup = koinInject()) {
    if (!lookup.isAvailable) return
    val busy by lookup.busy.collectAsStateWithLifecycle()
    val failed by lookup.failed.collectAsStateWithLifecycle()
    val hours = OpeningHours.parse(spot.openingHours)
    val id = spot.meta.id

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.spot_opening_hours),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (hours == null) {
            Note(stringResource(R.string.hours_not_looked_up))
        } else {
            WeekHours(hours)
            Note(stringResource(R.string.hours_checked, LocalTimes.formatDay(hours.checked)))
        }
        when {
            !spot.canLookUpHours -> Note(stringResource(R.string.hours_cannot_look_up))
            id in busy -> Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Note(stringResource(R.string.hours_looking_up))
            }
            else -> {
                OutlinedButton(onClick = { lookup.lookUp(id) }) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(if (hours == null) R.string.hours_look_up else R.string.hours_look_up_again))
                }
                if (id in failed) {
                    Text(
                        text = stringResource(R.string.hours_lookup_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A run asked for but not yet confirmed: where, and the spots it would look up. */
data class HoursBulkRequest(val runId: String, val name: String, val due: List<SpotEntity>)

/** Asking for a run over a trip or a city, and the dialog that confirms it. */
@Stable
class HoursBulk internal constructor(
    val lookup: OpeningHoursLookup,
    private val scope: CoroutineScope,
) {
    var request by mutableStateOf<HoursBulkRequest?>(null)
        internal set

    /** Works out which spots [load] would look up, then asks before spending anything. */
    fun ask(runId: String, name: String, load: suspend () -> List<SpotEntity>) {
        scope.launch { request = HoursBulkRequest(runId, name, load()) }
    }

    internal fun confirm() {
        request?.let { lookup.lookUpAll(it.runId, it.name, it.due) }
        request = null
    }

    internal fun cancel() {
        request = null
    }
}

@Composable
fun rememberHoursBulk(lookup: OpeningHoursLookup = koinInject()): HoursBulk {
    val scope = rememberCoroutineScope()
    return remember(lookup, scope) { HoursBulk(lookup, scope) }
}

/**
 * Says how many spots a run would look up before it starts, since each one is a Google request,
 * or that there is nothing to do because everything here was checked this week.
 */
@Composable
fun HoursBulkDialog(bulk: HoursBulk) {
    val request = bulk.request ?: return
    val count = request.due.size
    AlertDialog(
        onDismissRequest = bulk::cancel,
        title = { Text(stringResource(R.string.hours_bulk_title)) },
        text = {
            Text(
                if (count == 0) {
                    stringResource(R.string.hours_bulk_none, request.name, HOURS_BULK_SKIP_DAYS)
                } else {
                    pluralStringResource(R.plurals.hours_bulk_message, count, count, request.name, HOURS_BULK_SKIP_DAYS)
                },
            )
        },
        confirmButton = {
            if (count > 0) TextButton(onClick = bulk::confirm) { Text(stringResource(R.string.hours_bulk_start)) }
        },
        dismissButton = {
            TextButton(onClick = bulk::cancel) {
                Text(stringResource(if (count == 0) R.string.action_close else R.string.action_cancel))
            }
        },
    )
}

/**
 * How a run is getting on, floating at the foot of the screen that started it, and what it found
 * once it is done. [shows] picks the runs that belong to this screen.
 */
@Composable
fun HoursRunBanner(shows: (HoursRun) -> Boolean, lookup: OpeningHoursLookup = koinInject()) {
    val run by lookup.run.collectAsStateWithLifecycle()
    val current = run?.takeIf(shows) ?: return
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (current.finished) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val done = stringResource(R.string.hours_bulk_done, current.name, current.found, current.total)
                    val unreached =
                        pluralStringResource(R.plurals.hours_bulk_unreached, current.unreached, current.unreached)
                    Text(
                        text = if (current.unreached == 0) done else "$done $unreached",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = lookup::dismissRun) { Text(stringResource(R.string.action_close)) }
                }
            } else {
                Text(
                    text = stringResource(R.string.hours_bulk_progress, current.name, current.done, current.total),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(
                    progress = { current.done.toFloat() / current.total },
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 4.dp),
                )
            }
        }
    }
}
