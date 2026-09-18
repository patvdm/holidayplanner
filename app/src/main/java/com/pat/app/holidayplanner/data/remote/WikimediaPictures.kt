package com.pat.app.holidayplanner.data.remote

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import java.io.IOException
import java.text.Normalizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "WikimediaPictures"
private const val TIMEOUT_MS = 10_000L
private const val THUMB_WIDTH = 1200
private const val WIKIVOYAGE = "https://en.wikivoyage.org/w/api.php"
private const val WIKIPEDIA = "https://en.wikipedia.org/w/api.php"

/** Wikimedia asks every client to say who it is. */
private const val USER_AGENT = "HolidayPlanner/1.0 (Android; private two-person app)"

/** Words too short or too common to prove a page is about the spot rather than its city. */
private const val MIN_TOKEN_LENGTH = 3
private val STOP_WORDS = setOf("the", "and", "der", "die", "das", "del", "della", "les", "los", "las", "van", "von")

/**
 * Pictures from Wikivoyage and Wikipedia, which cost nothing and cover most places worth a trip.
 *
 * A country or city is looked up on Wikivoyage first, whose page image is the wide banner a guide
 * opens with, and on Wikipedia when Wikivoyage has no page. A spot is searched for on Wikipedia,
 * and the first hit is only trusted when its title shares a real word with the spot's name: a
 * search for a bar nobody has written about lands on the city's page, whose picture would be wrong.
 */
class WikimediaPictures(
    private val clientFactory: () -> HttpClient = ::defaultClient,
) : PictureSource {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun forPlace(name: String, within: String): Result<String?> = runCatchingNetwork {
        byTitle(WIKIVOYAGE, name)
            ?: bySearch(WIKIPEDIA, "$name $within".trim(), mustMatch = null)
    }

    override suspend fun forSpot(name: String, cityName: String, countryName: String): Result<String?> =
        runCatchingNetwork {
            val proof = distinctiveTokens(name, cityName, countryName)
            if (proof.isEmpty()) return@runCatchingNetwork null
            bySearch(WIKIPEDIA, "$name $cityName".trim(), mustMatch = proof)
        }

    private suspend fun byTitle(api: String, title: String): String? = clientFactory().use { client ->
        val body = client.get(api) {
            parameter("action", "query")
            parameter("format", "json")
            parameter("formatversion", "2")
            parameter("redirects", "1")
            parameter("prop", "pageimages")
            parameter("piprop", "thumbnail")
            parameter("pithumbsize", THUMB_WIDTH)
            parameter("titles", title)
        }.bodyAsText()
        firstThumbnail(body, mustMatch = null)
    }

    private suspend fun bySearch(api: String, query: String, mustMatch: Set<String>?): String? =
        clientFactory().use { client ->
            val body = client.get(api) {
                parameter("action", "query")
                parameter("format", "json")
                parameter("formatversion", "2")
                parameter("generator", "search")
                parameter("gsrsearch", query)
                parameter("gsrlimit", "3")
                parameter("prop", "pageimages")
                parameter("piprop", "thumbnail")
                parameter("pithumbsize", THUMB_WIDTH)
            }.bodyAsText()
            firstThumbnail(body, mustMatch)
        }

    /** The first page with a usable picture whose title passes [mustMatch], if any. */
    private fun firstThumbnail(body: String, mustMatch: Set<String>?): String? {
        val pages = json.parseToJsonElement(body).jsonObject["query"]?.jsonObject?.get("pages")?.jsonArray
            ?: return null
        return pages.asSequence()
            .map { it.jsonObject }
            .filter { page ->
                mustMatch == null || titleProves(page["title"]?.jsonPrimitive?.content.orEmpty(), mustMatch)
            }
            .mapNotNull { page -> page["thumbnail"]?.jsonObject?.get("source")?.jsonPrimitive?.content }
            .firstOrNull { it.isPhotograph() }
    }

    private fun titleProves(title: String, proof: Set<String>): Boolean {
        val plain = title.plain()
        return proof.any { plain.contains(it) }
    }

    /** The words of a spot's name that are not also the name of where it is. */
    private fun distinctiveTokens(name: String, cityName: String, countryName: String): Set<String> {
        val place = tokens("$cityName $countryName")
        return tokens(name).filter { it !in place && it !in STOP_WORDS }.toSet()
    }

    private fun tokens(text: String): Set<String> =
        text.plain().split(' ').filter { it.length >= MIN_TOKEN_LENGTH }.toSet()

    private suspend fun runCatchingNetwork(block: suspend () -> String?): Result<String?> = try {
        Result.success(block())
    } catch (e: IOException) {
        Log.w(TAG, "network failure: ${e.message}")
        Result.failure(e)
    } catch (e: IllegalStateException) {
        Log.w(TAG, "client failure: ${e.message}")
        Result.failure(e)
    } catch (e: IllegalArgumentException) {
        // A body that is not the JSON we expect, which is not worth retrying either.
        Log.w(TAG, "unreadable answer: ${e.message}")
        Result.success(null)
    }
}

/** Lowercase, accents stripped, punctuation turned to spaces: "Café Central" and "cafe central" agree. */
private fun String.plain(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

/** A flag, a map or a logo is a picture of the page, not of the place. */
private fun String.isPhotograph(): Boolean {
    val file = substringAfterLast('/').lowercase()
    return !file.contains(".svg") && !file.contains("flag_of") && !file.contains("_map") &&
        !file.startsWith("map_") && !file.contains("logo") && !file.contains("coat_of_arms")
}

private fun defaultClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = true
    install(HttpTimeout) {
        requestTimeoutMillis = TIMEOUT_MS
        connectTimeoutMillis = TIMEOUT_MS
        socketTimeoutMillis = TIMEOUT_MS
    }
    defaultRequest {
        header(HttpHeaders.UserAgent, USER_AGENT)
        header(HttpHeaders.Accept, "application/json")
    }
}
