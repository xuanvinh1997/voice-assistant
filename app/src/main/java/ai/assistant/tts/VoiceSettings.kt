package ai.assistant.tts

import android.content.SharedPreferences
import org.json.JSONException
import org.json.JSONObject


class VoiceSettings(
    private val mPreferences: SharedPreferences,
    private val mEngine: SpeechSynthesis
) {
    val voiceVariant: VoiceVariant?
        get() {
            val variant = mPreferences.getString(PREF_VARIANT, null)
            if (variant == null) {
                val gender = getPreferenceValue(PREF_DEFAULT_GENDER, SpeechSynthesis.GENDER_MALE)
                if (gender == SpeechSynthesis.GENDER_FEMALE) {
                    return VoiceVariant.parseVoiceVariant(VoiceVariant.FEMALE)
                }
                return VoiceVariant.parseVoiceVariant(VoiceVariant.MALE)
            }
            return VoiceVariant.parseVoiceVariant(variant)
        }

    val rate: Int
        get() {
            val min: Int = mEngine.Rate.minValue
            val max: Int = mEngine.Rate.maxValue

            var rate = getPreferenceValue(PREF_RATE, Int.MIN_VALUE)
            if (rate == Int.MIN_VALUE) {
                rate = (getPreferenceValue(
                    PREF_DEFAULT_RATE,
                    100
                ).toFloat() / 100 * mEngine.Rate.defaultValue).toInt()
            }

            if (rate > max) rate = max
            if (rate < min) rate = min
            return rate
        }

    val pitch: Int
        get() {
            val min: Int = mEngine.Pitch.minValue
            val max: Int = mEngine.Pitch.maxValue

            var pitch = getPreferenceValue(PREF_PITCH, Int.MIN_VALUE)
            if (pitch == Int.MIN_VALUE) {
                pitch = getPreferenceValue(PREF_DEFAULT_PITCH, 100) / 2
            }

            if (pitch > max) pitch = max
            if (pitch < min) pitch = min
            return pitch
        }

    val pitchRange: Int
        get() {
            val min: Int = mEngine.PitchRange.minValue
            val max: Int = mEngine.PitchRange.maxValue

            var range = getPreferenceValue(PREF_PITCH_RANGE, mEngine.PitchRange.defaultValue)
            if (range > max) range = max
            if (range < min) range = min
            return range
        }

    val volume: Int
        get() {
            val min: Int = mEngine.Volume.minValue
            val max: Int = mEngine.Volume.maxValue

            var range = getPreferenceValue(PREF_VOLUME, mEngine.Volume.defaultValue)
            if (range > max) range = max
            if (range < min) range = min
            return range
        }

    val punctuationLevel: Int
        get() {
            val min: Int = mEngine.Punctuation.minValue
            val max: Int = mEngine.Punctuation.maxValue

            var level =
                getPreferenceValue(PREF_PUNCTUATION_LEVEL, mEngine.Punctuation.defaultValue)
            if (level > max) level = max
            if (level < min) level = min
            return level
        }

    val punctuationCharacters: String?
        get() = mPreferences.getString(PREF_PUNCTUATION_CHARACTERS, null)

    private fun getPreferenceValue(preference: String, defaultValue: Int): Int {
        val prefString = mPreferences.getString(preference, null) ?: return defaultValue
        return prefString.toInt()
    }

    @Throws(JSONException::class)
    fun toJSON(): JSONObject {
        val settings = JSONObject()
        settings.put(PRESET_VARIANT, voiceVariant.toString())
        settings.put(PRESET_RATE, rate)
        settings.put(PRESET_PITCH, pitch)
        settings.put(PRESET_PITCH_RANGE, pitchRange)
        settings.put(PRESET_VOLUME, volume)
        settings.put(
            PRESET_PUNCTUATION_CHARACTERS,
            punctuationCharacters
        )
        when (punctuationLevel) {
            SpeechSynthesis.PUNCT_NONE -> settings.put(PRESET_PUNCTUATION_LEVEL, PUNCTUATION_NONE)
            SpeechSynthesis.PUNCT_SOME -> settings.put(PRESET_PUNCTUATION_LEVEL, PUNCTUATION_SOME)
            SpeechSynthesis.PUNCT_ALL -> settings.put(PRESET_PUNCTUATION_LEVEL, PUNCTUATION_ALL)
        }
        return settings
    }

    companion object {
        const val PREF_DEFAULT_GENDER: String = "default_gender"
        const val PREF_VARIANT: String = "espeak_variant"
        const val PREF_DEFAULT_RATE: String = "default_rate"
        const val PREF_RATE: String = "espeak_rate"
        const val PREF_DEFAULT_PITCH: String = "default_pitch"
        const val PREF_PITCH: String = "espeak_pitch"
        const val PREF_PITCH_RANGE: String = "espeak_pitch_range"
        const val PREF_VOLUME: String = "espeak_volume"
        const val PREF_PUNCTUATION_LEVEL: String = "espeak_punctuation_level"
        const val PREF_PUNCTUATION_CHARACTERS: String = "espeak_punctuation_characters"

        const val PRESET_VARIANT: String = "variant"
        const val PRESET_RATE: String = "rate"
        const val PRESET_PITCH: String = "pitch"
        const val PRESET_PITCH_RANGE: String = "pitch-range"
        const val PRESET_VOLUME: String = "volume"
        const val PRESET_PUNCTUATION_LEVEL: String = "punctuation-level"
        const val PRESET_PUNCTUATION_CHARACTERS: String = "punctuation-characters"

        const val PUNCTUATION_NONE: String = "none"
        const val PUNCTUATION_SOME: String = "some"
        const val PUNCTUATION_ALL: String = "all"
    }
}
