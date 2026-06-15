package com.zack.recomptracker.ai.harness

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One judge verdict for a single generated card. */
@Serializable
data class JudgeScores(
    val accuracy: Int,
    val actionability: Int,
    val proactivity: Int,
    val tone: Int,
    val brevity: Int,
    val shouldFire: Boolean,
    val notes: String = "",
) {
    /** A card passes an iteration when every 1-5 axis is >= 4. */
    fun passes(): Boolean =
        accuracy >= 4 && actionability >= 4 && proactivity >= 4 && tone >= 4 && brevity >= 4

    fun compactLine(): String =
        "acc=$accuracy act=$actionability pro=$proactivity tone=$tone brev=$brevity " +
            "fire=${if (shouldFire) "yes" else "no"}"
}

/** Builds the judge prompt (rubric from the output doctrine) and parses its JSON reply. */
object InsightJudge {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun buildPrompt(cardLabel: String, dataPrompt: String, output: String): String = """
        You are a strict reviewer of AI coaching "insight cards" for a body-recomposition app.
        Score the CARD OUTPUT against the DATA it was given. Reply with ONLY a JSON object.

        Rubric (each 1-5, 5 best):
        - accuracy: uses ONLY numbers present in the data; invents nothing.
        - actionability: gives exactly one concrete, small next step.
        - proactivity: compares to a baseline, names the driver, non-obvious synthesis.
        - tone: warm, autonomy-supporting, zero shame.
        - brevity: <= 2 sentences, no preamble or filler.
        Plus:
        - shouldFire (boolean): given this data, SHOULD a card have spoken at all?
          false when the user is on-track and flat and the card is just filler.
        - notes: one short sentence on the biggest weakness.

        Return exactly: {"accuracy":n,"actionability":n,"proactivity":n,"tone":n,"brevity":n,"shouldFire":bool,"notes":"..."}

        CARD: $cardLabel
        DATA GIVEN TO THE CARD:
        $dataPrompt

        CARD OUTPUT:
        $output
    """.trimIndent()

    /** Extracts the first JSON object from [raw] (tolerating fences/prose) and parses it. */
    fun parse(raw: String): JudgeScores? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            json.decodeFromString(JudgeScores.serializer(), raw.substring(start, end + 1))
        } catch (_: Exception) {
            null
        }
    }
}
