# OfflineBot

OfflineBot is an Android-first, private offline second brain. The MVP goal is intentionally narrow:

1. Record voice with one tap.
2. Save audio locally.
3. Transcribe offline with `whisper.cpp`.
4. Search old thoughts with local embeddings.
5. Add summaries and weekly intelligence after capture and retrieval are stable.

The app should not request internet permission in the initial MVP. Model files are user-managed and should be placed on-device only when the feature needs them.

## MVP Model Policy

Do not download giant models first. Start with:

| Purpose | Model | Runtime |
| --- | --- | --- |
| Speech-to-text | `ggml-base.en.bin` | `whisper.cpp` JNI |
| Search | MiniLM ONNX | ONNX Runtime Android |

Skip local LLM summaries until recording, transcription, and search are smooth on a real phone.

Later options:

| Purpose | Model |
| --- | --- |
| Lightweight tagging | Gemma 3 1B quantized |
| Advanced summaries | Phi-3 Mini Q4 GGUF |
| Better embeddings | bge-small-en |

## Local Model Layout

Use app-private external storage for the Android MVP:

```text
Android/data/com.offlinebot/files/models/
├── whisper/
│   └── ggml-base.en.bin
├── embeddings/
│   └── minilm/
│       └── model.onnx
└── llm/
    └── gemma-4-E2B-it-Q3_K_M.gguf
```

The processing pipeline must load one model at a time:

```text
Record audio
Load Whisper
Transcribe
Unload Whisper
Load embeddings
Generate vector
Unload embeddings
Defer LLM summaries
```

## Project Shape

```text
app/src/main/java/com/offlinebot/
├── ai/
├── data/
├── recorder/
├── security/
├── ui/
└── utils/
```

## Current State

This repo now contains the initial Kotlin/Jetpack Compose Android skeleton:

- Home, Timeline, Search, Weekly Buckets, and Settings screens.
- Foreground recording service.
- Local audio storage under app-private external files.
- Room entities for recordings, transcripts, buckets, and embeddings.
- Optional local Contacts/SMS context import from Settings.
- WorkManager queue placeholder for deferred offline processing.
- No internet permission in the Android manifest.

## Build

### Requirements

- Android Studio Ladybug or newer.
- JDK 17.
- Android SDK platform 35.
- Android SDK Build Tools.
- Gradle 8.7+ if building from the command line without Android Studio.

This project uses `com.microsoft.onnxruntime:onnxruntime-android:1.18.0` so MiniLM `model.onnx` loads correctly. The smaller `onnxruntime-mobile` package only supports ORT-format (`.ort`) models and will fail with `ONNX format model is not supported in this build`.

This project uses Dagger/Hilt `2.57.1` so annotation processing can read Kotlin 2.1 metadata while remaining compatible with the AGP 8 build line.

### Build Debug APK From Android Studio

1. Open this folder in Android Studio.
2. Let Gradle sync finish.
3. Select `app` as the run configuration.
4. Use `Build > Build Bundle(s) / APK(s) > Build APK(s)`.

The debug APK is created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Build Debug APK From Terminal

Use JDK 17. If your shell defaults to a newer Java version, set `JAVA_HOME` first:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

Make sure Android SDK platform 35 and build-tools are installed. If needed:

```bash
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
```

Then build with the Gradle wrapper:

```bash
./gradlew assembleDebug
```

Install the APK on a connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

This repo was verified with:

```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/private/tmp/android-sdk \
./gradlew assembleDebug
```

### Build Release APK

Create a local keystore first:

```bash
keytool -genkeypair \
  -v \
  -keystore offlinebot-release.jks \
  -alias offlinebot \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

Add signing config before distributing a release build. Until then, use debug APKs only for local testing.

### Important Model Note

The APK does not include Whisper or MiniLM model files. Install the APK first, then push the models with the script below. This keeps the APK small and avoids shipping large binaries by accident.

## Install Downloaded Models On A Device

The MVP models are downloaded to the workspace under `models/`, but they are not committed and not packaged into the APK.

Downloaded files currently present locally:

| File | SHA-256 |
| --- | --- |
| `models/whisper/ggml-base.en.bin` | `a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002` |
| `models/embeddings/minilm/model.onnx` | `6fd5d72fe4589f189f8ebc006442dbb529bb7ce38f8082112682524616046452` |
| `models/embeddings/minilm/vocab.txt` | `07eced375cec144d27c900241f3e339478dec958f92fddbc551f295c992038a3` |
| `models/embeddings/minilm/tokenizer.json` | `be50c3628f2bf5bb5e3a7f17b1f74611b2561a3a27eeab05e5aa30f411572037` |

With a device connected through ADB:

```bash
chmod +x scripts/install-models-to-device.sh
scripts/install-models-to-device.sh
```

The app expects:

```text
/sdcard/Android/data/com.offlinebot/files/models/
├── whisper/ggml-base.en.bin
├── embeddings/minilm/
│   ├── model.onnx
│   ├── vocab.txt
│   └── tokenizer.json
└── llm/gemma-4-E2B-it-Q3_K_M.gguf
```

Place the Gemma GGUF at `models/llm/gemma-4-E2B-it-Q3_K_M.gguf` locally before running `scripts/install-models-to-device.sh` (the script pushes it when the file exists).

## On-device chat (Gemma 4)

Chat uses the bundled `offlinebot-llama` JNI library (llama.cpp + Jinja chat templates from `llama.cpp/common`). The model must be on device at:

```text
Android/data/com.offlinebot/files/models/llm/gemma-4-E2B-it-Q3_K_M.gguf
```

**Fix (May 2026):** Gemma loads on app start with a warmup decode (green dot = ready). Chat is blocked until the dot is green. Messages persist in Room. Timeline resolves audio paths under `files/audio/`, plays WAV, and saves transcripts. JNI decode uses `common_batch_add` + `llama_tokenize` (llama.android pattern).

Requirements:

- Sibling checkout of [llama.cpp](https://github.com/ggml-org/llama.cpp) at `../llama.cpp` relative to this repo (see `app/src/main/cpp/CMakeLists.txt`).
- On first launch wait ~1 min for the **green dot** before chatting. Old timeline recordings may show “file missing” if the WAV was deleted — record again.

Logcat filter for LLM issues:

```bash
adb logcat | grep -i "offlinebot-llama\|LlamaCppEngine"
```

## Phone Context Import

Contacts and SMS are optional local context sources. The app does not ask for these permissions on launch. Open Settings and tap `Import contacts and SMS locally` if you want the app to copy a bounded local context set into Room.

Notes:

- `READ_CONTACTS` and `READ_SMS` are sensitive Android permissions.
- SMS access is heavily restricted for Play Store distribution. Keep this feature for local/private builds unless the product qualifies for Google's SMS policy.
- Imported messages are truncated to 1000 characters each and stay local.

## Recorder Troubleshooting

If tapping the mic closes the app on a test build:

1. Install the newest APK from `app/build/outputs/apk/debug/app-debug.apk`.
2. Open Android app settings for OfflineBot and confirm `Microphone` is allowed.
3. On Android 13+, allow notifications so the foreground recording notification can show.
4. Reopen the app and tap the mic again.

The recorder path now catches microphone startup failures and shows a local error instead of crashing the process.

For live device logs:

```bash
adb logcat | grep -i "OfflineBot\|RecordingService\|AndroidRuntime"
```
