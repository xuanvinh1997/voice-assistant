package ai.assistant.llm

import android.util.Log


class GenAIWrapper(modelPath: String) : AutoCloseable {
    private var nativeModel: Long
    private var nativeTokenizer: Long
    private var listener: TokenUpdateListener? = null

    interface TokenUpdateListener {
        fun onTokenUpdate(token: String?)
    }

    init {
        nativeModel = loadModel(modelPath)
        nativeTokenizer = createTokenizer(nativeModel)
    }

    fun setTokenUpdateListener(listener: TokenUpdateListener?) {
        this.listener = listener
    }

    @Throws(GenAIException::class)
    fun run(prompt: String) {
        run(nativeModel, nativeTokenizer, prompt,  /* useCallback */true)
    }

    @Throws(Exception::class)
    override fun close() {
        if (nativeTokenizer != 0L) {
            releaseTokenizer(nativeTokenizer)
        }

        if (nativeModel != 0L) {
            releaseModel(nativeModel)
        }

        nativeTokenizer = 0
        nativeModel = 0
    }

    fun gotNextToken(token: String) {
//        Log.i("GenAI", "gotNextToken: $token")
        if (listener != null) {
            listener!!.onTokenUpdate(token)
        }
    }

    private external fun loadModel(modelPath: String): Long

    private external fun releaseModel(nativeModel: Long)

    private external fun createTokenizer(nativeModel: Long): Long

    private external fun releaseTokenizer(nativeTokenizer: Long)

    private external fun run(
        nativeModel: Long,
        nativeTokenizer: Long,
        prompt: String,
        useCallback: Boolean
    ): String?

    companion object {
        // Load the GenAI library on application startup.
        init {
            System.loadLibrary("assistant") // JNI layer
            System.loadLibrary("onnxruntime-genai")
            System.loadLibrary("onnxruntime")
        }
    }
}
