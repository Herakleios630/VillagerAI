"""
Embedding-Client for Ollama's nomic-embed-text model.

Provides:
    - get_embedding(text) -> list[float] | None
    - ensure_model_loaded()
    - cosine_similarity(vec_a, vec_b) -> float
    - pack_embedding / unpack_embedding (for SQLite BLOB storage)

Uses Ollama's /api/embed endpoint. No numpy – only math standard library.

Graceful degradation: All network errors are logged and None is returned
instead of raising exceptions.
"""

import json
import logging
import math
import struct
import time
import urllib.error
import urllib.request

logger = logging.getLogger(__name__)

# nomic-embed-text produces 768-dimensional float embeddings
EMBEDDING_DIMS = 768
OLLAMA_EMBED_ENDPOINT = "http://127.0.0.1:11434/api/embed"
EMBED_MODEL = "nomic-embed-text"
DEFAULT_TIMEOUT = 30
DEFAULT_KEEP_ALIVE = "30m"


# ---------------------------------------------------------------------------
# Core embedding function
# ---------------------------------------------------------------------------

def get_embedding(text: str, timeout: int = DEFAULT_TIMEOUT) -> list | None:
    """
    Generate a 768-dimensional embedding vector for *text*.

    Args:
        text: The text to embed.
        timeout: HTTP timeout in seconds.

    Returns:
        list[float] – 768 floats, or None on any error (graceful degradation).
    """
    if not text or not text.strip():
        return [0.0] * EMBEDDING_DIMS

    request_body = json.dumps({
        "model": EMBED_MODEL,
        "input": text,
        "keep_alive": DEFAULT_KEEP_ALIVE,
    }).encode("utf-8")

    request = urllib.request.Request(
        OLLAMA_EMBED_ENDPOINT,
        data=request_body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    started_at = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            response_body = response.read().decode("utf-8")
        elapsed_ms = (time.perf_counter() - started_at) * 1000.0
        logger.info(
            "Embedding API call finished in %.1f ms (chars=%d)",
            elapsed_ms,
            len(text),
        )
    except urllib.error.HTTPError as error:
        elapsed_ms = (time.perf_counter() - started_at) * 1000.0
        error_body = error.read().decode("utf-8", errors="replace")
        logger.error(
            "Ollama embedding HTTP %s after %.1f ms: %s | error=%r",
            error.code,
            elapsed_ms,
            error_body,
            error,
        )
        return None
    except urllib.error.URLError as error:
        elapsed_ms = (time.perf_counter() - started_at) * 1000.0
        logger.error(
            "Ollama (embedding) ist nicht erreichbar nach %.1f ms: %s | error=%r",
            elapsed_ms,
            error,
            error,
        )
        return None

    try:
        parsed = json.loads(response_body)
    except json.JSONDecodeError:
        elapsed_ms = (time.perf_counter() - started_at) * 1000.0
        logger.error(
            "Ollama embedding lieferte ungueltiges JSON nach %.1f ms: %s",
            elapsed_ms,
            response_body,
        )
        return None

    # /api/embed returns {"embeddings": [[...], ...]} – one per input
    embeddings = parsed.get("embeddings")
    if not embeddings or not isinstance(embeddings, list) or len(embeddings) == 0:
        elapsed_ms = (time.perf_counter() - started_at) * 1000.0
        logger.error(
            "Ollama embedding lieferte keine Vektoren nach %.1f ms: %s",
            elapsed_ms,
            response_body,
        )
        return None

    vector = embeddings[0]
    if not isinstance(vector, list) or len(vector) == 0:
        elapsed_ms = (time.perf_counter() - started_at) * 1000.0
        logger.error(
            "Ollama embedding lieferte leeren Vektor nach %.1f ms: %s",
            elapsed_ms,
            response_body,
        )
        return None

    logger.info("Embedding generated OK dims=%d", len(vector))

    return [float(v) for v in vector]


# ---------------------------------------------------------------------------
# Model warm-up
# ---------------------------------------------------------------------------

def ensure_model_loaded(timeout: int = DEFAULT_TIMEOUT) -> None:
    """
    Ensure nomic-embed-text is loaded into VRAM.

    Sends a minimal embedding request to trigger model loading.
    Does not raise on failure – embedding will still work on the
    first real call, just with higher first-request latency.

    Returns silently on success or failure.
    """
    # Call get_embedding; on None (error) we silently skip – first real call
    # will trigger model loading.
    get_embedding("ping", timeout=timeout)


# ---------------------------------------------------------------------------
# Cosine similarity (no numpy)
# ---------------------------------------------------------------------------

def cosine_similarity(vec_a: list, vec_b: list) -> float:
    """
    Compute cosine similarity between two float vectors.

    cos(A, B) = dot(A,B) / (||A|| * ||B||)

    Returns:
        float in [-1, 1]. Returns 0.0 if either vector has zero magnitude.
    """
    if len(vec_a) != len(vec_b):
        raise ValueError(
            f"Vektor-Dimensionen stimmen nicht ueberein: "
            f"{len(vec_a)} vs {len(vec_b)}"
        )

    dot_product = 0.0
    norm_a = 0.0
    norm_b = 0.0

    for a, b in zip(vec_a, vec_b):
        dot_product += a * b
        norm_a += a * a
        norm_b += b * b

    if norm_a == 0.0 or norm_b == 0.0:
        return 0.0

    return dot_product / (math.sqrt(norm_a) * math.sqrt(norm_b))


# ---------------------------------------------------------------------------
# Serialization helpers (list[float] <-> BLOB for SQLite)
# ---------------------------------------------------------------------------

def pack_embedding(embedding: list) -> bytes:
    """
    Pack a list of floats into a binary BLOB for storage.

    Uses IEEE 754 double-precision (8 bytes per float).
    """
    if len(embedding) != EMBEDDING_DIMS:
        raise ValueError(
            f"embedding hat {len(embedding)} Dims, erwartet {EMBEDDING_DIMS}"
        )
    return struct.pack(f">{EMBEDDING_DIMS}d", *embedding)


def unpack_embedding(blob: bytes) -> list:
    """
    Unpack a binary BLOB back into a list of floats.
    """
    expected_size = EMBEDDING_DIMS * 8
    if len(blob) != expected_size:
        raise ValueError(
            f"BLOB-Groesse {len(blob)} passt nicht zu {EMBEDDING_DIMS} Dims"
        )
    return list(struct.unpack(f">{EMBEDDING_DIMS}d", blob))


# ---------------------------------------------------------------------------
# CLI smoke test
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    print("--- embedding_client smoke test ---")

    # Test pack/unpack roundtrip
    original = [float(i) / 100.0 for i in range(EMBEDDING_DIMS)]
    packed = pack_embedding(original)
    unpacked = unpack_embedding(packed)
    assert len(unpacked) == EMBEDDING_DIMS, "unpack dims mismatch"
    for i, (o, u) in enumerate(zip(original, unpacked)):
        assert abs(o - u) < 0.0001, f"roundtrip error at index {i}"
    print("pack/unpack roundtrip OK")

    # Test cosine_similarity
    a = [1.0, 0.0, 0.0]
    b = [0.0, 1.0, 0.0]
    assert abs(cosine_similarity(a, b) - 0.0) < 0.001, "orthogonal should be 0"
    assert abs(cosine_similarity(a, a) - 1.0) < 0.001, "identical should be 1"
    assert abs(cosine_similarity(a, [-1.0, 0.0, 0.0]) + 1.0) < 0.001, "opposite should be -1"
    print("cosine_similarity tests OK")

    # Test get_embedding (only if Ollama is reachable)
    vec = get_embedding("Das ist ein Test.")
    if vec is not None:
        print(f"get_embedding OK, dims={len(vec)}, first_vals={vec[:5]}")
    else:
        print("get_embedding skipped (Ollama not reachable)")

    # Test ensure_model_loaded
    ensure_model_loaded()
    print("ensure_model_loaded OK")

    print("--- smoke test complete ---")