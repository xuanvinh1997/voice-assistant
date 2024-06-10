package ai.assistant.asr


import ai.assistant.R
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import org.jetbrains.kotlinx.multik.api.arange
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.ndarray.operations.toFloatArray

class WakeWord(context: Context) {
    private val audioFeatures = lazy { AudioFeatures(context = context) }.value
    private val env = OrtEnvironment.getEnvironment()
    private val sessionOptions = OrtSession.SessionOptions()
    private var session: OrtSession? = null

    init {
        sessionOptions.addNnapi()
        session = env.createSession(
            context.resources.openRawResource(R.raw.alexa).use { it.readBytes() },
            sessionOptions
        )

    }
    private fun infer(input: FloatArray): Float {
        val inputTensor = createFloatTensor(env, input, tensorShape(1, 16, 96))
        val inputs = mutableMapOf<String, OnnxTensor>()
        inputs["onnx::Flatten_0"] = inputTensor
        val output = session!!.run(inputs)

        val outputTensor = output[0].value as Array<FloatArray>

        return outputTensor[0][0]
    }
    fun invoke(x: FloatArray): Boolean {
        val nPreparedSamples = audioFeatures.invoke(x)
        if(nPreparedSamples>1280){
            for (i in mk.arange<Int>(nPreparedSamples.div(1280) - 1, -1, -1)) {
                val start = 16 - i
                val features = audioFeatures.getFeatures(16, start)
                val prediction = infer(features!!.toFloatArray())
                Log.d("WakeWord", "Prediction: $prediction")
            }
//
        }else {
            if (nPreparedSamples == 1280) {
                val features = audioFeatures.getFeatures(16, 0)
//                Log.d("WakeWord", "Features: ${features!!.size}")
                val prediction = infer(features!!.toFloatArray())
                if(prediction > 0.5){
                    Log.d("WakeWord", "Prediction: $prediction")
                }
            }
        }
        return true
    }
}
