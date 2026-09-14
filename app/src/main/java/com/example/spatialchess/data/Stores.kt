package com.example.spatialchess.data

import android.content.Context
import android.util.Log
import com.example.spatialchess.model.BoardState
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Local, offline persistence (PRD chapter 9 "Persistence"): serial writes to a temp file, validation,
 * atomic rename, one valid backup kept. A corrupted file is isolated and never overwrites the backup.
 */
class BoardStore(context: Context) {
    private val dir = context.filesDir
    private val main = File(dir, "board_state.json")
    private val tmp = File(dir, "board_state.tmp")
    private val backup = File(dir, "board_state.bak")
    private val corrupt = File(dir, "board_state.corrupt")

    /** Test hook (debug broadcast `failsave on`) to exercise the "could not save" UI. */
    @Volatile var simulateFailure: Boolean = false

    @Synchronized
    fun save(state: BoardState): Result<Unit> = runCatching {
        if (simulateFailure) throw IllegalStateException("simulated save failure")
        val json = state.toJson().toString()
        FileOutputStream(tmp).use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        // validate what we just wrote before replacing the current save
        BoardState.fromJson(JSONObject(tmp.readText())) ?: throw IllegalStateException("written file invalid")
        if (main.exists()) {
            backup.delete()
            main.copyTo(backup, overwrite = true)
        }
        if (!tmp.renameTo(main)) throw IllegalStateException("rename failed")
        Unit
    }.onFailure { Log.e(TAG, "save failed", it) }

    sealed class LoadResult {
        data class Loaded(val state: BoardState, val fromBackup: Boolean) : LoadResult()
        object Empty : LoadResult()
        object Corrupted : LoadResult()
    }

    @Synchronized
    fun load(): LoadResult {
        if (!main.exists() && !backup.exists()) return LoadResult.Empty
        parse(main)?.let { return LoadResult.Loaded(it, false) }
        if (main.exists()) {
            corrupt.delete(); main.renameTo(corrupt)
            Log.w(TAG, "main save corrupted, isolated to ${corrupt.name}")
        }
        parse(backup)?.let { return LoadResult.Loaded(it, true) }
        return LoadResult.Corrupted
    }

    private fun parse(f: File): BoardState? = runCatching {
        if (!f.exists()) return null
        BoardState.fromJson(JSONObject(f.readText()))
    }.getOrNull()

    companion object { private const val TAG = "SpatialChess.Store" }
}

/** Placement & display preferences (PRD chapter 7). Independent from move history. */
data class Settings(
    val scalePercent: Int = 100,          // 80..140
    val yawDegrees: Int = 0,              // 0/90/180/270
    val heightOffsetCm: Int = 0,          // -1 / 0 / +1
    val tiltDegrees: Int = 0,             // UI 1.1: 0 / 20 / 40, tilt about the near edge
    val showCoordinates: Boolean = false,
    val moveSound: Boolean = true,
    val reduceMotion: Boolean = false,
    val locale: String? = null,           // null = follow system
    val onboardingDone: Boolean = false,
) {
    val scale: Float get() = scalePercent / 100f
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): Settings = Settings(
        scalePercent = prefs.getInt("scale", 100).coerceIn(80, 140),
        yawDegrees = prefs.getInt("yaw", 0),
        heightOffsetCm = prefs.getInt("height", 0).coerceIn(-3, 3),
        tiltDegrees = prefs.getInt("tilt", 0).coerceIn(0, 40),
        showCoordinates = prefs.getBoolean("coords", false),
        moveSound = prefs.getBoolean("sound", true),
        reduceMotion = prefs.getBoolean("reduceMotion", false),
        locale = prefs.getString("locale", null),
        onboardingDone = prefs.getBoolean("onboardingDone", false),
    )

    fun save(s: Settings) {
        prefs.edit()
            .putInt("scale", s.scalePercent).putInt("yaw", s.yawDegrees).putInt("height", s.heightOffsetCm).putInt("tilt", s.tiltDegrees)
            .putBoolean("coords", s.showCoordinates).putBoolean("sound", s.moveSound)
            .putBoolean("reduceMotion", s.reduceMotion).putString("locale", s.locale)
            .putBoolean("onboardingDone", s.onboardingDone)
            .apply()
    }
}
