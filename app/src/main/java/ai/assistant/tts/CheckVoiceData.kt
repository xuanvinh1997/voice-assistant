package ai.assistant.tts


import ai.assistant.R
import ai.assistant.UnzipUtils
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
//import android.os.FileUtils
import android.speech.tts.TextToSpeech
import android.util.Log
//import com.reecedunn.espeak.SpeechSynthesis.SynthReadyCallback
import java.io.File
import java.util.Locale
import java.util.zip.ZipInputStream

class CheckVoiceData : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val storageContext: Context = App.getStorageContext()
        val availableLanguages = ArrayList<String>()
        val unavailableLanguages = ArrayList<String>()

        val haveBaseResources = hasBaseResources(storageContext)
        if (!haveBaseResources || canUpgradeResources(storageContext)) {
            if (!haveBaseResources) {
                unavailableLanguages.add(Locale.ENGLISH.toString())
            }
            returnResults(
                TextToSpeech.Engine.CHECK_VOICE_DATA_FAIL,
                availableLanguages,
                unavailableLanguages
            )
            return
        }

        val engine = SpeechSynthesis(storageContext, mSynthReadyCallback)
        val voices: List<Voice> = engine.availableVoices

        for (voice in voices) {
            availableLanguages.add(voice.toString())
        }

        returnResults(
            TextToSpeech.Engine.CHECK_VOICE_DATA_PASS,
            availableLanguages,
            unavailableLanguages
        )
    }

    private fun returnResults(
        result: Int,
        availableLanguages: ArrayList<String>,
        unavailableLanguages: ArrayList<String>
    ) {
        val returnData = Intent()
        returnData.putStringArrayListExtra(
            TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES,
            availableLanguages
        )
        returnData.putStringArrayListExtra(
            TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES,
            unavailableLanguages
        )
        setResult(result, returnData)
        finish()
    }

    private val mSynthReadyCallback: SpeechSynthesis.SynthReadyCallback = object : SpeechSynthesis.SynthReadyCallback {
        override fun onSynthDataReady(audioData: ByteArray?) {
            // Do nothing.
        }

        override fun onSynthDataComplete() {
            // Do nothing.
        }
    }

    companion object {
        private const val TAG = "eSpeakTTS"

        /** Resources required for eSpeak to run correctly.  */
        private val BASE_RESOURCES = arrayOf(
            "version",
            "intonations",
            "phondata",
            "phonindex",
            "phontab",
            "en_dict",
        )

        fun getDataPath(context: Context): File {
            return File(context.getDir("voices", MODE_PRIVATE), "espeak-ng-data")
        }

        fun hasBaseResources(context: Context): Boolean {
            val dataPath = getDataPath(context)

            for (resource in BASE_RESOURCES) {
                val resourceFile = File(dataPath, resource)

                if (!resourceFile.exists()) {
                    Log.e(TAG, "Missing base resource: " + resourceFile.path)
                    return false
                }
            }

            return true
        }

        fun canUpgradeResources(context: Context): Boolean {
            try {
                val version: String =
                    FileUtils.read(context.resources.openRawResource(R.raw.version))
                val installedVersion: String = FileUtils.read(File(getDataPath(context), "version"))
                return version != installedVersion
            } catch (e: Exception) {
                return false
            }
        }
    }
}
