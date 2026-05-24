# Model Files

This directory is only a development placeholder. Do not commit large model files.

Recommended MVP files:

```text
models/
├── whisper/
│   └── ggml-base.en.bin
└── embeddings/
    └── minilm/
        └── model.onnx
```

Later optional files:

```text
models/
└── llm/
    └── phi3-q4.gguf
```

The Android app should copy or reference these from device storage, not package multi-GB files into the APK.
