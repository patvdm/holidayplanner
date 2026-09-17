package com.pat.app.holidayplanner

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pat.app.holidayplanner.ui.AppRoot
import com.pat.app.holidayplanner.ui.theme.HolidayPlannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Every menu is a separate window, and closing one hands focus back here. Left to decide for
        // itself the system would sometimes raise the keyboard on that hand-back, with no text field
        // anywhere near. Set in code rather than the manifest, where it was not reaching the window.
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setContent {
            HolidayPlannerTheme {
                AppRoot()
            }
        }
    }
}
