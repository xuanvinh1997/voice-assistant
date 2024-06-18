package ai.assistant.service

import ai.assistant.R
import ai.assistant.SharedAudioRecorder
import ai.assistant.Utils
import ai.assistant.asr.SpeechRecognizer
import ai.assistant.asr.tensorShape
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.nio.FloatBuffer


class ASRService:Service(), SharedAudioRecorder.AudioDataListener {

    private var kwsDetected = false
    private lateinit var speechRecognizer: SpeechRecognizer


//    private val llm = lazy { LLM(mContext) }.value
    private val env = OrtEnvironment.getEnvironment()


    // 30 seconds of audio at 16kHz
    private var audioData: FloatArray = FloatArray(0)


    // Receiving Service
    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Handle the event
            kwsDetected = true
        }
    }
    override fun onCreate() {
        super.onCreate()

//        llm.downloadModels(mContext)
//        llm.setAsrIAssistantListener(this)

        // Initialize the Voice Activity Detector
        try {

            speechRecognizer = SpeechRecognizer(
                Utils.copyAssetsToInternalStorage(
                    this,
                    R.raw.whisper_cpu_int8_cpu_cpu_model,
                    "asr.onnx"
                )
            )
//            wakeWord = WakeWord(mContext)
        } catch (e: OrtException) {
            Log.e(ASRService::class.java.name, "Error initializing: " + e.message)
        }

    }



    private fun recognizeSpeech() {
        Log.d("ASRService", "Recognizing speech ${audioData.size} samples")
        if (audioData.isEmpty()) {
            return
        }

        val input = OnnxTensor.createTensor(
            env, FloatBuffer.wrap(audioData),
            tensorShape(1, audioData.size.toLong())
        )

        val result = speechRecognizer.run(input)
        // important to close the input tensor
        input.close()
        Log.d("ASRService", "Recognized text in ${result.inferenceTimeInMs}")

    }


    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onAudioData(data: ShortArray?, length: Int) {
        audioData += data!!.map { it.toFloat() / 32768.0f }.toFloatArray()

    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        super.onDestroy()
    }
}