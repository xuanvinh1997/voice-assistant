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
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.api.ones
import org.jetbrains.kotlinx.multik.ndarray.data.D1
import org.jetbrains.kotlinx.multik.ndarray.data.D2
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.MultiArray
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.data.slice
import org.jetbrains.kotlinx.multik.ndarray.operations.div
import org.jetbrains.kotlinx.multik.ndarray.operations.plus
import org.jetbrains.kotlinx.multik.ndarray.operations.stack
import org.jetbrains.kotlinx.multik.ndarray.operations.times
import org.jetbrains.kotlinx.multik.ndarray.operations.toFloatArray
import org.jetbrains.kotlinx.multik.ndarray.operations.toList

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
    private val featureBufferMaxLen = 120
    private val mSampleRate = sampleRate
    private var rawDataBuffer = FloatArray(0)
    private var featureBuffer = mk.ones<Float>(16,96)

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
        val buffer = rawDataBuffer + x
        rawDataBuffer = buffer
//        return buffer
    }

    private fun clear() {
        rawDataBuffer = FloatArray(0)
    }

    private fun getEmbeddingModelPredict(x: D2Array<Float>): FloatArray {
        val input = createFloatTensor(env, x.toFloatArray(), tensorShape(1, 76, 32, 1))
        val inputs = mutableMapOf<String, OnnxTensor>()
        inputs["input_1"] = input
        val output = embeddingModel!!.run(inputs)
        input.close()
        val tmp = output.use { it[0].value as Array<Array<Array<FloatArray>>> }[0][0][0]
        return tmp
    }

    private fun streamingMelSpectrogram(nSamples: Int) {
        if (rawDataBuffer.size < 400) {
            throw IllegalArgumentException("The number of input frames must be at least 400 samples @ 16khz (25 ms)!")
        }
        val tmp = rawDataBuffer.takeLast(nSamples + 160 * 3)

        val melSpec = getMelSpec(tmp.toFloatArray())
        var melSpecTrans:MultiArray<Float, D2> = mk.ndarray(melSpec, melSpec.size/32, 32)
        melSpecTrans = melSpecTrans.times(0.1f).plus(2.0f)
        melSpectrogramBuffer = melSpectrogramBuffer.cat(melSpecTrans, axis = 0)
//        melSpectrogramBuffer
        if (melSpectrogramBuffer.shape[0] > melSpectrogramMaxLen) {
            // update melSpectrogramBuffer from the end of the buffer to the beginning melSpectrogramMaxLen
            melSpectrogramBuffer = melSpectrogramBuffer.slice(
                (melSpectrogramBuffer.shape[0] - melSpectrogramMaxLen) until melSpectrogramMaxLen,
                axis = 0
            )
        }
    }

    private fun streamingFeatures(x: FloatArray): Int {
        var processedSamples = 0
        var x = x
//        Log.d("accumulatedSamples", accumulatedSamples.toString())
        if (rawDataReminder.isNotEmpty()) {
            x = rawDataReminder + x
            rawDataReminder = FloatArray(0)
        }
        if (accumulatedSamples + x.size >= 1280) {
            val reminder = (accumulatedSamples + x.size) % 1280
            if (reminder != 0) {
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
                    (-76 + ndx) until ndx,
                    axis = 0
                )
//                if x.shape[1] == 76:
                if (tmp.shape[0] == 76) {
                    val output = getEmbeddingModelPredict(tmp)
                    featureBuffer = featureBuffer.cat(mk.ndarray(output,1 ,96), axis = 0)
                }
            }

            processedSamples = accumulatedSamples
            accumulatedSamples = 0
        }
        if (featureBuffer.shape[0] > featureBufferMaxLen) {
            featureBuffer = featureBuffer.slice(featureBuffer.shape[0]-featureBufferMaxLen until featureBuffer.shape[0], axis = 0)
        }
//        return processed_samples if processed_samples != 0 else self.accumulated_samples
        return if (processedSamples != 0) processedSamples else accumulatedSamples
    }

    fun getFeatures(nFeatureFrames:Int = 16, startNdx: Int = 0): MultiArray<Float, D2> {
//        Log.d("featureBuffer", "${featureBuffer.shape[0]}, ${featureBuffer.shape[1]}")
//        if start_ndx != -1:
//            end_ndx = start_ndx + int(n_feature_frames) if start_ndx + n_feature_frames != 0 else len(self.feature_buffer)
        if (startNdx != 0) {
            val endNdx = if ((startNdx + nFeatureFrames) != 0) (startNdx + nFeatureFrames)  else featureBuffer.shape[0]
            return featureBuffer.slice(startNdx until endNdx, axis = 0)
        }else {
//            return self.feature_buffer[int(-1*n_feature_frames):, :][None, ].astype(np.float32)
            return featureBuffer.slice(featureBuffer.shape[0] - nFeatureFrames until featureBuffer.shape[0], axis = 0)
        }

    }

    fun invoke(x: FloatArray): Int {
        return streamingFeatures(x)
    }

    private fun getMelSpec(audioData: FloatArray, windowSize: Int = 76): FloatArray {
        val melSpecInput =
            createFloatTensor(env, audioData, tensorShape(1, audioData.size.toLong()))
        val inputs = mutableMapOf<String, OnnxTensor>()
        inputs["input"] = melSpecInput
        val melSpecOutput = melSpecModel!!.run(inputs)
        melSpecInput.close()
        val tmp =
            melSpecOutput.use { it[0].value as Array<Array<Array<FloatArray>>> }[0][0].flatMap { it.toList() }
                .toFloatArray()

        return tmp
    }
}