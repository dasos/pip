# Pip — Server API Contract

Pip is a Wear OS voice-capture app. The watch records a short WAV clip (16 kHz
mono PCM) and pushes it to the paired phone over the Wear OS Data Layer. The
phone then uploads the audio file, **one-way**, to a custom REST endpoint. The
server is responsible for storing (and optionally transcribing) the audio; the
phone never reads back a transcript or manages note content.

---

## `POST /audio`

Uploads a single recorded WAV file.

### Request

```
POST /audio
Authorization: Bearer <token>
Content-Type: multipart/form-data; boundary=...
```

Form fields:

| Field        | Type | Description                                                  |
|--------------|------|--------------------------------------------------------------|
| `file`       | file | The WAV recording (16 kHz, 16-bit, mono PCM).                |
| `created_at` | string | ISO-8601 UTC timestamp of when the clip was captured on the watch. |

### Responses

| Code | Meaning                                                            |
|------|--------------------------------------------------------------------|
| 200  | OK — accepted.                                                     |
| 201  | Created — accepted.                                                |
| 401  | Unauthorized — token invalid or revoked.                           |
| 5xx  | Transient server error; the phone retries with backoff.            |

### Client behavior

- `2xx` → item marked **Uploaded** in the phone's local list.
- `401` → token revoked; item marked **Failed** (no infinite retries).
- Other `4xx`/`5xx`/network error → item stays **Pending** and is retried
  (periodically and when new audio arrives).
- Uploaded audio is retained locally for 7 days so it can be replayed in the
  phone list, then evicted along with its database row.
- The audio bytes are sent as-is; **no transcoding on the phone**.

---

## Authentication

- HTTPS is recommended but **not enforced or validated** by the app. The phone
  saves whatever scheme it's given; cleartext `http://` is blocked only by the
  OS default for targetSdk 28+ (app targets 35). An `http://` URL will be
  stored and simply fail to connect. See Known risks.
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
Phone ──(upload WAV over HTTP, retry queue)──► server
   │  queue (max 20, 7d) if offline
   ▼
Server ──POST /audio──► stores audio (optionally transcribes)
```

---

## Known risks / notes

- Whisper (whisper.cpp) can be run server-side to get text out of the WAV.
- The watch only ever sends the raw WAV; it does not receive text back.