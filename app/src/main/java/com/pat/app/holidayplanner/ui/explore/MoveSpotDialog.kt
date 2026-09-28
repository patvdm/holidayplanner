package com.pat.app.holidayplanner.ui.explore

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.local.SpotEntity
import com.pat.app.holidayplanner.data.local.SpotHome
import com.pat.app.holidayplanner.domain.CountryCatalog
import com.pat.app.holidayplanner.ui.components.PickFromListDialog

/**
 * Everywhere [spot] could move to, leaving out where it already is. The countries of [hereFirst]
 * lead, since a spot filed in the wrong place is usually one town over, not one country over.
 */
fun spotHomeChoices(homes: List<SpotHome>, spot: SpotEntity, hereFirst: String?): List<SpotHome> =
    homes
        .filterNot { home ->
            if (spot.parentCityId.isEmpty()) {
                home.isCountry && home.countryId == spot.parentCountryId
            } else {
                home.cityId == spot.parentCityId
            }
        }
        .sortedBy { it.countryId != hereFirst }

/** Where to file a spot: any country itself, or any city in one. */
@Composable
fun MoveSpotDialog(
    spot: SpotEntity,
    homes: List<SpotHome>,
    hereFirst: String?,
    onPick: (SpotHome) -> Unit,
    onDismiss: () -> Unit,
) {
    val choices = spotHomeChoices(homes, spot, hereFirst)
    PickFromListDialog(
        title = stringResource(R.string.spot_move),
        options = choices.mapIndexed { index, home ->
            index.toString() to if (home.isCountry) {
                "${CountryCatalog.flag(home.isoCode)} ${home.countryName}".trim()
            } else {
                "${home.countryName} › ${home.cityName}"
            }
        },
        onPick = { picked -> picked.toIntOrNull()?.let(choices::getOrNull)?.let(onPick) },
        onDismiss = onDismiss,
    )
}
