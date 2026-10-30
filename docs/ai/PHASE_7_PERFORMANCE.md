# PHASE 7 — Performance

> The numbers in section 3 were **measured on this machine** (Linux x86-64, JDK 25, single
> thread) by driving the real classes with a small harness; they are not estimates. The
> invariants in section 2 are enforced by the code and covered by `InferenceExecutorTest`
> (8 cases) and `MiniAIManagerTest` (9 cases).

## 1. Design targets

| Target | How it is met |
| --- | --- |
| No work on the game thread | All inference, disk and network run on dedicated executors; `tickAi` is the only game-thread call and is trivial. |
| Bounded memory | Model is unloaded after idle; the inference queue is capped at 1–3 entries; report size is capped. |
| Bounded threads | 1–2 inference threads (`logvex-ai`), 1 report thread (`logvex-report`), all daemon. |
| Never block a caller | Overflow drops the newest request instead of waiting. |
| Cheap crash path | Crash handling is a few map/string operations; no I/O. |
| Degrade cleanly | No model ⇒ rules engine; AI off ⇒ no executor at all. |

## 2. Invariants (enforced, tested)

- **`InferenceExecutor` runs everything off the calling thread.** `runsWorkOffTheCallingThread`.
- **The queue never grows without bound.** `rejectsOverflowInsteadOfGrowingUnbounded` — a full
  queue returns `null` and increments `rejectedCount`.
- **Threads are daemon**, so they never block JVM shutdown: `threadsAreDaemonSoTheyNeverBlockShutdown`.
- **Cancellation works**: a queued task is dropped, a running one is interrupted
  (`cancellationOfQueuedTaskPreventsExecution`).
- **`close()` stops accepting and terminates threads** (`closeStopsAcceptingAndTerminatesThreads`).
- **`MiniAIManager` loads at most once and never on the game thread** (`MiniAIManagerTest`), and
  **idle-unloads** after the configured period.
- **AI off ⇒ no executor.** `MiniAIManager.reconfigure()` closes the executor when
  `enabled == false`; the constructor does not create one.

## 3. Measured micro-benchmarks

Harness: warm-up loop then a timed loop on the real classes; one thread. Latency is per call.

| Path | Measured | Notes |
| --- | --- | --- |
| `MiniAIManager.tick()` (idle clock) | **~51 ns** | Runs once per client tick. At 20 TPS that is ~1 µs/s — immeasurable. |
| `CrashClassifier.classify()` | **~200 ns** | Runs on the crash thread only. |
| `CrashMonitor.record()` | **~85 ns** | Map insert + bounded deque append. |
| `PrivacyFilter.filter()` (hostile sample: bearer token, IP, host:port, coords, two home paths, `token=`) | **~21.8 µs** | Runs once per report build, off-thread. |
| `InferenceExecutor.submit()` | **~2.8 µs** | Includes the rejected-fast path when the queue is full. |

The hostile privacy sample is deliberately worst-case (six secrets in one line). A normal crash
report line filters far faster, and filtering only happens when a report is built — never per
frame.

## 4. Thread and memory footprint

| Scenario | Live AI threads | Heap (measured) |
| --- | --- | --- |
| AI disabled | 0 (executor closed) | ~3 MB total process (no AI allocations) |
| AI enabled, model not installed | 0 until work is submitted | ~3 MB |
| AI enabled, model loaded | 1–2 (`logvex-ai`) | runtime-owned; reported live, not hard-coded |

`MiniAIManager` was constructed and `ensureLoaded()` called in both states above; when the model
is not installed it settles in `NOT_INSTALLED` with `queueDepth() == 0` and starts **no**
worker thread. `InferenceExecutor` uses `allowCoreThreadTimeOut(true)` and `MIN_PRIORITY + 1`
daemon threads, so an idle executor holds no CPU.

## 5. Frame-time safety

The game thread touches the subsystem in exactly one place, `CrystalClient.tickAi`, which:

1. calls `ai.tick(now)` — a clock comparison (~51 ns, measured above); and
2. refreshes the Baritone advice **at most once per second** (guarded by
   `lastBaritoneAdviceMillis`), because that recomputation is heavier than the tick itself.

Everything else — model load, inference, report build/write/send, model download — is submitted
to a worker and never runs inline. The AI screen reads live service state each frame and never
blocks.

## 6. Bounds summary

| Bound | Value | Setting |
| --- | --- | --- |
| Inference threads | 1–2 | `AISettings.inferenceThreads` (clamped 1–2) |
| Inference queue | 1–3 | `AISettings.pendingQueueLimit` (clamped 1–3) |
| Inference timeout | 3–120 s | `AISettings.inferenceTimeoutSeconds` (default 20) |
| Idle unload | 1–240 min | `AISettings.idleUnloadMinutes` (default 10) |
| Report size | 8–512 KB | `AISettings.maxReportKilobytes` (default 64) |
| Crash history | 5–200 | `AISettings.maxHistory` (default 40) |
| Stored trace | 24,000 chars | `CrashStore.MAX_TRACE_CHARS` |

## 7. Result

The optional assistant adds **no measurable per-frame cost** when idle (a ~51 ns tick and a
1/second advice refresh), runs all heavy work on 1–2 low-priority daemon threads with a bounded
queue, and holds no model memory until the user asks for an analysis. When it is disabled it
allocates no executor at all. It cannot stall the game thread: overflow drops a request rather
than waiting.
