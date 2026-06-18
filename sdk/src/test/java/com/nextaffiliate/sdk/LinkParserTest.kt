package com.nextaffiliate.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkParserTest {

    @Test
    fun `schemeOf returns lower-cased scheme`() {
        assertEquals("myapp", LinkParser.schemeOf("MyApp://open?x=1"))
        assertEquals("https", LinkParser.schemeOf("https://a.b/trk/x"))
        assertNull(LinkParser.schemeOf("not a url"))
    }

    @Test
    fun `isTrackingLink detects trk path on https`() {
        assertTrue(LinkParser.isTrackingLink("https://acme.next-ads-server-dev.com/trk/abc"))
        assertFalse(LinkParser.isTrackingLink("https://acme.next-ads-server-dev.com/home"))
        assertFalse(LinkParser.isTrackingLink("myapp://open"))
    }

    @Test
    fun `queryParams parses and decodes`() {
        val params = LinkParser.queryParams("myapp://open?nx_pb=A%20B&nx_click_id=c1")
        assertEquals("A B", params["nx_pb"])
        assertEquals("c1", params["nx_click_id"])
    }

    @Test
    fun `queryParamsFromLocation handles absolute url`() {
        val params = LinkParser.queryParamsFromLocation("https://s.com/p?nx_pb=TOKEN&u=1")
        assertEquals("TOKEN", params["nx_pb"])
    }

    @Test
    fun `queryParamsFromLocation handles relative path`() {
        val params = LinkParser.queryParamsFromLocation("/landing?nx_pb=TOKEN")
        assertEquals("TOKEN", params["nx_pb"])
    }

    @Test
    fun `queryParamsFromLocation with no query is empty`() {
        assertTrue(LinkParser.queryParamsFromLocation("https://s.com/p").isEmpty())
    }
}
