package com.zack.recomptracker.domain.review

/** EARLY = 7–13 logged days or no actionable verdict; FULL = 14+ days with a real verdict. */
enum class BriefingPhase { EARLY, FULL }

enum class SignalDirection { UP, DOWN, FLAT }
