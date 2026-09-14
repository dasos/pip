# Pip

Pip is a Wear OS voice-capture app. Hold the record button on the watch to
capture a short WAV clip. The watch sends the clip to its paired phone through
the Wear OS Data Layer; the phone stores it locally and uploads the WAV
**one-way** to a configured server. Pip does not transcribe or otherwise
inspect audio on either device.

## Project layout

| Path | Description |
|---|---|
| `wear/` | Standalone Wear OS recorder: recording UI, foreground microphone service, tile, local delivery queue, and Data Layer sender. |
| `phone/` | Companion app: receives WAV assets, maintains the recording list, plays retained audio, and uploads it to the server. |
| `docs/API.md` | Server API contract, including upload and connection-test endpoints. |

## Modules

Both Gradle modules build Android apps at version `0.2.3` (version code `5`).
They deliberately share the application ID and signing key so Wear OS Data
Layer pairing works.

| Module | Namespace | Application ID | minSdk | target/compileSdk |
|---|---|---|---:|---:|
| Wear | `com.pip.wear` | `com.pip` | 30 | 35 |
| Phone | `com.pip.phone` | `com.pip` | 26 | 35 |

## How it works

```text
Watch ──(hold to record 16 kHz PCM WAV)──► local queue (max 20, 7 days)
  │  Wear OS Data Layer Asset
  ▼
Phone ──(save WAV and enqueue upload)──► local list / upload worker
  │  multipart POST /api/audio
  ▼
Server ──► stores audio (and may transcribe it server-side)
```

### Watch

- The recording screen has a press-and-hold control and the app also provides a
  Wear OS recording tile.
- A foreground service owns microphone capture, so recording can continue when
  the screen turns off. It requires the `RECORD_AUDIO` permission.
- Recordings are 16 kHz, 16-bit, mono PCM WAV files.
- The watch keeps undelivered recordings in a local queue capped at 20 files,
  with 7-day retention. It tries to send a fresh recording immediately and a
  WorkManager job retries queued recordings every 15 minutes.
- Audio travels as a Wear OS Data Layer `Asset` on `/pip/audio/...` rather
  than in a DataItem payload. Once the phone has saved the asset and enqueued
  its upload, it acknowledges the recording so the watch can remove the local
  queued copy.

### Phone

- The phone listens for Wear Data Layer audio assets, saves each WAV under its
  app-private storage, creates a Room `notes` row, and immediately enqueues an
  upload worker.
- The recording list shows capture time and one of **Pending**, **Uploaded**,
  or **Failed**. Retained recordings can be played in the phone app.
- The database list is trimmed to its 20 newest rows. Audio files older than
  seven days are removed during queue-policy enforcement; pending rows whose
  files are gone are also removed.
- A one-time upload worker runs on arrival. A network-constrained periodic
  WorkManager job revisits pending uploads every 15 minutes.
- Uploads use `multipart/form-data` to `POST {base}/api/audio`, sending the
  WAV as `file` (`audio/wav`) and the watch capture time as `created_at` in
  ISO-8601 UTC. The app sends `Authorization: Bearer <token>`.
- Any successful 2xx response marks the item **Uploaded**. A 401 is terminal
  and marks it **Failed**. Other HTTP failures and network failures stay
  **Pending** and are retried.

## Setup

1. Install the Wear app on the watch and the phone app on its paired phone.
2. In the phone app, open **Settings**, enter the base server URL and bearer
   token, and tap **Save**. Saving stores the values in encrypted preferences
   and also sends them to the watch over the Data Layer. The watch currently
   stores this configuration but does not use it for uploads.
3. Tap **Test connection** to call `GET {base}/api/health/audio` with the
   bearer token. A 2xx response is shown as connected; 401 and 403 are shown
   as unauthorized; other HTTP and network errors are reported in the UI.
4. Record on the watch by holding the record control, then release it to queue
   and send the WAV.

### Server URL and HTTPS

The app parses the entered base URL when making a request but does not require
or validate HTTPS before saving it. With the app targeting SDK 35, Android's
default cleartext policy normally rejects `http://` connections unless the
platform/network configuration permits them. Use HTTPS and a valid bearer token
for production deployments.

## Building

The project requires JDK 17 and the Android SDK.

```bash
./gradlew assembleDebug          # build both apps
./gradlew :wear:assembleDebug    # build the Wear app
./gradlew :phone:assembleDebug   # build the phone app
```

Set the SDK location in gitignored `local.properties` (`sdk.dir=...`) or via
`ANDROID_HOME`.

## Server contract

The complete protocol is in [docs/API.md](docs/API.md). The app currently
expects these endpoints:

| Endpoint | Purpose |
|---|---|
| `POST /api/audio` | Receives one WAV file and its `created_at` timestamp. |
| `GET /api/health/audio` | Side-effect-free connection and token check from Settings. |

The server owns long-term audio storage and any optional transcription. It does
not send a transcript or other content back to Pip.

## Operational notes

- Wear OS Data Layer Assets are intended for larger payloads than DataItems,
  but recording length and real-device delivery throughput should still be
  validated.
- Audio is not a long-term local archive: phone-side file cleanup runs when
  queue policies are enforced, and watch-side undelivered recordings expire
  after seven days.
- The phone's bearer token is stored with `EncryptedSharedPreferences`; do not
  hardcode tokens or log them.
