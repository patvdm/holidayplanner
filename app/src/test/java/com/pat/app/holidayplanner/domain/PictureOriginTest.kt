package com.pat.app.holidayplanner.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PictureOriginTest {

    @Test
    fun `a written origin is believed`() {
        assertEquals(PictureOrigin.CHOSEN, PictureOrigin.of("https://upload.wikimedia.org/x.jpg", "CHOSEN"))
        assertEquals(PictureOrigin.PHOTO, PictureOrigin.of("none", "PHOTO"))
    }

    @Test
    fun `without one, nothing yet or a Wikimedia picture is the app's own doing`() {
        assertEquals(PictureOrigin.AUTO, PictureOrigin.of("", ""))
        assertEquals(PictureOrigin.AUTO, PictureOrigin.of(NO_PICTURE, ""))
        assertEquals(PictureOrigin.AUTO, PictureOrigin.of("https://upload.wikimedia.org/x.jpg", ""))
    }

    @Test
    fun `without one, any other picture was chosen by somebody`() {
        assertEquals(PictureOrigin.CHOSEN, PictureOrigin.of("https://static-cms.routard.com/x.jpg", ""))
        assertEquals(PictureOrigin.CHOSEN, PictureOrigin.of("https://holiday-planner.workers.dev/photos/a.jpg", ""))
    }

    @Test
    fun `a blank picture stores no origin, so the search may run`() {
        assertEquals("", PictureOrigin.CHOSEN.stored(""))
        assertEquals("AUTO", PictureOrigin.AUTO.stored("https://x.jpg"))
    }
}
