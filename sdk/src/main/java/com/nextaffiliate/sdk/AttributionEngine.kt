package com.nextaffiliate.sdk

/**
 * Pure, framework-free implementation of the attribution behaviour. Depends only on the
 * injected [HttpClient] and [AttributionStore] seams so it is fully unit-testable on the JVM.
 *
 * Every method is best-effort: all failures are swallowed and surfaced as `null`. Nothing
 * here may throw to the host app.
 */
internal class AttributionEngine(
    private val config: NextAffiliateConfig,
    private val http: HttpClient,
    private val store: AttributionStore,
) {

    fun handleLink(url: String): Attribution? {
        return try {
            val scheme = LinkParser.schemeOf(url) ?: return null
            when {
                scheme == config.scheme.lowercase() -> handleSchemeLink(url)
                LinkParser.isTrackingLink(url) -> resolveUniversalLink(url)
                else -> null
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun handleSchemeLink(url: String): Attribution? {
        val params = LinkParser.queryParams(url)
        val nxPb = params["nx_pb"]?.takeIf { it.isNotEmpty() }
        val clickId = params["nx_click_id"]?.takeIf { it.isNotEmpty() }
        if (nxPb == null && clickId == null) return null
        val route = params["route"]?.takeIf { it.isNotEmpty() }
        store.save(nxPb = nxPb, clickId = clickId, source = AttributionSource.SCHEME, route = route)
        return store.read()
    }

    /**
     * Re-hits the tracking redirect with a spoofed desktop UA, does NOT follow the redirect,
     * and reads `nx_pb` out of the `Location` header. No `nx_pb` ⇒ not attributed.
     */
    private fun resolveUniversalLink(url: String): Attribution? {
        val response = http.getNoRedirect(url, DESKTOP_USER_AGENT, config.timeoutMs) ?: return null
        if (response.statusCode !in 300..399) return null
        val location = response.header("Location") ?: return null
        val nxPb = LinkParser.queryParamsFromLocation(location)["nx_pb"]
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        // The route lives on the INCOMING link the app was opened with, not on the resolved
        // Location header (which is the offer URL).
        val route = LinkParser.queryParams(url)["route"]?.takeIf { it.isNotEmpty() }
        store.save(
            nxPb = nxPb,
            clickId = null,
            source = AttributionSource.UNIVERSAL_LINK,
            route = route,
        )
        return store.read()
    }

    /** Deferred match, guarded to run at most once per install via a persisted flag. */
    fun checkDeferredOnFirstLaunch(): Attribution? {
        return try {
            if (store.isDeferredChecked()) return store.read()
            // Mark before the call so a crash/retry can't trigger a second deferred match.
            store.markDeferredChecked()

            val url = "${config.resolvedDeferredMatchBaseUrl}/trk/deferred-match"
            val response = http.postJson(url, """{"platform":"android"}""", config.timeoutMs)
                ?: return store.read()
            if (response.statusCode !in 200..299) return store.read()

            val body = response.body ?: return store.read()
            if (!parseMatched(body)) return store.read()
            val clickId = parseClickId(body)?.takeIf { it.isNotEmpty() } ?: return store.read()

            store.save(nxPb = null, clickId = clickId, source = AttributionSource.DEFERRED, route = null)
            store.read()
        } catch (t: Throwable) {
            null
        }
    }

    fun getAttribution(): Attribution? = try {
        store.read()
    } catch (t: Throwable) {
        null
    }

    fun clearAttribution() {
        try {
            store.clearAttribution()
        } catch (t: Throwable) {
            // best-effort
        }
    }

    // --- Minimal, dependency-free JSON extraction (the response shape is tiny & fixed). ---

    private fun parseMatched(body: String): Boolean =
        Regex("\"matched\"\\s*:\\s*(true|false)")
            .find(body)?.groupValues?.get(1) == "true"

    private fun parseClickId(body: String): String? =
        Regex("\"clickId\"\\s*:\\s*\"([^\"]*)\"")
            .find(body)?.groupValues?.get(1)

    companion object {
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
