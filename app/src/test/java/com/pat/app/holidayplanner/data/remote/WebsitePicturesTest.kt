package com.pat.app.holidayplanner.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebsitePicturesTest {

    @Test
    fun `the open graph picture is taken, made absolute`() {
        val html = """<head><meta property="og:image" content="/img/terrace.jpg"></head>"""

        assertEquals("https://cafedeklos.nl/img/terrace.jpg", sharePictureIn(html, "https://cafedeklos.nl/menu"))
    }

    @Test
    fun `either quote style and attribute order work, and the secure address wins`() {
        val html = """
            <meta content='https://x.nl/a.jpg' property='og:image'>
            <meta property="og:image:secure_url" content="https://x.nl/b.jpg">
        """

        assertEquals("https://x.nl/b.jpg", sharePictureIn(html, "https://x.nl"))
    }

    @Test
    fun `the twitter picture stands in when there is no open graph one`() {
        val html = """<meta name="twitter:image" content="https://x.nl/c.jpg?w=1200&amp;h=630">"""

        assertEquals("https://x.nl/c.jpg?w=1200&h=630", sharePictureIn(html, "https://x.nl"))
    }

    @Test
    fun `a logo or an svg is not a picture of the place`() {
        fun shared(picture: String) = """<meta property="og:image" content="$picture">"""
        assertNull(sharePictureIn(shared("https://x.nl/logo-white.png"), "https://x.nl"))
        assertNull(sharePictureIn(shared("https://x.nl/brand.svg"), "https://x.nl"))
        assertNull(sharePictureIn("<html><head></head></html>", "https://x.nl"))
    }
}
