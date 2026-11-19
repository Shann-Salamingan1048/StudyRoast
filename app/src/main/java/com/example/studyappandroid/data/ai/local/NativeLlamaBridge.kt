package com.example.studyappandroid.data.ai.local

object NativeLlamaBridge {
    init {
        System.loadLibrary("studyroast_llama")
    }

    external fun nativeInit(nativeLibDir: String)

    external fun nativeLoadModel(
        modelPath: String,
        nCtx: Int,
        requestedThreads: Int,
    ): Int

    external fun nativePreparePrompt(
        roles: Array<String>,
        contents: Array<String>,
        maxTokens: Int,
        temperature: Float,
    ): Int

    external fun nativeStepToken(): String?

    external fun nativeCancel()

    external fun nativeUnload()

    external fun nativeSystemInfo(): String
}
