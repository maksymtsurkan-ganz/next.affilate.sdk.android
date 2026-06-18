package com.nextaffiliate.sdk

import java.net.URI
import java.net.URLDecoder

/**
 * Pure URL parsing helpers. Implemented with [java.net.URI] (not Android's `Uri`) so the
 * logic is exercised by plain JVM unit tests.
 */
internal object LinkParser {

    /** Lower-cased scheme of [url], or null if it cannot be parsed. */
    fun schemeOf(url: String): String? = runCatching {
        URI(url).scheme?.lowercase()
    }.getOrNull()

    /** True if [url] is an HTTPS tracking link of the form `https://.../trk/<shortCode>`. */
    fun isTrackingLink(url: String): Boolean = runCatching {
        val uri = URI(url)
        val scheme = uri.scheme?.lowercase()
        (scheme == "https" || scheme == "http") && (uri.path ?: "").contains("/trk/")
    }.getOrDefault(false)

    /** Parses the query string of [url] into a decoded key → value map. */
    fun queryParams(url: String): Map<String, String> = runCatching {
        parseQuery(URI(url).rawQuery)
    }.getOrDefault(emptyMap())

    /**
     * Parses the query string of a raw `Location` redirect target. Accepts either a full
     * URL or a relative path with a query. Returns a decoded key → value map.
     */
    fun queryParamsFromLocation(location: String): Map<String, String> {
        val rawQuery = location.substringAfter('?', missingDelimiterValue = "")
        if (rawQuery.isEmpty()) return emptyMap()
        return parseQuery(rawQuery)
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrEmpty()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        for (pair in rawQuery.split('&')) {
            if (pair.isEmpty()) continue
            val idx = pair.indexOf('=')
            val key = if (idx >= 0) pair.substring(0, idx) else pair
            val value = if (idx >= 0) pair.substring(idx + 1) else ""
            result[decode(key)] = decode(value)
        }
        return result
    }

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value, "UTF-8")
    }.getOrDefault(value)
}
