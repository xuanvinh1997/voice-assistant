package ai.assistant.asr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import java.nio.FloatBuffer
import org.jetbrains.kotlinx.multik.*
import org.jetbrains.kotlinx.multik.api.EngineType
import org.jetbrains.kotlinx.multik.api.NativeEngineType
import org.jetbrains.kotlinx.multik.api.d4array
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.api.ones
import org.jetbrains.kotlinx.multik.api.zeros
import org.jetbrains.kotlinx.multik.ndarray.data.D1
import org.jetbrains.kotlinx.multik.ndarray.data.D4
import org.jetbrains.kotlinx.multik.ndarray.data.D4Array
import org.jetbrains.kotlinx.multik.ndarray.data.MultiArray
import org.jetbrains.kotlinx.multik.ndarray.data.NDArray
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.data.slice


class WakeWord(melSpecModelPath: String, embeddingModelPath: String) {
    private var melSpecModel: OrtSession? = null
    private var embeddingModel: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()
    private val sessionOptions = OrtSession.SessionOptions()
    private var rawDataReminder = FloatArray(0)
    private var accumulatedSamples = 0
    private var melSpectrogramBuffer = mk.ones<Float>(76, 32)
    private val melSpectrogramMaxLen = 10 * 97
    private fun loadMelspecModel(melSpecModelPath: String) {
        melSpecModel = env.createSession(melSpecModelPath, sessionOptions)
    }

    private fun loadEmbeddingModel(embeddingModelPath: String) {
        embeddingModel = env.createSession(embeddingModelPath, sessionOptions)
    }

    init {
        mk!!.addEngine(NativeEngineType)

        loadMelspecModel(melSpecModelPath)
        loadEmbeddingModel(embeddingModelPath)
    }

    fun bufferRawData(x: FloatArray) {
//        self.raw_data_buffer.extend(x.tolist() if isinstance(x, np.ndarray) else x)
        val buffer = rawDataReminder + x
        rawDataReminder = buffer
//        return buffer
    }

    fun streamingMelSpectrogram(nSamples: Int) {
//        if len(self.raw_data_buffer) < 400:
        if (rawDataReminder.size < 400) {
//        raise ValueError("The number of input frames must be at least 400 samples @ 16khz (25 ms)!")
            throw IllegalArgumentException("The number of input frames must be at least 400 samples @ 16khz (25 ms)!")
        }
//        self.melspectrogram_buffer = np.vstack(
//            (self.melspectrogram_buffer, self._get_melspectrogram(list(self.raw_data_buffer)[-n_samples-160*3:]))
//        )
        val startPoint =
            if (rawDataReminder.size - nSamples - 160 * 3 >= 0) rawDataReminder.size - nSamples - 160 * 3 else 0
        val tmp = rawDataReminder.sliceArray(startPoint until rawDataReminder.size)
        val melSpec = getMelSpec(tmp)
//        melSpectrogramBuffer
        if (melSpectrogramBuffer.shape[0] > melSpectrogramMaxLen) {
            val tmp = melSpectrogramBuffer[-melSpectrogramMaxLen]
        }
        Log.d("melSpec", melSpec.size.toString())
    }

    fun streamingFeatures(x: FloatArray) {
        val processedSamples = 0

        if (rawDataReminder.size != 0) {
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
//        # Only calculate melspectrogram once minimum samples are accumulated
//        if self.accumulated_samples >= 1280 and self.accumulated_samples % 1280 == 0:
        if (accumulatedSamples >= 1280 && accumulatedSamples % 1280 == 0) {
            streamingMelSpectrogram(accumulatedSamples)
        }
    }

    fun invoke(x: FloatArray): FloatArray {
        streamingFeatures(x)
        return FloatArray(0)
    }

    fun getMelSpec(audioData: FloatArray, windowSize: Int = 76): FloatArray {
        val melSpecInput =
            createFloatTensor(env, audioData, tensorShape(1, audioData.size.toLong()))
        Log.d("audio data size", audioData.size.toString())
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
