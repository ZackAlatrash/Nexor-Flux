# Knowledge Research Library

Raw, inspectable source material collected for the cloud-coach knowledge base. This folder is a
**research staging area** — it is NOT wired into the app. Later, selected, distilled notes from
here will be turned into corpus chunks under `knowledge-sources/` and ingested via
`tools/ingest_knowledge.py`. Nothing in this folder ships in the APK as-is.

Collected: 2026-06-14.

## What's here

71 source extracts across four domains. Every extract is one markdown file with YAML frontmatter
(title, authors, year, url, license, topics) plus: full citation, source URL, access level, key
findings **with the actual numbers**, verbatim quotes, and practical takeaways for a recomp coach.

| Domain | Folder | Extracts | Index |
|---|---|---|---|
| Nutrition & macros | [`nutrition/`](nutrition/) | 16 | [`nutrition/_index.md`](nutrition/_index.md) |
| Training & hypertrophy | [`training/`](training/) | 19 | [`training/_index.md`](training/_index.md) |
| Recovery & sleep | [`recovery/`](recovery/) | 18 | [`recovery/_index.md`](recovery/_index.md) |
| Supplements & foods | [`supplements-foods/`](supplements-foods/) | 18 | [`supplements-foods/_index.md`](supplements-foods/_index.md) |

Each domain's `_index.md` lists its files with title, year, access level, and topics.

## Sourcing rules used

- **Open-access / public-domain only** — ISSN/JISSN position stands (BMC, CC BY), PMC open-access
  papers, MDPI/Frontiers/PLoS (CC BY), government/NASEM (public domain), USDA FoodData Central (CC0),
  AIS framework. Each file records its own `license`/`Access` field so you can trace provenance.
- **Numbers come from the fetched source**, not memory. A handful of sources were reachable only as
  abstracts (paywalled full text) or were blocked by reCAPTCHA/large-PDF timeouts; those are marked
  `Access: abstract-only` in the file and listed in each domain's `_index.md`. Cross-checked figures
  are noted where a primary full text couldn't be opened.

## Notable cross-source findings worth knowing

These came up during collection and are useful when deciding what to put in the corpus:

- **Protein target depends on context.** Morton 2018 meta-analysis puts the dose-response breakpoint
  at ~1.6 g/kg (ceiling ~2.2). ISSN raises this to **2.3–3.1 g/kg during a calorie deficit** to
  protect muscle. Complementary, not contradictory — the higher figure is for cutting.
- **Per-meal protein:** ~20–25 g maxes MPS for *fast* isolated proteins, but mixed meals can use
  40 g+ (Schoenfeld & Aragon 2018; Areta 2013). Total daily protein dominates.
- **Keto has no fat-loss advantage** when calories + protein are equated (ISSN Diets 2017); its only
  edge is ~appetite suppression under ad-libitum eating.
- **Training volume** is the main hypertrophy driver: ~10 sets/week threshold, **12–20 sets/week**
  the commonly-cited sweet spot; frequency matters mostly as a way to distribute volume.
- **Train near failure (0–3 RIR)** for hypertrophy; strength is less sensitive to failure proximity.
- **Sleep 7–9 h**; restriction blunts fat loss, raises appetite, and impairs recovery. Some recovery
  modalities (chronic cold-water immersion after lifting; high-dose antioxidants) may *blunt*
  muscle adaptations — a nuance worth capturing.
- **Strong-evidence supplements:** creatine, caffeine, beta-alanine (1–4 min efforts), protein,
  sodium bicarbonate. HMB moderate/mixed; citrulline limited.

Per-domain disagreements and caveats are documented at the bottom of each `_index.md`.

## Next step (not done yet)

You inspect this library and decide what to distill into the corpus. When ready, the workflow is:
write/curate `knowledge-sources/<topic>.md` notes (cited, with user-facing tags) → run
`python3 tools/ingest_knowledge.py` → commit `app/src/main/assets/knowledge/corpus.json`.
