package com.example.stocksignal.util

import android.text.Html
import androidx.core.text.HtmlCompat

/**
 * Utilities for cleaning HTML content from text.
 */
object HtmlUtils {

    /**
     * Strips HTML tags and decodes HTML entities from a string.
     * 
     * Examples:
     * - `<b>VGT</b>.US` → `VGT.US`
     * - `AT&amp;T Inc` → `AT&T Inc`
     * - `Johnson &amp; Johnson` → `Johnson & Johnson`
     * - `Price &lt; 100` → `Price < 100`
     * 
     * @param html The string potentially containing HTML tags and entities
     * @return Cleaned plain text string
     */
    fun stripHtml(html: String?): String {
        if (html.isNullOrBlank()) return ""
        
        // Use HtmlCompat to decode entities and strip tags
        val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
        return spanned.toString().trim()
    }
}
