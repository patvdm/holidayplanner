package com.pat.app.holidayplanner.di

import android.content.Context
import com.pat.app.holidayplanner.ui.components.PlaceSearchTarget
import com.pat.app.holidayplanner.ui.components.PlaceSearchViewModel
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify

/**
 * Checks that every constructor in the dependency graph can actually be satisfied.
 *
 * This is reflection only: nothing is instantiated, so it runs as a plain unit test. It exists
 * because a class registered under its own type but injected through an interface it implements
 * compiles perfectly and then crashes the moment the screen that needs it opens.
 */
class AppModuleTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `every dependency in the module can be resolved`() {
        appModule.verify(
            // Koin supplies the Android context itself at startup, so no definition declares it.
            extraTypes = listOf(Context::class),
            // Handed in by the screen that opens the sheet rather than resolved from the graph.
            injections = injectedParameters(
                definition<PlaceSearchViewModel>(PlaceSearchTarget::class),
            ),
        )
    }
}
