package ai.assistant.asr

import ai.assistant.R
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import org.jetbrains.kotlinx.multik.api.NativeEngineType
import org.jetbrains.kotlinx.multik.api.arange
import org.jetbrains.kotlinx.multik.api.d2array
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.api.ones
import org.jetbrains.kotlinx.multik.api.zeros
import org.jetbrains.kotlinx.multik.ndarray.data.D2
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.MultiArray
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.data.slice
import org.jetbrains.kotlinx.multik.ndarray.operations.plus
import org.jetbrains.kotlinx.multik.ndarray.operations.times
import org.jetbrains.kotlinx.multik.ndarray.operations.toFloatArray

class AudioFeatures(context: Context, sampleRate: Int = 16000) {
    private val mContext = context
    private var melSpecModel: OrtSession? = null
    private var embeddingModel: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()
    private val sessionOptions = OrtSession.SessionOptions()
    private var rawDataReminder = ShortArray(0)
    private var accumulatedSamples = 0
    private var melSpectrogramBuffer = mk.zeros<Float>(1, 32)
    private val melSpectrogramMaxLen = 10 * 97
    private val featureBufferMaxLen = 120
    private val mSampleRate = sampleRate
    private var rawDataBuffer = ShortArray(0)
    private var featureBuffer = mk.zeros<Float>(1, 96)

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

    private fun bufferRawData(x: ShortArray) {
//        self.raw_data_buffer.extend(x.tolist() if isinstance(x, np.ndarray) else x)
        val buffer = rawDataBuffer + x
        rawDataBuffer = buffer
//        return buffer
    }


    private fun getEmbeddingModelPredict(x: D2Array<Float>): FloatArray {
        if (x.shape[0] < 76) {
            return floatArrayOf()
        }
        // get last 76 frames
        val frames = x[x.shape[0] - 76 until x.shape[0]]
        val input = createFloatTensor(env, frames.toFloatArray(), tensorShape(1, 76, 32, 1))
        val inputs = mutableMapOf<String, OnnxTensor>()
        inputs["input_1"] = input
        val output = embeddingModel!!.run(inputs)
        input.close()
        val tmp = output.use { it[0].value as Array<Array<Array<FloatArray>>> }[0][0][0]
        return tmp
    }

    fun streamingFeatures(x: ShortArray) {
        val melSpec = getMelSpec(x)
        var melSpecTrans: MultiArray<Float, D2> = mk.ndarray(melSpec, melSpec.size / 32, 32)
        melSpecTrans = melSpecTrans.times(0.1f).plus(2.0f)
        melSpectrogramBuffer = melSpectrogramBuffer.cat(melSpecTrans, axis = 0)
        val feature = getEmbeddingModelPredict(melSpectrogramBuffer)
        if (feature.size == 96) {
            featureBuffer = featureBuffer.cat(mk.ndarray(feature, 1, 96), axis = 0)
        }
        if (featureBuffer.shape[0] > featureBufferMaxLen) {
            featureBuffer =
                featureBuffer.slice(featureBuffer.shape[0] - featureBufferMaxLen until featureBuffer.shape[0])
        }
    }

    fun getFeatures(nFrames: Int = 16): FloatArray {
        if (featureBuffer.shape[0] < nFrames) {
            return floatArrayOf()
        }
        val featuresArray: MultiArray<Float, D2> = featureBuffer.slice(
            featureBuffer.shape[0] - nFrames until featureBuffer.shape[0],
            axis = 0
        )
        if (featureBuffer.shape[0] > featureBufferMaxLen) {
            featureBuffer = featureBuffer.slice(
                featuresArray.shape[0] - featureBufferMaxLen until featuresArray.shape[0],
                axis = 0
            )
        }
        return featuresArray.flatten().toFloatArray()
    }

    private fun getMelSpec(audioData: ShortArray, windowSize: Int = 76): FloatArray {
        val floatArray = audioData.map { it.toFloat() }.toFloatArray()
        val melSpecInput =
            createFloatTensor(env, floatArray, tensorShape(1, audioData.size.toLong()))
        val inputs = mutableMapOf<String, OnnxTensor>()
        inputs["input"] = melSpecInput
        val melSpecOutput = melSpecModel!!.run(inputs)
        melSpecInput.close()
        val tmp =
            melSpecOutput.use { it[0].value as Array<Array<Array<FloatArray>>> }[0][0]
                .flatMap { it.toList() }
                .toFloatArray()

        return tmp
    }
}