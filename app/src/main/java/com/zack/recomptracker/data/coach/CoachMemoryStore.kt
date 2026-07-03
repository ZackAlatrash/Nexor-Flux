package com.zack.recomptracker.data.coach

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zack.recomptracker.core.time.DateProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.coachMemoryDataStore by preferencesDataStore(name = "coach_memory")

/** One freeform fact the coach knows about the user. */
@Serializable
data class CoachMemoryEntry(val id: String, val text: String, val createdAtIso: String)

/** Read/write surface for the coach's freeform memory, so callers unit-test against a fake. */
interface CoachMemory {
    fun observe(): Flow<List<CoachMemoryEntry>>
    suspend fun all(): List<CoachMemoryEntry>
    /** Adds a trimmed entry (dedupes case-insensitive duplicates); null if blank. */
    suspend fun add(text: String): CoachMemoryEntry?
    suspend fun update(id: String, text: String)
    suspend fun delete(id: String)
    /** Removes the single best word-match for [query]; null if nothing matches. */
    suspend fun removeMatching(query: String): CoachMemoryEntry?
}

/** Inert memory for tests / Context-free construction. */
object NoopCoachMemory : CoachMemory {
    override fun observe(): Flow<List<CoachMemoryEntry>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override suspend fun all(): List<CoachMemoryEntry> = emptyList()
    override suspend fun add(text: String): CoachMemoryEntry? = null
    override suspend fun update(id: String, text: String) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun removeMatching(query: String): CoachMemoryEntry? = null
}

/**
 * DataStore-backed flat list of user facts. Mirrors [CoachJourneyStore]: `preferencesDataStore`
 * delegate, `.edit{}` setters, one JSON key, deterministic "today" via [DateProvider]. Boundary rule:
 * imports nothing from `ai/local`.
 */
class CoachMemoryStore(
    private val dataStore: DataStore<Preferences>,
    private val dateProvider: DateProvider,
) : CoachMemory {
    constructor(context: Context, dateProvider: DateProvider) :
        this(context.coachMemoryDataStore, dateProvider)

    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(raw: String?): List<CoachMemoryEntry> =
        raw?.let { runCatching { json.decodeFromString<List<CoachMemoryEntry>>(it) }.getOrDefault(emptyList()) }
            ?: emptyList()

    override fun observe(): Flow<List<CoachMemoryEntry>> = dataStore.data.map { decode(it[ENTRIES]) }

    override suspend fun all(): List<CoachMemoryEntry> = decode(dataStore.data.first()[ENTRIES])

    override suspend fun add(text: String): CoachMemoryEntry? {
        val t = text.trim()
        if (t.isBlank()) return null
        var result: CoachMemoryEntry? = null
        dataStore.edit { prefs ->
            val list = decode(prefs[ENTRIES]).toMutableList()
            val existing = list.firstOrNull { it.text.equals(t, ignoreCase = true) }
            if (existing != null) { result = existing; return@edit }
            val nextId = ((list.mapNotNull { it.id.toLongOrNull() }.maxOrNull() ?: 0L) + 1L).toString()
            val entry = CoachMemoryEntry(nextId, t, dateProvider.today().toString())
            list.add(entry)
            val capped = if (list.size > MAX_ENTRIES) list.takeLast(MAX_ENTRIES) else list
            prefs[ENTRIES] = json.encodeToString(capped)
            result = entry
        }
        return result
    }

    override suspend fun update(id: String, text: String) {
        val t = text.trim()
        if (t.isBlank()) return
        dataStore.edit { prefs ->
            val list = decode(prefs[ENTRIES]).map { if (it.id == id) it.copy(text = t) else it }
            prefs[ENTRIES] = json.encodeToString(list)
        }
    }

    override suspend fun delete(id: String) {
        dataStore.edit { prefs ->
            prefs[ENTRIES] = json.encodeToString(decode(prefs[ENTRIES]).filter { it.id != id })
        }
    }

    override suspend fun removeMatching(query: String): CoachMemoryEntry? {
        var removed: CoachMemoryEntry? = null
        dataStore.edit { prefs ->
            val list = decode(prefs[ENTRIES])
            val match = bestMatch(query, list) ?: return@edit
            prefs[ENTRIES] = json.encodeToString(list.filter { it.id != match.id })
            removed = match
        }
        return removed
    }

    /** Confident word match (exact › startsWith › contains › all-words); null otherwise. */
    private fun bestMatch(query: String, list: List<CoachMemoryEntry>): CoachMemoryEntry? {
        val q = query.lowercase().trim()
        val words = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
        fun score(text: String): Int {
            val n = text.lowercase()
            return when {
                n == q -> 3
                n.startsWith(q) || q.startsWith(n) -> 2
                n.contains(q) || q.contains(n) -> 1
                words.isNotEmpty() && words.any { n.contains(it) } -> 0
                else -> -1
            }
        }
        return list.map { it to score(it.text) }.filter { it.second >= 0 }.maxByOrNull { it.second }?.first
    }

    private companion object {
        val ENTRIES = stringPreferencesKey("entries")
        const val MAX_ENTRIES = 50
    }
}
