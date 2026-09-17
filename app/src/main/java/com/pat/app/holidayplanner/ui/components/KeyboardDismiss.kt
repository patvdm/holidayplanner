package com.pat.app.holidayplanner.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * Puts the keyboard away and drops focus. For menus, which have no field of their own.
 *
 * Call it from the same composition as the menu. A menu is its own window, and letting one close
 * while the window behind still expects input makes the system offer a keyboard nobody asked for.
 */
@Composable
fun rememberKeyboardDismiss(): () -> Unit {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    return remember(keyboard, focus) {
        {
            focus.clearFocus(force = true)
            keyboard?.hide()
        }
    }
}

/**
 * Drops focus from a sheet's own field as soon as that sheet starts closing.
 *
 * This has to be called from INSIDE the sheet content, because a sheet is its own window with its
 * own focus owner: clearing focus from the screen that opened it reaches a different window
 * entirely and leaves the field focused. A focused field whose window is then destroyed is what
 * makes the keyboard reappear about half a second later, once the exit animation finishes.
 *
 * Keying on the target rather than the current value means this runs when the exit begins, leaving
 * the whole animation as room to settle before the window goes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropFocusWhileSheetCloses(sheetState: SheetState) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    LaunchedEffect(sheetState.targetValue) {
        if (sheetState.targetValue == SheetValue.Hidden) {
            focus.clearFocus(force = true)
            keyboard?.hide()
        }
    }

    // Picking a result closes the sheet outright rather than animating it away, which never sets
    // the target above. This catches those exits, still from inside the sheet's own window.
    DisposableEffect(Unit) {
        onDispose {
            focus.clearFocus(force = true)
            keyboard?.hide()
        }
    }
}
