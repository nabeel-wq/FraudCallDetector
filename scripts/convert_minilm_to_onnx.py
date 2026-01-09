#!/usr/bin/env python3
# coding: utf-8
"""
MiniLM-L6-v2 to ONNX Converter

Exports sentence-transformers/all-MiniLM-L6-v2 model to ONNX format
optimized for Android deployment.

Requirements:
    pip install transformers onnx optimum[exporters] sentence-transformers torch

Usage:
    python convert_minilm_to_onnx.py
"""

import os
import json
import numpy as np
from pathlib import Path
from transformers import AutoTokenizer, AutoModel
import torch
import torch.onnx

print("=" * 70)
print("MiniLM-L6-v2 to ONNX Converter")
print("=" * 70)

# Configuration
MODEL_NAME = "sentence-transformers/all-MiniLM-L6-v2"
OUTPUT_DIR = Path("../app/src/main/assets/models")
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

# Step 1: Load model and tokenizer
print("\n[1/5] Loading MiniLM-L6-v2 model and tokenizer...")
tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
model = AutoModel.from_pretrained(MODEL_NAME)
model.eval()  # Set to evaluation mode

print(f"[OK] Model loaded: {MODEL_NAME}")
print(f"  Vocab size: {tokenizer.vocab_size}")
print(f"  Max sequence length: {tokenizer.model_max_length}")

# Step 2: Export to ONNX
print("\n[2/5] Exporting model to ONNX format...")

# Create dummy input for tracing
dummy_text = "This is a sample sentence for model export."
dummy_input = tokenizer(
    dummy_text,
    return_tensors="pt",
    padding="max_length",
    truncation=True,
    max_length=128
)

onnx_model_path = OUTPUT_DIR / "minilm_l6_v2.onnx"

# Export to ONNX
torch.onnx.export(
    model,
    (dummy_input["input_ids"], dummy_input["attention_mask"]),
    str(onnx_model_path),
    input_names=["input_ids", "attention_mask"],
    output_names=["last_hidden_state", "pooler_output"],
    dynamic_axes={
        "input_ids": {0: "batch_size", 1: "sequence_length"},
        "attention_mask": {0: "batch_size", 1: "sequence_length"},
        "last_hidden_state": {0: "batch_size", 1: "sequence_length"},
        "pooler_output": {0: "batch_size"}
    },
    opset_version=14,
    do_constant_folding=True
)

original_size = onnx_model_path.stat().st_size / (1024 * 1024)
print(f"[OK] Model exported to ONNX")
print(f"  Model size: {original_size:.2f} MB")
print(f"  Path: {onnx_model_path}")

# Step 3: Quantize model for mobile
print("\n[3/5] Quantizing model for mobile deployment...")

try:
    from onnxruntime.quantization import quantize_dynamic, QuantType
    
    quantized_model_path = OUTPUT_DIR / "minilm_l6_v2_quantized.onnx"
    
    quantize_dynamic(
        model_input=str(onnx_model_path),
        model_output=str(quantized_model_path),
        weight_type=QuantType.QUInt8,
        optimize_model=True
    )
    
    quantized_size = quantized_model_path.stat().st_size / (1024 * 1024)
    
    print(f"[OK] Model quantized")
    print(f"  Original size: {original_size:.2f} MB")
    print(f"  Quantized size: {quantized_size:.2f} MB")
    print(f"  Compression: {(1 - quantized_size/original_size)*100:.1f}%")
    
    # Use quantized model for deployment
    final_model_path = quantized_model_path
    
except Exception as e:
    print(f"[WARNING] Quantization failed: {e}")
    print(f"  Using non-quantized model")
    final_model_path = onnx_model_path

# Step 4: Export tokenizer vocabulary
print("\n[4/5] Exporting tokenizer vocabulary...")

vocab_path = OUTPUT_DIR / "vocab.txt"
with open(vocab_path, "w", encoding="utf-8") as f:
    vocab = tokenizer.get_vocab()
    # Sort by token ID
    sorted_vocab = sorted(vocab.items(), key=lambda x: x[1])
    for token, _ in sorted_vocab:
        f.write(f"{token}\n")

print(f"[OK] Vocabulary saved: {vocab_path}")
print(f"  Vocabulary size: {len(vocab)}")

# Step 5: Export tokenizer config
print("\n[5/5] Exporting tokenizer configuration...")

tokenizer_config = {
    "vocab_size": tokenizer.vocab_size,
    "max_length": 128,  # Reduced for mobile
    "do_lower_case": True,
    "cls_token": tokenizer.cls_token,
    "sep_token": tokenizer.sep_token,
    "pad_token": tokenizer.pad_token,
    "unk_token": tokenizer.unk_token,
    "cls_token_id": tokenizer.cls_token_id,
    "sep_token_id": tokenizer.sep_token_id,
    "pad_token_id": tokenizer.pad_token_id,
    "unk_token_id": tokenizer.unk_token_id,
}

config_path = OUTPUT_DIR / "tokenizer_config.json"
with open(config_path, "w", encoding="utf-8") as f:
    json.dump(tokenizer_config, f, indent=2)

print(f"[OK] Tokenizer config saved: {config_path}")

# Step 6: Test inference
print("\n[6/6] Testing ONNX inference...")

try:
    import onnxruntime as ort
    
    # Load ONNX model
    session = ort.InferenceSession(str(final_model_path))
    
    # Test encoding
    test_text = "Your account will be suspended unless you verify your information"
    inputs = tokenizer(
        test_text,
        return_tensors="np",
        padding="max_length",
        truncation=True,
        max_length=128
    )
    
    # Run inference
    outputs = session.run(
        None,
        {
            "input_ids": inputs["input_ids"].astype(np.int64),
            "attention_mask": inputs["attention_mask"].astype(np.int64)
        }
    )
    
    # Mean pooling
    last_hidden_state = outputs[0]
    attention_mask = inputs["attention_mask"]
    
    # Expand attention mask
    input_mask_expanded = np.expand_dims(attention_mask, -1)
    input_mask_expanded = np.broadcast_to(
        input_mask_expanded,
        last_hidden_state.shape
    ).astype(np.float32)
    
    # Sum embeddings
    sum_embeddings = np.sum(last_hidden_state * input_mask_expanded, axis=1)
    sum_mask = np.clip(input_mask_expanded.sum(axis=1), a_min=1e-9, a_max=None)
    
    # Mean pooling
    embedding = sum_embeddings / sum_mask
    
    # Normalize
    norm = np.linalg.norm(embedding, axis=1, keepdims=True)
    embedding = embedding / norm
    
    print(f"[OK] Test inference successful")
    print(f"  Input text: {test_text[:50]}...")
    print(f"  Embedding shape: {embedding.shape}")
    print(f"  Embedding dimension: {embedding.shape[1]}")
    print(f"  Embedding norm: {np.linalg.norm(embedding):.3f}")
    
except Exception as e:
    print(f"[WARNING] Test inference failed: {e}")
    print("  Model exported but not tested. Verify on Android device.")

# Summary
print("\n" + "=" * 70)
print("EXPORT COMPLETE")
print("=" * 70)
print(f"\n[OK] ONNX Model: {final_model_path}")
print(f"[OK] Vocabulary: {vocab_path}")
print(f"[OK] Config: {config_path}")
print(f"\nModel ready for Android deployment!")
print(f"  Expected inference time: ~30-50ms per text on mobile")
print(f"  Memory usage: ~150-200 MB")
print("=" * 70)
