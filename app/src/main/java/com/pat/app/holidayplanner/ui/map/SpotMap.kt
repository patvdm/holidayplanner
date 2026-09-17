package com.pat.app.holidayplanner.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.local.SpotPin
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.ui.components.EmptyListMessage
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Close enough to read street names, which is the scale a day of walking happens at. */
private const val CITY_ZOOM = 12f
private const val WIDE_ZOOM = 5f

/** Above this span the pins are spread across a country rather than a city. */
private const val WIDE_SPAN_DEGREES = 1.0

/**
 * Spots as pins, coloured by whether they have been visited.
 *
 * Tapping a pin raises a card over the map rather than opening the spot, so you can look at two or
 * three before committing to one.
 */
@Composable
fun SpotMap(
    scope: MapScope,
    onOpenSpot: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = koinViewModel { parametersOf(scope) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<SpotPin?>(null) }

    if (state.pins.isEmpty()) {
        EmptyListMessage(stringResource(R.string.map_no_pins), modifier)
        return
    }

    val camera = rememberCameraPositionState()

    // The pins arrive after the first composition, so the camera follows once they are known.
    LaunchedEffect(state.centre, state.spanDegrees) {
        val centre = state.centre ?: return@LaunchedEffect
        val zoom = if (state.spanDegrees > WIDE_SPAN_DEGREES) WIDE_ZOOM else CITY_ZOOM
        camera.move(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.fromLatLngZoom(LatLng(centre.lat, centre.lng), zoom),
            ),
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            onMapClick = { selected = null },
        ) {
            state.pins.forEach { pin ->
                Marker(
                    state = rememberUpdatedMarkerState(position = LatLng(pin.lat, pin.lng)),
                    title = pin.name,
                    snippet = pin.cityName,
                    icon = BitmapDescriptorFactory.defaultMarker(pin.status.markerHue()),
                    onClick = {
                        selected = pin
                        // Consume it, so the map does not also recentre and hide the card.
                        true
                    },
                )
            }
        }

        selected?.let { pin ->
            SpotCard(
                pin = pin,
                onOpen = { onOpenSpot(pin.spotId) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun SpotCard(pin: SpotPin, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(pin.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = pin.cityName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpen) { Text(stringResource(R.string.map_open_spot)) }
        }
    }
}

/** Green for somewhere you have been, red for somewhere still on the list. */
private fun SpotStatus.markerHue(): Float = when (this) {
    SpotStatus.VISITED -> BitmapDescriptorFactory.HUE_GREEN
    SpotStatus.UNVISITED -> BitmapDescriptorFactory.HUE_RED
}
