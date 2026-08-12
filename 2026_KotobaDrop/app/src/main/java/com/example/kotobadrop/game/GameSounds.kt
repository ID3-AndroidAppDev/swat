package com.example.kotobadrop.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.kotobadrop.R

enum class GameSound { CLEAR, MISS, WIN, GAME_OVER, SKEBOB }

/**
 * Playback for the four soft bundled SFX (§12 step 9, "behind soundEnabled, default off").
 * The WAVs are synthesized offline by tools/sfx/generate_sfx.py into res/raw. Callers gate
 * on the live soundEnabled setting — this class doesn't read settings itself, so it stays
 * a dumb audio wrapper.
 */
class GameSounds(context: Context) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    // load() is async; play() before a sound finishes loading is a silent no-op, which is
    // fine — loading takes milliseconds and the first clear can't happen that fast.
    private val ids = mapOf(
        GameSound.CLEAR to soundPool.load(context, R.raw.sfx_clear, 1),
        GameSound.MISS to soundPool.load(context, R.raw.sfx_miss, 1),
        GameSound.WIN to soundPool.load(context, R.raw.sfx_win, 1),
        GameSound.GAME_OVER to soundPool.load(context, R.raw.sfx_game_over, 1),
        // Do-not-touch-mode clear sound (user-supplied mp3) — has a chance to replace CLEAR
        // when the do-not-touch toggle is on.
        GameSound.SKEBOB to soundPool.load(context, R.raw.sfx_skebob, 1),
    )

    fun play(sound: GameSound) {
        soundPool.play(ids.getValue(sound), 0.6f, 0.6f, 1, 0, 1f)
    }

    fun release() {
        soundPool.release()
    }
}

@Composable
fun rememberGameSounds(): GameSounds {
    val context = LocalContext.current.applicationContext
    val sounds = remember { GameSounds(context) }
    DisposableEffect(Unit) {
        onDispose { sounds.release() }
    }
    return sounds
}
