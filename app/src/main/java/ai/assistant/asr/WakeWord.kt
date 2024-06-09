package ai.assistant.asr

import ai.assistant.Utils
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.extensions.OrtxPackage
import java.nio.FloatBuffer

class WakeWord(melSpecModelPath: String, embeddingModelPath: String) {
    private var melSpecModel: OrtSession? = null
    private var embeddingModel: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()
    private val sessionOptions = OrtSession.SessionOptions()


    private fun loadMelspecModel(melSpecModelPath: String) {
        melSpecModel = env.createSession(melSpecModelPath, sessionOptions)
    }

    private fun loadEmbeddingModel(embeddingModelPath: String) {
        embeddingModel = env.createSession(embeddingModelPath, sessionOptions)
    }

    init {
        loadMelspecModel(melSpecModelPath)
        loadEmbeddingModel(embeddingModelPath)
    }

    // lambda x: x/10 + 2
    private fun transformMelSpec(melSpec: Array<Array<FloatArray>>) {
        for (i in melSpec.indices) {
            for (j in melSpec[i]) {
                for (k in j.indices) {
                        j[k] = j[k] / 10 + 2
                }
            }
        }
    }

    fun getMelSpec(audioData: FloatArray, windowSize: Int = 76): MelSpecResult {
        val start = System.currentTimeMillis()
        val melSpecInput =
            createFloatTensor(env, audioData, tensorShape(1, audioData.size.toLong()))
        val inputs = mutableMapOf<String, OnnxTensor>()
        inputs["input"] = melSpecInput
        val melSpecOutput = melSpecModel!!.run(inputs)
        melSpecInput.close()
        val melSpec = melSpecOutput.use {
            // float32[time,1,Clipoutput_dim_2,32]
            (melSpecOutput[0].value as Array<Array<Array<FloatArray>>>)[0]
        }
        // Arbitrary transform of melSpectrogram
        transformMelSpec(melSpec)
        // Window
        val windows = mutableListOf<Array<Array<FloatArray>>>()
        for (i in 0 until melSpec.size step 8) {
            val window = melSpec.sliceArray(i until i + windowSize)
            if (window.size == windowSize) { // truncate short windows
                windows.add(window)
            }
        }
        // Expand dims
        val arrayWindows = windows.toTypedArray()

        // create input tensor
        val input = OnnxTensor.createTensor(
            env, FloatBuffer.wrap(flatten(arrayWindows)),
            tensorShape(arrayWindows.size.toLong(), 76, 32, 1)
        )

        val end = System.currentTimeMillis()

        return MelSpecResult(melSpec, end - start)
    }
}
fun flatten(input: Array<Array<Array<FloatArray>>>): FloatArray {
    return input.flatMap { it.flatMap { it.flatMap { it.asIterable() } } }.toFloatArray()
}

// MelSpecResult
data class MelSpecResult(val melSpec: Array<*>, val inferenceTimeInMs: Long)