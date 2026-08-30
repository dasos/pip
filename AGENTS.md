# AGENTS.md

Guidance for Claude Code (and humans) working in this repository. Keep this file
in sync when architecture or conventions change.

## What this is

**Pip** — a Wear OS voice-capture app (Kotlin + Jetpack Compose). The watch
records a short WAV clip (16 kHz, 16-bit, mono PCM) with a press-and-hold
button, relays it to the paired phone over the Wear OS Data Layer, and the
phone uploads the file **one-way** to a server (`POST {base}/audio`,
multipart). No transcription happens on device — the server stores (and
optionally transcribes) the audio. The phone app keeps a local list of
recordings showing capture time, upload status, and an audio player.

- Wear records and delivers; it never receives text back.
- The phone relays and uploads; it never transcodes.
- The server contract lives in `docs/API.md` — keep it in sync with the uploader.

## Build / test / run

```bash
./gradlew assembleDebug            # build both apps
./gradlew :phone:assembleDebug     # phone APK
./gradlew :wear:assembleDebug      # wear APK
```

- Requires **JDK 17** and the **Android SDK** (platform 35/minSdk differs per
  module). There is no JVM-only module — all code is Android.
- Dependency versions live in `gradle/libs.versions.toml` and are a
  **compatible set** (AGP / Kotlin / Compose-BOM / KSP / Room). Bump together,
  not piecemeal.

## Module / package map

Two app modules — **respect the boundary**:

- **`:wear`** — the recorder. `recording/` (foreground `RecordingService`,
  `WavRecorder`, press-and-hold UI in `ui/recording/`), `tile/` (launcher
  `RecordTileService`), `data/` (`WearSendClient`, `WatchConfigStore`,
  `WearCapabilityListenerService`), `audio/` (`AudioQueueManager` — local queue,
  cap 20, 7-day retention).
- **`:phone`** — the relay + list UI. `wear/` (`WearListenerService` receives
  the Asset, `WearPaths`), `data/` (Room `notes` table), `upload/`
  (`AudioUploader` — multipart POST), `worker/` (`AudioUploadWorker` +
  periodic retry), `config/` (`ServerConfig` — server URL + bearer token),
  `ui/` (`NotesScreen`, `SettingsScreen`).

The Wear Data Layer paths (`WearPaths`) exist on both sides and must stay
in sync when changed.

## Architecture invariants — do not break these

1. **One-way upload.** The phone never transcribes, converts, or inspects
   audio content; it only persists the WAV, uploads it, and records status.
2. **Status flow is strict.** `PENDING → UPLOADED | FAILED`. `401` (auth
   revoked) is terminal (`FAILED`) — never retry forever. Network/timeouts
   stay `PENDING` and retry (WorkManager, one-shot on arrival + periodic).
3. **Ack protocol.** After the phone saves + uploads-enqueues a WAV it acks
   the watch on the ACK path so the watch can drop its local queue entry.
   Keep the two ends' `KEY_*` names in sync.
4. **24/7 Android 14+ mic** — the recording service is a foreground service
   (`foregroundServiceType="microphone"`); starting it requires `RECORD_AUDIO`
   granted and the push path never bypasses the permission gate.

## Non-obvious constraints / gotchas

- Wear Data Layer **Assets** are how large files travel (DataItems are capped
  at ~100 KB). 16 kHz mono is ~1.9 MB/min — keep clips short.
- The phone retains audio files for **7 days** for playback, then evicts the
  row + file. Do not use the WAV path as long-term storage.
- Secrets (server token) live only in `ServerConfig`
  (EncryptedSharedPreferences) — never plain prefs, never logs, never in an APK.
- Room uses `fallbackToDestructiveMigration()` — add real `Migration`s and
  drop the fallback before the schema changes in a shipped build.
- Don't add a transcriber/encoder on the phone; if server-side Whisper needs
  a different format, change the watch recorder (16k mono WAV is precisely
  what whisper.cpp consumes natively).

## Tests

No JVM test module exists yet. Non-trivial logic should leave **one runnable
check behind** (small test or assert-based self-check) — keep it small.

## Releases

CI triggers only on `v*` tags. To make a release, tag the **specific commit**
you want to ship **only when you're ready** — not after every merge:

```bash
git tag v0.2.0 && git push origin v0.2.0
```

CI builds and auto-creates a GitHub Release with the APKs attached and
generated release notes. CI enforces the tag matches `versionName` in the app
build files — bump the version before tagging. Do **not** tag for
workflow-only changes (e.g. CI config) — those commit to `main` without
triggering a build.

## Commits

Commit frequently. Offer to push when appropriate.

# Development style

You are a lazy senior developer. Lazy means efficient, not careless. The best
code is the code never written.

Before writing any code, stop at the first rung that holds:

1. Does this need to be built at all? (YAGNI)
2. Does it already exist in this codebase? Reuse the helper, util, or pattern
   that's already here — don't rewrite it.
3. Does the standard library already do this? Use it.
4. Does a native platform feature cover it? Use it.
5. Does an already-installed dependency solve it? Use it.
6. Can this be one line? Make it one line.
7. Only then: write the minimum code that works.

The ladder runs after you understand the problem, not instead of it: read the
task and the code it touches, trace the real flow end to end, then climb.

Bug fix = root cause, not just the reported symptom: find every caller of the
function you touch and fix the shared function once.

Rules:

- No abstractions that weren't explicitly requested.
- No new dependency if it can be avoided.
- No boilerplate nobody asked for.
- Deletion over addition. Boring over clever. Fewest files possible.
- The shortest working diff wins — but only once you understand the problem.
  The smallest change in the wrong place isn't lazy, it's a second bug.
- Mark deliberate simplifications that cut a real corner with a known ceiling
  (global lock, O(n²) scan) with a `ponytail:` comment naming the ceiling and
  the upgrade path.

Not lazy about: understanding the problem, input validation at trust
boundaries, error handling that prevents data loss, security, accessibility,
and anything explicitly requested.