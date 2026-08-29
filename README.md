# Pip

A Wear OS voice-capture app. The watch records a short audio clip via a press-and-hold
gesture, sends it to the paired phone over the Wear OS Data Layer, the phone transcribes
it **on-device** (ML Kit), and the resulting text is uploaded to a custom REST endpoint.
The server only ever stores text + a timestamp — never audio.

## Project layout

| Path            | Description                                                        |
|-----------------|--------------------------------------------------------------------|
| `wear/`         | Wear OS app. Records WAV clips and syncs them to the phone.        |
| `phone/`        | Companion phone app. Transcribes audio, uploads notes to the server, hosts settings UI. |
| `docs/API.md`   | Server API contract and wire-format reference.                     |

## Modules

Both modules are Android apps (`com.pip.wear`, `com.pip.phone`). Current version: `0.1.0`.

| Module | App ID          | minSdk | target/compileSdk |
|--------|-----------------|--------|-------------------|
| Wear   | `com.pip.wear`  | 30     | 35                |
| Phone  | `com.pip.phone` | 26     | 35                |

## How it works

```
Watch ──(press-and-hold, record WAV)──► local queue (max 20, 7d)
  │  Wear OS Data Layer (Asset)
  ▼
Phone ──(ML Kit Transcriber, on-device)──► text
  │  queue (max 20, 7d) if offline
  ▼
Server ──POST /notes──► stored note
```

- **Watch**: records 16-bit PCM, mono, 16 kHz WAV via a press-and-hold button. Clips are
  queued on the watch (max 20, 7-day retention) and delivered to the phone over the Wear
  OS Data Layer using `Asset` transfer (`/pip/audio`). No configuration UI exists on the
  watch; it receives config silently from the phone.
- **Phone**: receives each clip, writes it to disk, inserts it into a Room-backed queue
  (max 20, 7-day retention), transcribes it with ML Kit
  `SpeechRecognition.Transcriber`, and uploads the resulting text with
  `POST {server}/notes`. Uploads are retried with backoff; a `401` marks the note
  `FAILED` (token revoked, no infinite retries). A periodic WorkManager job re-runs
  uploads every 15 minutes while online.
- **Server**: a third-party endpoint that accepts `{ "text": "...", "created_at": "<ISO-8601 UTC>" }`
  with a `Authorization: Bearer <token>` header. See `docs/API.md` for the full contract.

## Setup

1. **Phone app (first run)**: the app shows the Settings screen. Enter the server URL and
   bearer token, then tap **Save**. Optionally tap **Test connection** first — note this
   only does an HTTP `HEAD` to the base URL (not `/notes`) and treats any 2xx as success;
   it does not validate the URL scheme or probe the API.
2. Config is stored in **EncryptedSharedPreferences** (token is never hardcoded) and
   mirrored to the watch over the Wear OS Data Layer (`/pip/config`) for forward-looking
   use.
3. Build and install the Wear app on the watch and the companion on the phone.

### HTTPS

HTTPS is **recommended but not enforced** by the app. The phone saves whatever scheme it's
given and relies on the platform default (cleartext blocked for targetSdk 28+, here 35) to
reject plain `http://`. An `http://` URL will be stored but simply fail to connect. See
"Known risks" below and `docs/API.md`.

## Building

```bash
./gradlew :wear:assembleDebug   # build the Wear app
./gradlew :phone:assembleDebug  # build the phone app
```

Requires the Android SDK; set the location via `local.properties` (`sdk.dir=...`) or the
`ANDROID_HOME` environment variable. `local.properties` is gitignored and not committed.

## Known risks / to verify on-device

- **ML Kit `SpeechRecognition.Transcriber` (beta)** transcribes a pre-recorded WAV file
  directly. A ~100 MB language model downloads on first use, then offline after that.
  Confirm model-download behavior and WAV acceptance on a real device.
- **WAV format**: the watch records 16-bit PCM mono 16 kHz WAV, which the transcriber
  requires. No conversion is performed on the phone.
- **Wear OS Data Layer** is used with `Asset` transfer for audio; large or long clips
  should be validated.
- **Large/long clips** from the watch may strain the Data Layer; verify throughput on a
  real device.

## Server contract

The full server-side API contract is documented in [`docs/API.md`](docs/API.md), including
request/response formats, auth, retry behavior, an optional future `/transcribe` endpoint,
and configuration. An ML Kit fallback audio-based `/transcribe` endpoint is documented
there as well but is **not** implemented in the client.