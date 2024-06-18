package ai.assistant.service

import ai.assistant.Events
import ai.assistant.R
import ai.assistant.SharedAudioRecorder
import ai.assistant.asr.SileroVadDetector
import ai.assistant.asr.SileroVadOnnxModel
import ai.assistant.asr.WakeWord
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.concurrent.Executors


class WakeWordService : Service(), SharedAudioRecorder.AudioDataListener {

    // local binder
    // Define the Binder object that the client will use to interact with the service
    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService(): WakeWordService = this@WakeWordService
    }

//    private val vadModel: SileroVadOnnxModel by lazy {
//        this.resources.openRawResource(R.raw.silero_vad).use {
//            val modelBytes = it.readBytes()
//            SileroVadOnnxModel(modelBytes)
//        }
//    }
    private val sampleRate = 16000
    private val startThresholds = 0.6f
    private val endThresholds = 0.45f
    private val minSilenceDurationMs = 600
    private val speechPadMs = 500
    private var mVadDetector: SileroVadDetector? = null
    private val windowSizeSamples = 1280
    private var lastBroadcastTime = 0L
    private val broadcastInterval = 3000 // 30 seconds in milliseconds

    private lateinit var mWakeWord: WakeWord
    private var isDetected = false
    override fun onCreate() {
        super.onCreate()

//        mVadDetector = SileroVadDetector(
//            vadModel,
//            startThresholds,
//            endThresholds,
//            sampleRate,
//            minSilenceDurationMs,
//            speechPadMs
//        )
        mWakeWord = WakeWord(this)
    }

    override fun onBind(p0: Intent?): IBinder? {
        return binder
    }

    private fun detectWakeWord(audioBuffer: ShortArray) {
//            val floatArray = audioBuffer.map { it / 32768.0f }.toFloatArray()
//            val detectResult = mVadDetector!!.apply(floatArray, true)


        if (mWakeWord.invoke(audioBuffer)) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBroadcastTime >= broadcastInterval) {
                lastBroadcastTime = currentTime

                val intent = Intent(Events.KWS_DETECTED)
                intent.putExtra("key", "value")
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            }
        }

    }

    override fun onAudioData(data: ShortArray?, length: Int) {
        Executors.newSingleThreadExecutor().submit {
            detectWakeWord(data!!)
        }
    }
}