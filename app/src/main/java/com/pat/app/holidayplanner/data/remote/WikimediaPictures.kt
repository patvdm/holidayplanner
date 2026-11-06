package com.pat.app.holidayplanner.data.remote

import android.util.Log
import com.pat.app.holidayplanner.domain.GeoPoint
import com.pat.app.holidayplanner.domain.Proximity
import com.pat.app.holidayplanner.domain.WikiLanguages
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.text.Normalizer
import kotlin.math.roundToInt

private const val TAG = "WikimediaPictures"
private const val TIMEOUT_MS = 10_000L
private const val THUMB_WIDTH = 1200
private const val WIKIVOYAGE = "https://en.wikivoyage.org/w/api.php"
private const val WIKIPEDIA = "https://en.wikipedia.org/w/api.php"
private const val COMMONS = "https://commons.wikimedia.org/w/api.php"

/** The API of the Wikipedia in [language], such as "nl". */
private fun wikipedia(language: String) = "https://$language.wikipedia.org/w/api.php"

/** Words too short or too common to prove a page is about the spot rather than its city. */
private const val MIN_TOKEN_LENGTH = 3
private val STOP_WORDS = setOf("the", "and", "der", "die", "das", "del", "della", "les", "los", "las", "van", "von")

/** A page found by name further than this from the spot is about somewhere else of the same name. */
private const val SAME_PLACE_METRES = 15_000.0

/** How far around a spot to look for articles about it. */
private const val NEAR_PAGES_METRES = 400

/** How far around a spot to look for photos taken of it. */
private const val NEAR_FILES_METRES = 200

private const val SEARCH_HITS = 3
private const val NEAR_LIMIT = 12
private const val COMMONS_HITS = 12
private const val MAX_CANDIDATES = 18

/** A page or a file as the API describes it: its title, its picture if any, and where it is if known. */
private data class Page(val title: String, val picture: String?, val at: GeoPoint?, val index: Int)

/**
 * Pictures from Wikivoyage, Wikipedia and Wikimedia Commons, which cost nothing and cover most
 * places worth a trip, and from a spot's own website where they do not.
 *
 * A country or city is looked up on Wikivoyage first. Its pages open with a wide banner kept on
 * Commons as "<Title> banner.jpg", which is the best picture of a place anyone has chosen; failing
 * that its lead image, failing that Wikipedia's page of the same name, failing that a search.
 *
 * A spot is searched for by name, and a hit is only trusted when its title shares a real word with
 * the spot's name and, when both have a position, it is near the spot: a search for "Waag" in
 * Amsterdam must not come back with the one in Paramaribo. English first, then the country's own
 * language. Failing those, articles and then Commons photos near the spot whose title names it,
 * and last the share picture of the spot's own website.
 */
class WikimediaPictures(
    private val clientFactory: () -> HttpClient = ::defaultClient,
    private val website: WebsitePictures = WebsitePictures(),
) : PictureSource {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun forPlace(name: String, within: String): Result<String?> =
        runCatchingNetwork { placePicture(name, within) }

    private suspend fun placePicture(name: String, within: String): String? {
        val guide = pageOf(WIKIVOYAGE, name)
        val fromGuide = guide?.let { banner(it.title) ?: it.picture?.takeIf { url -> url.isPhotograph() } }
        return fromGuide
            ?: pageOf(WIKIPEDIA, name)?.picture?.takeIf { it.isPhotograph() }
            ?: searchPages(WIKIPEDIA, "$name $within".trim()).firstNotNullOfOrNull { it.photo() }
    }

    override suspend fun forSpot(query: SpotPictureQuery): Result<String?> = runCatchingNetwork {
        val proof = distinctiveTokens(query)
        // A spot called nothing but its town, such as a share of "Bruges" itself, is the town: it
        // gets the town's picture, found the way a city's is.
        if (proof.isEmpty()) return@runCatchingNetwork placePicture(query.name, within = query.countryName)
        val here = query.position()
        val languages = query.languages()

        fun Page.isOfIt(): Boolean = titleProves(title, proof) && isNear(here, SAME_PLACE_METRES)

        languages.firstNotNullOfOrNull { api ->
            searchPages(api, query.searchText()).filter { it.isOfIt() }.firstNotNullOfOrNull { it.photo() }
        } ?: here?.let { point ->
            languages.firstNotNullOfOrNull { api ->
                nearPages(api, point, NEAR_PAGES_METRES).filter { it.isOfIt() }.nearest(point)
            } ?: nearFiles(point, NEAR_FILES_METRES).filter { it.isOfIt() }.nearest(point)
        } ?: query.websites.firstNotNullOfOrNull { website.sharePicture(it) }
    }

    override suspend fun candidatesForPlace(name: String, within: String): Result<List<PictureCandidate>> =
        Result.success(
            buildList {
                orNothing { pageOf(WIKIVOYAGE, name) }?.let { guide ->
                    orNothing { banner(guide.title) }?.let { add(PictureCandidate(it, PictureFrom.WIKIVOYAGE)) }
                    guide.photo()?.let { add(PictureCandidate(it, PictureFrom.WIKIVOYAGE)) }
                }
                orNothing { pageOf(WIKIPEDIA, name) }?.photo()
                    ?.let { add(PictureCandidate(it, PictureFrom.WIKIPEDIA)) }
                orNone { searchPages(WIKIPEDIA, "$name $within".trim()) }
                    .mapNotNull { it.photo() }
                    .forEach { add(PictureCandidate(it, PictureFrom.WIKIPEDIA)) }
                orNone { searchFiles("$name $within".trim()) }
                    .mapNotNull { it.photo() }
                    .forEach { add(PictureCandidate(it, PictureFrom.COMMONS)) }
            }.distinctPictures(),
        )

    override suspend fun candidatesForSpot(query: SpotPictureQuery): Result<List<PictureCandidate>> {
        val proof = distinctiveTokens(query)
        if (proof.isEmpty()) return candidatesForPlace(query.name, query.countryName)
        val here = query.position()
        return Result.success(
            (named(query, proof, here) + nearby(query, proof, here)).distinctPictures(),
        )
    }

    /** Pictures that name the spot: its Wikipedia articles, its own website, Commons photos of it. */
    private suspend fun named(query: SpotPictureQuery, proof: Set<String>, here: GeoPoint?): List<PictureCandidate> {
        fun Page.named() = titleProves(title, proof) && isNear(here, SAME_PLACE_METRES)
        val articles = query.languages().flatMap { api -> orNone { searchPages(api, query.searchText()) } }
            .filter { it.named() }
            .mapNotNull { page -> page.photo()?.let { PictureCandidate(it, PictureFrom.WIKIPEDIA) } }
        val ownSite = query.websites
            .mapNotNull { orNothing { website.sharePicture(it) } }
            .map { PictureCandidate(it, PictureFrom.WEBSITE) }
        val files = orNone { searchFiles(query.searchText()) }
            .filter { it.named() }
            .mapNotNull { page -> page.photo()?.let { PictureCandidate(it, PictureFrom.COMMONS) } }
        return articles + ownSite + files
    }

    /** Whatever is close to the spot, named or not, closest first: articles, then photos. */
    private suspend fun nearby(query: SpotPictureQuery, proof: Set<String>, here: GeoPoint?): List<PictureCandidate> {
        if (here == null) return emptyList()
        val articles = query.languages().flatMap { api -> orNone { nearPages(api, here, NEAR_PAGES_METRES) } }
        val files = orNone { nearFiles(here, NEAR_FILES_METRES) }
        return articles.offered(here, proof, PictureFrom.WIKIPEDIA) + files.offered(here, proof, PictureFrom.COMMONS)
    }

    /** The page called [title], or null when there is none. Redirects are followed. */
    private suspend fun pageOf(api: String, title: String): Page? = pages(api) {
        parameter("redirects", "1")
        parameter("prop", "pageimages")
        parameter("piprop", "thumbnail")
        parameter("pithumbsize", THUMB_WIDTH)
        parameter("titles", title)
    }.firstOrNull { it["missing"] == null }?.toPage()

    /** The Wikivoyage banner for a page, which lives on Commons under a name that follows the page's. */
    private suspend fun banner(title: String): String? = pages(WIKIVOYAGE) {
        parameter("prop", "imageinfo")
        parameter("iiprop", "url")
        parameter("iiurlwidth", THUMB_WIDTH)
        parameter("titles", "File:$title banner.jpg")
    }.firstOrNull()?.toPage()?.photo()

    /** Articles matching [query], best hit first, with their pictures and positions. */
    private suspend fun searchPages(api: String, query: String): List<Page> = pages(api) {
        parameter("generator", "search")
        parameter("gsrsearch", query)
        parameter("gsrlimit", SEARCH_HITS)
        parameter("prop", "pageimages|coordinates")
        parameter("piprop", "thumbnail")
        parameter("pithumbsize", THUMB_WIDTH)
    }.map { it.toPage() }.sortedBy { it.index }

    /** Articles about places within [metres] of [point]. */
    private suspend fun nearPages(api: String, point: GeoPoint, metres: Int): List<Page> = pages(api) {
        parameter("generator", "geosearch")
        parameter("ggscoord", "${point.lat}|${point.lng}")
        parameter("ggsradius", metres)
        parameter("ggslimit", NEAR_LIMIT)
        parameter("prop", "pageimages|coordinates")
        parameter("piprop", "thumbnail")
        parameter("pithumbsize", THUMB_WIDTH)
    }.map { it.toPage() }

    /** Photos on Commons taken within [metres] of [point]. */
    private suspend fun nearFiles(point: GeoPoint, metres: Int): List<Page> = pages(COMMONS) {
        parameter("generator", "geosearch")
        parameter("ggsnamespace", "6")
        parameter("ggscoord", "${point.lat}|${point.lng}")
        parameter("ggsradius", metres)
        parameter("ggslimit", NEAR_LIMIT)
        parameter("prop", "imageinfo|coordinates")
        parameter("iiprop", "url")
        parameter("iiurlwidth", THUMB_WIDTH)
    }.map { it.toPage() }

    /** Photos on Commons whose name or description matches [query]. */
    private suspend fun searchFiles(query: String): List<Page> = pages(COMMONS) {
        parameter("generator", "search")
        parameter("gsrnamespace", "6")
        parameter("gsrsearch", query)
        parameter("gsrlimit", COMMONS_HITS)
        parameter("prop", "imageinfo")
        parameter("iiprop", "url")
        parameter("iiurlwidth", THUMB_WIDTH)
    }.map { it.toPage() }.sortedBy { it.index }

    /** One query call, returning the pages it describes. */
    private suspend fun pages(api: String, params: HttpRequestBuilder.() -> Unit): List<JsonObject> {
        val body = clientFactory().use { client ->
            client.get(api) {
                parameter("action", "query")
                parameter("format", "json")
                parameter("formatversion", "2")
                params()
            }.bodyAsText()
        }
        return json.parseToJsonElement(body).jsonObject["query"]?.jsonObject?.get("pages")?.jsonArray
            ?.map { it.jsonObject }
            .orEmpty()
    }

    private fun JsonObject.toPage(): Page {
        val thumbnail = this["thumbnail"]?.jsonObject?.get("source")?.jsonPrimitive?.content
        val fileThumb = this["imageinfo"]?.jsonArray?.firstOrNull()?.jsonObject?.get("thumburl")?.jsonPrimitive?.content
        val where = this["coordinates"]?.jsonArray?.firstOrNull()?.jsonObject
        val lat = where?.get("lat")?.jsonPrimitive?.doubleOrNull
        val lng = where?.get("lon")?.jsonPrimitive?.doubleOrNull
        return Page(
            title = this["title"]?.jsonPrimitive?.content.orEmpty(),
            picture = thumbnail ?: fileThumb,
            at = if (lat != null && lng != null) GeoPoint(lat, lng) else null,
            index = this["index"]?.jsonPrimitive?.intOrNull ?: Int.MAX_VALUE,
        )
    }

    /** The words of a spot's name that are not also the name of where it is. */
    private fun distinctiveTokens(query: SpotPictureQuery): Set<String> {
        val place = tokens("${query.cityName} ${query.countryName}")
        return tokens(query.name).filter { it !in place && it !in STOP_WORDS }.toSet()
    }

    private fun tokens(text: String): Set<String> =
        text.plain().split(' ').filter { it.length >= MIN_TOKEN_LENGTH }.toSet()

    private suspend fun runCatchingNetwork(block: suspend () -> String?): Result<String?> = try {
        Result.success(block())
    } catch (e: IOException) {
        Log.w(TAG, "network failure: ${e.message}")
        Result.failure(e)
    } catch (e: IllegalStateException) {
        // Includes being told to slow down, which is worth another try later rather than never.
        Log.w(TAG, "client failure: ${e.message}")
        Result.failure(e)
    } catch (e: IllegalArgumentException) {
        // A body that is not the JSON we expect, which is not worth retrying either.
        Log.w(TAG, "unreadable answer: ${e.message}")
        Result.success(null)
    }

    /** For the chooser: one source failing leaves the others to fill the grid. */
    private suspend fun <T> orNothing(block: suspend () -> T?): T? = try {
        block()
    } catch (e: IOException) {
        Log.w(TAG, "candidates: ${e.message}")
        null
    } catch (e: IllegalStateException) {
        Log.w(TAG, "candidates: ${e.message}")
        null
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "candidates: ${e.message}")
        null
    }

    private suspend fun <T> orNone(block: suspend () -> List<T>): List<T> = orNothing(block).orEmpty()
}

private fun SpotPictureQuery.position(): GeoPoint? = if (lat != null && lng != null) GeoPoint(lat, lng) else null

private fun SpotPictureQuery.searchText(): String = "$name $cityName".trim()

/** English Wikipedia, then the one in the country's own language when it has one. */
private fun SpotPictureQuery.languages(): List<String> =
    listOfNotNull(WIKIPEDIA, WikiLanguages.forCountry(isoCode)?.let(::wikipedia))

/** The same picture found twice, by name and nearby say, is offered once. */
private fun List<PictureCandidate>.distinctPictures(): List<PictureCandidate> =
    distinctBy { it.url.substringBefore('?') }.take(MAX_CANDIDATES)

/** Lowercase, accents stripped, punctuation turned to spaces: "Café Central" and "cafe central" agree. */
private fun String.plain(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

/**
 * Only a JPEG is taken to be a photograph. Maps, flags, logos and diagrams arrive as PNG or SVG,
 * and a page's lead image is one of those surprisingly often.
 */
private fun String.isPhotograph(): Boolean {
    val file = substringAfterLast('/').substringBefore('?').lowercase()
    return (file.endsWith(".jpg") || file.endsWith(".jpeg")) && !file.contains("flag_of") && !file.contains("_map")
}

private fun defaultClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = true
    install(HttpTimeout) {
        requestTimeoutMillis = TIMEOUT_MS
        connectTimeoutMillis = TIMEOUT_MS
        socketTimeoutMillis = TIMEOUT_MS
    }
    defaultRequest {
        header(HttpHeaders.UserAgent, APP_USER_AGENT)
        header(HttpHeaders.Accept, "application/json")
    }
}

private fun Page.photo(): String? = picture?.takeIf { it.isPhotograph() }

/** Close enough to [here] to be the same place, or no position to say otherwise. */
private fun Page.isNear(here: GeoPoint?, metres: Double): Boolean =
    here == null || at == null || Proximity.distanceMetres(here, at) <= metres

private fun Page.metresFrom(point: GeoPoint?): Int? =
    if (point == null || at == null) null else Proximity.distanceMetres(point, at).roundToInt()

/** The photo of the closest of these to [point]. */
private fun List<Page>.nearest(point: GeoPoint): String? =
    sortedBy { it.metresFrom(point) ?: Int.MAX_VALUE }.firstNotNullOfOrNull { it.photo() }

private fun titleProves(title: String, proof: Set<String>): Boolean {
    val plain = title.plain()
    return proof.any { plain.contains(it) }
}

private fun List<Page>.offered(here: GeoPoint, proof: Set<String>, from: PictureFrom): List<PictureCandidate> =
    sortedBy { it.metresFrom(here) ?: Int.MAX_VALUE }
        .mapNotNull { page ->
            page.photo()?.let { photo ->
                PictureCandidate(photo, from, page.metresFrom(here), named = titleProves(page.title, proof))
            }
        }
