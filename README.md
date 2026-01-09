# Fraud Call Detector 

An experimental Android app that attempts to detect scam phone calls using on-device ML. This project is no longer maintained, but anyone interested is free to do whatever with this.

## Project Status: Abandoned

The point of this project was to have on device call autopicking, processing and analysis (as of this commit iphones, google pixels and any device with truecaller assistant offer this but thats not completely on device); with the intention of not having to deal with unnecessary spam calls for starters. But it can be spun out into a general semantic analysis and per contact context for calls if you so wish, AND theres the obvious privacy angle. The core ML pipeline works, but audio recording during calls does not work reliably on Samsung devices (and possibly other manufacturers) due to Android's strict call recording restrictions. Iphone does not permit third party access at all. I have at present decided to abandon it because:
1.At present theres only a few ML models suitable for edge deployment and these are not capable enough. But you can expect both SoC's to improve and smaller ML models to become more capable soon. 
2.Really dont need as much privacy right now lol.
3.Too much work looking for workarounds for the samsung audio capture problem.

Feel free to clone, fork, learn from, or build upon this code. No support will be provided.

## What Works

- Whisper.cpp integration for speech-to-text (via JNI/NDK)
- MiniLM-L6-v2 text classification (via ONNX Runtime)
- Real-time analysis pipeline with sliding context window
- Modular architecture for swapping ASR/classifier models
- Basic dialer UI and InCallService implementation

## What Doesn't Work

- **Audio recording on Samsung devices** - Samsung blocks microphone access during calls, even with speakerphone enabled. We tried:
  - MediaRecorder with various audio sources (VOICE_COMMUNICATION, MIC, etc.)
  - Low-level AudioRecord API
  - Speakerphone workaround
  - Multiple audio source fallbacks

  None of these produced actual audio on Samsung. Files are created but contain silence.

- Call recording on Android 10+ is heavily restricted for third-party apps in general.

## Architecture

```
Incoming Call → Auto-Answer → Audio Recording → Whisper ASR → MiniLM Classification → Decision
```

The idea was solid, the implementation is there, but Android's security model seems to have killed it.

## Tech Stack

- Kotlin + Coroutines
- Whisper.cpp (native C++ via CMake/NDK)
- ONNX Runtime for MiniLM inference
- AudioRecord API for audio capture

---

## Setup (If You Want to Try)

### Prerequisites

- Android Studio (with NDK 25.x installed)
- Python 3.8+ (for model generation)
- ~500MB disk space for models

### 1. Clone the Repository

```bash
git clone <repo-url>
cd FraudCallDetectorApp
```

### 2. Download/Generate Models

Models are not included in the repo due to size (~200MB total). You need to set them up:

#### Create the models directory
```bash
mkdir -p app/src/main/assets/models
cd app/src/main/assets/models
```

#### Download Whisper Model (~78MB)

```bash
# Linux/Mac
wget https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin

# Windows PowerShell
Invoke-WebRequest -Uri "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin" -OutFile "ggml-tiny.bin"
```

Other Whisper model options:
- `ggml-tiny.bin` (78MB) - Fastest, lower accuracy
- `ggml-base.bin` (148MB) - Balanced
- `ggml-small.bin` (488MB) - Better accuracy, slower

Download from: https://huggingface.co/ggerganov/whisper.cpp/tree/main

#### Generate MiniLM Model + Embeddings

```bash
cd ../../../../scripts

# Install dependencies
pip install transformers onnx onnxruntime torch numpy sentence-transformers

# Generate ONNX model (~91MB), vocab.txt, and tokenizer_config.json
python convert_minilm_to_onnx.py

# Generate scam/legitimate reference embeddings
python generate_embeddings_simple.py
```

This creates:
- `minilm_l6_v2.onnx` - The classification model
- `vocab.txt` - Tokenizer vocabulary
- `tokenizer_config.json` - Tokenizer settings
- `scam_embeddings.npy` - Reference scam text embeddings
- `legitimate_embeddings.npy` - Reference legitimate text embeddings

#### (Optional) Vosk ASR Model (~41MB)

Alternative ASR engine if Whisper doesn't work for you:

```bash
cd app/src/main/assets/models
wget https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip -O vosk-model.zip
```

Download other languages from: https://alphacephei.com/vosk/models

#### (Optional) Sherpa-ONNX Model (~321MB)

Another ASR alternative with streaming support:

```bash
cd app/src/main/assets/models
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-en-2023-06-26.tar.bz2
tar -xjf sherpa-onnx-streaming-zipformer-en-2023-06-26.tar.bz2
rm sherpa-onnx-streaming-zipformer-en-2023-06-26.tar.bz2
```

### 3. Verify Model Files

Your `app/src/main/assets/models/` should contain at minimum:

```
models/
├── ggml-tiny.bin              # Whisper ASR model (required)
├── minilm_l6_v2.onnx          # Text classifier (required)
├── vocab.txt                  # Tokenizer vocab (required)
├── tokenizer_config.json      # Tokenizer config (required)
├── scam_embeddings.npy        # Reference embeddings (required)
└── legitimate_embeddings.npy  # Reference embeddings (required)
```

### 4. Build the App

```bash
# From project root
./gradlew assembleDebug

# Or open in Android Studio and build from there
```

Build outputs: `app/build/outputs/apk/debug/app-debug.apk`

### 5. Install on Device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 6. Device Setup

1. Open the app
2. Grant all requested permissions (Phone, Microphone, Notifications)
3. Set as default dialer when prompted
4. The app will attempt to auto-answer and analyze incoming calls

---

## Project Structure

```
FraudCallDetectorApp/
├── app/src/main/
│   ├── java/com/frauddetector/
│   │   ├── ml/           # ML models (Whisper, MiniLM, interfaces)
│   │   ├── services/     # InCallService, audio recording, analysis
│   │   ├── ui/           # Activities and fragments
│   │   └── utils/        # Helpers and diagnostics
│   ├── cpp/              # Native Whisper.cpp code
│   └── assets/models/    # ML model files (not in git)
├── scripts/              # Python scripts for model generation
└── gradle/               # Build configuration
```

## Potential Paths Forward

If someone wants to continue this project:

1. **Accessibility Service** - Might bypass audio restrictions but requires user to manually enable in Android settings
2. **Root/System App** - Would need `CAPTURE_AUDIO_OUTPUT` permission (requires root or system signing)
3. **Analyze User Speech Only** - Just analyze what the device mic captures (limited but might work)
4. **Different Platform** - iOS does not support third party access to my knowledge.

## License

MIT - Do whatever you want with it. 

---


