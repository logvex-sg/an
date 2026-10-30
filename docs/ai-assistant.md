# LOGVEX CRYSTAL — Optional local AI assistant

> Verified against the source tree on branch `feat/femboy-product-depth`, with a real
> `./gradlew build` (JDK 25 build JVM / Java 21 toolchain). Build result: **BUILD SUCCESSFUL,
> 581 tests, 0 failures, 0 errors**. Every claim below names the file that implements it.

## 1. What this subsystem is

An **opt-in, local-first** mini-AI behind one package, `com.logvex.crystal.ai`. It adds three
things to the client and nothing else:

| Feature | Deterministic core | Optional model step |
| --- | --- | --- |
| Crash intelligence | `crash/CrashCapture`, `crash/CrashAnalyzer`, `crash/CrashMonitor` | a one-paragraph note via `crash/CrashAnalyzerService` |
| Baritone diagnostics | `baritone/BaritoneAssistant` | a short suggestion via the same model |
| Bug reports | `report/BugReportBuilder`, `report/BugReportQueue` | none — the body is assembled deterministically |

The deterministic path always works with **no model loaded**. The model is only ever consulted
when the user asks for it, and a report is only ever sent after the user approves it. Nothing
here is bundled with the client and nothing runs on the game thread.

## 2. Privacy

`privacy/PrivacyFilter` runs on every prompt and every report body **before** it leaves the
subsystem. It redacts, in order:

- home directories and user names (Windows and POSIX),
- IPv4 addresses and `host:port` server addresses,
- block coordinates and position triples,
- bearer tokens, JWTs, Discord webhook URLs and `key=value` secrets,
- any name the user lists in `extraRedactions`.

`BugReportBuilderTest` asserts a token is redacted and the `[redacted]` marker is present;
`PrivacyFilterTest` (11 cases) covers each shape, plus idempotence. Reports are built from a
filtered summary, so displaying a queued report can never reveal what was filtered out.

## 3. Resource limits

Inference runs through `model/InferenceExecutor`: a `ThreadPoolExecutor` with **one or two
daemon threads** and a **bounded queue** (`AISettings.MIN/MAX_THREADS`, `MIN/MAX_QUEUE`).
Overflow is handled by rejecting the newest request, so the oldest still-relevant work
completes rather than blocking the caller or growing the queue. `close()` shuts down and
awaits termination so a teardown cannot race an in-flight write. `InferenceExecutorTest`
covers off-thread execution, overflow rejection, cancellation and clean shutdown.

Models are downloaded on demand from a curated catalogue (`model/ModelCatalog`), verified
(size, GGUF magic, and SHA-256 when the entry is pinned), and unloaded after an idle timeout.

## 4. Command surface

`; ai <subcommand>`:

| Subcommand | Effect |
| --- | --- |
| `status` | enabled/local/email flags, current model, queue depth, crash and report counts |
| `gui` | open the assistant screen (`gui/AIAssistantScreen`) |
| `on` / `off` | master enable |
| `local <on\|off>` | enable/disable local-model analysis |
| `model [id]` | list the catalogue, or select an entry |
| `crashes` | list captured crashes |
| `analyse` | deterministic diagnosis of the latest crash |
| `baritone` | current Baritone advice |
| `report` / `reports` | queue a report / list the queue |
| `send <id>` | approve and send a queued report |
| `mailto <id>` | copy a `mailto:` link for a report |

The same surface is reachable from the `AIAssistant` module (69th registered module) and the
`; ai gui` screen. Module settings mirror `AISettings` one-for-one and persist to `ai.json`.

## 5. Persistence

All preferences persist to `<config>/logvex-crystal/ai.json` through `config/AISettingsStore`
(schema-versioned, atomic write, malformed values fall back to defaults, a newer schema is
refused). Crash history and the report queue persist under `ai/` via `crash/CrashStore` and
`report/BugReportQueue`, both with the same resilience guarantees.

## 6. What is deliberately not done

- **No telemetry and no hidden network calls.** The only outbound paths are an explicit model
  download and an approved report send (SMTP or `mailto:`); both are user-initiated.
- **No secrets in the tree.** SMTP credentials are user-supplied at runtime and stored only in
  the user's own `ai.json`; none are bundled.
- **The model never replaces the deterministic verdict.** An AI enhancement is appended and the
  diagnosis is flagged `aiGenerated`, so the UI can label it while the confirmed facts and
  fixes stay authoritative.

## 7. Manual validation still required

These need a live client and cannot run in the pure-logic test source set:

1. Trigger a real crash and confirm capture, classification and (if enabled) the model note.
2. Download a model and confirm the idle unload and the RAM ceiling.
3. Send a report by SMTP against a real server, and copy the `mailto:` link.
4. Confirm the HUD readout and the assistant screen at several GUI scales.
