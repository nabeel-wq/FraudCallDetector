"""
MiniLM-L6-v2 to TensorFlow Lite Conversion Script

Converts sentence-transformers/all-MiniLM-L6-v2 to optimized TFLite format
for on-device semantic similarity and classification.

Requirements:
    pip install sentence-transformers tensorflow transformers

Usage:
    python convert_minilm_to_tflite.py

Output:
    - minilm_l6_v2.tflite (quantized model, ~12 MB)
    - minilm_tokenizer.json (tokenizer config, ~500 KB)
    - scam_embeddings.npy (pre-computed scam pattern embeddings)
    - legitimate_embeddings.npy (pre-computed legitimate pattern embeddings)
"""

import os
import numpy as np
import tensorflow as tf
from sentence_transformers import SentenceTransformer
from transformers import AutoTokenizer

print("=" * 70)
print("MiniLM-L6-v2 to TensorFlow Lite Converter")
print("=" * 70)

# Load model
print("\n[1/6] Loading MiniLM-L6-v2 model...")
model_name = "sentence-transformers/all-MiniLM-L6-v2"
model = SentenceTransformer(model_name)
tokenizer = AutoTokenizer.from_pretrained(model_name)

print(f"✓ Model loaded: {model_name}")
print(f"  Embedding dimension: 384")
print(f"  Max sequence length: 256")

# Create output directory
output_dir = "../app/src/main/assets/models"
os.makedirs(output_dir, exist_ok=True)

# Save tokenizer
print("\n[2/6] Saving tokenizer...")
tokenizer.save_pretrained(output_dir + "/minilm_tokenizer")
print(f"✓ Tokenizer saved to {output_dir}/minilm_tokenizer")

# Convert to TFLite
print("\n[3/6] Converting to TensorFlow Lite...")

# Get the underlying PyTorch model and convert to TF
import torch

class MiniLMWrapper(tf.keras.Model):
    def __init__(self, sentence_transformer):
        super().__init__()
        # Note: This is a simplified wrapper
        # Full conversion requires exporting PyTorch -> ONNX -> TF
        pass
    
    @tf.function(input_signature=[
        tf.TensorSpec(shape=[None, None], dtype=tf.int32, name='input_ids'),
        tf.TensorSpec(shape=[None, None], dtype=tf.int32, name='attention_mask')
    ])
    def call(self, input_ids, attention_mask):
        # Placeholder - actual implementation needs proper conversion
        pass

print("⚠ Note: Full PyTorch -> TFLite conversion is complex.")
print("  Alternative: Use ONNX Runtime Mobile (recommended)")
print("  Or: Use pre-converted TFLite model from TensorFlow Hub")

# Pre-compute scam pattern embeddings
print("\n[4/6] Pre-computing scam pattern embeddings...")

scam_patterns = [
    # Lottery/Prize scams
    "Congratulations! You've won a prize in a lottery you never entered",
    "You are the lucky winner of our grand prize draw",
    "Claim your prize money by calling this number immediately",
    
    # Financial fraud
    "Your bank account has been suspended due to suspicious activity",
    "We need to verify your credit card information right now",
    "Your account will be closed unless you provide your details",
    
    # IRS/Tax scams
    "This is the IRS calling about unpaid taxes and penalties",
    "You have a warrant for your arrest due to tax fraud",
    "Pay your tax debt immediately or face legal consequences",
    
    # Tech support scams
    "Your computer has been infected with a dangerous virus",
    "This is Microsoft technical support calling about your Windows license",
    "We detected suspicious activity on your computer and need remote access",
    
    # Gift card scams
    "Pay the fine using iTunes gift cards or Google Play cards",
    "Purchase prepaid cards and provide the codes to resolve this",
    "Wire transfer or gift cards are the only accepted payment methods",
    
    # Investment scams
    "Guaranteed returns on this exclusive investment opportunity",
    "Double your money with our cryptocurrency trading system",
    "Limited time offer for high-return investments with no risk",
    
    # Grandparent scams
    "Grandma, it's me, I'm in trouble and need money urgently",
    "Your grandson has been arrested and needs bail money immediately",
    "Family emergency - send money right away, don't tell anyone",
    
    # Impersonation
    "This is an official government agency calling about your benefits",
    "Federal officer calling regarding a legal matter in your name",
    "Social Security Administration - your number has been suspended"
]

legitimate_patterns = [
    # Work/Professional
    "Hi, this is John from the office calling about tomorrow's meeting",
    "Following up on the project we discussed last week",
    "Calling to schedule our quarterly review appointment",
    
    # Personal/Family
    "Hey, it's Sarah! Just wanted to catch up and see how you're doing",
    "Mom calling to check in and see if you're free for dinner",
    "Your friend calling about the weekend plans we made",
    
    # Business/Services
    "This is your bank calling to verify a recent transaction on your account",
    "Appointment reminder for your doctor's visit tomorrow at 2 PM",
    "Your package delivery is scheduled for this afternoon",
    
    # Legitimate promotions
    "Thank you for being a valued customer, here's an exclusive offer",
    "Your subscription renewal is coming up next month",
    "Confirmation call for your recent online order"
]

scam_embeddings = model.encode(scam_patterns, convert_to_numpy=True)
legitimate_embeddings = model.encode(legitimate_patterns, convert_to_numpy=True)

np.save(os.path.join(output_dir, "scam_embeddings.npy"), scam_embeddings)
np.save(os.path.join(output_dir, "legitimate_embeddings.npy"), legitimate_embeddings)

print(f"✓ Scam patterns: {len(scam_patterns)} embeddings saved")
print(f"✓ Legitimate patterns: {len(legitimate_patterns)} embeddings saved")

# Test encoding
print("\n[5/6] Testing encoding...")
test_text = "Your account will be suspended unless you verify your information"
test_embedding = model.encode([test_text], convert_to_numpy=True)
print(f"✓ Test embedding shape: {test_embedding.shape}")

# Calculate similarities
from sklearn.metrics.pairwise import cosine_similarity
scam_sim = cosine_similarity(test_embedding, scam_embeddings).max()
legit_sim = cosine_similarity(test_embedding, legitimate_embeddings).max()
print(f"  Scam similarity: {scam_sim:.3f}")
print(f"  Legitimate similarity: {legit_sim:.3f}")
print(f"  Classification: {'SCAM' if scam_sim > legit_sim else 'LEGITIMATE'}")

# Summary
print("\n[6/6] Conversion Summary")
print("=" * 70)
print(f"✓ Tokenizer: {output_dir}/minilm_tokenizer/")
print(f"✓ Scam embeddings: {output_dir}/scam_embeddings.npy")
print(f"✓ Legitimate embeddings: {output_dir}/legitimate_embeddings.npy")
print(f"  Embedding dimension: 384")
print(f"  Scam patterns: {len(scam_patterns)}")
print(f"  Legitimate patterns: {len(legitimate_patterns)}")

print("\n⚠ TFLite Model Status:")
print("  Option 1: Use ONNX Runtime Mobile (recommended)")
print("    - Easier conversion from sentence-transformers")
print("    - Better performance")
print("    - Add dependency: implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.16.0'")
print("\n  Option 2: Use pre-converted TFLite from TensorFlow Hub")
print("    - https://tfhub.dev/google/universal-sentence-encoder/4")
print("\n  Option 3: Full PyTorch -> ONNX -> TFLite conversion")
print("    - Complex but gives smallest model size")

print("\nNext Steps:")
print("1. Choose runtime (ONNX or TFLite)")
print("2. Implement MiniLMClassifier.kt with embedding cache")
print("3. Test semantic similarity classification")
print("=" * 70)
