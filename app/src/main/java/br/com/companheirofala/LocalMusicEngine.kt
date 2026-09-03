package br.com.companheirofala

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/** Melodia original curta, criada no aparelho, sem internet nem músicas protegidas. */
class LocalMusicEngine {
    private val handler = Handler(Looper.getMainLooper())
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 65)
    private val notes = intArrayOf(
        ToneGenerator.TONE_DTMF_5, ToneGenerator.TONE_DTMF_7,
        ToneGenerator.TONE_DTMF_9, ToneGenerator.TONE_DTMF_7,
        ToneGenerator.TONE_DTMF_5, ToneGenerator.TONE_DTMF_2
    )

    fun play() {
        notes.forEachIndexed { index, note ->
            handler.postDelayed({ tone.startTone(note, 160) }, index * 190L)
        }
    }

    fun release() = tone.release()
}
