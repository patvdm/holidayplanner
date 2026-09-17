package com.pat.app.holidayplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pat.app.holidayplanner.ui.AppRoot
import com.pat.app.holidayplanner.ui.theme.HolidayPlannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HolidayPlannerTheme {
                AppRoot()
            }
        }
    }
}
