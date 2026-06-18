package com.nextaffiliate.sdk

import java.net.HttpURLConnection
import java.net.URL

/** Minimal HTTP response used by the SDK's network seam. */
internal data class HttpResponse(
    val statusCode: Int,
    /** Lower-cased header lookup; only the headers the SDK needs are guaranteed present. */
    val headers: Map<String, String>,
    val body: String?,
) {
    fun header(name: String): String? = headers[name.lowercase()]
}

/**
 * Injectable HTTP seam. The SDK depends only on this interface so the network can be
 * mocked in unit tests without hitting a real server.
 */
internal interface HttpClient {

    /**
     * Issues a GET that MUST NOT follow redirects, returning the raw 3xx response so the
     * caller can read the `Location` header. Used for the Universal-Link resolve.
     */
    fun getNoRedirect(url: String, userAgent: String, timeoutMs: Int): HttpResponse?

    /** Issues a POST with a JSON body. Used for the deferred match. */
    fun postJson(url: String, jsonBody: String, timeoutMs: Int): HttpResponse?
}

/** Production [HttpClient] backed by [HttpURLConnection] (no third-party deps). */
internal class UrlConnectionHttpClient : HttpClient {

    override fun getNoRedirect(url: String, userAgent: String, timeoutMs: Int): HttpResponse? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("User-Agent", userAgent)
            }
            val status = connection.responseCode
            // Read Location case-insensitively; HttpURLConnection canonicalises header names.
            val location = connection.getHeaderField("Location")
                ?: connection.getHeaderField("location")
            HttpResponse(
                statusCode = status,
                headers = if (location != null) mapOf("location" to location) else emptyMap(),
                body = null,
            )
        } catch (t: Throwable) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    override fun postJson(url: String, jsonBody: String, timeoutMs: Int): HttpResponse? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            HttpResponse(statusCode = status, headers = emptyMap(), body = body)
        } catch (t: Throwable) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
