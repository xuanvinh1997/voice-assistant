package ai.assistant.service

import ai.assistant.Events
import ai.assistant.MainActivity
import ai.assistant.llm.GenAIException
import ai.assistant.llm.GenAIWrapper
import ai.assistant.llm.ModelDownloader
import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.File
import java.util.Arrays
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.FutureTask

class LLMService:Service(), GenAIWrapper.TokenUpdateListener {
    private var isRunning = false
    private var runningThread: Future<*> = FutureTask<Any?> { null }
    private var genAIWrapper: GenAIWrapper? = null
    private var messageResponse = ""
    private val timeOutMs = 1000 // 1 second
    private var lastMessageTime = 0L
    private val binder = LocalBinder()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    inner class LocalBinder : Binder() {
        fun getService(): LLMService = this@LLMService
    }



    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val data = intent?.getStringExtra("message")
            Log.d("LLMService", "Received message: $data")
            if(isGenerating()) {
                stopCurrentRunningThread()
            }
            runInference(data!!)
        }
    }
    override fun onCreate() {
        super.onCreate()
        downloadModels()
        val filter = IntentFilter(Events.ON_USER_MESSAGE)
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter)
        Log.d("LLMService", "Service started")
    }
    override fun onBind(p0: Intent?): IBinder? {
        return binder
    }

    private fun fileExists(context: Context, fileName: String): Boolean {
        val file = File(context.filesDir, fileName)
        return file.exists()
    }

    @Throws(GenAIException::class)
    private fun createGenAIWrapper(): GenAIWrapper {
        // Create GenAIWrapper object and load model from android device file path.
        val wrapper = GenAIWrapper(this.filesDir.path)
        wrapper.setTokenUpdateListener(this)
        return wrapper
    }

    fun runInference(input: String) {
        runningThread = executor.submit {
            genAIWrapper!!.run(input)
        }
    }

    private fun isTimeOut(): Boolean {
        return System.currentTimeMillis() - lastMessageTime > timeOutMs
    }
    @Throws(GenAIException::class)
    fun downloadModels() {
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
            this,
            "Downloading model for the app... Model Size greater than 2GB, please allow a few minutes to download.",
            Toast.LENGTH_SHORT
        ).show()

        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        for (i in urlFilePairs.indices) {
            val index = i
            val url = urlFilePairs[index].first
            val fileName = urlFilePairs[index].second
            if (fileExists(this, fileName)) {
                // Display a message using Toast
                Toast.makeText(this, "File already exists. Skipping Download.", Toast.LENGTH_SHORT)
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
                    this,
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
        if (runningThread == null) {
            return false
        }
        return !runningThread.isDone && !runningThread.isCancelled
    }

    fun stopCurrentRunningThread() {
        runningThread.cancel(true)
    }
    private val stopSentenceRegex = ".*[.!?]".toRegex()

    @SuppressLint("SetTextI18n")
    override fun onTokenUpdate(token: String?) {
        lastMessageTime = System.currentTimeMillis()
        // Update the messageResponse with the token
        messageResponse += token

        // Check if the token is a stop sentence
        if (stopSentenceRegex.matches(token!!)) {
            // Send the message to the MainActivity
            val intent = Intent(Events.ON_ASSISTANT_MESSAGE)
            intent.putExtra("message", messageResponse)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            messageResponse = ""
        }
//        val intent = Intent(Events.ON_MESSAGE_GENERATED)
//        intent.putExtra("message", token)
//        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        genAIWrapper!!.close()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
    }
}