package com.opendarts.ui.common

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/** A short beep for every scored dart and a spoken call at the end of a visit. One switch (remembered) turns both off. */
class Announcer(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("opendarts_sound", Context.MODE_PRIVATE)
    private var ready = false
    private val speech: TextToSpeech? = runCatching {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS && engine?.setLanguage(Locale.getDefault()).let { it != null && it >= TextToSpeech.LANG_AVAILABLE }
        }
        engine
    }.getOrNull()
    private val tone: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull()

    var enabled by mutableStateOf(prefs.getBoolean("on", true))
        private set

    fun toggle() {
        enabled = !enabled
        prefs.edit().putBoolean("on", enabled).apply()
        if (!enabled) speech?.stop()
    }

    fun dart() {
        if (enabled) runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 90) }
    }

    fun say(text: String) {
        if (enabled && ready) runCatching { speech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "visit") }
    }

    fun release() {
        runCatching { speech?.stop(); speech?.shutdown() }
        runCatching { tone?.release() }
    }
}

val LocalAnnouncer = staticCompositionLocalOf<Announcer?> { null }
