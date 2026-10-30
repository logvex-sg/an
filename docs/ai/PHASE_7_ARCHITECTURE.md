# PHASE 7 — Architecture

> Verified against the source tree on branch `feat/femboy-product-depth`, with a real
> `./gradlew build` (JDK 25 build JVM / Java 21 mod toolchain). Every claim below names the
> file that implements it. Package: `com.logvex.crystal.ai`.

## 1. Goals and non-goals

**Goals**

- An **optional, local-only** assistant with three capabilities: crash intelligence, Baritone
  diagnostics, and local model explanations.
- **Zero cloud**: no external AI API, no API key, nothing sent anywhere unless the user
  explicitly approves a bug report.
- **No expensive work on the game thread**: model load, inference, disk and network all run on
  dedicated bounded worker threads.

**Non-goals**

- Not a general chat assistant. There is no free-form chat surface, and no fabricated
  responses: every panel is backed by a real service (`AIAssistantScreen`).
- Not an autonomous actor. The assistant never edits source, changes settings, deletes files,
  disables modules, or sends mail on its own.

## 2. Package map

| File | Responsibility |
| --- | --- |
| `AISettings` | All preferences; plain fields + `clamp()`. Persisted by `config/AISettingsStore` to `ai.json`. |
| `MiniAIManager` | Model lifecycle: lazy load, single copy, idle unload, one bounded executor. The only place a model loads. |
| `AiPromptBuilder` | Builds the (already privacy-filtered) prompts handed to the model. |
| `model/MiniAIModel` | The minimal model interface (`complete`, `unload`, `loadedBytes`). |
| `model/ModelSpec` | An immutable catalogue entry (id, URL, size, SHA-256, licence, quant, params, context). |
| `model/ModelCatalog` | The curated, permissively-licensed model list. Nothing is bundled. |
| `model/ModelLoader` | On-disk lifecycle: locate, download (progress + verify), validate GGUF, detect partials, remove, load. |
| `model/LlamaRuntimeBinding` | Reflective bridge to an optional local GGUF runtime, resolved by fixed class name. |
| `model/InferenceExecutor` | The single bounded `ThreadPoolExecutor` (1-2 daemon threads, small queue). |
| `crash/CrashMonitor` | In-memory registry: fingerprint dedupe, bounded history, listener fan-out. |
| `crash/CrashCapture` | A chained `Thread.setDefaultUncaughtExceptionHandler`; the tiny crash path. |
| `crash/CrashClassifier` | Deterministic exception -> category/severity/suspected-module rules. |
| `crash/CrashAnalyzer` | Deterministic diagnosis (cause, facts, hypotheses, fixes, confidence, key frames). |
| `crash/CrashAnalyzerService` | Merges the deterministic diagnosis with an optional, labelled model note. |
| `crash/CrashStore` | Atomic JSON persistence of crash history, so a crash survives to the next launch. |
| `context/DiagnosticContext` | Debounced, bounded environment/event snapshot for reports and prompts. |
| `privacy/PrivacyFilter` | The single redaction choke point for anything displayed or transmitted. |
| `report/BugReport`, `BugReportBuilder`, `BugReportQueue` | Report value, sanitised builder, deduplicated persistent queue. |
| `report/ReportService` | The pipeline: build -> queue -> (approved) send; all off-thread. |
| `report/EmailTransport`, `SmtpEmailTransport` | Delivery seam + a small socket SMTP client (user-supplied credentials). |
| `baritone/BaritoneAssistant`, `BaritoneAdvice` | Deterministic Baritone diagnostics from the privacy-filtered status. |
| `gui/AIAssistantScreen` | The AI workspace: Overview, Crashes, Baritone, Model, Reports, Settings. |
| `gui/hud/AIHudElement` | A compact, opt-in HUD readout. |
| `module/misc/AIAssistantModule` | The module home (settings <-> `AISettings`, HUD toggle). |

## 3. Threading model

The whole subsystem is built around one rule: **the game thread never blocks**.

| Work | Where it runs |
| --- | --- |
| `tickAi` (idle-unload clock, debounced context refresh) | Game thread, cheap only |
| Crash capture (format, classify, fingerprint, record) | The crashing thread — intentionally tiny |
| Report build, disk write, SMTP send | `ReportService`'s own single-thread executor (`logvex-report`) |
| Model download | A daemon thread started by `CrystalClient.downloadModel` |
| Model load + inference | `MiniAIManager`'s `InferenceExecutor` (`logvex-ai`, 1-2 daemon threads) |

Every executor is a bounded `ThreadPoolExecutor` with a fixed (1-2) thread count and a small
`ArrayBlockingQueue`. `InferenceExecutor.submit` returns `null` on overflow instead of
blocking, so a burst of requests can never stall a caller — the newest request is dropped and
counted (`rejectedCount`).

## 4. Model lifecycle

`MiniAIManager` owns the only model reference. The guarantees and where they live:

- **Never at startup** — nothing loads in the constructor; `ensureLoaded()` is called only when
  an analysis is requested.
- **Never two copies** — a single `AtomicReference<MiniAIModel>` plus a `loadInProgress` flag;
  `ensureLoaded()` is idempotent.
- **Never on the game thread** — the load and every inference run on `InferenceExecutor`.
- **Idle unload** — `tick(now)` unloads after `idleUnloadMinutes` (default 10) with no activity.
- **Failure isolation** — a load failure sets `FAILED`/`RUNTIME_UNAVAILABLE` and the rules-based
  engine keeps working; the rest of the client is untouched.
- **Deduplication / cancellation** — `submitOnce(key, ...)` refuses a second identical request
  while one is pending; `cancelInFlight()` cancels the current one when superseded.

State machine: `DISABLED -> IDLE -> LOADING -> READY`, with `NOT_INSTALLED`,
`RUNTIME_UNAVAILABLE` and `FAILED` as terminal-ish off-ramps. All are surfaced to the UI.

## 5. Crash path

`CrashCapture.install()` chains to `Thread.getDefaultUncaughtExceptionHandler()`, so it only
sees exceptions no other handler (including Minecraft's own crash report) already handled, and
it always delegates to the previous handler. The handler does exactly four cheap things:
format the trace, `CrashClassifier.classify`, `CrashFingerprint`, `CrashMonitor.record`.

**No** disk write, network, email or model work happens there. Persistence is a *listener* on
the monitor (`CrashStore`) that runs off the crash path. This preserves Minecraft's original
crash reporting untouched and keeps the handler safe to run while the JVM is already failing.

## 6. Baritone assistant

`BaritoneAssistant` is deterministic: it reads the same privacy-filtered `BaritoneStatus` the
rest of the client uses and turns it into a summary plus a list of likely issues and fixes.
It never guesses API method names — the Baritone integration itself is reflective and optional
(see `docs/baritone-integration.md`), and the assistant only reports what that seam exposes.
The optional model note is additive and clearly labelled.

## 7. Safety boundary

- The model output is treated as **untrusted text**: it is only ever appended to a diagnosis
  as a hypothesis and displayed; it is never parsed into a command, never executed, and never
  used to change configuration or trigger a send.
- Every string shown in a report or handed to the model passes through `PrivacyFilter` first.
- Reports are only sent after explicit approval (or when the user has turned off per-report
  approval *and* enabled email), and a failed send is kept for retry.

## 8. Optionality

If the model cannot be downloaded, verified or loaded, or if no inference runtime is present,
the assistant degrades to the deterministic engine: crash classification, crash analysis,
Baritone diagnostics and report building all work with no model at all. Disabling AI
(`; ai off`, or `enabled = false`) tears the executor down and leaves crash detection intact.
