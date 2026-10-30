# LOGVEX CRYSTAL — Scripting: not implemented, and why

> The mission spec lists a scripting layer as an optional Phase 4 item. It is **not
> implemented**. This document states that plainly and explains the reasoning, so the omission
> is a recorded decision rather than a silent gap.

## 1. What would be required

Running user-authored scripts safely inside the client means running untrusted code with:

- a **CPU budget** per script, so a loop cannot stall the game tick;
- a **memory budget**, so a script cannot exhaust the heap;
- a **capability boundary**, so a script can only touch the API surface it is granted;
- **deterministic cancellation**, so a runaway script can be stopped.

## 2. Why it is not shipped

The JVM has no supported way to enforce all four cheaply:

- A plain `ScriptEngine` (Nashorn, GraalJS embedded) runs **in-process** with the client's full
  privileges. It is not a sandbox, so it fails the capability boundary outright.
- A **separate process or container** would provide the boundary and the limits, but adds a
  large runtime dependency, a cross-process API, and a packaging/startup cost that is out of
  proportion to the feature. It also cannot touch the client's live world/entity state without a
  serialization layer.
- A **from-scratch interpreter** for a small DSL is the only lightweight option that could be
  made safe, but it is a substantial project on its own and would still be limited to a narrow
  subset of client state.

Shipping a scripting layer that looks sandboxed but is not would be worse than not shipping one,
because it would invite users to run untrusted code under a false sense of safety.

## 3. What is offered instead

The **extension API** (`docs/extension-api.md`) is the supported way to add behaviour. It is
compile-time, lifecycle-managed and honest about its trust model (`docs/extension-security.md`).
An author who wants data-driven behaviour without writing Java should use the **automation
rules** (`; automation`), which are a bounded, data-only engine with no arbitrary code execution.

## 4. Revisiting this decision

If a maintained embeddable runtime with real CPU/memory limits and a capability boundary becomes
available as a light dependency, a scripting layer could be built on top of the extension API:
scripts would register through the same `ClientApi`, so they would inherit the same scoped
cleanup and explicit-approval model. Until then, this stays out of scope.
