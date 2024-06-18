package ai.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.app.ActivityCompat


class SharedAudioRecorder(context: Context, sampleRate: Int, bufferSize: Int) {
    private lateinit var audioRecord: AudioRecord
    private val mSampleRate = sampleRate
    private val mBufferSize = bufferSize
    private var isRecording = false
    private val listeners: MutableList<AudioDataListener> = ArrayList()
    private val mContext = context
    init {
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
            throw RuntimeException("No permission")
        }
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
    }
    fun startRecording() {
        isRecording = true
        audioRecord.startRecording()
        Thread(AudioRecordingRunnable()).start()
    }

    fun stopRecording() {
        isRecording = false
        audioRecord.stop()
    }

    fun addListener(listener: AudioDataListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: AudioDataListener) {
        listeners.remove(listener)
    }

    private inner class AudioRecordingRunnable : Runnable {
        override fun run() {
            val buffer = ShortArray(mBufferSize)
            while (isRecording) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) {
                    for (listener in listeners) {
                        listener.onAudioData(buffer, read)
                    }
                }
            }
        }
    }

    interface AudioDataListener {
        fun onAudioData(data: ShortArray?, length: Int)
    }
}
