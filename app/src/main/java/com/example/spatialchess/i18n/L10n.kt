package com.example.spatialchess.i18n

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * Offline bilingual copy loaded from `assets/locales/{zh-CN,en-US}.json` (PRD chapter 15).
 *
 * - First launch opens in en-US regardless of the system language (product decision 2026-09-14);
 *   a manual choice in Settings is persisted and wins afterwards.
 * - A manual choice takes effect immediately (Compose state) and is persisted by the caller.
 * - A missing key falls back to en-US and is logged in debug builds; keys and placeholders are
 *   shared between both languages, sentences are never concatenated from fragments.
 */
object L10n {
    const val ZH = "zh-CN"
    const val EN = "en-US"
    private const val TAG = "SpatialChess.L10n"

    private val tables = HashMap<String, Map<String, String>>()

    var locale: String by mutableStateOf(EN)
        private set

    val isChinese: Boolean get() = locale == ZH

    fun init(context: Context, savedLocale: String?) {
        if (tables.isEmpty()) {
            for (name in listOf(ZH, EN)) {
                tables[name] = load(context, name)
            }
        }
        locale = savedLocale ?: defaultForSystem()
    }

    /** Default for a fresh install: English first; the player can switch to 中文 in Settings. */
    fun defaultForSystem(): String = EN

    fun switchLocale(name: String) {
        locale = if (name == ZH) ZH else EN
    }

    private fun load(context: Context, name: String): Map<String, String> = try {
        val text = context.assets.open("locales/$name.json").bufferedReader().use { it.readText() }
        val json = JSONObject(text)
        val map = HashMap<String, String>()
        for (key in json.keys()) map[key] = json.getString(key)
        map
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load locale $name", e)
        emptyMap()
    }

    /** Static copy by key, e.g. `ui.text_008`. */
    fun t(key: String): String {
        tables[locale]?.get(key)?.let { return it }
        tables[EN]?.get(key)?.let {
            Log.w(TAG, "Missing key '$key' for $locale, using en-US")
            return it
        }
        Log.e(TAG, "Missing key '$key' in every locale")
        return key
    }

    /** Runtime template with `{name}` placeholders, e.g. `runtime.tray.count`. */
    fun tf(key: String, vararg args: Pair<String, Any>): String {
        var s = t(key)
        for ((k, v) in args) s = s.replace("{$k}", v.toString())
        return s
    }

    /** "白方兵" / "White pawn": the only locale-specific join rule in code. */
    fun pieceName(sideKey: String, pieceKey: String): String =
        if (isChinese) t(sideKey) + t(pieceKey) else t(sideKey) + " " + t(pieceKey)
}
