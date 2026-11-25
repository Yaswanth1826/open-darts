package com.opendarts.ui.common

import com.opendarts.session.SoundEffect
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundFxTest {
    @Test fun bothEffectsAreAudibleAndShort() {
        for (effect in SoundEffect.values()) {
            val pcm = SoundFx.pcm(effect)
            val seconds = pcm.size.toDouble() / SoundFx.RATE
            assertTrue("$effect length $seconds", seconds in 0.3..2.0)
            val peak = pcm.maxOf { kotlin.math.abs(it.toInt()) }
            assertTrue("$effect peak $peak", peak in 20000..32767)
        }
    }
}
