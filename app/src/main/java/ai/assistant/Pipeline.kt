package ai.assistant

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord

class Pipeline(context: Context) {
    private val mContext = context
    private val sharedAudioRecorder: SharedAudioRecorder
    init {
        val sampleRate = 16000
        val windowSizeSamples = 1280
//        val bufferSize = AudioRecord.getMinBufferSize(
//            sampleRate,
//            AudioFormat.CHANNEL_IN_MONO,
//            AudioFormat.ENCODING_PCM_16BIT,
////            windowSizeSamples,
//        )
        sharedAudioRecorder = SharedAudioRecorder(mContext, sampleRate, windowSizeSamples)
    }
    fun stopRecording() {
        sharedAudioRecorder.stopRecording()
    }
    fun startRecording() {
        sharedAudioRecorder.startRecording()
    }

    fun addListener(listener: SharedAudioRecorder.AudioDataListener) {
        sharedAudioRecorder.addListener(listener)
    }
}