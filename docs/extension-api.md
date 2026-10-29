# LOGVEX CRYSTAL — Extension API (Phase 4)

> Verified against the source tree on branch `feat/femboy-product-depth`. Build result:
> **BUILD SUCCESSFUL, 481 tests, 0 failures** (64 test classes, 258 main classes, 68
> registered modules). The API package is `com.logvex.crystal.api`; the lifecycle is
> `com.logvex.crystal.extension`.

## 1. What this is

A small, versioned facade that lets a separate Fabric mod register behaviour with the client:
event subscriptions, modules, chat commands and HUD elements. The first-party client reaches
its own services through the same facade, so the contract is exercised rather than decorative.

There is **no runtime classloader and no sandbox.** An extension is an ordinary Fabric mod that
depends on `logvex-crystal`; discovery reads metadata for mods the Fabric loader already
accepted. See `docs/extension-security.md` for why that is the honest design.

## 2. The pieces

| Type | Role |
| --- | --- |
| `ApiVersion` | The compatibility contract. `MAJOR`/`MINOR`, `supports(major, minor)`. |
| `Capabilities` | The capability names an extension can ask about (`hud`, `events`, `waypoints`, `baritone`, ...). |
| `ClientApi` | The per-owner handle an extension is given. Every registration returns an `ApiHandle`. |
| `ApiHandle` | A closeable registration. `describe()`, `active()`, `close()`. Closing twice is safe. |
| `ApiServices` | The seam between the registry and the live managers. Swapped for a fake in tests. |
| `ClientApiServices` | The real `ApiServices`: thin glue over the event bus, module/command/HUD managers. |
| `ApiRegistry` | Owns registrations per owner, and releases them in reverse order. |
| `LogvexExtension` | The entrypoint an extension implements: `initialize(ClientApi)`, optional `shutdown()`. |

### Ownership and cleanup

Every registration belongs to an **owner** (the extension id). `ApiRegistry.open(owner)` returns
a `ClientApi` scoped to that owner; each call on it is remembered. Closing the scope - on
disable, on failure, or at shutdown - releases every handle in reverse registration order, so
nothing can outlive its owner. Opening the same owner twice returns the existing scope, so a
re-enable cannot leak the first set of registrations.

If a registration is rejected (for example an extension tries to shadow a built-in command),
the registry records the reason under the owner and returns an **inert** handle rather than a
live one. An extension therefore cannot accidentally keep a dead registration.

## 3. The extension lifecycle

`ExtensionManager` is Minecraft-independent: the host is an `ExtensionHost` and the logger is
injected, so the whole lifecycle is unit tested with a fake host.

1. **Discover** — `FabricExtensionDiscovery.discover(loader)` reads each loaded mod's
   `custom.logvex.extension` manifest and builds an `ExtensionCandidate` with a **lazy**
   entrypoint supplier. No extension code runs during discovery.
2. **Validate** — the manifest is validated; a malformed or incompatible manifest is recorded
   as `INVALID`/`INCOMPATIBLE` and is never enableable.
3. **Enable** — `enable(id)` is the only path to activation. It opens the owner scope, creates
   the entrypoint, and calls `initialize(api)`. If that throws, every registration made before
   the throw is released and the extension is marked `FAILED`, never `ACTIVE`.
4. **Approve** — a successful enable persists the id in `extensions.json`. At startup,
   `enableApproved()` re-enables only extensions the user approved in a previous session;
   nothing is enabled automatically.
5. **Disable** — `disable(id)` releases the scope, then calls the extension's `shutdown()`.
   The classes stay loaded (see the security note); only the registrations are removed.

Statuses: `AVAILABLE`, `ACTIVE`, `DISABLED`, `INVALID`, `INCOMPATIBLE`, `FAILED`.

## 4. Command surface

`; extensions list` — installed extensions and their state.
`; extensions enable <id>` — activate (and persist approval).
`; extensions disable <id>` — deactivate (and revoke approval).
`; extensions reload` — rescan the loaded mods.

## 5. Compatibility rules

`ApiVersion.isCompatible(hostMajor, hostMinor, requiredMajor, requiredMinor)` returns true only
when the majors match and the host minor is at least the required minor. Additive changes bump
the minor and never remove a symbol; a breaking change bumps the major. An extension declares
its requirement in the manifest's `api` block.

## 6. Testing

`ApiRegistryTest` uses a recording `ApiServices` to prove the ownership/rollback rules with no
game runtime: reverse-order release, idempotent reopen, inert handles for rejected
registrations, and `closeAll` on shutdown. `ExtensionManagerTest` covers the lifecycle with a
fake host: discovery never loads code, failure rolls back, disable re-enables, incompatible and
invalid manifests are never enableable, and unapproved extensions are never auto-enabled.

## 7. Out of scope (and why)

- **Scripting.** No maintained embeddable runtime with the required CPU/memory limits is
  available without a heavy dependency. The honest choice is to omit it. See `docs/scripting.md`.
- **A settings group owned by an extension.** Settings currently belong to a module; an
  extension registers a module to get one. A standalone extension-owned settings store is a
  future addition.
