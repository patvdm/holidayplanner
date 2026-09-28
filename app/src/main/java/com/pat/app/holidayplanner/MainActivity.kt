package com.pat.app.holidayplanner

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pat.app.holidayplanner.ui.AppRoot
import androidx.compose.runtime.LaunchedEffect
import com.pat.app.holidayplanner.ui.theme.HolidayPlannerTheme
import com.pat.app.holidayplanner.ui.theme.rememberDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dark bars from the first frame, since dark is the default; corrected below once the saved
        // choice is read, so the bar icons follow the app rather than the phone's own setting.
        enableEdgeToEdge(systemBarsFor(dark = true), systemBarsFor(dark = true))
        // Every menu is a separate window, and closing one hands focus back here. Left to decide for
        // itself the system would sometimes raise the keyboard on that hand-back, with no text field
        // anywhere near. Set in code rather than the manifest, where it was not reaching the window.
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setContent {
            val dark = rememberDarkTheme()
            LaunchedEffect(dark) { enableEdgeToEdge(systemBarsFor(dark), systemBarsFor(dark)) }
            HolidayPlannerTheme(darkTheme = dark) {
                AppRoot()
            }
        }
    }

    /** Transparent either way; the style decides whether the bar's icons are light or dark. */
    private fun systemBarsFor(dark: Boolean): SystemBarStyle =
        if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)

    override fun onResume() {
        super.onResume()
        // Re-asserted here because setting it in onCreate alone does not survive the content view.
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }
}
