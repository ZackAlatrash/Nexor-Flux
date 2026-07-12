# Recomp Tracker

Recomp Tracker is a local-first Android app for logging nutrition, body metrics, recovery, training, and weekly calorie-adjustment decisions. All data lives on-device; optional cloud features (AI coach/insights via an OpenAI-compatible API, web search, Open Food Facts barcode lookup) require a user-supplied API key and network access.

## AI features

Recomp Tracker's AI is **cloud-only and bring-your-own-key**: every AI feature is gated on an OpenAI-compatible API key you supply, and nothing is sent anywhere until you provide one. The guiding principle is **deterministic-first** — the app's own engines compute every number, target, and verdict, and the model is only ever allowed to add prose. The AI can phrase, explain, and act on your data, but it can never invent a metric.

### AI coach (chat)
A multi-turn conversational coach grounded in your actual logged data. It can **read** your day, trends, body metrics, and training, and — behind an explicit confirmation step — **take actions** on your behalf. It has 19 tools:
- **Reads** — today's summary, weekly macro trends, body-measurement trends, training summary, food-library search, meal suggestions for your remaining macros, and web search (via Tavily) for foods or facts it doesn't already have.
- **Writes (confirmed)** — log a meal, log a metric, update the calorie target, create/edit routines, create a custom exercise, and edit/delete a logged meal. Every write shows a confirmation card describing exactly what will happen before it runs.
- **Memory** — remembers durable facts about you (diet, injuries, preferences) and forgets them on request.

Responses stream token-by-token, and the coach is seeded once per conversation with a live snapshot of your plan, profile, and today's totals (re-seeded when the day rolls over).

### Insight cards
Short, streamed **Recovery** and **Progress** verdicts (two sentences each) that appear on the relevant screens. They're generated from pre-computed signals and regenerate only when the underlying data actually moves — not on every screen open.

### Weekly AI briefing
The weekly review is narrated by the model over a **deterministic skeleton**: the engine builds the verdict and the numbers, and the model only fills in the prose around them. If the model returns nothing usable, the app falls back to the engine's own wording — the numbers are never at risk.

### Proactive coaching
A deterministic **signal engine** (18 detectors across nutrition, body, training, and recovery) runs in the background, selects at most one prioritized insight per day, and can surface it as a rate-limited push notification (quiet hours 22:00–07:00, capped per week). This pipeline needs no LLM at all — the model only optionally rephrases the chosen card.

### Cited knowledge base (RAG)
The coach, insights, and briefing are grounded in a curated **retrieval-augmented knowledge base** (77 cited chunks across nutrition and training domains). Relevant passages are retrieved per request, injected into the prompt within a fixed budget, and the model cites its sources.

### Smaller AI touches
- **Recipe namer** — suggests a short name for a recipe you build.
- **Signal & rebalance phrasing** — reword proactive-signal and rebalance-offer cards, always with a deterministic fallback if the model fails.

### Configuration & privacy
- Set the **base URL, model, and API key** in Settings. The default provider is **OpenRouter**; any OpenAI-compatible endpoint works.
- The API key (and optional Tavily web-search key) are stored in **AES-256-GCM `EncryptedSharedPreferences`** and never logged.
- All AI features stay disabled until a key is present, and only the data needed for a given request is sent to your chosen provider — everything else stays on-device.

For the full technical design (turn loop, tool schemas, guardrails, retrieval), see [`docs/ai-coach.md`](docs/ai-coach.md).

## Stack
- Kotlin
- Jetpack Compose and Material 3
- Single-activity architecture with Compose Navigation
- Coroutines and Flow/StateFlow for async work and UI state
- Room for local app data
- DataStore Preferences for plan settings
- EncryptedSharedPreferences (AES-256-GCM) for the AI/web-search API keys
- OkHttp for the OpenAI-compatible AI client (SSE streaming + tool calling), web search, and barcode lookups
- WorkManager for background sync and the daily proactive-coach digest
- Vico for progress charts
- kotlinx.serialization for local JSON backups

## Common commands
```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
./gradlew connectedAndroidTest
```

## Local food libraries
- Import an official NEVO CSV export from Settings to search Dutch generic foods offline. When loaded, the app displays the required RIVM attribution.
- Export or merge-import personal foods as a separate JSON file without replacing logs or plan settings.
- With Health Connect nutrition and full-history permissions, scan the last 365 days of named nutrition records and review selected foods before saving them locally.

NEVO data are not bundled in this repository or APK. Download the official dataset or CSV export from RIVM after accepting its conditions, then import the local file through Settings.

Android builds require Java 17 and an Android SDK with API 36 installed. This workspace can use the local `.android-sdk` and `.jdks` directories when present:

```bash
JAVA_HOME="$PWD/.jdks/amazon-corretto-17.jdk/Contents/Home" ANDROID_SDK_ROOT="$PWD/.android-sdk" ./gradlew assembleDebug
```
