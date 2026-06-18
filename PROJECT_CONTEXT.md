# SYSTEM CONTEXT: Vox Commander Android App

## 1. Project Identity & Philosophy
* **App Name:** Vox Commander
* **Package Name:** com.voxcommander.app
* **Nature:** FOSS (Free and Open-Source Software), Android Native.
* **Core Philosophy:** Privacy-first, local-first, absolute user sovereignty. Zero tracking, zero telemetry.
* **Monetization/API Model:** BYOK (Bring Your Own Key). Direct connections to APIs without intermediate servers.

## 2. Tech Stack & Tools
* **Language:** Kotlin
* **UI Framework:** Jetpack Compose (Material 3)
* **Async/Threading:** Kotlin Coroutines & Flow
* **Local Database:** Room (using KSP/KAPT) for persistent storage.
* **Networking:** Retrofit2 + OkHttp3 (for direct LLM API calls).
* **Security:** EncryptedSharedPreferences (for storing API keys locally).
* **Build System:** Gradle (Kotlin DSL - `build.gradle.kts`).
* **Architecture Pattern:** Clean Architecture (Domain, Data, UI, Router).

## 3. Core Architecture & Components
The app acts as a pipeline: Audio -> Text -> Intent (JSON-like structure) -> OS Execution.

### A. The Universal Contract (Domain Layer)
All processing engines must resolve to a single standardized data class:
`IntentPayload(val actionType: String, val target: String, val query: String?)`
All engines implement the interface:
`interface AssistantEngine { suspend fun processCommand(spokenText: String): IntentPayload? }`

### B. The Engines (Intent Processing)
1.  **Fast Map Engine (Primary/Tier 1):** O(1) execution time. Uses Regex pattern matching against predefined rules stored in Room DB. If a match is found, it bypasses AI completely and returns an `IntentPayload`.
2.  **Local SLM (Tier 2 - Planned):** On-device small language model (e.g., Llama.cpp / MLC LLM) for offline intent generation.
3.  **Cloud LLM (Tier 3 - Fallback):** Direct Retrofit call to OpenAI/Anthropic using the user's encrypted API key.

### C. The Router (Execution Layer)
Translates the `IntentPayload` into native Android actions using `Intents` (e.g., `ACTION_VIEW`, `PLAY_FROM_SEARCH`, managing Bluetooth connections, toggling system settings).

### D. Data Layer (Room DB)
Stores the "Fast Map" rules.
Entity `FastMapRule`: `id` (Int), `triggerPattern` (Regex String), `actionType` (String), `target` (String).

## 4. UI/UX Design (Jetpack Compose)
* **Main Screen:** Chat-like history (`LazyColumn`) showing transcribed voice commands and system execution status.
* **Settings Screen:** Inputs for BYOK (OpenAI API key), default routing preferences (e.g., Default Audio App, Default Nav App), and a "Wipe Data/Amnesia" button.
* **Fast Map Editor:** A UI for users to create Regex rules without writing JSON. Must use **Cascading Dropdowns** to prevent invalid configurations.
    * *Level 1:* Category (Audio, System, Navigation).
    * *Level 2:* Target App (Spotify, Piped, Waze) - dynamically populated based on Level 1.
    * *Level 3:* Action (Search & Play, Open App).

## 5. Current Implementation State
* Empty Compose project initialized.
* Git repository initialized.
* `AndroidManifest.xml` updated with permissions: `RECORD_AUDIO`, `INTERNET`, `BLUETOOTH_CONNECT`.

## 6. Agent Instructions
When writing code for this project:
* Strictly adhere to Clean Architecture principles. Do not mix UI logic with data logic.
* Prefer Compose-declarative solutions over imperative ones.
* Ensure all data models mapped from network or database are completely null-safe.
* Do not suggest proprietary SDKs or tracking libraries (e.g., Firebase Analytics, Crashlytics) under any circumstances.