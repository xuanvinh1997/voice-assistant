package ai.assistant.service

import ai.assistant.R
import ai.assistant.Utils
import ai.assistant.asr.SileroVadDetector
import ai.assistant.asr.SileroVadOnnxModel
import ai.assistant.asr.SpeechRecognizer
import ai.assistant.asr.WakeWord
import ai.assistant.asr.tensorShape
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.util.concurrent.Executors


class ASRService : Service() {

    private var audioRecord: AudioRecord? = null
    private val sampleRate = 16000
    private val startThresholds = 0.6f
    private val endThresholds = 0.45f
    private val minSilenceDurationMs = 600
    private val speechPadMs = 500
    private val windowSizeSamples = 1280
    private val vadModel: SileroVadOnnxModel by lazy {
        resources.openRawResource(R.raw.silero_vad).use {
            val modelBytes = it.readBytes()
            SileroVadOnnxModel(modelBytes)
        }
    }
    private lateinit var speechRecognizer: SpeechRecognizer

    private val env = OrtEnvironment.getEnvironment()
    private val executor = Executors.newSingleThreadExecutor()
    private val recognizerExecutors = Executors.newFixedThreadPool(2)
    private val wakeWordExecutors = Executors.newFixedThreadPool(1)
    private var isStarted = false
    private var wakeWord: WakeWord? = null
    private var vadDetector: SileroVadDetector? = null

    // 30 seconds of audio at 16kHz
    private val maxBufferSize = 30 * sampleRate

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private var audioData: FloatArray = FloatArray(0)

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("WakeWordService", "Service starting...")

        // Initialize the Voice Activity Detector
        try {
            vadDetector = SileroVadDetector(
                vadModel,
                startThresholds,
                endThresholds,
                sampleRate,
                minSilenceDurationMs,
                speechPadMs
            )
        } catch (e: OrtException) {
            Log.e(ASRService::class.java.name, "Error initializing the VAD detector: " + e.message)
        }
//        wakeWord = WakeWord()
        speechRecognizer = SpeechRecognizer(
            Utils.copyAssetsToInternalStorage(
                this,
                R.raw.whisper_cpu_int8_cpu_cpu_model,
                "asr.onnx"
            )
        )
        wakeWord = WakeWord(
            Utils.copyAssetsToInternalStorage(
                this,
                R.raw.melspectrogram,
                "mel_spec.onnx"
            ),
            Utils.copyAssetsToInternalStorage(
                this,
                R.raw.embedding_model,
                "embedding.onnx"
            )
        )
        startRecording()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
    }

    private fun startRecording() {
        if (audioRecord == null) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                windowSizeSamples,

                )
//            audioRecord!!.set
        }
        if (!isStarted) {
            audioRecord?.startRecording()
            isStarted = true
            executor.submit {
                processAudioStream()
            }
            Log.d("WakeWordService", "Voice stream started")
        } else {
            Log.d("WakeWordService", "Voice stream has already started!")
        }

    }

    private fun stopRecording() {
        isStarted = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        Log.d("WakeWordService", "Voice stream stopped")
    }

    private var isTalking = false
    private var startVoice = 0.0
    private var endVoice = 0.0
    private fun processAudioStream() {
        val audioBuffer = FloatArray(windowSizeSamples)
        while (isStarted) {
            val read =
                audioRecord?.read(audioBuffer, 0, audioBuffer.size, AudioRecord.READ_BLOCKING) ?: 0

            wakeWordExecutors.submit {
                detectWakeWord(audioBuffer)
            }

            if (read > 0) {
                // Process the audioBuffer here
                // make sure audioData size < maxBufferSize
                audioData += audioBuffer
                if (audioData.size > maxBufferSize) {
                    audioData =
                        audioData.sliceArray(audioData.size - maxBufferSize until audioData.size)
                }
//                Log.d("WakewordService", "Processing audio stream")
//                wakeWord!!.detectWakeWord(0, melspecModel!!, 0, audioBuffer)
//                ...
//                if (isWakeWordDetected()) {
                val detectResult = vadDetector!!.apply(audioBuffer, true)
                if (detectResult.containsKey("start")) {
                    Log.d("ASRService", "Start talking")
                    startVoice = detectResult["start"]!!
                    isTalking = true
                } else if (detectResult.containsKey("end")) {
                    Log.d("ASRService", "End talking")
                    endVoice = detectResult["end"]!!
                    isTalking = false
                }

                if (audioData.isNotEmpty() && detectResult.containsKey("end")) {
                    // new thread to recognize speech
                    Log.d("ASRService", "Voice length: ${endVoice - startVoice}")
                    recognizerExecutors.submit {
                        recognizeSpeech()
                    }

                }
            }
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
        Log.d("ASRService", "Recognized text: ${result.text} in ${result.inferenceTimeInMs}")
        audioData = FloatArray(0)

    }

    private fun detectWakeWord(audioBuffer: FloatArray) {
        Log.d(
            "WakeWord",
            "Inference melspectrogram: ${wakeWord!!.getMelSpec(audioBuffer)!!.inferenceTimeInMs}"
        )
    }

}