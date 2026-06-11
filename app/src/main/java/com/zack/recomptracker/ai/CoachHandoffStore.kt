package com.zack.recomptracker.ai

/**
 * One-shot carrier for weekly-briefing context handed to the coach. The cloud coach consumes it
 * when seeding the next conversation, so it influences exactly one chat session.
 */
class CoachHandoffStore {
    @Volatile private var pending: String? = null

    fun set(context: String) { pending = context }

    /** Returns the pending context (if any) and clears it. */
    fun consume(): String? {
        val value = pending
        pending = null
        return value
    }
}
