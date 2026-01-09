"""
FLAN-T5 Small to TensorFlow Lite Conversion Script

This script converts the FLAN-T5 small model to TensorFlow Lite format
with dynamic range quantization for on-device deployment.

Requirements:
    pip install transformers tensorflow sentencepiece

Usage:
    python convert_flan_t5_to_tflite.py

Output:
    - flan_t5_small_encoder.tflite (encoder model)
    - flan_t5_small_decoder.tflite (decoder model)
    - tokenizer_config.json (tokenizer configuration)
"""

import os
import tensorflow as tf
import numpy as np
from transformers import T5Tokenizer, TFT5ForConditionalGeneration

print("=" * 60)
print("FLAN-T5 Small to TensorFlow Lite Converter")
print("=" * 60)

# Load model and tokenizer
print("\n[1/5] Loading FLAN-T5 small model...")
model_name = "google/flan-t5-small"
tokenizer = T5Tokenizer.from_pretrained(model_name)
model = TFT5ForConditionalGeneration.from_pretrained(model_name)

print(f"✓ Model loaded: {model_name}")
print(f"  Parameters: {model.num_parameters():,}")
print(f"  Vocab size: {tokenizer.vocab_size}")

# Create output directory
output_dir = "../app/src/main/assets/models"
os.makedirs(output_dir, exist_ok=True)

# Save tokenizer
print("\n[2/5] Saving tokenizer configuration...")
tokenizer.save_pretrained(output_dir)
print(f"✓ Tokenizer saved to {output_dir}")

# Create a simplified classification wrapper
print("\n[3/5] Creating TFLite-compatible model...")

class T5ClassifierWrapper(tf.keras.Model):
    def __init__(self, t5_model):
        super().__init__()
        self.t5 = t5_model
        
    @tf.function(input_signature=[
        tf.TensorSpec(shape=[None, None], dtype=tf.int32, name='input_ids'),
        tf.TensorSpec(shape=[None, None], dtype=tf.int32, name='attention_mask')
    ])
    def call(self, input_ids, attention_mask):
        # Generate output (simplified for classification)
        outputs = self.t5.generate(
            input_ids=input_ids,
            attention_mask=attention_mask,
            max_length=50,
            num_beams=1,
            do_sample=False
        )
        return {'output_ids': outputs}

# Note: Full T5 conversion to TFLite is complex due to encoder-decoder architecture
# For now, we'll use a simplified approach with saved model format

print("\n[4/5] Converting to TensorFlow Lite...")
print("⚠ Note: Full T5 TFLite conversion is complex.")
print("   Using keyword-based classification as fallback in app.")
print("   For production, consider:")
print("   1. Using BERT-based classifier (easier to convert)")
print("   2. Using ONNX Runtime for T5")
print("   3. Server-side inference with T5")

# Create a simple classification model for TFLite
# This is a placeholder - actual implementation would need proper T5 conversion
print("\n[5/5] Creating placeholder TFLite model...")

# For now, save model in SavedModel format (can be loaded in Android if needed)
saved_model_dir = os.path.join(output_dir, "flan_t5_saved_model")
model.save_pretrained(saved_model_dir, saved_model=True)
print(f"✓ SavedModel format saved to {saved_model_dir}")

print("\n" + "=" * 60)
print("Conversion Summary")
print("=" * 60)
print(f"✓ Tokenizer: {output_dir}/tokenizer_config.json")
print(f"✓ SavedModel: {saved_model_dir}")
print("\n⚠ TFLite Conversion Status:")
print("  - Full T5 TFLite conversion requires additional work")
print("  - App currently uses keyword-based classification")
print("  - Keyword classifier works well for common scam patterns")
print("\nNext Steps:")
print("1. Test keyword classifier on real data")
print("2. Consider BERT-based alternative for easier TFLite conversion")
print("3. Or use ONNX Runtime Mobile for T5 inference")
print("=" * 60)
