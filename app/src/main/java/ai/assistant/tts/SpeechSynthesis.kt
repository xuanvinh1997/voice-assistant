package ai.assistant.tts

import android.content.Context
import android.util.Log
import java.io.File
import java.util.LinkedList
import java.util.Locale
import java.util.MissingResourceException


class SpeechSynthesis(context: Context, callback: SynthReadyCallback?) {
    private val mContext: Context
    private val mCallback: SynthReadyCallback?
    private val mDatapath: String

    private var mInitialized = false
    var sampleRate: Int = 0
        private set
    private external fun nativeCreate(path: String): Int

    private external fun nativeGetAvailableVoices(): Array<String>

    private external fun nativeSetVoiceByName(name: String): Boolean

    private external fun nativeSetVoiceByProperties(
        language: String,
        gender: Int,
        age: Int
    ): Boolean

    private external fun nativeSetParameter(parameter: Int, value: Int): Boolean

    private external fun nativeGetParameter(parameter: Int, current: Int): Int

    private external fun nativeSetPunctuationCharacters(characters: String): Boolean

    private external fun nativeSynthesize(text: String, isSsml: Boolean): Boolean

    private external fun nativeStop(): Boolean
    private external fun nativeClassInit(): Boolean

    private external fun nativeGetVersion(): String
    private fun getLocaleFromLanguageName(name: String): Locale? {
        if (mLocaleFixes.containsKey(name)) {
            return mLocaleFixes[name]
        }
        val parts = name.split("-".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        return when (parts.size) {
            1 -> Locale(parts[0])
            2 -> Locale(parts[0], parts[1])
            3 -> Locale(parts[0], parts[1], parts[2])
            4 -> Locale(parts[0], parts[1], parts[3])
            else -> null
        }
    }

    val availableVoices: List<Voice>
        get() {
            val voices: MutableList<Voice> = LinkedList<Voice>()
            val results = nativeGetAvailableVoices()
            voiceCount = results.size / 4

            var i = 0
            while (i < results.size) {
                val name = results[i]
                val identifier = results[i + 1]
                val gender = results[i + 2].toInt()
                val age = results[i + 3].toInt()

                try {
                    require(identifier != "asia/fa-en-us") { "Voice '$identifier' is a duplicate voice." }
                    val locale = getLocaleFromLanguageName(name)
                    requireNotNull(locale) { "Locale not supported." }

                    val language = locale.isO3Language
                    require(language != "") { "Language '" + locale.language + "' not supported." }

                    val country = locale.isO3Country
                    require(!(country == "" && locale.country != "")) { "Country '" + locale.country + "' not supported." }

                    val voice = Voice(name, identifier, gender, age, locale)
                    voices.add(voice)
                } catch (e: MissingResourceException) {
                    // Android 4.3 throws this exception if the 3-letter language
                    // (e.g. nci) or country (e.g. 021) code is missing for a locale.
                    // Earlier versions return an empty string (handled above).
                    Log.d(TAG, "getAvailableResources: skipping " + name + " => " + e.message)
                } catch (e: IllegalArgumentException) {
                    Log.d(TAG, "getAvailableResources: skipping " + name + " => " + e.message)
                }
                i += 4
            }

            return voices
        }

    fun setVoice(voice: Voice, variant: VoiceVariant) {
        // NOTE: espeak_SetVoiceByProperties does not support specifying the
        // voice variant (e.g. klatt), but espeak_SetVoiceByName does.
        if (variant.variant == null) {
            nativeSetVoiceByProperties(voice.name, variant.gender, variant.age)
        } else {
            nativeSetVoiceByName(voice.identifier + "+" + variant.variant)
        }
    }

    fun setPunctuationCharacters(characters: String) {
        nativeSetPunctuationCharacters(characters)
    }

    enum class UnitType {
        Percentage,
        WordsPerMinute,

        /** One of the PUNCT_* constants.  */
        Punctuation,
    }

    inner class Parameter constructor(
        private val id: Int,
        val minValue: Int,
        val maxValue: Int,
        val unitType: UnitType
    ) {
        val defaultValue: Int
            get() = nativeGetParameter(id, 0)

        var value: Int
            get() = nativeGetParameter(id, 1)
            set(value) {
                nativeSetParameter(id, value)
            }

        fun setValue(value: Int, scale: Int) {
            this.value = value * scale / 100
        }

    }

    /** Speech rate.  */
    val Rate: Parameter = Parameter(1, 80, 449, UnitType.WordsPerMinute)

    /** Audio volume.  */
    val Volume: Parameter = Parameter(2, 0, 200, UnitType.Percentage)

    /** Base pitch.  */
    val Pitch: Parameter = Parameter(3, 0, 100, UnitType.Percentage)

    /** Pitch range (monotone = 0).  */
    val PitchRange: Parameter = Parameter(4, 0, 100, UnitType.Percentage)

    /** Which punctuation characters to announce.  */
    val Punctuation: Parameter = Parameter(5, 0, 2, UnitType.Punctuation)

    fun synthesize(text: String, isSsml: Boolean) {
        nativeSynthesize(text, isSsml)
    }

    fun stop() {
        nativeStop()
    }

    private fun nativeSynthCallback(audioData: ByteArray?) {
        if (mCallback == null) return

        if (audioData == null) {
            mCallback.onSynthDataComplete()
        } else {
            mCallback.onSynthDataReady(audioData)
        }
    }

    private fun attemptInit() {
        if (mInitialized) {
            return
        }

        if (!CheckVoiceData.hasBaseResources(mContext)) {
            Log.e(TAG, "Missing base resources")
            return
        }

        sampleRate = nativeCreate(mDatapath)
        if (sampleRate == 0) {
            Log.e(TAG, "Failed to initialize speech synthesis library")
            return
        }

        Log.i(TAG, "Initialized synthesis library with sample rate = " + sampleRate)

        mInitialized = true
    }



    interface SynthReadyCallback {
        fun onSynthDataReady(audioData: ByteArray?)

        fun onSynthDataComplete()
    }

    init {
        System.loadLibrary("ttsespeak")
        nativeClassInit()
        // First, ensure the data directory exists, otherwise init will crash.
        val dataPath: File = CheckVoiceData.getDataPath(context)

        if (!dataPath.exists()) {
            Log.e(TAG, "Missing voice data")
            dataPath.mkdirs()
        }
        // unzip raw resources to the data directory


        mContext = context
        mCallback = callback
        mDatapath = dataPath.parentFile.path

        attemptInit()
    }


    companion object {
        private val TAG: String = SpeechSynthesis::class.java.simpleName

        const val GENDER_UNSPECIFIED: Int = 0
        const val GENDER_MALE: Int = 1
        const val GENDER_FEMALE: Int = 2

        const val AGE_ANY: Int = 0
        const val AGE_YOUNG: Int = 12
        const val AGE_OLD: Int = 60

        val channelCount: Int =1
        val audioFormat: Int = 2


        var voiceCount: Int = 0
            private set


        /** Don't announce any punctuation characters.  */
        const val PUNCT_NONE: Int = 0

        /** Announce every punctuation character.  */
        const val PUNCT_ALL: Int = 1

        /** Announce some of the punctuation characters.  */
        const val PUNCT_SOME: Int = 2

//        fun getSampleText(context: Context, locale: Locale): String {
//            val metrics = context.resources.displayMetrics
//            val config = context.resources.configuration
//
//            val language = getIanaLanguageCode(locale.language)
//            val country = getIanaCountryCode(locale.country)
//            config.locale = Locale(language, country, locale.variant)
//
//            val res = Resources(context.assets, metrics, config)
//            return res.getString(R.string.sample_text, config.locale.getDisplayName(config.locale))
//        }


        fun getIanaLanguageCode(code: String): String {
            return getIanaLocaleCode(code, mJavaToIanaLanguageCode)
        }

        fun getIanaCountryCode(code: String): String {
            return getIanaLocaleCode(code, mJavaToIanaCountryCode)
        }

        private fun getIanaLocaleCode(code: String, javaToIana: Map<String, String>): String {
            val iana = javaToIana[code]
            if (iana != null) {
                return iana
            }
            return code
        }

        private val mJavaToIanaLanguageCode: MutableMap<String, String> = HashMap()
        private val mJavaToIanaCountryCode: MutableMap<String, String> = HashMap()
        private val mLocaleFixes = HashMap<String, Locale>()

        init {
            mJavaToIanaLanguageCode["afr"] = "af"
            mJavaToIanaLanguageCode["amh"] = "am"
            mJavaToIanaLanguageCode["ara"] = "ar"
            mJavaToIanaLanguageCode["arg"] = "an"
            mJavaToIanaLanguageCode["asm"] = "as"
            mJavaToIanaLanguageCode["aze"] = "az"
            mJavaToIanaLanguageCode["bul"] = "bg"
            mJavaToIanaLanguageCode["ben"] = "bn"
            mJavaToIanaLanguageCode["bos"] = "bs"
            mJavaToIanaLanguageCode["cat"] = "ca"
            mJavaToIanaLanguageCode["ces"] = "cs"
            mJavaToIanaLanguageCode["cym"] = "cy"
            mJavaToIanaLanguageCode["dan"] = "da"
            mJavaToIanaLanguageCode["deu"] = "de"
            mJavaToIanaLanguageCode["ell"] = "el"
            mJavaToIanaLanguageCode["eng"] = "en"
            mJavaToIanaLanguageCode["epo"] = "eo"
            mJavaToIanaLanguageCode["spa"] = "es"
            mJavaToIanaLanguageCode["est"] = "et"
            mJavaToIanaLanguageCode["eus"] = "eu"
            mJavaToIanaLanguageCode["fas"] = "fa"
            mJavaToIanaLanguageCode["fin"] = "fi"
            mJavaToIanaLanguageCode["fra"] = "fr"
            mJavaToIanaLanguageCode["gle"] = "ga"
            mJavaToIanaLanguageCode["gla"] = "gd"
            mJavaToIanaLanguageCode["grn"] = "gn"
            mJavaToIanaLanguageCode["guj"] = "gu"
            mJavaToIanaLanguageCode["hin"] = "hi"
            mJavaToIanaLanguageCode["hrv"] = "hr"
            mJavaToIanaLanguageCode["hun"] = "hu"
            mJavaToIanaLanguageCode["hye"] = "hy"
            mJavaToIanaLanguageCode["ina"] = "ia"
            mJavaToIanaLanguageCode["ind"] =
                "in" // NOTE: The deprecated 'in' code is used by Java/Android.
            mJavaToIanaLanguageCode["isl"] = "is"
            mJavaToIanaLanguageCode["ita"] = "it"
            mJavaToIanaLanguageCode["jpn"] = "ja"
            mJavaToIanaLanguageCode["kat"] = "ka"
            mJavaToIanaLanguageCode["kal"] = "kl"
            mJavaToIanaLanguageCode["kan"] = "kn"
            mJavaToIanaLanguageCode["kir"] = "ky"
            mJavaToIanaLanguageCode["kor"] = "ko"
            mJavaToIanaLanguageCode["kur"] = "ku"
            mJavaToIanaLanguageCode["lat"] = "la"
            mJavaToIanaLanguageCode["lit"] = "lt"
            mJavaToIanaLanguageCode["lav"] = "lv"
            mJavaToIanaLanguageCode["mkd"] = "mk"
            mJavaToIanaLanguageCode["mal"] = "ml"
            mJavaToIanaLanguageCode["mar"] = "mr"
            mJavaToIanaLanguageCode["mlt"] = "mt"
            mJavaToIanaLanguageCode["mri"] = "mi"
            mJavaToIanaLanguageCode["msa"] = "ms"
            mJavaToIanaLanguageCode["mya"] = "my"
            mJavaToIanaLanguageCode["nep"] = "ne"
            mJavaToIanaLanguageCode["nld"] = "nl"
            mJavaToIanaLanguageCode["nob"] = "nb"
            mJavaToIanaLanguageCode["nor"] = "no"
            mJavaToIanaLanguageCode["ori"] = "or"
            mJavaToIanaLanguageCode["orm"] = "om"
            mJavaToIanaLanguageCode["pan"] = "pa"
            mJavaToIanaLanguageCode["pol"] = "pl"
            mJavaToIanaLanguageCode["por"] = "pt"
            mJavaToIanaLanguageCode["ron"] = "ro"
            mJavaToIanaLanguageCode["rus"] = "ru"
            mJavaToIanaLanguageCode["sin"] = "si"
            mJavaToIanaLanguageCode["slk"] = "sk"
            mJavaToIanaLanguageCode["slv"] = "sl"
            mJavaToIanaLanguageCode["snd"] = "sd"
            mJavaToIanaLanguageCode["sqi"] = "sq"
            mJavaToIanaLanguageCode["srp"] = "sr"
            mJavaToIanaLanguageCode["swe"] = "sv"
            mJavaToIanaLanguageCode["swa"] = "sw"
            mJavaToIanaLanguageCode["tam"] = "ta"
            mJavaToIanaLanguageCode["tel"] = "te"
            mJavaToIanaLanguageCode["tat"] = "tt"
            mJavaToIanaLanguageCode["tsn"] = "tn"
            mJavaToIanaLanguageCode["tur"] = "tr"
            mJavaToIanaLanguageCode["urd"] = "ur"
            mJavaToIanaLanguageCode["vie"] = "vi"
            mJavaToIanaLanguageCode["zho"] = "zh"

            mJavaToIanaCountryCode["ARM"] = "AM"
            mJavaToIanaCountryCode["BEL"] = "BE"
            mJavaToIanaCountryCode["BRA"] = "BR"
            mJavaToIanaCountryCode["CHE"] = "CH"
            mJavaToIanaCountryCode["FRA"] = "FR"
            mJavaToIanaCountryCode["GBR"] = "GB"
            mJavaToIanaCountryCode["HKG"] = "HK"
            mJavaToIanaCountryCode["JAM"] = "JM"
            mJavaToIanaCountryCode["MEX"] = "MX"
            mJavaToIanaCountryCode["PRT"] = "PT"
            mJavaToIanaCountryCode["USA"] = "US"
            mJavaToIanaCountryCode["VNM"] = "VN"

            // Fix up BCP47 locales not handled correctly by Android:
            mLocaleFixes["cmn"] = Locale("zh")
            mLocaleFixes["en-029"] = Locale("en", "JM")
            mLocaleFixes["es-419"] = Locale("es", "MX")
            mLocaleFixes["hy-arevmda"] =
                Locale("hy", "AM", "arevmda") // hy-arevmda crashes on Android 5.0
            mLocaleFixes["yue"] = Locale("zh", "HK")
        }
    }
}
