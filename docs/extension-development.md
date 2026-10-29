# LOGVEX CRYSTAL — Writing an Extension

> An extension is an ordinary Fabric mod that depends on `logvex-crystal` and registers
> behaviour through the client's API. This guide is for authors; see
> `docs/extension-security.md` for what the system does and does not protect against.

## 1. Project setup

Depend on the client the same way any Fabric mod depends on another:

```groovy
dependencies {
    modImplementation "com.logvex:logvex-crystal:${logvex_version}"
}
```

The extension is discovered through its Fabric metadata, so no separate registration file or
download step is needed.

## 2. Declare the manifest

Add a `custom.logvex.extension` block to your `fabric.mod.json`:

```json
"custom": {
  "logvex": {
    "extension": {
      "id": "example-waypoints",
      "name": "Example Waypoints",
      "version": "1.0.0",
      "api": { "major": 1, "minor": 0 },
      "capabilities": ["waypoints", "hud"],
      "optionalDependencies": ["baritone"],
      "author": "you",
      "license": "MIT"
    }
  }
}
```

The `id` is required and must be lower-case letters, digits, `-` or `_`. Everything else is
optional; `api` defaults to `1.0`.

## 3. Declare the entrypoint

Register a `logvex-crystal-extension` entrypoint that implements `LogvexExtension`:

```json
"entrypoints": {
  "logvex-crystal-extension": ["com.example.waypoints.ExampleExtension"]
}
```

```java
public final class ExampleExtension implements LogvexExtension {

    @Override
    public void initialize(ClientApi api) {
        api.registerCommand("waypoints-demo", args -> {
            api.notify("hello from an extension", "cute");
            return true;
        });

        api.subscribe(Set.of(EventKind.TARGET_CHANGED), event ->
                api.notify("target changed", "info"));

        api.registerHudElement(new MyHudElement());
    }

    @Override
    public void shutdown() {
        // Release anything the API does not own.
    }
}
```

## 4. The rules

- **Register through the `ClientApi` you are given.** Every registration returns an `ApiHandle`
  that the client will close for you when your extension is disabled. If you keep your own
  references, close them in `shutdown()`.
- **Do not shadow built-in commands.** A name the core dispatch already owns is rejected; the
  handle you get back is inert and the reason is logged.
- **Keep `initialize` fast and exception-safe.** If it throws, everything you registered is
  rolled back and your extension is marked failed.
- **You cannot be unloaded.** Disabling releases your registrations but leaves your classes in
  memory. Do not rely on a fresh classloader.

## 5. Checking what the client supports

```java
if (api.capabilities().has(Capabilities.BARITONE)) {
    // Baritone is actually present; wire the optional path.
}
```

Capabilities are computed live, so an optional integration only reports present when it truly
is. The running API version is `api.apiVersion()`; use `ApiVersion.supports(major, minor)` if you
need to gate a newer call behind a version check.

## 6. Testing and debugging

Enable your extension in-game with `; extensions enable <id>` and watch for a notification. A
failed initialization is reported in chat and in the diagnostics log with the exception class
and message. `; extensions list` shows the current state of every installed extension.
