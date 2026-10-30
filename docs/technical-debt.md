# LOGVEX CRYSTAL - Technical Debt Register

> Audited at commit `984b3f8` (baseline: `./gradlew build` green, 47 test classes / 345 tests).
> Each item is either fixed in this pass (with the commit that fixed it) or tracked with a
> concrete next action. Nothing here is hidden or hand-waved.

## Severity legend

| Level | Meaning |
| --- | --- |
| **High** | Can cause wrong behaviour, crashes, or data loss in normal use. |
| **Medium** | Correctness/perf/robustness risk in edge cases, or a spec gap. |
| **Low** | Cosmetic, maintainability, or documentation. |

## Resolved in this pass

| # | Severity | Item | Resolution |
| --- | --- | --- | --- |
| 1 | High | `TargetProtection` used **4 EPF per level** for Blast Protection. The vanilla 1.21.11 enchantment data (`data/minecraft/enchantment/blast_protection.json`, value `base 2 / per_level_above_first 2`) makes it **2 EPF per level**; the estimate overstated blast resistance by 2x. | Fixed (commit `2dc3ed4`, PR #2): `blast * 2.0`, tests corrected. |
| 2 | Medium | `AutoCrystalModule.predictSelf`/`predictTarget` ignored Resistance and protection while the crystal-damage HUD applied them, so the module's accept/reject decision could disagree with the displayed estimate. | Fixed (commit `2dc3ed4`): both call sites (and `HoverTotemModule`) now pass `TargetProtection` values. |
| 3 | Low | Dead `CombatService.predict(...)` 5-arg overload left after the protection-aware version landed. | Fixed (commit `2dc3ed4`): overload removed. |

## Open

| # | Severity | Item | Detail | Next action |
| --- | --- | --- | --- | --- |
| 4 | Medium | **No Baritone integration.** The mission spec treats Baritone as a first-class subsystem; the tree has zero references to it. | No detection, capability model, diagnostics, controls or tests exist. | Implemented in this pass - `integration/baritone/` + `; baritone` + GUI + tests. See `docs/baritone-integration.md`. |
| 5 | Medium | Runtime (Minecraft-dependent) behaviour is only covered by the opt-in headless smoke driver, not by CI-runnable tests. | Rendering, mixins and interaction cannot run in the pure-logic test source set (Loom gives Minecraft to `main` only). | The service boundaries (damage, placement, filters, Baritone state parsing) are unit-tested; document the remaining manual matrix in `docs/release-readiness.md`. |
| 6 | Low | No CI workflow file. | Build/test is reproducible locally but nothing enforces it on push. | Optional: add a GitHub Actions workflow running `./gradlew build` with JDK 25. Out of scope for the client itself. |
| 7 | Low | The `-sources.jar` is produced unconditionally by `withSourcesJar()`. | For a paid/closed-source build the sources jar should not be published. | Gate `withSourcesJar()` behind a `-Pdev`/`-Prelease=false` flag in the release packaging pass. |

## Notes / non-issues (verified, left as-is)

- **Thread model is already safe.** Modules never touch world/entity/render state off the
  client thread; the only worker threads are the 6b6t-style background fetchers and the chest
  scan, both of which snapshot on the client thread and post results back. No change needed.
- **Config is already resilient.** Atomic writes, corrupt-file quarantine, newer-schema
  refusal, debounced autosave and shutdown flush all exist and are tested.
- **ESP already has a shared pipeline + culling.** `WorldEspCollector`/`WorldEspRenderer`
  with `Frustum` culling and per-module distance caps; no per-module world scan.
- **No fake modules found.** All 66 registered modules have real `onTick`/render behaviour.
