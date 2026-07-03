package com.zack.recomptracker.data.coach

import com.zack.recomptracker.domain.coach.CoachAction
import com.zack.recomptracker.domain.coach.CoachActionType
import com.zack.recomptracker.domain.coach.CoachSignal
import com.zack.recomptracker.domain.coach.CoachSurface
import com.zack.recomptracker.domain.coach.Confidence
import com.zack.recomptracker.domain.coach.SignalCategory
import com.zack.recomptracker.domain.coach.SignalFacts
import com.zack.recomptracker.domain.coach.SignalKind
import com.zack.recomptracker.domain.coach.SignalRationale
import com.zack.recomptracker.domain.coach.SignalTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for the decision-attached gate embedded in [CoachPushPayload.from]. */
class CoachPushPayloadTest {

    private fun signal(
        action: CoachAction = CoachAction(CoachActionType.LOG_WEIGHT, "Log weight"),
        verdict: String = "Weight and waist both up — check intake.",
        fallbackText: String = "Both trending up; log a weigh-in.",
    ) = CoachSignal(
        kind = SignalKind.FAT_GAIN_WARNING,
        tier = SignalTier.P0,
        category = SignalCategory.BODY,
        severity = 60,
        facts = SignalFacts(),
        verdict = verdict,
        action = action,
        rationale = SignalRationale(confidence = Confidence.HIGH),
        dedupKey = "FAT_GAIN_WARNING|w27",
        surface = CoachSurface.PUSH,
        fallbackText = fallbackText,
    )

    @Test
    fun `a verdict-and-action signal builds a payload`() {
        val payload = CoachPushPayload.from(signal(), CoachPushChannel.COACHING)
        assertNotNull(payload)
        assertEquals(CoachPushChannel.COACHING, payload!!.channel)
        assertEquals("Weight and waist both up — check intake.", payload.title)
        assertEquals("Both trending up; log a weigh-in.", payload.body)
        assertEquals(CoachActionType.LOG_WEIGHT, payload.target)
    }

    @Test
    fun `a signal with no actionable destination is rejected`() {
        val payload = CoachPushPayload.from(signal(action = CoachAction.None), CoachPushChannel.COACHING)
        assertNull("NONE action -> no payload (never a number without a 'so do X')", payload)
    }

    @Test
    fun `channel is carried through to the payload`() {
        val payload = CoachPushPayload.from(
            signal(action = CoachAction(CoachActionType.OPEN_WEEKLY_REVIEW, "Open review")),
            CoachPushChannel.WEEKLY_CHECK_IN,
        )
        assertEquals(CoachPushChannel.WEEKLY_CHECK_IN, payload!!.channel)
        assertEquals(CoachActionType.OPEN_WEEKLY_REVIEW, payload.target)
    }
}
