package com.pat.app.holidayplanner.ui.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * A quadcopter seen from above, for spots marked as good for flying a drone: four rotor guards on
 * crossed arms around a round body. Material has no drone of its own. Drawn in one colour on the
 * usual 24 by 24 grid, so an Icon's tint recolours it like any other.
 */
val DroneIcon: ImageVector by lazy {
    val ink = SolidColor(Color.Black)
    ImageVector.Builder(
        name = "Drone",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(pathData = addPathNodes(ROTOR_GUARDS), stroke = ink, strokeLineWidth = 2f)
        addPath(pathData = addPathNodes(ARMS), stroke = ink, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round)
        addPath(pathData = addPathNodes(BODY_AND_HUBS), fill = ink)
    }.build()
}

private const val ROTOR_GUARDS =
    "M2,5.5a3.5,3.5 0 1,0 7,0a3.5,3.5 0 1,0 -7,0" +
        "M15,5.5a3.5,3.5 0 1,0 7,0a3.5,3.5 0 1,0 -7,0" +
        "M2,18.5a3.5,3.5 0 1,0 7,0a3.5,3.5 0 1,0 -7,0" +
        "M15,18.5a3.5,3.5 0 1,0 7,0a3.5,3.5 0 1,0 -7,0"

private const val ARMS = "M8,8L16,16M16,8L8,16"

private const val BODY_AND_HUBS =
    "M9,12a3,3 0 1,0 6,0a3,3 0 1,0 -6,0" +
        "M4.5,5.5a1,1 0 1,0 2,0a1,1 0 1,0 -2,0" +
        "M17.5,5.5a1,1 0 1,0 2,0a1,1 0 1,0 -2,0" +
        "M4.5,18.5a1,1 0 1,0 2,0a1,1 0 1,0 -2,0" +
        "M17.5,18.5a1,1 0 1,0 2,0a1,1 0 1,0 -2,0"
