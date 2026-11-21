package com.projects.selfdrop.android.ui

import androidx.media3.exoplayer.ExoPlayer

/**
 * Exactly one inline video may play at a time. Buffering/prepared players are
 * cheap; decoding is not, and the old VideoView setup made several simultaneous
 * decoders fight over the device.
 */
internal object VideoPlayback {
    private var playing: ExoPlayer? = null

    fun started(player: ExoPlayer) {
        if (playing === player) return
        playing?.pause()
        playing = player
    }

    fun released(player: ExoPlayer) {
        if (playing === player) playing = null
    }
}
