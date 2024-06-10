package ai.assistant.asr

import ai.assistant.R
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import org.jetbrains.kotlinx.multik.api.NativeEngineType
import org.jetbrains.kotlinx.multik.api.arange
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ones
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.slice

class AudioFeatures(context: Context, sampleRate: Int = 16000) {
    private val mContext = context
    private var melSpecModel: OrtSession? = null
    private var embeddingModel: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()
    private val sessionOptions = OrtSession.SessionOptions()
    private var rawDataReminder = FloatArray(0)
    private var accumulatedSamples = 0
    private var melSpectrogramBuffer = mk.ones<Float>(76, 32)
    private val melSpectrogramMaxLen = 10 * 97
    private val mSampleRate = sampleRate
    private var rawDataBuffer = FloatArray(0)
    init {
        mk!!.addEngine(NativeEngineType)
        // lightweight model
        melSpecModel = mContext.resources.openRawResource(R.raw.melspectrogram).use {
            val modelBytes = it.readBytes()
            env.createSession(modelBytes, sessionOptions)
        }
        embeddingModel = mContext.resources.openRawResource(R.raw.embedding_model).use {
            val modelBytes = it.readBytes()
            env.createSession(modelBytes, sessionOptions)
        }
    }

    private fun bufferRawData(x: FloatArray) {
//        self.raw_data_buffer.extend(x.tolist() if isinstance(x, np.ndarray) else x)
        val buffer = rawDataReminder + x
        rawDataReminder = buffer
//        return buffer
    }
    private fun clear() {
        rawDataBuffer = FloatArray(0)
    }
    private fun streamingMelSpectrogram(nSamples: Int) {
        if (rawDataBuffer.size < 400) {
            throw IllegalArgumentException("The number of input frames must be at least 400 samples @ 16khz (25 ms)!")
        }
        Log.d("rawDataBuffer", rawDataBuffer.size.toString())
        val melSpec = getMelSpec(rawDataBuffer.takeLast(nSamples + 160*3).toFloatArray())

//        melSpectrogramBuffer
        if (melSpectrogramBuffer.shape[0] > melSpectrogramMaxLen) {
            // update melSpectrogramBuffer from the end of the buffer to the beginning melSpectrogramMaxLen
            melSpectrogramBuffer = melSpectrogramBuffer.slice(
                (melSpectrogramBuffer.shape[0] - melSpectrogramMaxLen) until melSpectrogramMaxLen,
                axis = 0
            )
        }
        Log.d("melSpec", melSpec.size.toString())
    }

    private fun streamingFeatures(x: FloatArray): Int {
        var processedSamples = 0

        if (rawDataReminder.isNotEmpty()) {
            val x = rawDataReminder + x
            rawDataReminder = FloatArray(0)
        }
        if (accumulatedSamples + x.size < 1280) {
            val reminder = (accumulatedSamples + x.size) % 1280
            if (reminder != 0) {
//                x_even_chunks = x[0:-remainder]
                val xEvenChunks = x.sliceArray(0 until x.size - reminder)
                bufferRawData(xEvenChunks)
                accumulatedSamples += xEvenChunks.size
                rawDataReminder = x.sliceArray(x.size - reminder until x.size)
            } else {
                bufferRawData(x)
                accumulatedSamples += x.size
                rawDataReminder = FloatArray(0)
            }
        } else {
//            self.accumulated_samples += x.shape[0]
//            self._buffer_raw_data(x)
            accumulatedSamples += x.size
            bufferRawData(x)
        }
//        # Only calculate melSpectrogram once minimum samples are accumulated
//        if self.accumulated_samples >= 1280 and self.accumulated_samples % 1280 == 0:
        if (accumulatedSamples >= 1280 && accumulatedSamples % 1280 == 0) {
            streamingMelSpectrogram(accumulatedSamples)
            for (i in mk.arange<Int>(accumulatedSamples.div(1280) - 1, -1, -1)) {
//                ndx = -8*i
                var ndx = -8 * i
//                ndx = ndx if ndx != 0 else len(self.melspectrogram_buffer)
                ndx = if (ndx != 0) ndx else melSpectrogramBuffer.shape[0]
//                x = self.melspectrogram_buffer[-76 + ndx:ndx].astype(np.float32)[None, :, :, None]
                val tmp: D2Array<Float> = melSpectrogramBuffer.slice(
                    (melSpectrogramBuffer.shape[0] - 76 + ndx) until ndx,
                  axis = 0
                )
//                if x.shape[1] == 76:
                if (tmp.shape[1] == 76) {

                }
//                self.feature_buffer = np.vstack((self.feature_buffer,
//                    self.embedding_model_predict(x)))

            }

            processedSamples = accumulatedSamples
            accumulatedSamples = 0
        }
//        return processed_samples if processed_samples != 0 else self.accumulated_samples
        return if (processedSamples != 0) processedSamples else accumulatedSamples
    }

    fun invoke(x: FloatArray): FloatArray {
        streamingFeatures(x)
        return FloatArray(0)
    }

    private fun getMelSpec(audioData: FloatArray, windowSize: Int = 76): FloatArray {
        val melSpecInput =
            createFloatTensor(env, audioData, tensorShape(1, audioData.size.toLong()))
        val inputs = mutableMapOf<String, OnnxTensor>()
        inputs["input"] = melSpecInput
        val melSpecOutput = melSpecModel!!.run(inputs)
        melSpecInput.close()
        val tmp =
            melSpecOutput.use { it[0].value as Array<Array<Array<FloatArray>>> }[0][0].flatMap { it.asIterable() }
                .toFloatArray()

        return tmp
    }
}