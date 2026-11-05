package com.pat.app.holidayplanner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.domain.OpenPeriod
import com.pat.app.holidayplanner.domain.OpeningHours
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** "18:00–01:00", in the phone's own clock style. */
fun OpenPeriod.label(): String = "${open.format(TimeFormat)}–${close.format(TimeFormat)}"

/** A day's stretches, "12:00–15:00, 18:00–23:00", or null on a day it does not open. */
fun List<OpenPeriod>.label(): String? = takeIf { it.isNotEmpty() }?.joinToString(", ") { it.label() }

/** One line for [day]: its hours, closed, or open all day. Null while the hours are unknown. */
@Composable
fun dayHoursText(hours: OpeningHours, day: DayOfWeek): String? = when {
    hours.always -> stringResource(R.string.hours_always)
    hours.unknown -> null
    else -> hours.on(day).label() ?: stringResource(R.string.hours_closed)
}

/**
 * The week, Monday first, with today picked out. Unknown hours say so plainly, since "no hours"
 * should not read as "closed".
 */
@Composable
fun WeekHours(hours: OpeningHours, today: LocalDate = LocalDate.now()) {
    if (hours.unknown) {
        Text(
            text = stringResource(R.string.hours_unknown),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column {
        DayOfWeek.entries.forEach { day ->
            val isToday = day == today.dayOfWeek
            val weight = if (isToday) FontWeight.Bold else FontWeight.Normal
            Row {
                Text(
                    text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = weight,
                    modifier = Modifier.width(DAY_COLUMN),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = dayHoursText(hours, day).orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = weight,
                )
            }
        }
    }
}

private val DAY_COLUMN = 48.dp
