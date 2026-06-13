package com.zack.recomptracker.ui.component

/**
 * Status of a chart's trend verdict, rendered as a colored pill (see [VioletBadge]).
 *
 * Lives in the component layer so the shared badge depends only on shared code; the
 * feature-side mapping ([com.zack.recomptracker.ui.progress.pillStatus]) imports it
 * from here.
 *
 * CAUTION is reserved for future "slightly over/under target" logic — no current input
 * produces it, but it is kept in the enum (and given a color) so the rendering path is
 * ready when that signal is added.
 */
enum class PillStatus { GOOD, CAUTION, OFF_TRACK, NEUTRAL }
