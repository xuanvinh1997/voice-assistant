package ai.assistant

import ai.assistant.llm.GenAIException
import ai.assistant.llm.GenAIWrapper
import ai.assistant.llm.ModelDownloader.DownloadCallback
import ai.assistant.llm.ModelDownloader.downloadModel
import ai.assistant.service.RedirectService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.File
import java.util.Arrays
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity(), GenAIWrapper.TokenUpdateListener {

    private val OVERLAY_PERMISSION_REQUEST_CODE = 1
    private val REQUEST_RECORD_AUDIO_PERMISSION:Int = 200
    private var genAIWrapper: GenAIWrapper? = null
    private val permissions = arrayOf(android.Manifest.permission.RECORD_AUDIO)
    private var userMsgEdt: EditText? = null
    private var sendMsgIB: ImageButton? = null
    private var generatedTV: TextView? = null
    private var promptTV: TextView? = null
    private fun fileExists(context: Context, fileName: String): Boolean {
        val file = File(context.filesDir, fileName)
        return file.exists()
    }
    @Throws(GenAIException::class)
    private fun downloadModels(context: Context) {
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
            if (fileExists(context, fileName)) {
                // Display a message using Toast
                Toast.makeText(this, "File already exists. Skipping Download.", Toast.LENGTH_SHORT)
                    .show()

                Log.d(TAG, "File $fileName already exists. Skipping download.")
                // note: since we always download the files lists together for once,
                // so assuming if one filename exists, then the download model step has already
                // be
                // done.
                genAIWrapper = createGenAIWrapper()
                break
            }
            executor.execute {
                downloadModel(context, url, fileName, object : DownloadCallback {
                    @Throws(GenAIException::class)
                    override fun onDownloadComplete() {
                        Log.d(TAG, "Download complete for $fileName")
                        if (index == urlFilePairs.size - 1) {
                            // Last download completed, create GenAIWrapper
                            genAIWrapper = createGenAIWrapper()
                            Log.d(TAG, "All downloads completed")
                        }
                    }
                })
            }
        }
        executor.shutdown()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)

        try {
            downloadModels(
                applicationContext
            )
        } catch (e: GenAIException) {
            throw RuntimeException(e)
        }

        sendMsgIB = findViewById(R.id.idIBSend);
        userMsgEdt = findViewById(R.id.idEdtMessage);
        generatedTV = findViewById(R.id.sample_text);
        promptTV = findViewById(R.id.user_text);
//
//        // click button map
//        findViewById<android.widget.Button>(R.id.use_go_to_map_button).setOnClickListener {
//            openRedirectService("Vincom Nguyễn Chí Thanh", "map")
//        }
//
//        // click button media
//        findViewById<android.widget.Button>(R.id.media_button).setOnClickListener {
//            openRedirectService("", "media")
//        }
//
//        // click button youtube
//        findViewById<android.widget.Button>(R.id.youtube_button).setOnClickListener {
//            openRedirectService("Học lập trình Android", "youtube")
//        }
//
//        // click button call
//        findViewById<android.widget.Button>(R.id.call_button).setOnClickListener {
//            openRedirectService("0966913714", "call")
//        }

        // adding on click listener for send message button.
        sendMsgIB!!.setOnClickListener(View.OnClickListener { // Checking if the message entered
            // by user is empty or not.
            if (userMsgEdt!!.text.toString().isEmpty()) {
                // if the edit text is empty display a toast message.
                Toast.makeText(this@MainActivity, "Please enter your message..", Toast.LENGTH_SHORT)
                    .show()
                return@OnClickListener
            }

            val promptQuestion = userMsgEdt!!.text.toString()
            val promptQuestion_formatted =
                "<|user|>\n$promptQuestion<|end|>\n<|assistant|>"
            Log.i("GenAI: prompt question", promptQuestion_formatted)
            setVisibility()

            // Disable send button while responding to prompt.
            sendMsgIB!!.isEnabled = false

            promptTV!!.text = promptQuestion
            // Clear Edit Text or prompt question.
            userMsgEdt!!.setText("")
            generatedTV!!.text = ""
            Thread {
                try {
                    genAIWrapper!!.run(promptQuestion_formatted)
                } catch (e: GenAIException) {
                    throw java.lang.RuntimeException(e)
                }
                runOnUiThread {
                    sendMsgIB!!.isEnabled = true
                }
            }.start()
        })
    }
    @Throws(GenAIException::class)
    private fun createGenAIWrapper(): GenAIWrapper {
        // Create GenAIWrapper object and load model from android device file path.
        val wrapper = GenAIWrapper(filesDir.path)
        wrapper.setTokenUpdateListener(this)
        return wrapper
    }

    @SuppressLint("SetTextI18n")
    override fun onTokenUpdate(token: String?) {
        runOnUiThread {
            // Update and aggregate the generated text and write to text box.
            val generated = generatedTV!!.text
            generatedTV!!.setText("$generated$token")
            generatedTV!!.invalidate()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_RECORD_AUDIO_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this, permissions, OVERLAY_PERMISSION_REQUEST_CODE
                    )

                    // Permission granted, you can start your service here if needed
//                    val intent = Intent(this, ASRService::class.java)
//                    startService(intent)

                } else {
                    // Permission denied, handle accordingly

                }
            }

            OVERLAY_PERMISSION_REQUEST_CODE -> {

            }
        }
    }

    companion object {
        val TAG: String? = MainActivity::class.simpleName
    }

    private fun openRedirectService(value: String, case: String) {
        val serviceIntent = Intent(this, RedirectService::class.java).apply {
            putExtra("value", value)
            putExtra("case", case)
        }
        startService(serviceIntent)
    }
    fun setVisibility() {
        val view = findViewById<View>(R.id.user_text) as TextView
        view.visibility = View.VISIBLE
        val botView = findViewById<View>(R.id.sample_text) as TextView
        botView.visibility = View.VISIBLE
    }

}

