# LOGVEX CRYSTAL — Extension Security Model

> This document states plainly what the extension system does and does not protect against.
> The design goal is honesty: a Java classloader is not a security sandbox, so the client does
> not pretend to have one.

## 1. The threat model, stated up front

An extension is **code that runs in the client's process with the client's full privileges.**
It can read the screen, send network packets, read and write files, and crash the game. There is
no permission prompt that can change that: once a class is loaded and running, it can do
anything the process can do.

The extension system therefore does **not** claim to make untrusted code safe. What it does
provide is:

- **Explicit consent.** No extension runs until the user enables it, and the choice is persisted.
- **Lifecycle hygiene.** Every registration an extension makes is owned and released; a failed
  extension cannot leave half-registered listeners behind.
- **Honest metadata.** The manifest describes what the extension intends, so a user can decide.

## 2. What "discovery" actually does

Extensions are **Fabric mods**. Discovery iterates the mods the Fabric loader has already
accepted and reads their `custom.logvex.extension` metadata. It never downloads, scans or loads
arbitrary files, and it never instantiates an entrypoint during discovery - only when the user
enables the extension.

The consequence, stated plainly: if a mod is installed, it is already running its own
`ModInitializer` and mixins with full privileges, regardless of the extension system. The
extension API is a **convenience and lifecycle** layer, not a gate that keeps an installed mod
from doing something.

## 3. What the manifest is and is not

The manifest is **metadata, not proof of safety.** It is written by the extension author, so it
can claim anything. `ExtensionManifest.validate()` only rejects malformed or incompatible
declarations (blank ids, missing versions, a newer API major); it does not and cannot attest
that the extension is benign. A user should treat enabling an extension exactly as they treat
installing any mod.

## 4. What the API does enforce

- **No shadowing of built-ins.** `CommandManager.registerDynamic` rejects a name the core
  dispatch already owns, so an extension cannot hijack a built-in command.
- **Scoped cleanup.** `ApiRegistry` releases every registration in reverse order when an owner's
  scope closes, whether that is a clean disable or a rollback after `initialize` threw.
- **Failure isolation.** A throwing `initialize` is caught; the extension is marked `FAILED` and
  its partial registrations are rolled back. A throwing `shutdown` is logged and does not stop
  other extensions from shutting down.
- **No auto-run.** `enableApproved()` only re-enables ids the user approved before. Discovery
  never enables anything.

## 5. What the API does not enforce

- **No sandbox.** Extensions share the process, the classpath and the permissions of the client.
- **No unload.** Disabling releases registrations but does **not** unload classes; the JVM has no
  supported way to unload a class once loaded. A disabled extension's classes remain in memory.
- **No resource limits.** There is no CPU, memory or network cap on an extension. A malicious or
  buggy extension can still stall a tick or exhaust memory.

## 6. Guidance for users

Only enable extensions you trust, exactly as you would only install mods you trust. Read the
manifest, prefer open-source extensions, and remember that "it went through the extension API"
says nothing about whether the code is safe.

## 7. Guidance for authors

Keep `initialize` fast and exception-safe. Register through the `ClientApi` you are given rather
than reaching into client internals, so your registrations are cleaned up correctly. Implement
`shutdown` to release anything the API does not own. Do not assume you can be unloaded.
