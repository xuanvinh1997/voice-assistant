package ai.assistant.service

import ai.assistant.Message
import ai.assistant.R
import ai.assistant.Utils
import ai.assistant.asr.SileroVadDetector
import ai.assistant.asr.SileroVadOnnxModel
import ai.assistant.asr.SpeechRecognizer
import ai.assistant.asr.WakeWord
import ai.assistant.asr.tensorShape
import ai.assistant.llm.LLM
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import java.nio.FloatBuffer
import java.util.concurrent.Executors


class ASR(context: Context): IAssistantListener {

    private val mContext = context
    private var audioRecord: AudioRecord? = null
    private val sampleRate = 16000
    private val startThresholds = 0.6f
    private val endThresholds = 0.45f
    private val minSilenceDurationMs = 600
    private val speechPadMs = 500
    private val windowSizeSamples = 1280
    private val vadModel: SileroVadOnnxModel by lazy {
        mContext.resources.openRawResource(R.raw.silero_vad).use {
            val modelBytes = it.readBytes()
            SileroVadOnnxModel(modelBytes)
        }
    }
    private var assistantListener: IAssistantListener? = null
    fun setAssistantListener(listener: IAssistantListener) {
        assistantListener = listener
    }
    private lateinit var speechRecognizer: SpeechRecognizer


    private val llm = lazy { LLM(mContext) }.value
    private val env = OrtEnvironment.getEnvironment()
    private val executor = Executors.newSingleThreadExecutor()
    private val recognizerExecutors = Executors.newFixedThreadPool(2)
    private val wakeWordExecutors = Executors.newFixedThreadPool(2)
    private var isStarted = false
    private var isWakeWordDetected = false
    private var wakeWord: WakeWord? = null
    private var vadDetector: SileroVadDetector? = null

    // 30 seconds of audio at 16kHz
    private val maxBufferSize = 30 * sampleRate


    private var audioData: FloatArray = FloatArray(0)


    fun start(): Int {
        Log.d("WakeWordService", "Service starting...")
        llm.downloadModels(mContext)
        llm.setAsrIAssistantListener(this)


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
            //        wakeWord = WakeWord()
            speechRecognizer = SpeechRecognizer(
                Utils.copyAssetsToInternalStorage(
                    mContext,
                    R.raw.whisper_cpu_int8_cpu_cpu_model,
                    "asr.onnx"
                )
            )
            wakeWord = WakeWord(mContext)
        } catch (e: OrtException) {
            Log.e(ASR::class.java.name, "Error initializing: " + e.message)
        }


        startRecording()
        return 1
    }

    fun stop() {
        stopRecording()
        llm.terminate()
        vadDetector!!.close()
    }

    private fun startRecording() {
        if (audioRecord == null) {
            if (ActivityCompat.checkSelfPermission(
                    mContext,
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
                AudioFormat.ENCODING_PCM_16BIT,
                windowSizeSamples,

                )
//            val echoCanceler = AcousticEchoCanceler.create(audioRecord!!.audioSessionId)
//            if (echoCanceler != null) {
//                echoCanceler.enabled = true
//            }
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
        val audioBuffer = ShortArray(windowSizeSamples)
        while (isStarted) {
            val read =
                audioRecord?.read(audioBuffer, 0, audioBuffer.size, AudioRecord.READ_BLOCKING) ?: 0
//            Log.d("ASRService", "Read $read samples")
            if (read > 0) {

                wakeWordExecutors.submit {
                    detectWakeWord(audioBuffer)
                }

                val floatArray = audioBuffer.map { it / 32768.0f }.toFloatArray()
                val detectResult = vadDetector!!.apply(floatArray, true)
                if (detectResult.containsKey("start")) {

                    Log.d("ASRService", "Start talking")
                    startVoice = detectResult["start"]!!
                    isTalking = true
                }
                if (isTalking) {
                    audioData += floatArray
                    if (audioData.size > maxBufferSize) {
                        audioData =
                            audioData.sliceArray(audioData.size - maxBufferSize until audioData.size)
                    }
                }

                if (detectResult.containsKey("end")) {
                    Log.d("ASRService", "End talking")
                    endVoice = detectResult["end"]!!
                    isTalking = false
                }

                if (audioData.isNotEmpty() && detectResult.containsKey("end")) {
                    if (isWakeWordDetected) {
                        // new thread to recognize speech
                        Log.d("ASRService", "Voice length: ${endVoice - startVoice}")

                        recognizerExecutors.submit {
                            recognizeSpeech()
                        }
                    } else {
                        audioData = FloatArray(0)
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
        Log.d("ASRService", "Recognized text in ${result.inferenceTimeInMs}")
        if (llm.isGenerating()) {
            llm.stopCurrentRunningThread()
        }
        val promptQuestionFormatted = """
            ${"<|user|>\n${result.text}"}<|end|>
            <|assistant|>
            """.trimIndent()
        assistantListener!!.onNewMessageSent(Message(result.text, true))
        llm.runInference(promptQuestionFormatted)

        audioData = FloatArray(0)
        isWakeWordDetected = false
    }

    private fun detectWakeWord(audioBuffer: ShortArray) {
//        val t0 = System.currentTimeMillis()
        if (wakeWord?.invoke(audioBuffer)!!) {
            isWakeWordDetected = true
//            recognizerExecutors.submit {
//                recognizeSpeech()
//            }
        }
//        val t1 = System.currentTimeMillis()
//        Log.d("ASRService", "Wake word detection time: ${t1 - t0} ms")
    }

    override fun onNewMessageSent(message: Message) {
        assistantListener!!.onNewMessageSent(message)
    }

}