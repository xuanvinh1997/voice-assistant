package ai.assistant.asr


import ai.assistant.R
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log


class WakeWord(context: Context) {
    private val audioFeatures = lazy { AudioFeatures(context = context) }.value
    private val env = OrtEnvironment.getEnvironment()
    private val sessionOptions = OrtSession.SessionOptions()
    private var session: OrtSession? = null

    init {
        sessionOptions.addNnapi()
        session = env.createSession(
            context.resources.openRawResource(R.raw.mo_shi_mo_shi).use { it.readBytes() },
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


    fun invoke(x: ShortArray): Boolean {
        // Extract features from audio
        audioFeatures.streamingFeatures(x)
        val features = audioFeatures.getFeatures()
        if(features.isEmpty()) {
            return false
        }
        val prediction = infer(features)
        if (prediction > 0.3) {
            Log.d("WakeWord", "Wake word detected with confidence $prediction")
            return true
        }
        return false
    }
}
