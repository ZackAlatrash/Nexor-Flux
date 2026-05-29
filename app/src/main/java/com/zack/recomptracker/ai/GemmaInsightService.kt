package com.zack.recomptracker.ai

data class InsightAvailability(
    val available: Boolean,
    val reason: String,
)

class GemmaInsightService {
    fun availability(): InsightAvailability = InsightAvailability(
        available = false,
        reason = "Gemma is intentionally excluded from MVP calorie decisions. Add a local-only summary layer after the rule engine is proven.",
    )
}
