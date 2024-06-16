package ai.assistant.llm

import ai.assistant.MainActivity
import ai.assistant.Message
import ai.assistant.R
import ai.assistant.service.IAssistantListener
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.widget.Toast
import java.io.File
import java.util.Arrays
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future


class LLM(context: Context): GenAIWrapper.TokenUpdateListener {

    private val mContext = context
    private var isRunning = false
    private lateinit var runningThread: Future<*>
    private var genAIWrapper: GenAIWrapper? = null
    private var asrIAssistantListener: IAssistantListener? = null
    private var messageResponse = ""
    private val timeOutMs = 1000 // 1 second
    private var lastMessageTime = 0L
    private fun fileExists(context: Context, fileName: String): Boolean {
        val file = File(context.filesDir, fileName)
        return file.exists()
    }

    @Throws(GenAIException::class)
    private fun createGenAIWrapper(): GenAIWrapper {
        // Create GenAIWrapper object and load model from android device file path.
        val wrapper = GenAIWrapper(mContext.filesDir.path)
        wrapper.setTokenUpdateListener(this)
        return wrapper
    }
    fun setAsrIAssistantListener(listener: IAssistantListener) {
        asrIAssistantListener = listener
    }
    fun runInference(input: String) {
        runningThread = Executors.newSingleThreadExecutor().submit {
            genAIWrapper!!.run(input)
        }
    }

    @Throws(GenAIException::class)
    fun downloadModels(context: Context) {
        val urlFilePairs = Arrays.asList(
            Pair(
                "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/added_tokens.json?download=true",
                "added_tokens.json"
            ),
            Pair(
                "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/config.json?download=true",
                "config.json"
            ),
            Pair(
                "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/configuration_phi3.py?download=true",
                "configuration_phi3.py"
            ),
            Pair(
                "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/genai_config.json?download=true",
                "genai_config.json"
            ),
            Pair(
                "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/phi3-mini-4k-instruct-cpu-int4-rtn-block-32-acc-level-4.onnx?download=true",
                "phi3-mini-4k-instruct-cpu-int4-rtn-block-32-acc-level-4.onnx"
            ),
            Pair(
                "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/phi3-mini-4k-instruct-cpu-int4-rtn-block-32-acc-level-4.onnx.data?download=true",
                "phi3-mini-4k-instruct-cpu-int4-rtn-block-32-acc-level-4.onnx.data"
            ),
            Pair(
                "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/special_tokens_map.json?download=true",
                "special_tokens_map.json"
            ),
            Pair(
                "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/tokenizer.json?download=true",
                "tokenizer.json"
            ),
            Pair(
                "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/tokenizer.model?download=true",
                "tokenizer.model"
            ),
            Pair(
                "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/tokenizer_config.json?download=true",
                "tokenizer_config.json"
            )
        )
        Toast.makeText(
            mContext,
            "Downloading model for the app... Model Size greater than 2GB, please allow a few minutes to download.",
            Toast.LENGTH_SHORT
        ).show()

        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        for (i in urlFilePairs.indices) {
            val index = i
            val url = urlFilePairs[index].first
            val fileName = urlFilePairs[index].second
            if (fileExists(context, fileName)) {
                // Display a message using Toast
                Toast.makeText(mContext, "File already exists. Skipping Download.", Toast.LENGTH_SHORT)
                    .show()

                Log.d(MainActivity.TAG, "File $fileName already exists. Skipping download.")
                // note: since we always download the files lists together for once,
                // so assuming if one filename exists, then the download model step has already
                // be
                // done.
                genAIWrapper = createGenAIWrapper()
                break
            }
            executor.execute {
                ModelDownloader.downloadModel(
                    context,
                    url,
                    fileName,
                    object : ModelDownloader.DownloadCallback {
                        @Throws(GenAIException::class)
                        override fun onDownloadComplete() {
                            Log.d(MainActivity.TAG, "Download complete for $fileName")
                            if (index == urlFilePairs.size - 1) {
                                // Last download completed, create GenAIWrapper
                                genAIWrapper = createGenAIWrapper()
                                Log.d(MainActivity.TAG, "All downloads completed")
                            }
                        }
                    })
            }
        }
        executor.shutdown()
    }
    fun isReady(): Boolean {
        return genAIWrapper != null
    }

    fun isGenerating(): Boolean {
        return !runningThread.isDone && !runningThread.isCancelled
    }

    fun stopCurrentRunningThread() {
        runningThread.cancel(true)
    }

    @SuppressLint("SetTextI18n")
    override fun onTokenUpdate(token: String?) {
        lastMessageTime = System.currentTimeMillis()
        // Update and aggregate the generated text and write to text box.
//        Log.i("GenAI", "onTokenUpdate: $token")
        asrIAssistantListener!!.onNewMessageSent(Message(token!!, false))
        // Update the messageResponse with the token
        messageResponse += token

    }

    fun terminate() {
        isRunning = false
        genAIWrapper!!.close()
    }

}
