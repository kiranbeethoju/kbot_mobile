# Architecture

## Product Positioning

OfflineBot should feel like a private offline second brain, not a chatbot. The highest-value loop is:

```text
Capture thought -> preserve locally -> retrieve instantly
```

Everything that increases heat, battery drain, permission anxiety, or setup friction should be delayed.

## Runtime Pipeline

```text
Android App
  -> Foreground Recording Service
  -> App-private Audio Storage
  -> Deferred WorkManager Job
  -> Whisper.cpp Transcription
  -> Embedding Generator
  -> Room DB + Vector Search
  -> Weekly Summary Engine
```

## Module Responsibilities

| Module | Responsibility |
| --- | --- |
| `recorder` | Foreground recording, audio file lifecycle, playback later |
| `data` | Room database, DAOs, repositories |
| `ai.whisper` | Whisper JNI wrapper and transcription contracts |
| `ai.embeddings` | ONNX Runtime Mobile embedding generation |
| `ai.llm` | llama.cpp GGUF summaries and tags in later phases |
| `ui` | Compose screens and navigation |
| `security` | Encryption and export controls |

## Database Tables

### `recordings`

Stores the source audio file and processing pointers.

### `transcripts`

Stores transcript text, summaries, keywords, sentiment, and creation time.

### `embeddings`

Stores vector data for semantic search. For MVP scale, vectors can live in Room and be scanned in-process. If the database grows large, move to a dedicated vector index.

### `buckets`

Stores weekly summaries and date ranges.

## Processing Rules

- Do not run AI continuously.
- Do not load Whisper, embeddings, and LLM models at the same time.
- Prefer WorkManager jobs with battery constraints.
- Keep the first APK offline-only.
- Avoid cloud sync, accounts, login, and chatbot UI in version 1.

## Phases

| Phase | Goal |
| --- | --- |
| 1 | Recorder APK with local timeline |
| 2 | Offline Whisper transcription |
| 3 | Transcript and embedding search |
| 4 | Weekly buckets and summaries |
| 5 | Optional local LLM tagging and insights |
