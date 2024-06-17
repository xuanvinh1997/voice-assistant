package ai.assistant.tts

import java.util.regex.Pattern


class VoiceVariant protected constructor(variant: String, age: Int) {
    var variant: String? = null
    var gender: Int = 0
    val age: Int

    init {
        if (variant == MALE) {
            this.variant = null
            this.gender = SpeechSynthesis.GENDER_MALE
        } else if (variant == FEMALE) {
            this.variant = null
            this.gender = SpeechSynthesis.GENDER_FEMALE
        } else {
            this.variant = variant
            this.gender = SpeechSynthesis.GENDER_UNSPECIFIED
        }
        this.age = age
    }

    override fun toString(): String {
        val ret = if (gender == SpeechSynthesis.GENDER_MALE) {
            MALE
        } else if (gender == SpeechSynthesis.GENDER_FEMALE) {
            FEMALE
        } else {
            variant!!
        }
        if (age == SpeechSynthesis.AGE_YOUNG) {
            return "$ret-young"
        } else if (age == SpeechSynthesis.AGE_OLD) {
            return "$ret-old"
        }
        return ret
    }

    override fun equals(o: Any?): Boolean {
        if (o is VoiceVariant) {
            val other = o
            if (variant == null || other.variant == null) {
                return other.variant == null && variant == null && other.gender == gender && other.age == age
            }
            return other.variant == variant && other.gender == gender && other.age == age
        }
        return false
    }

    companion object {
        private val mVariantPattern: Pattern = Pattern.compile("-")

        const val MALE: String = "male"
        const val FEMALE: String = "female"

        fun parseVoiceVariant(value: String?): VoiceVariant? {
            val parts = mVariantPattern.split(value)
            var age = SpeechSynthesis.AGE_ANY
            when (parts.size) {
                1 -> {}
                2 -> age =
                    if (parts[1] == "young") SpeechSynthesis.AGE_YOUNG else SpeechSynthesis.AGE_OLD

                else -> return null
            }
            return VoiceVariant(parts[0], age)
        }
    }
}