package com.zack.recomptracker.ui.train.component

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import org.json.JSONArray

/**
 * Loads and caches the muscle-silhouette art (front/back) shipped in
 * assets/muscles/body_{front,back}.json. Each entry is a slug + a Compose [Path] parsed from
 * the SVG d string. Thread-safe and idempotent; parse happens once.
 */
object MuscleArt {

    data class MusclePath(val slug: String, val path: Path)

    @Volatile private var front: List<MusclePath> = emptyList()
    @Volatile private var back: List<MusclePath> = emptyList()
    @Volatile private var loaded = false

    /** Parses both views once. Safe to call repeatedly and from any thread. */
    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        front = parse(context, "muscles/body_front.json")
        back = parse(context, "muscles/body_back.json")
        loaded = true
    }

    fun front(): List<MusclePath> = front
    fun back(): List<MusclePath> = back

    private fun parse(context: Context, asset: String): List<MusclePath> = runCatching {
        val text = context.assets.open(asset).bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val path = PathParser().parsePathString(o.getString("d")).toPath()
                add(MusclePath(o.getString("slug"), path))
            }
        }
    }.onFailure {
        Log.w("MuscleArt", "Failed to load $asset - muscle icons will fall back", it)
    }.getOrDefault(emptyList())
}
