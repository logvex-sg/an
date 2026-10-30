# PHASE 7 — Model Evaluation

> Every figure below was measured on this machine (Linux x86-64, JDK 25) or read from the
> upstream release. Nothing here is copied from a blog post.

## 1. Requirements

The spec asks for a genuinely small model, not "a large model because it is easier":

| Requirement | Target |
| --- | --- |
| Parameters | ~0.5B–1.5B |
| Quantisation | 4-bit (Q4_K_M) |
| Download size | preferably < ~300 MB |
| Runtime | CPU inference, no mandatory GPU |
| Licence | permissive and redistributable if bundled |
| Startup | never on the critical path; lazy load; idle unload |

The governing instruction is **prioritise reliability and low overhead over model
intelligence**, so the smallest model that produces useful diagnostic prose wins.

## 2. Candidate survey

| Model | Params | Quant | File size | Licence | Verdict |
| --- | --- | --- | --- | --- | --- |
| **SmolLM2 360M Instruct** | 0.36B | Q4_K_M | **258 MiB / 270,590,880 B** | Apache-2.0 | **Selected (default)** |
| Qwen2.5 0.5B Instruct | 0.5B | Q4_K_M | 379 MiB / 397,808,192 B | Apache-2.0 | Selected (opt-in) |
| SmolLM2 1.7B Instruct | 1.7B | Q4_K_M | ~1.0 GB | Apache-2.0 | Rejected — over the size budget |
| TinyLlama 1.1B | 1.1B | Q4_K_M | ~640 MB | Apache-2.0 | Rejected — over the size budget |
| Phi-3 mini | 3.8B | Q4 | ~2.2 GB | MIT | Rejected — far over budget, poor CPU latency |
| Llama 3.2 1B | 1.2B | Q4_K_M | ~800 MB | Llama 3.2 Community | Rejected — over budget and a non-OSI licence |

Sizes for the two selected models are the **measured** `Content-Length` of the exact files the
catalogue points at. The rejected sizes are the published release sizes.

## 3. Selected model: SmolLM2 360M Instruct

- **Upstream:** `HuggingFaceTB/SmolLM2-360M-Instruct`; GGUF by `bartowski`.
- **Licence:** Apache-2.0 (OSI-approved, redistributable).
- **Quantisation:** Q4_K_M (4-bit, k-quant mix).
- **Parameters:** ~362M.
- **Context:** 2048 tokens (more than enough for a crash digest).
- **Format:** GGUF (magic `GGUF`), verified before load.
- **Download:** `https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q4_K_M.gguf`
- **Verified SHA-256:** `2fa3f013dcdd7b99f9b237717fa0b12d75bbb89984cc1274be1471a465bac9c2`
- **Verified size:** 270,590,880 bytes.

## 4. Opt-in model: Qwen2.5 0.5B Instruct

- **Licence:** Apache-2.0.
- **Quantisation:** Q4_K_M. **Context:** 32768 tokens.
- **Verified SHA-256:** `6eb923e7d26e9cea28811e1a8e852009b21242fb157b26149d3b188f3a8c8653`
- **Verified size:** 397,808,192 bytes.
- Chosen as the second entry because it is still CPU-friendly but gives noticeably better
  prose; it exceeds the 300 MB target, so it is **not** the default.

## 5. Hash verification (performed)

Both files were downloaded in full on this machine and hashed with `sha256sum`:

```
2fa3f013dcdd7b99f9b237717fa0b12d75bbb89984cc1274be1471a465bac9c2  SmolLM2-360M-Instruct-Q4_K_M.gguf   (270590880 bytes)
6eb923e7d26e9cea28811e1a8e852009b21242fb157b26149d3b188f3a8c8653  Qwen2.5-0.5B-Instruct-Q4_K_M.gguf   (397808192 bytes)
```

Both hashes match the upstream `ETag` returned by the Hugging Face resolve endpoint, so the
catalogue now **pins** them (`ModelSpec.sha256`, `hasPinnedHash()`). `ModelLoader.download`
verifies the SHA-256 of the finished file and refuses to install it on mismatch, on a missing
GGUF magic, or on a truncated download (a `.part` file is only moved into place after all
checks pass).

## 6. Runtime

The client **bundles no native LLM runtime**. Inference is resolved reflectively by
`LlamaRuntimeBinding`, which probes two fixed, well-known class names
(`de.kherud.llama.LlamaModel`, `org.llama.LlamaModel`) and never loads arbitrary user code.
On a stock install nothing is found, `ModelLoader.load` reports the runtime unavailable, and the
deterministic engine takes over. This is deliberate:

- It keeps the JAR free of a platform-specific native binary (no per-OS packaging, no
  licence bundling for llama.cpp).
- It means a machine without a runtime still gets full crash analysis and Baritone diagnostics.
- A user who wants model prose can drop a compatible binding on the classpath.

`ModelLoader.load` validates the installed file (exists, at least 90% of the expected size, GGUF
magic) **before** handing it to the runtime, so an incomplete or foreign file is rejected rather
than loaded.

## 7. Measured footprint

| Metric | Value | How measured |
| --- | --- | --- |
| Model file size | 258 MiB (SmolLM2) | `sha256sum` / `Content-Length` |
| Idle overhead, AI off | ~0 (no threads, no model) | `InferenceExecutor` is not created until `enabled` |
| Idle overhead, AI on, unloaded | one idle executor, 0 live threads after core-timeout | `allowCoreThreadTimeOut(true)` |
| Loaded model memory | runtime-owned (not measurable without a binding) | reported as `-1` when unknown, never fabricated |
| Inference latency | reported per request (`lastLatencyMillis`) | `MiniAIManager.submitOnce` timing |
| Game-thread inference | never | `InferenceExecutor` runs all work off-thread; asserted by `InferenceExecutorTest` |

Because no binding is bundled, the loaded-model memory and inference latency are reported by
the runtime at run time rather than hard-coded here. Where a value is unknown the UI shows `-`
rather than a guess (`MiniAIManager.loadedBytes()` returns `-1`).

## 8. Decision

**Ship the deterministic engine as the baseline, and the local model as an optional
installation.** The default catalogue entry (SmolLM2 360M) is inside the size budget, has a
verified hash, is permissively licensed, and needs no GPU. The client works fully without it.

If a future runtime change makes even this model too heavy, the rules-based engine already
stands alone: nothing in the crash, report or Baritone paths depends on a model being present.
