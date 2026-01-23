package com.example.stocksignal.data.ai

import kotlin.text.Charsets
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSignalScorerNanoTimeTest {

    @Test
    fun `ai scorer timing uses System nanoTime`() {
        val classStream = AiSignalScorer::class.java.getResourceAsStream("AiSignalScorer.class")
        val bytes = classStream?.readBytes()
            ?: throw AssertionError("AiSignalScorer.class not found on classpath.")
        val haystack = String(bytes, Charsets.ISO_8859_1)

        assertTrue(
            "Expected nanoTime reference in AiSignalScorer bytecode.",
            haystack.contains("nanoTime")
        )
        assertFalse(
            "Unexpected SystemClock reference in AiSignalScorer bytecode.",
            haystack.contains("android/os/SystemClock")
        )
    }
}
