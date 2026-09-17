package com.pat.app.holidayplanner.ui.nearme

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.ui.components.BackTopBar
import com.pat.app.holidayplanner.ui.components.EmptyListMessage
import java.util.Locale
import org.koin.compose.viewmodel.koinViewModel

/**
 * Unvisited spots close to where you are, nearest first.
 *
 * The permission is asked for on arrival rather than behind a button, because the screen has
 * nothing at all to show without it, and the reason is spelled out above the prompt.
 */
@Composable
fun NearMeScreen(
    onBack: () -> Unit,
    onOpenSpot: (String) -> Unit,
    viewModel: NearMeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.locate() }

    LaunchedEffect(Unit) { viewModel.locate() }

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.near_me), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            RadiusChips(state.radiusMetres, viewModel::setRadius)

            when {
                state.locating -> Centred { CircularProgressIndicator() }

                state.permissionDenied -> Centred {
                    Text(
                        text = stringResource(R.string.near_me_rationale),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = { permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text(stringResource(R.string.near_me_allow))
                    }
                }

                !state.located -> Centred {
                    Text(stringResource(R.string.near_me_no_fix), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = viewModel::locate, modifier = Modifier.padding(top = 12.dp)) {
                        Text(stringResource(R.string.near_me_retry))
                    }
                }

                state.nothingNear -> EmptyListMessage(
                    stringResource(R.string.near_me_nothing, distanceLabel(state.radiusMetres.toDouble())),
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.nearby, key = { it.pin.spotId }) { nearby ->
                        ListItem(
                            headlineContent = { Text(nearby.pin.name) },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        R.string.near_me_where,
                                        nearby.pin.cityName,
                                        nearby.pin.countryName,
                                    ),
                                )
                            },
                            trailingContent = { Text(distanceLabel(nearby.metres)) },
                            modifier = Modifier.clickable { onOpenSpot(nearby.pin.spotId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RadiusChips(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RADIUS_OPTIONS_M.forEach { metres ->
            FilterChip(
                selected = metres == selected,
                onClick = { onSelect(metres) },
                label = { Text(distanceLabel(metres.toDouble())) },
            )
        }
    }
}

@Composable
private fun Centred(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}

private const val METRES_PER_KM = 1000.0

/** Metres while it is still a walk you can picture, kilometres once it is not. */
@Composable
private fun distanceLabel(metres: Double): String = if (metres < METRES_PER_KM) {
    stringResource(R.string.near_me_metres, metres.toInt())
} else {
    stringResource(R.string.near_me_km, String.format(Locale.getDefault(), "%.1f", metres / METRES_PER_KM))
}
