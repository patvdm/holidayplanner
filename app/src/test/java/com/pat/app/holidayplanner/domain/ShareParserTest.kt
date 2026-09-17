package com.pat.app.holidayplanner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures shaped like what the share sheet actually delivers: a caption, a title line, or a bare
 * address, with the link somewhere inside. Share text varies per app and per version, which is why
 * the URL is always regexed out rather than parsed positionally.
 */
class ShareParserTest {

    @Test
    fun `an Instagram reel share keeps the reel and drops the sharer id`() {
        val shared = ShareParser.parse(
            "Check this out https://www.instagram.com/reel/C1a2B3c4D5e/?igsh=MXY0dGx4aGZ3bWZncQ%3D%3D",
        )

        assertEquals("https://instagram.com/reel/C1a2B3c4D5e/", shared?.url)
        assertEquals(LinkSource.INSTAGRAM, shared?.source)
        assertFalse(shared?.isMapsLink ?: true)
    }

    @Test
    fun `the older Instagram parameter name is dropped too`() {
        val shared = ShareParser.parse("https://www.instagram.com/p/CxYzAbC/?igshid=MzRlODBiNWFlZA==")

        assertEquals("https://instagram.com/p/CxYzAbC/", shared?.url)
    }

    @Test
    fun `a TikTok short link is recognised`() {
        val shared = ShareParser.parse(
            "Kijk hier eens naar! https://vm.tiktok.com/ZGeAbCdEf/ Gemaakt met TikTok",
        )

        assertEquals("https://vm.tiktok.com/ZGeAbCdEf/", shared?.url)
        assertEquals(LinkSource.TIKTOK, shared?.source)
    }

    @Test
    fun `a full TikTok url loses its device parameters`() {
        val shared = ShareParser.parse(
            "https://www.tiktok.com/@someone/video/7300000000000000000" +
                "?is_from_webapp=1&sender_device=pc&web_id=123",
        )

        assertEquals("https://tiktok.com/@someone/video/7300000000000000000?web_id=123", shared?.url)
        assertEquals(LinkSource.TIKTOK, shared?.source)
    }

    @Test
    fun `a YouTube share drops the si parameter`() {
        val shared = ShareParser.parse("https://youtu.be/dQw4w9WgXcQ?si=AbCdEfGhIjKl")

        assertEquals("https://youtu.be/dQw4w9WgXcQ", shared?.url)
        assertEquals(LinkSource.YOUTUBE, shared?.source)
    }

    @Test
    fun `a YouTube watch url keeps the video id`() {
        val shared = ShareParser.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=share")

        assertEquals("https://youtube.com/watch?v=dQw4w9WgXcQ", shared?.url)
        assertEquals(LinkSource.YOUTUBE, shared?.source)
    }

    @Test
    fun `a Maps short link is flagged as a place rather than a link`() {
        val shared = ShareParser.parse("Mauritshuis\nPlein 29, Den Haag\nhttps://maps.app.goo.gl/AbCdEfGhIjK")

        assertEquals("https://maps.app.goo.gl/AbCdEfGhIjK", shared?.url)
        assertEquals(LinkSource.MAPS, shared?.source)
        assertTrue(shared?.isMapsLink ?: false)
    }

    @Test
    fun `the older Maps short form is recognised`() {
        assertEquals(LinkSource.MAPS, ShareParser.parse("https://goo.gl/maps/AbCdEf")?.source)
    }

    @Test
    fun `a full Maps url is recognised on any Google domain`() {
        assertEquals(LinkSource.MAPS, ShareParser.sourceOf("https://www.google.com/maps/place/Mauritshuis"))
        assertEquals(LinkSource.MAPS, ShareParser.sourceOf("https://www.google.nl/maps/place/Mauritshuis"))
        assertEquals(LinkSource.MAPS, ShareParser.sourceOf("https://maps.google.com/?q=52.08,4.31"))
    }

    @Test
    fun `a Google link that is not Maps is just a web link`() {
        assertEquals(LinkSource.WEB, ShareParser.sourceOf("https://www.google.com/search?q=mauritshuis"))
    }

    @Test
    fun `anything else is a web link`() {
        val shared = ShareParser.parse("https://www.mauritshuis.nl/en/visit/")

        assertEquals("https://mauritshuis.nl/en/visit/", shared?.url)
        assertEquals(LinkSource.WEB, shared?.source)
    }

    @Test
    fun `campaign parameters are stripped whatever the site`() {
        val shared = ShareParser.parse(
            "https://example.com/article?utm_source=news&utm_medium=email&id=42&utm_campaign=spring",
        )

        assertEquals("https://example.com/article?id=42", shared?.url)
    }

    @Test
    fun `the first link wins when the text has several`() {
        val shared = ShareParser.parse(
            "See https://instagram.com/reel/AAA/ and also https://example.com/other",
        )

        assertEquals("https://instagram.com/reel/AAA/", shared?.url)
    }

    @Test
    fun `sentence punctuation after a link is not part of it`() {
        assertEquals("https://example.com/page", ShareParser.parse("Look at https://example.com/page.")?.url)
        assertEquals("https://example.com/page", ShareParser.parse("(https://example.com/page)")?.url)
    }

    @Test
    fun `a fragment is dropped`() {
        assertEquals("https://example.com/page", ShareParser.normalise("https://example.com/page#section"))
    }

    @Test
    fun `the host is lower cased and www is dropped so duplicates match`() {
        val first = ShareParser.normalise("HTTPS://WWW.Instagram.com/reel/ABC/")
        val second = ShareParser.normalise("https://instagram.com/reel/ABC/")

        assertEquals(first, second)
    }

    @Test
    fun `the same reel shared twice by different people normalises the same`() {
        val mine = ShareParser.parse("https://www.instagram.com/reel/XYZ/?igsh=aaaa")?.url
        val yours = ShareParser.parse("https://www.instagram.com/reel/XYZ/?igsh=bbbb")?.url

        assertEquals(mine, yours)
    }

    @Test
    fun `text with no link at all gives nothing`() {
        assertNull(ShareParser.parse("just some words"))
        assertNull(ShareParser.parse(""))
        assertNull(ShareParser.parse(null))
    }

    @Test
    fun `a bare address with no surrounding text still works`() {
        assertEquals("https://example.com/", ShareParser.parse("https://example.com/")?.url)
    }

    @Test
    fun `an address that cannot be parsed is passed through rather than lost`() {
        val messy = "https://example.com/a path"
        assertEquals("https://example.com/a", ShareParser.parse(messy)?.url)
    }

    @Test
    fun `an unusual port is preserved`() {
        assertEquals("https://example.com:8443/page", ShareParser.normalise("https://example.com:8443/page"))
    }
}
