package com.example.stocksignal.data.translation

import android.content.Context
import android.util.Log
import com.example.stocksignal.core.ExternalExecutionGate
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NewsTranslationServiceLiveTest {

    @Test
    fun translatePolishHeadlineToEnglish() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val service = NewsTranslationService(
                context,
                LiteRtLlmRuntimeFactory(),
                ExternalExecutionGate()
            )
            val localUsable = service.isLocalModelUsable()
            assumeTrue("Local LiteRT-LM model not usable on this device.", localUsable)

            val input =
                "Bank Pekao domaga się spłaty przez Grupę Azoty Polyolefins 3,952 mld zł zobowiązań. " +
                    "16 sty, 15:54 * DM BOS"
            val translated = service.translateWithLocalModel(input)
            assertNotNull("Translation should not be null", translated)
            val cleaned = translated!!.trim()
            assertTrue("Translation should not be empty", cleaned.isNotEmpty())
            assertNotEquals("Translation should differ from input", input.trim(), cleaned)
            Log.i("NewsTranslationServiceLiveTest", "Translated: $cleaned")
        }
    }

}
