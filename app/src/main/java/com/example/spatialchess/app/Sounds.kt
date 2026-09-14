package com.example.spatialchess.app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.resource.AudioResource

/**
 * Move / store sound effects ("move sounds", PRD F09). The short clips ship inside the APK
 * (assets/sfx). Playback is spatialised on the moved piece through the Spatial SDK; if the SDK
 * audio path is unavailable (e.g. on the emulator), it falls back to a plain SoundPool.
 */
class Sounds(private val context: Context) {
    enum class Kind(val asset: String) { MOVE("sfx/move.ogg"), STORE("sfx/store.ogg") }

    private val spatial = HashMap<Kind, AudioResource>()
    private val pool: SoundPool by lazy {
        SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()
    }
    private val poolIds = HashMap<Kind, Int>()
    private var spatialBroken = false

    fun play(kind: Kind, at: Entity?) {
        if (at != null && !spatialBroken) {
            val ok = runCatching {
                val res = spatial.getOrPut(kind) { AudioResource.load(kind.name.lowercase(), "asset://" + kind.asset) }
                at.playAudio(res)
            }.onFailure { Log.w(TAG, "spatial audio unavailable, using SoundPool", it); spatialBroken = true }.isSuccess
            if (ok) { Log.i(TAG, "spatial sfx ${kind.name}"); return }
        }
        runCatching {
            val id = poolIds.getOrPut(kind) {
                context.assets.openFd(kind.asset).use { pool.load(it, 1) }
            }
            pool.play(id, 0.9f, 0.9f, 1, 0, 1f)
            Log.i(TAG, "pool sfx ${kind.name}")
        }.onFailure { Log.e(TAG, "sfx failed", it) }
    }

    companion object { private const val TAG = "SpatialChess.Sfx" }
}
