# Pip — Server API Contract

Pip is a Wear OS voice-capture app. The watch records a short audio clip, the paired
phone transcribes it **on-device** (ML Kit), and the resulting text is uploaded to a
custom REST endpoint. The server only ever stores text + a timestamp — never audio.

---

## `POST /notes`

Creates a single note.

### Request

```
POST /notes
Authorization: Bearer <token>
Content-Type: application/json
```

Body (JSON):

```json
{
  "text": "buy milk",
  "created_at": "2025-04-10T10:15:00Z"
}
```

| Field        | Type   | Description                                             |
|--------------|--------|---------------------------------------------------------|
| `text`       | string | The transcribed note text.                              |
| `created_at` | string | ISO-8601 UTC timestamp of when the note was captured on the watch. |

### Responses

| Code | Meaning                                                          |
|------|------------------------------------------------------------------|
| 200  | OK — note accepted.                                              |
| 201  | Created — note accepted.                                         |
| 401  | Unauthorized — token invalid or revoked.                         |
| 500  | Internal server error — transient; client retries with backoff.  |

### Client behavior

- `2xx` → note marked **Uploaded**.
- `401` → token revoked; note marked **Failed** (no infinite retries).
- Other `4xx`/`5xx` → note kept pending and retried with backoff.

---

## Authentication

- HTTPS is recommended but **not enforced or validated** by the app. The phone saves whatever scheme it's given; cleartext `http://` is blocked only by the OS default for targetSdk 28+ (app targets 35). An `http://` URL will be stored and simply fail to connect. See Known risks.
- Token-based **Bearer** auth (`Authorization: Bearer <token>`).
- The token is per-user and can be revoked.
- The token is entered once in the phone app during setup and stored in
  **EncryptedSharedPreferences**; it is never hardcoded in an APK.

---

## Data flow

```
Watch ──(record WAV, press-and-hold)──► local queue (max 20, 7d)
  │  Wear OS Data Layer (Asset)
  ▼
Phone ──(ML Kit Transcriber, on-device)──► text
  │  queue (max 20, 7d) if offline
  ▼
Server ──POST /notes──► stored note
```

---

## Optional future endpoint (not required)

If on-device transcription proves inadequate on some devices, a server-side
transcription endpoint could be added:

```
POST /transcribe
Authorization: Bearer <token>
Content-Type: audio/wav
Body: raw WAV (16-bit PCM, 16 kHz, mono)

200 → { "text": "buy milk" }
```

This is documented as a fallback and is **not** implemented in the current client.

---

## Configuration

- **Phone app** (first run): user enters the server URL and bearer token, then taps
  "Test connection". Config is saved to EncryptedSharedPreferences and mirrored to the
  watch over the Wear OS Data Layer (`/pip/config`) for forward-looking use.
- **Watch app**: no direct configuration UI.

---

## Known risks / TODO(verify-on-device)

- **ML Kit `SpeechRecognition.Transcriber` (beta)** transcribes a pre-recorded WAV file
  directly. A ~100 MB language model downloads on first use; offline after that.
  Confirm model-download behavior and WAV acceptance on a real device.
- **WAV format**: watch records 16-bit PCM mono 16 kHz WAV, which the transcriber
  requires. No conversion is performed on the phone.
- **Wear OS Data Layer** is used with `Asset` transfer for audio; large or long clips
  should be validated.
- **HTTPS not enforced** (`ServerConfig`/`NoteUploader` use the URL as entered). The
  "Test connection" button sends a `HEAD` request to the bare base URL (not `/notes`)
  and treats any 2xx as success — it does not validate the scheme or probe the API.