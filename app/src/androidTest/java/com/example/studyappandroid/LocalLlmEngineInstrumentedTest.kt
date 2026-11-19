package com.example.studyappandroid

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.studyappandroid.data.ai.local.ChatTurn
import com.example.studyappandroid.data.ai.local.LocalModelManager
import com.example.studyappandroid.data.ai.local.LocalModelState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalLlmEngineInstrumentedTest {

    @Test
    fun loadsBundledQwenModelAndGeneratesTeacherReply() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as StudyRoastApplication
        val modelManager = LocalModelManager(context)
        val engine = app.localLlmEngine

        val budgetBefore = modelManager.getMemoryBudget()
        Log.i(
            "LocalLlmTest",
            "Memory budget: avail=${budgetBefore.availableBytes / (1024 * 1024)}MB, " +
                "total=${budgetBefore.totalBytes / (1024 * 1024)}MB, ctx=${budgetBefore.recommendedContextTokens}"
        )

        val readyResult = engine.ensureReady()
        assertTrue("Model failed to load: ${readyResult.exceptionOrNull()?.message}", readyResult.isSuccess)
        assertTrue("Expected Ready state, was ${engine.state.value}", engine.state.value is LocalModelState.Ready)
        Log.i("LocalLlmTest", "Diagnostics: ${engine.getSystemDiagnostics()}")

        val systemPrompt = "You are StudyRoast, a concise offline AI teacher. " +
            "Answer strictly using this handout excerpt: " +
            "\"Mitochondria are double-membrane organelles that generate most of a cell's ATP through cellular respiration.\" " +
            "If the excerpt does not contain the answer, clearly say it is not in the handout."

        val t0 = System.currentTimeMillis()
        val reply1 = engine.generate(
            messages = listOf(
                ChatTurn("system", systemPrompt),
                ChatTurn("user", "In one sentence, what do mitochondria do according to the handout?")
            ),
            maxTokens = 80,
            temperature = 0.25f,
        )
        val dt1 = System.currentTimeMillis() - t0
        assertTrue("Generation 1 failed: ${reply1.exceptionOrNull()?.message}", reply1.isSuccess)
        val text1 = reply1.getOrThrow()
        Log.i("LocalLlmTest", "Turn 1 (${dt1}ms): $text1")
        assertTrue("Reply 1 was empty", text1.isNotBlank())
        assertTrue("Reply 1 should mention ATP or respiration: $text1", text1.contains("ATP", ignoreCase = true) || text1.contains("respiration", ignoreCase = true) || text1.contains("energy", ignoreCase = true))

        val t1 = System.currentTimeMillis()
        val reply2 = engine.generate(
            messages = listOf(
                ChatTurn("system", systemPrompt),
                ChatTurn("user", "In one sentence, what do mitochondria do according to the handout?"),
                ChatTurn("assistant", text1),
                ChatTurn("user", "Does the handout mention volcanic rocks?")
            ),
            maxTokens = 64,
            temperature = 0.2f,
        )
        val dt2 = System.currentTimeMillis() - t1
        assertTrue("Generation 2 failed: ${reply2.exceptionOrNull()?.message}", reply2.isSuccess)
        val text2 = reply2.getOrThrow()
        Log.i("LocalLlmTest", "Turn 2 (${dt2}ms): $text2")
        assertTrue("Reply 2 was empty", text2.isNotBlank())
    }
}
