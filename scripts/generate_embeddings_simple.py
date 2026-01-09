"""
Simplified MiniLM Embedding Generator

Generates pre-computed embeddings for scam and legitimate patterns
without requiring full model conversion.

This creates placeholder embeddings that can be replaced with actual
MiniLM embeddings when you run the full conversion script.

Requirements:
    pip install numpy

Usage:
    python generate_embeddings_simple.py
"""

import os
import numpy as np

print("=" * 70)
print("Generating Placeholder Embeddings for MiniLM")
print("=" * 70)

# Create output directory
output_dir = "../app/src/main/assets/models"
os.makedirs(output_dir, exist_ok=True)

# Embedding dimension for MiniLM-L6-v2
EMBEDDING_DIM = 384

# Scam patterns (same as in full script)
scam_patterns = [
    "Congratulations! You've won a prize in a lottery you never entered",
    "You are the lucky winner of our grand prize draw",
    "Your bank account has been suspended due to suspicious activity",
    "We need to verify your credit card information right now",
    "This is the IRS calling about unpaid taxes and penalties",
    "You have a warrant for your arrest due to tax fraud",
    "Your computer has been infected with a dangerous virus",
    "This is Microsoft technical support calling about your Windows license",
    "Pay the fine using iTunes gift cards or Google Play cards",
    "Purchase prepaid cards and provide the codes to resolve this",
    "Guaranteed returns on this exclusive investment opportunity",
    "Double your money with our cryptocurrency trading system",
    "Grandma, it's me, I'm in trouble and need money urgently",
    "Your grandson has been arrested and needs bail money immediately",
    "This is an official government agency calling about your benefits",
    "Federal officer calling regarding a legal matter in your name",
]

legitimate_patterns = [
    "Hi, this is John from the office calling about tomorrow's meeting",
    "Following up on the project we discussed last week",
    "Hey, it's Sarah! Just wanted to catch up and see how you're doing",
    "Mom calling to check in and see if you're free for dinner",
    "This is your bank calling to verify a recent transaction on your account",
    "Appointment reminder for your doctor's visit tomorrow at 2 PM",
    "Your package delivery is scheduled for this afternoon",
    "Thank you for being a valued customer, here's an exclusive offer",
]

print(f"\n[1/3] Generating {len(scam_patterns)} scam pattern embeddings...")

# Generate random embeddings (normalized)
# In production, these would be actual MiniLM embeddings
scam_embeddings = []
for i, pattern in enumerate(scam_patterns):
    # Create a deterministic "embedding" based on pattern
    np.random.seed(hash(pattern) % (2**32))
    embedding = np.random.randn(EMBEDDING_DIM).astype(np.float32)
    # Normalize
    embedding = embedding / np.linalg.norm(embedding)
    scam_embeddings.append(embedding)

scam_embeddings = np.array(scam_embeddings, dtype=np.float32)

print(f"[OK] Scam embeddings shape: {scam_embeddings.shape}")

print(f"\n[2/3] Generating {len(legitimate_patterns)} legitimate pattern embeddings...")

legitimate_embeddings = []
for i, pattern in enumerate(legitimate_patterns):
    np.random.seed(hash(pattern) % (2**32))
    embedding = np.random.randn(EMBEDDING_DIM).astype(np.float32)
    embedding = embedding / np.linalg.norm(embedding)
    legitimate_embeddings.append(embedding)

legitimate_embeddings = np.array(legitimate_embeddings, dtype=np.float32)

print(f"[OK] Legitimate embeddings shape: {legitimate_embeddings.shape}")

print(f"\n[3/3] Saving embeddings...")

# Save as .npy files
np.save(os.path.join(output_dir, "scam_embeddings.npy"), scam_embeddings)
np.save(os.path.join(output_dir, "legitimate_embeddings.npy"), legitimate_embeddings)

print(f"[OK] Saved to {output_dir}/scam_embeddings.npy")
print(f"[OK] Saved to {output_dir}/legitimate_embeddings.npy")

print("\n" + "=" * 70)
print("Summary")
print("=" * 70)
print(f"[OK] Scam patterns: {len(scam_patterns)} embeddings")
print(f"[OK] Legitimate patterns: {len(legitimate_patterns)} embeddings")
print(f"[OK] Embedding dimension: {EMBEDDING_DIM}")
print(f"[OK] Files saved to: {output_dir}")

print("\n⚠ Note: These are placeholder embeddings for testing.")
print("  For production, run convert_minilm_to_tflite.py with:")
print("  pip install sentence-transformers tensorflow transformers")

print("\nNext Steps:")
print("1. Build the Android app in Android Studio")
print("2. Test with sample calls")
print("3. (Optional) Generate real MiniLM embeddings for better accuracy")
print("=" * 70)
