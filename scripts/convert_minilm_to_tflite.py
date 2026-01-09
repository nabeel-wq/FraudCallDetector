# coding: utf-8
"""
MiniLM-L6-v2 Embedding Generator

Generates real MiniLM embeddings for scam and legitimate patterns.

Requirements:
    pip install sentence-transformers numpy scikit-learn

Usage:
    python convert_minilm_to_tflite.py
"""

import os
import numpy as np
from sentence_transformers import SentenceTransformer

print("=" * 70)
print("MiniLM-L6-v2 Embedding Generator")
print("=" * 70)

# Load model
print("\n[1/4] Loading MiniLM-L6-v2 model...")
model_name = "sentence-transformers/all-MiniLM-L6-v2"
model = SentenceTransformer(model_name)

print(f"[OK] Model loaded: {model_name}")
print(f"  Embedding dimension: 384")

# Create output directory
output_dir = "../app/src/main/assets/models"
os.makedirs(output_dir, exist_ok=True)

# Pre-compute scam pattern embeddings
print("\n[2/4] Generating scam pattern embeddings...")

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

scam_embeddings = model.encode(scam_patterns, convert_to_numpy=True)
legitimate_embeddings = model.encode(legitimate_patterns, convert_to_numpy=True)

np.save(os.path.join(output_dir, "scam_embeddings.npy"), scam_embeddings)
np.save(os.path.join(output_dir, "legitimate_embeddings.npy"), legitimate_embeddings)

print(f"[OK] Scam patterns: {len(scam_patterns)} embeddings saved")
print(f"[OK] Legitimate patterns: {len(legitimate_patterns)} embeddings saved")

# Test encoding
print("\n[3/4] Testing encoding...")
test_text = "Your account will be suspended unless you verify your information"
test_embedding = model.encode([test_text], convert_to_numpy=True)
print(f"[OK] Test embedding shape: {test_embedding.shape}")

# Calculate similarities
from sklearn.metrics.pairwise import cosine_similarity
scam_sim = cosine_similarity(test_embedding, scam_embeddings).max()
legit_sim = cosine_similarity(test_embedding, legitimate_embeddings).max()
print(f"  Scam similarity: {scam_sim:.3f}")
print(f"  Legitimate similarity: {legit_sim:.3f}")
print(f"  Classification: {'SCAM' if scam_sim > legit_sim else 'LEGITIMATE'}")

# Summary
print("\n[4/4] Summary")
print("=" * 70)
print(f"[OK] Scam embeddings: {output_dir}/scam_embeddings.npy")
print(f"[OK] Legitimate embeddings: {output_dir}/legitimate_embeddings.npy")
print(f"  Embedding dimension: 384")
print(f"  Scam patterns: {len(scam_patterns)}")
print(f"  Legitimate patterns: {len(legitimate_patterns)}")
print("\nEmbeddings ready for Android app!")
print("=" * 70)
