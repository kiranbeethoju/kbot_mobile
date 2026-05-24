#!/usr/bin/env bash
set -euo pipefail

APP_ID="com.offlinebot"
LOCAL_ROOT="models"
DEVICE_ROOT="/sdcard/Android/data/${APP_ID}/files/models"

adb shell "mkdir -p '${DEVICE_ROOT}/whisper' '${DEVICE_ROOT}/embeddings/minilm' '${DEVICE_ROOT}/llm'"
adb push "${LOCAL_ROOT}/whisper/ggml-base.en.bin" "${DEVICE_ROOT}/whisper/ggml-base.en.bin"
adb push "${LOCAL_ROOT}/embeddings/minilm/model.onnx" "${DEVICE_ROOT}/embeddings/minilm/model.onnx"
adb push "${LOCAL_ROOT}/embeddings/minilm/vocab.txt" "${DEVICE_ROOT}/embeddings/minilm/vocab.txt"
adb push "${LOCAL_ROOT}/embeddings/minilm/tokenizer.json" "${DEVICE_ROOT}/embeddings/minilm/tokenizer.json"

GEMMA_GGUF="${LOCAL_ROOT}/llm/gemma-4-E2B-it-Q3_K_M.gguf"
if [[ -f "${GEMMA_GGUF}" ]]; then
  adb push "${GEMMA_GGUF}" "${DEVICE_ROOT}/llm/gemma-4-E2B-it-Q3_K_M.gguf"
  echo "Installed Gemma LLM to ${DEVICE_ROOT}/llm/"
else
  echo "Skipping Gemma LLM (not found at ${GEMMA_GGUF})"
fi

echo "Installed models to ${DEVICE_ROOT}"
