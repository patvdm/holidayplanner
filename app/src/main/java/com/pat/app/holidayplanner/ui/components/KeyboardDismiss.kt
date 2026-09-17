package com.pat.app.holidayplanner.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * Puts the keyboard away and drops focus, for use just before a sheet or menu closes.
 *
 * Every sheet and menu is its own window. Letting one close while a field inside it still holds
 * focus leaves an input session for a window that no longer exists, and the system restarts it
 * against whatever gains focus next, which is what makes the keyboard flash on screen after the
 * thing that wanted it has already gone.
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
