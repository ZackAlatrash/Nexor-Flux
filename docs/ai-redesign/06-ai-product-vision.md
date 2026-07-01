# AI Coaching — Product Vision

Synthesis of the five research docs in this folder (audit, value analysis, proactive design,
data utilization, market research). Written 2026-07-01 on branch `redesign/ai-coaching`.
**Planning only — nothing here is implemented.**

Architectural premise (decided): **cloud-only**. The on-device Gemma/LiteRT stack is removed.

---

## The one-sentence purpose

> **A recomposition coach that reasons across your nutrition, training, steps, and body metrics
> together, and reaches out only when it has something that will change what you do next.**

Every design choice below serves that sentence. If a feature doesn't help the AI *decide something
cross-domain and act on it*, it doesn't belong.

---

## The problem we're actually solving

Not "the app lacks AI features." The opposite — it has **9 insight cards, a chat coach, a weekly
briefing, and a recipe namer**, and the owner still ignored all of them for a month. The research is
unanimous on why:

1. **They narrate, they don't decide.** WEEKLY_PATTERN and CROSS_METRIC hand the model a finished
   numeric sentence and pay tokens to reword it. A card that tells you what you already saw on the
   chart changes nothing. (`01-current-ai-audit.md`, `02-user-value-analysis.md`)
2. **They're siloed.** Each card sees one pre-computed context object. Waist, skinfold, sleep, RIR,
   e1RM, and the free-text notes never reach a prompt — so in a *recomposition* app, the AI literally
   cannot see your training or your body composition. (`04-data-utilization.md`)
3. **They're 100% reactive.** Nothing reaches out. A card only exists if you happen to scroll to the
   screen it's on. The market's most-trusted coaches (MacroFactor, Whoop, Oura, Future) all *come to
   the user* on a cadence. (`03-proactive-ai-design.md`, `05-market-research.md`)

The model isn't the problem. The **product design around the model** is.

---

## Five principles

1. **Decide, don't narrate.** Every AI surface must attach a *decision* to a number ("weight flat,
   steps down 3k, lifting up → hold calories, add a daily walk"), never just restate it. Market lesson:
   *"every score is worthless unless a decision is attached to it."*
2. **Reason across domains — that's our moat.** The whole market is siloed: MacroFactor owns nutrition,
   Whoop/Oura own recovery, Hevy/RP own lifting. **Almost nobody reasons across all four at once.** Our
   single-app data + the existing adjustment engine can, and that is the only thing here a competitor
   can't trivially copy.
3. **Proactive spine, quiet most days.** The AI's home is a **weekly check-in** that comes to the user
   and a **daily "one thing" slot** — not an isolated chat screen. It stays silent unless it can change
   the trajectory. Restraint is a feature: the market punishes notification spam and vanity scores.
4. **Explain the why; earn trust; never lie.** Trust comes from the *explanation*, not the number
   (MacroFactor's reverse-calculated expenditure is the gold standard). One visibly-wrong output poisons
   the whole system (Whoop's "ghost workouts"), so smooth trends and state uncertainty honestly.
5. **One coach with memory, not a wall of cards.** It should feel like something that remembers your
   journey ("three weeks ago your bench stalled; it's moving again") — a single voice across weeks, not
   eight one-shot cards competing on one screen.

---

## The shape: one brain, three surfaces

A single deterministic **CoachSignalEngine** (pure Kotlin) decides *what* matters; the cloud LLM
decides *how to say it*. That split is the key architectural idea — the LLM never invents the facts, it
only phrases the decision the engine already justified. This kills hallucinated advice and makes every
surface testable.

| Surface | Role | Cadence | Push? |
|---|---|---|---|
| **Weekly Check-in** (the spine) | The recomposition verdict, cross-domain, with the *why* and one action. Extends the existing skeleton-merge `WeeklyBriefingGenerator`, now fed all four domains and fired proactively. | Weekly | Badge + ≤1 respectful push |
| **Today's Coaching** (home slot) | At most **one** thing that matters today — a plateau, a protein miss on a training day, a PR to celebrate, a planned meal to confirm. Ranked; silent if nothing clears the bar. | Daily, in-app only | No |
| **Coach Chat** | On-demand depth — now **training-aware** (a `get_training_summary` tool) and grounded in the knowledge base and body-measurement history, so a recomp coach can finally discuss lifts. | On-demand | No |

Rare **event pushes** (a new e1RM PR, a body-composition verdict) sit on top, hard-capped at **≤2
pushes/week, never consecutive days, ≤1 celebration/week**.

---

## When it speaks vs. stays silent

Speak when a deterministic signal clears its threshold AND a decision is attached AND it hasn't already
been said this week. Otherwise, silence. The engine ranks every firing signal by (priority tier P0–P3,
severity) and surfaces **exactly one winner** per slot. Examples of what earns a P0 interruption: a
fat-gain trend (weight ↑ + waist ↑), a recovery collapse, a recomposition win worth reinforcing. Noise
like "you walked less today" never pushes.

---

## What the AI must be able to see (that it can't today)

All of this data already exists — no new collection required (`04-data-utilization.md`):

- **Training**: per-set reps/weight/RIR, Epley e1RM + volume trends, session frequency, the `trained`
  flag. *Currently invisible to every AI surface.*
- **Body composition**: waist and waist-skinfold trends alongside weight — the actual recomposition
  signal, versus weight alone.
- **Recovery**: sleep, energy, hunger, soreness scores as trends, not just today.
- **Behavior**: unconfirmed planned meals, slipping streaks, quiet weigh-ins, meal timing/names, notes.

The five highest-value cross-domain insights — all possible from existing data — are the recomposition
signal, e1RM plateau detection, deload-due from RIR+soreness+sleep, training-day vs rest-day nutrition
adherence, and sleep↔performance. These become the coach's actual content.

---

## Cloud-only architecture (what it buys us)

Removing on-device collapses the mental model from three routed backends to one cloud client per role,
and lets us delete: the three `Routing*` coordinators, `GemmaInsightCoordinator`/`GemmaCoachCoordinator`,
`GemmaInsightService`/`GemmaServiceHolder`, `ModelVariant` + DownloadManager/SHA-256 plumbing, the
`AiBackend` toggle and `AiCapabilities`, `CLOUD_ONLY_KINDS`, and every 2B-specific patch
(`containsEchoPhrase`, empty-text nudge, the dead `rich` prompt modes that the cloud path never even
invoked). Less code, one voice, and the cloud model finally gets asked for cloud-quality output.

**Non-negotiable trust guardrails for a cloud coach:** the LLM phrases decisions the engine computed —
it never fabricates numbers; smooth all trends; state uncertainty honestly; and the app degrades
gracefully (deterministic text) when the network/model is unavailable, so the coach is never *broken*,
just quieter.

---

## What success looks like

The owner opens the app and the **Today's Coaching** slot tells them the one thing that matters. Once a
week the **check-in** gives a verdict they trust *because it explains the cross-domain why*. When they
hit a PR or drift off-plan, the coach notices first. They'd be annoyed if it disappeared — the test
Agent 2 says every current card fails. That is the bar: **a coach users rely on, not a card they scroll
past.**
