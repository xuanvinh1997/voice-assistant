package ai.assistant.asr

import ai.assistant.MainActivity
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean

class AudioTensorSource {
    companion object {
        private const val BYTES_PER_FLOAT = 4
        private const val SAMPLE_RATE = 16000
        private const val MAX_AUDIO_LENGTH_IN_SECONDS = 30

        fun fromRawPcmBytes(rawBytes: ByteArray): OnnxTensor {
            val rawByteBuffer = ByteBuffer.wrap(rawBytes)
            // TODO handle big-endian native order...
            if (ByteOrder.nativeOrder() != ByteOrder.LITTLE_ENDIAN) {
                throw NotImplementedError("Reading PCM data is only supported when native byte order is little-endian.")
            }
            rawByteBuffer.order(ByteOrder.nativeOrder())
            val floatBuffer = rawByteBuffer.asFloatBuffer()
            val numSamples = minOf(floatBuffer.capacity(), MAX_AUDIO_LENGTH_IN_SECONDS * SAMPLE_RATE)
            val env = OrtEnvironment.getEnvironment()
            return OnnxTensor.createTensor(
                env, floatBuffer, tensorShape(1, numSamples.toLong())
            )
        }

        @SuppressLint("MissingPermission")
        fun fromRecording(stopRecordingFlag: AtomicBoolean): OnnxTensor {
            val recordingChunkLengthInSeconds = 1

            val minBufferSize = maxOf(
                AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_FLOAT
                ),
                2 * recordingChunkLengthInSeconds * SAMPLE_RATE * BYTES_PER_FLOAT
            )

            val audioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize)
                .build()

            try {
                val floatAudioData = FloatArray(MAX_AUDIO_LENGTH_IN_SECONDS * SAMPLE_RATE) { 0.0f }
                var floatAudioDataOffset = 0

                audioRecord.startRecording()

                while (!stopRecordingFlag.get() && floatAudioDataOffset < floatAudioData.size) {
                    val numFloatsToRead = minOf(
                        recordingChunkLengthInSeconds * SAMPLE_RATE,
                        floatAudioData.size - floatAudioDataOffset
                    )

                    val readResult = audioRecord.read(
                        floatAudioData, floatAudioDataOffset, numFloatsToRead,
                        AudioRecord.READ_BLOCKING
                    )

                    Log.d(MainActivity.TAG, "AudioRecord.read(float[], ...) returned $readResult")

                    if (readResult >= 0) {
                        floatAudioDataOffset += readResult
                    } else {
                        throw RuntimeException("AudioRecord.read() returned error code $readResult")
                    }
                }

                audioRecord.stop()

                val env = OrtEnvironment.getEnvironment()
                val floatAudioDataBuffer = FloatBuffer.wrap(floatAudioData)

                return OnnxTensor.createTensor(
                    env, floatAudioDataBuffer,
                    tensorShape(1, floatAudioData.size.toLong())
                )

            } finally {
                if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop()
                }
                audioRecord.release()
            }
        }
    }

}