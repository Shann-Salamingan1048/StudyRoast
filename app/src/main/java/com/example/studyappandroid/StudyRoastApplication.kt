package com.example.studyappandroid

import android.app.Application
import com.example.studyappandroid.data.ai.local.LocalLlmEngine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class StudyRoastApplication : Application() {
    @Inject
    lateinit var localLlmEngine: LocalLlmEngine

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        // Offline LLM loads lazily when first needed to keep startup fast and RAM usage low.
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
            appScope.launch {
                localLlmEngine.unloadForLowMemory()
            }
        }
    }
}
