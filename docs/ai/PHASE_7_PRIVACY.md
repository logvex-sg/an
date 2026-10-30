# PHASE 7 — Privacy

> Implemented by `com.logvex.crystal.ai.privacy.PrivacyFilter` and enforced at build time by
> `report/BugReportBuilder`. Every rule below is covered by `PrivacyFilterTest` (11 cases) and
> `BugReportBuilderTest`.

## 1. Principle

Treat **every** crash report, log excerpt, stack trace and model input as untrusted. Redact
first, display the redacted text, and only ever transmit the redacted text. The report the user
previews is byte-for-byte the report that would be sent, because sanitisation happens once, at
build time, in `BugReportBuilder.build`.

## 2. The single choke point

`PrivacyFilter` is the only redaction code. It holds an ordered list of compiled `Pattern`
rules; `filter(text)` applies them in order. It is deliberately conservative — it would rather
over-redact a harmless string than leak a token — and it fails closed: the report path treats a
`RuntimeException` from the filter as "do not send".

`PrivacyFilter.defaultRules()` is the canonical rule set, so the tests, this document and the
implementation cannot drift.

## 3. Rules

Credentials and tokens (matched before the generic `key=value` rule):

| Rule | Matches |
| --- | --- |
| `bearer` | `Bearer <token>` |
| `basic-auth` | `Basic <base64>` |
| `jwt` | three-part `eyJ…` JSON Web Tokens |
| `github-token` | `ghp_`/`gho_`/`ghu_`/`ghs_`/`ghr_` tokens |
| `slack-token` | `xoxb-`/`xoxp-`/… tokens |
| `aws-key` | `AKIA…`/`ASIA…` access key ids |
| `private-key` | PEM `-----BEGIN … PRIVATE KEY-----` blocks |
| `session-cookie` | `token=`/`password=`/`api_key=`/`secret=`/`session=` … |
| `discord-webhook` | `discord(app).com/api/webhooks/<id>/<token>` |

Network endpoints:

| Rule | Matches |
| --- | --- |
| `ipv4` | dotted-quad IPv4 addresses |
| `mc-server` | hostnames with a known TLD and optional `:port` (the TLD must not be followed by a dot, so Java package prefixes such as `com.logvex` and `org.apache.commons` survive) |

Personal paths:

| Rule | Matches |
| --- | --- |
| `win-user-path` | `C:\Users\<name>` |
| `win-home-path` | `C:\Documents and Settings\<name>` |
| `unix-home-path` | `/home/<name>`, `/Users/<name>` |
| `mac-home-path` | `/Users/<name>` |

Coordinates (waypoints and positions):

| Rule | Matches |
| --- | --- |
| `coords` | `x=…`, `y:…`, `z=…` |
| `position-triple` | `(x, y, z)` |
| `blockpos` | `BlockPos{…}` |

User-supplied names:

- Every non-blank line of `AISettings.extraRedactions` is redacted wherever it appears
  (`PrivacyFilter.filter(text, knownNames)`). Short names (< 3 chars) are intentionally **not**
  redacted to avoid mangling substrings — this is asserted by the test
  `shortNamesAreNotRedactedToAvoidSubstringDamage`.

## 4. What a report contains

`BugReportBuilder` assembles, in order:

1. A header that names **LOGVEX CRYSTAL** as the sender and asks the user to review.
2. **Crash** — fingerprint, timestamp, category, severity, source, exception type/message,
   suspected module.
3. **Environment** (only when `includeEnvironment`) — client, Minecraft, loader, Java, OS,
   Baritone status, AI status.
4. **Diagnosis** (only when one exists) — the confirmed cause, suggested fixes, and the
   clearly-labelled *unconfirmed* notes.
5. **Recent events** (only when `includeLogs`) — a bounded list of recent diagnostic events.
6. **Sanitized stack trace**.

The whole body is then passed through `PrivacyFilter` and size-capped.

## 5. What a report never contains

- Account names or usernames (only the explicitly-configured names are redacted, plus paths).
- Access tokens, API keys, passwords, auth headers.
- Discord webhook URLs.
- Private server addresses and IPs.
- Chat messages (never captured into a crash record).
- Home directories / personal file paths.
- Coordinates and waypoints.

Nothing unrelated to the crash is attached, and no Minecraft log file is read wholesale — only
the bounded, already-collected recent events are included.

## 6. Size limit

`BugReportBuilder.enforceMaxSize` truncates the body to `maxReportKilobytes` (default 64 KB,
clamped 8–512) with a trailing `[report truncated to N KB]` note. The environment header is
kept; the stack trace is what gets cut. An oversized report is therefore impossible by
construction, and `BugReportBuilderTest` covers the truncation.

## 7. Model inputs

Prompts are built by `AiPromptBuilder` from the same privacy-filtered data the report uses, so
the model never receives a raw server address, path, token or coordinate. The model output is
never trusted: it is appended only as a labelled hypothesis.

## 8. Tests

`PrivacyFilterTest` asserts: bearer tokens, `key=value` secrets, JWTs and known token shapes,
Discord webhooks, server addresses and IPs, Java package names (must *not* be eaten), home
directories, coordinates/block positions, configured names, short-name safety, and idempotence
on already-clean text. `BugReportBuilderTest` asserts the report names LOGVEX CRYSTAL, that a
token is redacted to `[redacted]`, and that the size cap is enforced.
