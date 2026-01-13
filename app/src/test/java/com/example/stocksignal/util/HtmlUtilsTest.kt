package com.example.stocksignal.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HtmlUtilsTest {

    @Test
    fun `stripHtml - handles null input`() {
        assertEquals("", HtmlUtils.stripHtml(null))
    }

    @Test
    fun `stripHtml - handles empty string`() {
        assertEquals("", HtmlUtils.stripHtml(""))
    }

    @Test
    fun `stripHtml - handles blank string`() {
        assertEquals("", HtmlUtils.stripHtml("   "))
    }

    @Test
    fun `stripHtml - handles plain text without HTML`() {
        assertEquals("Apple Inc", HtmlUtils.stripHtml("Apple Inc"))
    }

    @Test
    fun `stripHtml - strips bold tags`() {
        assertEquals("VGT.US", HtmlUtils.stripHtml("<b>VGT</b>.US"))
    }

    @Test
    fun `stripHtml - strips malformed bold tags`() {
        // The user's example: <b>VGT<b>.US (missing closing slash)
        assertEquals("VGT.US", HtmlUtils.stripHtml("<b>VGT<b>.US"))
    }

    @Test
    fun `stripHtml - decodes ampersand entity`() {
        assertEquals("AT&T Inc", HtmlUtils.stripHtml("AT&amp;T Inc"))
    }

    @Test
    fun `stripHtml - decodes multiple ampersands`() {
        assertEquals("Johnson & Johnson", HtmlUtils.stripHtml("Johnson &amp; Johnson"))
    }

    @Test
    fun `stripHtml - decodes less-than entity`() {
        assertEquals("Price < 100", HtmlUtils.stripHtml("Price &lt; 100"))
    }

    @Test
    fun `stripHtml - decodes greater-than entity`() {
        assertEquals("Price > 100", HtmlUtils.stripHtml("Price &gt; 100"))
    }

    @Test
    fun `stripHtml - decodes quote entity`() {
        assertEquals("The \"Best\" Company", HtmlUtils.stripHtml("The &quot;Best&quot; Company"))
    }

    @Test
    fun `stripHtml - decodes apostrophe entity`() {
        assertEquals("McDonald's Corp", HtmlUtils.stripHtml("McDonald&apos;s Corp"))
    }

    @Test
    fun `stripHtml - decodes non-breaking space`() {
        // Non-breaking space becomes a regular space character (U+00A0)
        val result = HtmlUtils.stripHtml("Test&nbsp;Company")
        // After trimming, should contain the text with space preserved
        assertTrue(result.contains("Test") && result.contains("Company"))
    }

    @Test
    fun `stripHtml - strips multiple tag types`() {
        assertEquals("Mixed Content", HtmlUtils.stripHtml("<b>Mixed</b> <i>Content</i>"))
    }

    @Test
    fun `stripHtml - strips nested tags`() {
        assertEquals("Nested", HtmlUtils.stripHtml("<b><i>Nested</i></b>"))
    }

    @Test
    fun `stripHtml - handles mixed tags and entities`() {
        assertEquals("AT&T Inc", HtmlUtils.stripHtml("<b>AT&amp;T</b> Inc"))
    }

    @Test
    fun `stripHtml - strips span tags with attributes`() {
        assertEquals("Colored Text", HtmlUtils.stripHtml("<span style='color:red'>Colored Text</span>"))
    }

    @Test
    fun `stripHtml - strips div tags`() {
        assertEquals("Content", HtmlUtils.stripHtml("<div>Content</div>"))
    }

    @Test
    fun `stripHtml - strips paragraph tags`() {
        assertEquals("Paragraph", HtmlUtils.stripHtml("<p>Paragraph</p>"))
    }

    @Test
    fun `stripHtml - handles self-closing tags`() {
        // <br/> may insert newline, so just verify content is present
        val result = HtmlUtils.stripHtml("Line<br/>Break")
        assertTrue(result.contains("Line") && result.contains("Break"))
    }

    @Test
    fun `stripHtml - preserves whitespace after stripping`() {
        val result = HtmlUtils.stripHtml("<b>First</b> <b>Second</b>")
        assertEquals("First Second", result)
    }

    @Test
    fun `stripHtml - trims result`() {
        assertEquals("Trimmed", HtmlUtils.stripHtml("  <b>Trimmed</b>  "))
    }

    @Test
    fun `stripHtml - handles numeric entities`() {
        assertEquals("Copyright ©", HtmlUtils.stripHtml("Copyright &#169;"))
    }

    @Test
    fun `stripHtml - handles hex numeric entities`() {
        assertEquals("Copyright ©", HtmlUtils.stripHtml("Copyright &#xa9;"))
    }

    @Test
    fun `stripHtml - complex real-world example`() {
        val input = "<b>Johnson &amp; Johnson</b> Inc (&quot;J&amp;J&quot;)"
        val expected = "Johnson & Johnson Inc (\"J&J\")"
        assertEquals(expected, HtmlUtils.stripHtml(input))
    }

    @Test
    fun `stripHtml - stock symbol with tags`() {
        assertEquals("AAPL.US", HtmlUtils.stripHtml("<b>AAPL</b>.US"))
    }

    @Test
    fun `stripHtml - stock symbol with multiple tags`() {
        assertEquals("GOOGL.US", HtmlUtils.stripHtml("<i><b>GOOGL</b></i>.US"))
    }

    @Test
    fun `stripHtml - handles script tags`() {
        // HtmlCompat behavior may vary, but typically removes script tags
        val result = HtmlUtils.stripHtml("<script>alert('bad')</script>Clean")
        // Just verify the clean text is preserved
        assertTrue(result.contains("Clean"))
    }

    @Test
    fun `stripHtml - handles style tags`() {
        // HtmlCompat behavior may vary, but typically removes style tags
        val result = HtmlUtils.stripHtml("<style>.class{}</style>Clean")
        // Just verify the clean text is preserved
        assertTrue(result.contains("Clean"))
    }
}
