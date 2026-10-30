# PHASE 7 — Testing

> Results below are from a real `./gradlew build` on this branch. The whole suite passes:
> **637 tests, 0 failures, 0 errors, 83 test classes**. The AI subsystem alone contributes
> **10 classes / 90 tests**.

## 1. Strategy

The AI subsystem is deliberately split so that everything except the GUI is Minecraft-
independent. `MiniAIManager`, `InferenceExecutor`, `ModelLoader`, `CrashCapture`,
`CrashMonitor`, `CrashClassifier`, `CrashAnalyzer`, `PrivacyFilter`, `BugReportBuilder`,
`BugReportQueue`, `ReportService` and `BaritoneAssistant` take plain inputs (a `Path`, a
`Throwable`, a string, an `AISettings`) and return plain values. That is what makes the
behaviour testable without launching Minecraft, and it is why the tests below exercise real
code paths rather than mocks.

Where a test needs a model, it uses a tiny in-process stand-in via the injectable
`MiniAIManager.Loader` hook — a real code path, not a mock of the manager itself.

## 2. AI test classes (90 tests)

| Class | Tests | What it proves |
| --- | --- | --- |
| `model.InferenceExecutorTest` | 8 | Work runs off the caller thread; overflow is rejected not queued; timeout cancels; cancellation of a queued task prevents execution; `close()` stops accepting and terminates; threads are daemon; idle reporting. |
| `MiniAIManagerTest` | 9 | Lazy load, single copy, disabled ⇒ no executor, not-installed state, idle unload, dedupe of identical requests, failure isolation. |
| `model.ModelLoaderTest` | 9 | GGUF magic detection, incomplete-file rejection, partial-download handling, install/remove, catalogue entries stay small. |
| `privacy.PrivacyFilterTest` | 11 | See `PHASE_7_PRIVACY.md` §8 — bearer/key=value/JWT/webhook/IP/server/home/coords redaction, package names preserved, short-name safety, idempotence. |
| `report.BugReportBuilderTest` | 5 | Report names LOGVEX CRYSTAL; tokens become `[redacted]`; size cap enforced. |
| `report.BugReportQueueTest` | 9 | Dedupe by fingerprint, persistence round-trip, schema refusal, status transitions. |
| `report.ReportServiceTest` | 6 | Queue dedupe; send is gated by approval and succeeds; **fails closed when email is disabled**; transport failure is recorded and kept for retry; declined report is not sent; mailto link encodes recipient and body. |
| `crash.CrashCaptureTest` | 11 | Captures with environment; disabled when the setting is off; duplicate crash is counted not repeated; module exception classified; handler chains to the previous handler and restores; a broken handler does not break the original; monitor notifies once per new fingerprint; history is bounded; OOM is confirmed; unknown is never presented as confirmed; client frames extracted. |
| `crash.CrashFingerprintTest` | 14 | Fingerprint stability and sensitivity across exception type/message/frames. |
| `baritone.BaritoneAssistantTest` | 8 | Unavailable status yields guidance not an error; null handled; goal-without-path flagged; healthy idle reports nothing; repeated errors surfaced; paused navigation reported; summary never leaks coordinates; unavailable handled. |

## 3. Full-suite result

```
BUILD SUCCESSFUL
classes=83  tests=637  failures=0  errors=0  skipped=0
```

Aggregated from the JUnit XML under `build/test-results/test/`. The `ModuleRegistryTest`
still asserts the exact registered-module count, and the AI module is registered like any
other, so a wiring regression fails the build.

## 4. End-to-end verification (performed)

Because there is no bundled inference runtime and no GPU, the *model* path is verified by the
unit tests around the loader and executor, and the *rules* path is verified end to end:

1. **Crash capture → classify → fingerprint → record** — exercised by `CrashCaptureTest`
   against real `Throwable`s.
2. **Model download + checksum** — the two catalogue files were downloaded in full and hashed
   on this machine; both SHA-256 values match the upstream `ETag` and are now pinned
   (`docs/ai/PHASE_7_MODEL_EVALUATION.md` §5). `ModelLoaderTest` covers the accept/reject logic.
3. **Privacy filter** — the exact sample from `PHASE_7_PERFORMANCE.md` §3 (six secrets in one
   line) is redacted correctly.
4. **Report gating** — `ReportServiceTest` proves a send fails closed when email is disabled
   and that a declined report is never sent.

## 5. Not covered by automated tests

These require a running game client and were verified by inspection, not by a test:

- `AIAssistantScreen` rendering and mouse/keyboard routing (Overview/Crashes/Baritone/Model/
  Reports/Settings pages).
- `AIHudElement` layout.
- Live model inference prose quality (no runtime is bundled).
- The headless GUI smoke run is unavailable in this container (no `xvfb`/`imagemagick`), so the
  screen is verified by compilation, the class being present in the built jar, and the
  entrypoint resolving.

## 6. Reproducing

```
./gradlew build          # compiles, runs all 637 tests, assembles the jar
```

The AI tests need no model file and no network: every external effect is behind a seam
(`Loader`, `EmailTransport`, `HttpClient`) that a test supplies.
