#!/bin/bash

# Script to download Whisper tiny multilingual model
# Run this from the FraudCallDetectorApp directory

echo "Downloading Whisper tiny multilingual model..."

# Create models directory if it doesn't exist
mkdir -p app/src/main/assets/models

# Download model
cd app/src/main/assets/models

echo "Downloading ggml-tiny.bin (74 MB)..."
wget https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin

if [ $? -eq 0 ]; then
    echo "✓ Model downloaded successfully!"
    echo "Model location: app/src/main/assets/models/ggml-tiny.bin"
    echo "Model size: $(du -h ggml-tiny.bin | cut -f1)"
else
    echo "✗ Failed to download model"
    echo "Please download manually from:"
    echo "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin"
fi
