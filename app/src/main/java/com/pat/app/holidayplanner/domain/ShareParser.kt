package com.pat.app.holidayplanner.domain

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** A link pulled out of whatever text an app handed to the share sheet. */
data class SharedLink(
    /** Normalised, so the same reel shared twice looks the same. */
    val url: String,
    val source: LinkSource,
) {
    /** Maps links become spots rather than saved links, since the URL is derivable from the spot. */
    val isMapsLink: Boolean get() = source == LinkSource.MAPS
}

/** Query parameters that identify the sharer or the campaign rather than the content. */
private val TRACKING_PARAMS = setOf(
    "igsh", "igshid", "si", "feature", "fbclid", "gclid", "mibextid",
    "is_from_webapp", "sender_device", "sender_web_id", "share_app_id", "share_id",
    "_r", "_t", "_d", "checksum", "refer_method", "share_link_id", "ref", "ref_src", "ref_url",
)

private const val UTM_PREFIX = "utm_"

/** Share text is rarely just a URL, so the first http link in it is what counts. */
private val URL_PATTERN = Regex("""https?://[^\s<>"'\\]+""", RegexOption.IGNORE_CASE)

/** Trailing punctuation that belongs to the sentence, not the address. */
private const val TRAILING_JUNK = ".,;:!?)]}”’\"'"

private const val SHORTEST_USABLE_URL = 8

/**
 * Pulls a link out of shared text.
 *
 * Instagram and the rest send a caption, a title, sometimes several lines, with the URL somewhere
 * inside. The post itself is never parsed and never fetched; only the address is read.
 */
object ShareParser {

    fun parse(text: String?): SharedLink? {
        val raw = firstUrl(text) ?: return null
        val uri = parseUri(raw) ?: return SharedLink(url = raw, source = LinkSource.WEB)
        return SharedLink(url = normalise(uri) ?: raw, source = sourceOf(uri))
    }

    /**
     * The lines of the share that are not the link, in order.
     *
     * Maps shares a place as three lines: its name, its address, then the link. Those first two are
     * better than anything the URL carries, so they name the spot rather than an address does.
     */
    fun textLines(text: String?): List<String> = text.orEmpty()
        .lines()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.contains("://") }

    /** The first http(s) address in the text, with sentence punctuation trimmed off the end. */
    fun firstUrl(text: String?): String? {
        val match = text?.let { URL_PATTERN.find(it) }?.value ?: return null
        return match.trimEnd { it in TRAILING_JUNK }.takeIf { it.length > SHORTEST_USABLE_URL }
    }

    /**
     * Strips the fragment and the parameters that only say who shared it, and lowercases the scheme
     * and host. Two shares of the same thing then produce the same string, which is what makes the
     * duplicate check work.
     */
    fun normalise(url: String): String = parseUri(url)?.let(::normalise) ?: url.trim()

    fun sourceOf(url: String): LinkSource = parseUri(url)?.let(::sourceOf) ?: LinkSource.WEB

    private fun normalise(uri: URI): String? {
        val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: "https"
        val query = keptQuery(uri.rawQuery)
        val port = if (uri.port != -1) ":${uri.port}" else ""
        return buildString {
            append(scheme).append("://").append(host).append(port).append(uri.rawPath.orEmpty())
            if (query.isNotEmpty()) append('?').append(query)
        }
    }

    private fun sourceOf(uri: URI): LinkSource {
        val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.").orEmpty()
        val path = uri.rawPath.orEmpty()
        return when {
            host.contains("instagram") || host == "instagr.am" -> LinkSource.INSTAGRAM
            host.contains("tiktok") -> LinkSource.TIKTOK
            host.contains("youtube") || host == "youtu.be" -> LinkSource.YOUTUBE
            isMapsHost(host, path) -> LinkSource.MAPS
            else -> LinkSource.WEB
        }
    }

    private fun isMapsHost(host: String, path: String): Boolean = when {
        host == "maps.app.goo.gl" -> true
        host == "goo.gl" -> path.startsWith("/maps")
        host.startsWith("maps.google.") -> true
        host.startsWith("google.") || host.contains(".google.") -> path.startsWith("/maps")
        else -> false
    }

    private fun keptQuery(rawQuery: String?): String = rawQuery.orEmpty()
        .split('&')
        .filter { it.isNotEmpty() }
        .filterNot { param ->
            val name = param.substringBefore('=').lowercase(Locale.ROOT)
            name in TRACKING_PARAMS || name.startsWith(UTM_PREFIX)
        }
        .joinToString("&")

    /** A share can carry anything, so an address that will not parse is a normal outcome here. */
    private fun parseUri(url: String): URI? = try {
        URI(url.trim())
    } catch (e: URISyntaxException) {
        null
    }
}
