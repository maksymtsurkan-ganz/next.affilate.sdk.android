package com.nextaffiliate.sdk

/** In-memory [AttributionStore] for unit tests. */
internal class FakeAttributionStore : AttributionStore {
    private var nxPb: String? = null
    private var clickId: String? = null
    private var source: AttributionSource? = null
    private var deferredChecked = false

    override fun save(nxPb: String?, clickId: String?, source: AttributionSource) {
        this.nxPb = nxPb
        this.clickId = clickId
        this.source = source
    }

    override fun read(): Attribution? {
        val s = source ?: return null
        if (nxPb == null && clickId == null) return null
        return Attribution(nxPb = nxPb, clickId = clickId, source = s)
    }

    override fun clearAttribution() {
        nxPb = null
        clickId = null
        source = null
    }

    override fun isDeferredChecked(): Boolean = deferredChecked

    override fun markDeferredChecked() {
        deferredChecked = true
    }
}

/** Configurable [HttpClient] stub for unit tests. */
internal class FakeHttpClient(
    var getResponse: HttpResponse? = null,
    var postResponse: HttpResponse? = null,
) : HttpClient {
    var lastGetUrl: String? = null
    var lastUserAgent: String? = null
    var lastPostUrl: String? = null
    var lastPostBody: String? = null
    var getCallCount = 0
    var postCallCount = 0

    override fun getNoRedirect(url: String, userAgent: String, timeoutMs: Int): HttpResponse? {
        getCallCount++
        lastGetUrl = url
        lastUserAgent = userAgent
        return getResponse
    }

    override fun postJson(url: String, jsonBody: String, timeoutMs: Int): HttpResponse? {
        postCallCount++
        lastPostUrl = url
        lastPostBody = jsonBody
        return postResponse
    }
}
