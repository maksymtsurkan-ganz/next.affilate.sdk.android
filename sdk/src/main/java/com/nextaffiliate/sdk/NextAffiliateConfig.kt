package com.nextaffiliate.sdk

/**
 * Configuration for [NextAffiliate].
 *
 * @param baseDomain merchant base domain, e.g. `next-ads-server-dev.com`.
 * @param scheme the host app's custom URL scheme, e.g. `myapp`.
 * @param timeoutMs network timeout for best-effort calls, in milliseconds.
 * @param deferredMatchBaseUrl override for the deferred-match endpoint base URL.
 *        Defaults to `https://<baseDomain>`.
 */
data class NextAffiliateConfig(
    val baseDomain: String,
    val scheme: String,
    val timeoutMs: Int = 4000,
    val deferredMatchBaseUrl: String? = null,
) {
    internal val resolvedDeferredMatchBaseUrl: String
        get() = (deferredMatchBaseUrl ?: "https://$baseDomain").trimEnd('/')
}
