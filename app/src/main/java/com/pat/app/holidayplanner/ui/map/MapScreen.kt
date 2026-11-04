package com.pat.app.holidayplanner.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.local.SpotPin
import com.pat.app.holidayplanner.domain.MapView
import kotlinx.coroutines.flow.filter
import com.pat.app.holidayplanner.ui.components.BackTopBar
import com.pat.app.holidayplanner.ui.components.EmptyListMessage
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Every spot on one map. Each pin carries the start of its name and an icon for its kind; colour
 * says charcoal for a normal spot, orange for high priority, faded with a tick once visited
 * (see MapMarkers.kt).
 *
 * It always shows everything and only differs in where it opens, so a spot just over a city border
 * is visible rather than mysteriously missing. Your own position is drawn when location is allowed,
 * which is how you see what is near you.
 */
@Composable
fun MapScreen(
    scope: MapScope,
    onBack: () -> Unit,
    onOpenSpot: (String) -> Unit,
    viewModel: MapViewModel = koinViewModel { parametersOf(scope) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var locationAllowed by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> locationAllowed = granted }

    // Asked once on arrival. Refused is fine: the map works, it just has no blue dot.
    LaunchedEffect(Unit) {
        if (!locationAllowed) permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.map_title), onBack = onBack) },
    ) { padding ->
        if (state.pins.isEmpty()) {
            EmptyListMessage(stringResource(R.string.map_no_pins), Modifier.padding(padding))
            return@Scaffold
        }
        SpotMap(
            pins = state.pins,
            focus = state.focus,
            showMyLocation = locationAllowed,
            onOpenSpot = onOpenSpot,
            onSettled = viewModel::rememberView,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun SpotMap(
    pins: List<SpotPin>,
    focus: MapFocus?,
    showMyLocation: Boolean,
    onOpenSpot: (String) -> Unit,
    onSettled: (MapView) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<SpotPin?>(null) }
    // Both survive a trip to a spot and back, so the map is left where it was, not re-aimed.
    val camera = rememberCameraPositionState()
    var aimed by rememberSaveable { mutableStateOf(false) }

    // The pins arrive after the first composition, so the camera follows once they are known.
    LaunchedEffect(focus) {
        val target = focus ?: return@LaunchedEffect
        if (aimed) return@LaunchedEffect
        aimed = true
        camera.move(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.fromLatLngZoom(LatLng(target.centre.lat, target.centre.lng), target.zoom),
            ),
        )
        // Opened for one spot: its card is up too, so it is plain which pin is meant.
        if (target.spotId.isNotEmpty()) selected = pins.firstOrNull { it.spotId == target.spotId }
    }

    // Each time the map comes to rest after being aimed, remember the view for the next opening.
    LaunchedEffect(camera) {
        snapshotFlow { camera.isMoving }
            .filter { moving -> !moving && aimed }
            .collect {
                val at = camera.position
                onSettled(MapView(at.target.latitude, at.target.longitude, at.zoom))
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            properties = MapProperties(isMyLocationEnabled = showMyLocation),
            onMapClick = { selected = null },
        ) {
            pins.forEach { pin ->
                // Keyed on what the picture shows, so a pin is only redrawn when its look changes.
                MarkerComposable(
                    pin.name,
                    pin.pinStyle(),
                    pin.type,
                    state = rememberUpdatedMarkerState(position = LatLng(pin.lat, pin.lng)),
                    title = pin.name,
                    snippet = pin.cityName.ifBlank { pin.countryName },
                    // High priority draws over the rest where pins overlap.
                    zIndex = if (pin.pinStyle() == PinStyle.HIGH) 1f else 0f,
                    onClick = {
                        selected = pin
                        // Consume it, so the map does not also recentre and hide the card.
                        true
                    },
                ) {
                    PinMarker(pin)
                }
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
                    text = pin.cityName.ifBlank { pin.countryName },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpen) { Text(stringResource(R.string.map_open_spot)) }
        }
    }
}
