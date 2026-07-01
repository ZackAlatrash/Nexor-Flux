# AI Coaching Redesign

Research + product plan for rethinking the AI coaching system so users genuinely rely on it every day.
Produced 2026-07-01 on branch `redesign/ai-coaching` by six parallel research agents. **Planning only —
nothing is implemented.**

**Architectural decision:** the app goes **cloud-only** for AI; the on-device (Gemma/LiteRT) stack is
removed. All planning assumes a cloud model.

## The documents

| # | Doc | What it answers |
|---|---|---|
| 1 | [01-current-ai-audit.md](01-current-ai-audit.md) | Every AI feature/card/prompt, what data each uses vs ignores, how it fits the journey; redundancies & missed opportunities. |
| 2 | [02-user-value-analysis.md](02-user-value-analysis.md) | Brutal per-feature value verdict (Keep/Improve/Merge/Remove/Replace); delete list, merge map, keep-and-invest shortlist. |
| 3 | [03-proactive-ai-design.md](03-proactive-ai-design.md) | 19-trigger proactive catalog, cadence/restraint model, delivery surfaces, long-term coaching memory. |
| 4 | [04-data-utilization.md](04-data-utilization.md) | Every data source: how used, whether under-used, how a cloud AI could use it better; insights unlocked from existing data. |
| 5 | [05-market-research.md](05-market-research.md) | How 17 leading apps use AI; what works, what users love/ignore, market gaps, our opportunities (cited). |
| 6 | [06-ai-product-vision.md](06-ai-product-vision.md) | The redesigned AI: purpose, principles, one-brain-three-surfaces model, when to speak/stay silent. |
| 7 | [07-roadmap.md](07-roadmap.md) | Prioritized, phased plan (subtract first, then build the proactive spine). |
| 8 | [08-technical-architecture.md](08-technical-architecture.md) | **The source-of-truth technical architecture** every implementation follows: pipeline, deterministic engine, cloud responsibilities, prompts, service layer, data flow, caching, scheduling, proactive engine, memory, notifications. |

## The thesis in five lines

- The problem isn't the model — it's the product design around it. The app has ~9 AI cards and the
  owner ignored all of them for a month.
- Today's AI **narrates instead of decides**, is **siloed** (can't see training or body composition),
  and is **100% reactive**.
- The market rewards coaches that **adapt from your own data, explain the why, and come to you** on a
  cadence — and it's **siloed by domain**, so nobody reasons across nutrition + lifting + steps + body
  metrics at once. That cross-domain reasoning is our moat.
- The fix: **one deterministic engine decides what matters; the cloud LLM phrases it** — surfaced as a
  weekly check-in (spine) + a daily one-thing home slot + a training-aware chat. Subtract the weak cards
  first.
- Goal: **a coach users rely on, not a card they scroll past.**

## Suggested reading order
Start with the [vision](06-ai-product-vision.md) and [roadmap](07-roadmap.md); dive into 1–5 for the
evidence behind each claim.
