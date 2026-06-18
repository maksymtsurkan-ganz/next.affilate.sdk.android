package com.nextaffiliate.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttributionEngineTest {

    private val config = NextAffiliateConfig(
        baseDomain = "next-ads-server-dev.com",
        scheme = "myapp",
        timeoutMs = 4000,
    )

    private fun engine(
        http: HttpClient = FakeHttpClient(),
        store: AttributionStore = FakeAttributionStore(),
    ) = AttributionEngine(config, http, store)

    // --- scheme URL parsing ---

    @Test
    fun `scheme link stores nx_pb and nx_click_id`() {
        val store = FakeAttributionStore()
        val result = engine(store = store)
            .handleLink("myapp://open?nx_pb=DEMO_TOKEN&nx_click_id=demo-click")

        assertEquals("DEMO_TOKEN", result?.nxPb)
        assertEquals("demo-click", result?.clickId)
        assertEquals(AttributionSource.SCHEME, result?.source)
        assertTrue(result?.isAttributed == true)
        assertEquals("DEMO_TOKEN", store.read()?.nxPb)
    }

    @Test
    fun `scheme link url-decodes param values`() {
        val result = engine()
            .handleLink("myapp://open?nx_pb=a%20b%2Bc")
        assertEquals("a b+c", result?.nxPb)
    }

    @Test
    fun `scheme link with no relevant params returns null`() {
        assertNull(engine().handleLink("myapp://open?foo=bar"))
    }

    @Test
    fun `unrelated scheme is ignored`() {
        assertNull(engine().handleLink("other://open?nx_pb=X"))
    }

    // --- universal link resolve (no redirect following) ---

    @Test
    fun `universal link reads nx_pb from Location without following redirect`() {
        val http = FakeHttpClient(
            getResponse = HttpResponse(
                statusCode = 302,
                headers = mapOf("location" to "https://store.example.com/p/1?nx_pb=SIGNED_TOKEN&utm=x"),
                body = null,
            ),
        )
        val result = engine(http = http)
            .handleLink("https://acme.next-ads-server-dev.com/trk/abc123")

        assertEquals("SIGNED_TOKEN", result?.nxPb)
        assertEquals(AttributionSource.UNIVERSAL_LINK, result?.source)
        assertEquals(1, http.getCallCount)
        assertEquals(AttributionEngine.DESKTOP_USER_AGENT, http.lastUserAgent)
    }

    @Test
    fun `universal link with no nx_pb in Location is not attributed`() {
        val http = FakeHttpClient(
            getResponse = HttpResponse(
                statusCode = 302,
                headers = mapOf("location" to "https://store.example.com/rejected"),
                body = null,
            ),
        )
        val store = FakeAttributionStore()
        val result = engine(http = http, store = store).handleLink(
            "https://acme.next-ads-server-dev.com/trk/abc123",
        )

        assertNull(result)
        assertNull(store.read())
    }

    @Test
    fun `universal link non-3xx response is not attributed`() {
        val http = FakeHttpClient(
            getResponse = HttpResponse(statusCode = 200, headers = emptyMap(), body = null),
        )
        assertNull(engine(http = http).handleLink("https://acme.next-ads-server-dev.com/trk/x"))
    }

    @Test
    fun `universal link network failure returns null and never throws`() {
        val http = FakeHttpClient(getResponse = null)
        assertNull(engine(http = http).handleLink("https://acme.next-ads-server-dev.com/trk/x"))
    }

    // --- deferred once-guard ---

    @Test
    fun `deferred match stores clickId when matched`() {
        val http = FakeHttpClient(
            postResponse = HttpResponse(
                statusCode = 200,
                headers = emptyMap(),
                body = """{"matched":true,"clickId":"click-42"}""",
            ),
        )
        val result = engine(http = http).checkDeferredOnFirstLaunch()

        assertEquals("click-42", result?.clickId)
        assertEquals(AttributionSource.DEFERRED, result?.source)
        assertTrue(result?.isAttributed == true)
    }

    @Test
    fun `deferred match sends correct url and android platform`() {
        val http = FakeHttpClient(
            postResponse = HttpResponse(200, emptyMap(), """{"matched":false}"""),
        )
        engine(http = http).checkDeferredOnFirstLaunch()

        assertEquals(
            "https://next-ads-server-dev.com/trk/deferred-match",
            http.lastPostUrl,
        )
        assertTrue(http.lastPostBody!!.contains("\"platform\":\"android\""))
    }

    @Test
    fun `deferred match runs only once per install`() {
        val http = FakeHttpClient(
            postResponse = HttpResponse(200, emptyMap(), """{"matched":true,"clickId":"c1"}"""),
        )
        val store = FakeAttributionStore()
        val e = engine(http = http, store = store)

        e.checkDeferredOnFirstLaunch()
        val second = e.checkDeferredOnFirstLaunch()

        assertEquals(1, http.postCallCount)
        // second call returns the already-stored attribution, not a fresh network result
        assertEquals("c1", second?.clickId)
    }

    @Test
    fun `deferred match not matched stores nothing`() {
        val http = FakeHttpClient(
            postResponse = HttpResponse(200, emptyMap(), """{"matched":false}"""),
        )
        val store = FakeAttributionStore()
        assertNull(engine(http = http, store = store).checkDeferredOnFirstLaunch())
        assertNull(store.read())
    }

    // --- not-attributed path ---

    @Test
    fun `getAttribution returns null when nothing stored`() {
        assertNull(engine().getAttribution())
    }

    @Test
    fun `clearAttribution wipes stored attribution but keeps deferred guard`() {
        val store = FakeAttributionStore()
        store.markDeferredChecked()
        val e = engine(store = store)
        e.handleLink("myapp://open?nx_pb=TOKEN")
        assertTrue(e.getAttribution()?.isAttributed == true)

        e.clearAttribution()

        assertNull(e.getAttribution())
        assertTrue(store.isDeferredChecked())
    }

    @Test
    fun `isAttributed is false when both ids null`() {
        val a = Attribution(nxPb = null, clickId = null, source = AttributionSource.SCHEME)
        assertFalse(a.isAttributed)
    }
}
