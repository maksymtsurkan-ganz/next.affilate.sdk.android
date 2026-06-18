package com.nextaffiliate.sdk

/**
 * The recovered attribution for the current install.
 *
 * [nxPb] is an opaque, server-signed token: the SDK never parses it, it only stores and
 * forwards it as-is. The NEX server decodes it at postback time.
 */
data class Attribution(
    val nxPb: String? = null,
    val clickId: String? = null,
    val source: AttributionSource,
) {
    /** True when at least one usable identifier was recovered. */
    val isAttributed: Boolean
        get() = nxPb != null || clickId != null
}

/** Where an [Attribution] was recovered from. */
enum class AttributionSource(val wireValue: String) {
    /** Custom URL scheme deep-link (e.g. `myapp://open?nx_pb=...`). */
    SCHEME("scheme"),

    /** HTTPS Universal / App Link resolved via the tracking redirect. */
    UNIVERSAL_LINK("universalLink"),

    /** Deferred match performed on first launch. */
    DEFERRED("deferred");

    companion object {
        fun fromWire(value: String?): AttributionSource? =
            entries.firstOrNull { it.wireValue == value }
    }
}
