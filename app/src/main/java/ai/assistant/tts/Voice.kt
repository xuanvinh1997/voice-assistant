package ai.assistant.tts

import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.MissingResourceException


class Voice(
    val name: String,
    val identifier: String,
    val gender: Int,
    val age: Int,
    val locale: Locale
) {
    /**
     * Attempts a partial match against a query locale.
     *
     * @param query The locale to match.
     * @return A text-to-speech availability code. One of:
     *
     *  * [TextToSpeech.LANG_NOT_SUPPORTED]
     *  * [TextToSpeech.LANG_AVAILABLE]
     *  * [TextToSpeech.LANG_COUNTRY_AVAILABLE]
     *  * [TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE]
     *
     */
    fun match(query: Locale): Int {
        if (locale.isO3Language != query.isO3Language) {
            return TextToSpeech.LANG_NOT_SUPPORTED
        }
        try {
            if (locale.isO3Country != query.isO3Country) {
                return TextToSpeech.LANG_AVAILABLE
            }
        } catch (e: MissingResourceException) {
            return TextToSpeech.LANG_AVAILABLE
        }
        if (locale.variant != query.variant) {
            return TextToSpeech.LANG_COUNTRY_AVAILABLE
        }
        return TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
    }

    override fun toString(): String {
        var ret = locale.isO3Language
        if (locale.isO3Country != null && !locale.isO3Country.isEmpty()) {
            ret += '-'
            ret += locale.isO3Country
        }
        if (locale.variant != null && !locale.variant.isEmpty()) {
            ret += '-'
            ret += locale.variant
        }
        return ret
    }
}