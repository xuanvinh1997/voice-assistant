package ai.assistant.service

import ai.assistant.Events
import ai.assistant.R
import ai.assistant.SharedAudioRecorder
import ai.assistant.Utils
import ai.assistant.asr.SileroVadDetector
import ai.assistant.asr.SileroVadOnnxModel
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
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.nio.FloatBuffer


class ASRService : Service(), SharedAudioRecorder.AudioDataListener {

    // local binder
    // Define the Binder object that the client will use to interact with the service
    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService():ASRService = this@ASRService
    }

    private var kwsDetected = false
    private lateinit var speechRecognizer: SpeechRecognizer
    private val vadModel: SileroVadOnnxModel by lazy {
        this.resources.openRawResource(R.raw.silero_vad).use {
            val modelBytes = it.readBytes()
            SileroVadOnnxModel(modelBytes)
        }
    }
    private val sampleRate = 16000
    private val startThresholds = 0.6f
    private val endThresholds = 0.45f
    private val minSilenceDurationMs = 600
    private val speechPadMs = 500
    private var mVadDetector: SileroVadDetector? = null
    private val windowSizeSamples = 1280
    private val cacheQueue = ArrayDeque<FloatArray>(1)
    private val env = OrtEnvironment.getEnvironment()

    private var isTalking = false

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
        mVadDetector = SileroVadDetector(
            vadModel,
            startThresholds,
            endThresholds,
            sampleRate,
            minSilenceDurationMs,
            speechPadMs
        )
//        llm.downloadModels(mContext)
//        llm.setAsrIAssistantListener(this)
        val filter = IntentFilter(Events.KWS_DETECTED)
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter)
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

        Log.d("ASRService", "ASR Service started")
    }


    private fun recognizeSpeech() {
        Log.d("ASRService", "Recognizing speech ${audioData.size} samples")
        if (audioData.isEmpty()) {
            return
        }
//        audioData = cacheQueue.first()
        val input = OnnxTensor.createTensor(
            env, FloatBuffer.wrap(audioData),
            tensorShape(1, audioData.size.toLong())
        )

        val result = speechRecognizer.run(input)
        // important to close the input tensor
        input.close()
        Log.d("ASRService", "Recognized text: '${result.text}' in ${result.inferenceTimeInMs}")
        // Send the recognized text to the llm service
        val intent = Intent(Events.ON_USER_MESSAGE)
        intent.putExtra("message", result.text)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }


    override fun onBind(p0: Intent?): IBinder? {
        return binder
    }

    override fun onAudioData(data: ShortArray?, length: Int) {
        if (kwsDetected) {

            val floatArray = data!!.map { it / 32768.0f }.toFloatArray()
            val detectResult = mVadDetector!!.apply(floatArray, false)
//        cacheQueue.addLast(floatArray)

            if (detectResult.containsKey("start") && !isTalking) {
                isTalking = true
                audioData = floatArray
            } else if (detectResult.containsKey("end") && isTalking) {
                isTalking = false
                audioData += floatArray
                recognizeSpeech()
                audioData = FloatArray(0)
                kwsDetected = false
            }
            if (isTalking)
                audioData += floatArray
        }

    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        super.onDestroy()
    }
}