"""Unit tests for embedding_client.py.

Covers:
- cosine_similarity() basic math
- get_embedding() with mocked Ollama response (768 floats)
- ensure_model_loaded() no-error when model already loaded
- get_embedding() timeout → graceful degradation (None)
- get_embedding() server unreachable → error logged, no crash (None)
"""

import unittest
import json
import urllib.error
from unittest.mock import patch, MagicMock

import os
import sys
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from chief_ai_service import embedding_client


class CosineSimilarityTests(unittest.TestCase):
    """Tests for cosine_similarity() – no external dependencies."""

    def test_identical_vectors_return_one(self):
        vec = [1.0, 2.0, 3.0, 4.0, 5.0]
        result = embedding_client.cosine_similarity(vec, vec)
        self.assertAlmostEqual(result, 1.0, places=5)

    def test_orthogonal_vectors_return_zero(self):
        a = [1.0, 0.0, 0.0, 0.0]
        b = [0.0, 1.0, 0.0, 0.0]
        result = embedding_client.cosine_similarity(a, b)
        self.assertAlmostEqual(result, 0.0, places=5)

    def test_opposite_vectors_return_minus_one(self):
        a = [1.0, 2.0, 3.0]
        b = [-1.0, -2.0, -3.0]
        result = embedding_client.cosine_similarity(a, b)
        self.assertAlmostEqual(result, -1.0, places=5)

    def test_zero_vector_returns_zero(self):
        a = [0.0, 0.0, 0.0]
        b = [1.0, 2.0, 3.0]
        result = embedding_client.cosine_similarity(a, b)
        self.assertEqual(result, 0.0)

    def test_mismatched_dimensions_raises_value_error(self):
        with self.assertRaises(ValueError):
            embedding_client.cosine_similarity([1.0, 2.0], [1.0, 2.0, 3.0])


class GetEmbeddingTests(unittest.TestCase):
    """Tests for get_embedding() – mocked HTTP responses."""

    @classmethod
    def setUpClass(cls):
        # Pre-build a valid 768-dimensional embedding list
        cls._valid_embedding = [float(i) / 768.0 for i in range(768)]
        cls._valid_response_body = json.dumps({
            "embeddings": [cls._valid_embedding]
        }).encode("utf-8")

    def test_valid_response_returns_embedding_list(self):
        mock_response = MagicMock()
        mock_response.read.return_value = self._valid_response_body
        mock_response.__enter__.return_value = mock_response

        with patch("urllib.request.urlopen", return_value=mock_response):
            result = embedding_client.get_embedding("Test text")
            self.assertEqual(len(result), 768)
            self.assertIsInstance(result, list)
            self.assertAlmostEqual(result[0], 0.0 / 768.0, places=5)
            self.assertAlmostEqual(result[-1], 767.0 / 768.0, places=5)

    def test_empty_text_returns_zero_vector(self):
        result = embedding_client.get_embedding("   ")
        self.assertEqual(len(result), 768)
        self.assertEqual(result, [0.0] * 768)

    def test_timeout_returns_none(self):
        """Graceful degradation: timeout → None (no crash)."""
        with patch("urllib.request.urlopen") as mock_urlopen:
            mock_urlopen.side_effect = urllib.error.URLError("timed out")
            result = embedding_client.get_embedding("timeout text", timeout=1)
            self.assertIsNone(result, "Expected None on timeout (graceful degradation)")

    def test_server_unreachable_returns_none_and_logs(self):
        """Server not reachable → log error, return None, no crash."""
        with patch("urllib.request.urlopen") as mock_urlopen:
            mock_urlopen.side_effect = urllib.error.URLError(
                "Connection refused"
            )
            with self.assertLogs(level="ERROR") as log_ctx:
                result = embedding_client.get_embedding("test")
            self.assertIsNone(result, "Expected None on unreachable (graceful degradation)")
            self.assertTrue(
                any("nicht erreichbar" in msg for msg in log_ctx.output),
                f"Expected 'nicht erreichbar' in log, got: {log_ctx.output}",
            )

    def test_http_error_returns_none_and_logs(self):
        """HTTP error from Ollama → log error, return None."""
        error_response = MagicMock()
        error_response.read.return_value = b'{"error":"model not found"}'

        with patch("urllib.request.urlopen") as mock_urlopen:
            mock_urlopen.side_effect = urllib.error.HTTPError(
                url="http://127.0.0.1:11434/api/embed",
                code=500,
                msg="Internal Server Error",
                hdrs=MagicMock(),
                fp=error_response,
            )
            with self.assertLogs(level="ERROR") as log_ctx:
                result = embedding_client.get_embedding("test")
            self.assertIsNone(result, "Expected None on HTTP error (graceful degradation)")
            self.assertTrue(
                any("HTTP 500" in msg for msg in log_ctx.output),
                f"Expected 'HTTP 500' in log, got: {log_ctx.output}",
            )

    def test_invalid_json_returns_none(self):
        """Malformed JSON response → log error, return None."""
        mock_response = MagicMock()
        mock_response.read.return_value = b"not valid json"
        mock_response.__enter__.return_value = mock_response

        with patch("urllib.request.urlopen", return_value=mock_response):
            with self.assertLogs(level="ERROR") as log_ctx:
                result = embedding_client.get_embedding("test")
            self.assertIsNone(result, "Expected None on invalid JSON")
            self.assertTrue(
                any("ungueltiges JSON" in msg for msg in log_ctx.output),
                f"Expected 'ungueltiges JSON' in log, got: {log_ctx.output}",
            )

    def test_empty_embeddings_list_returns_none(self):
        """Response without embeddings → log error, return None."""
        mock_response = MagicMock()
        mock_response.read.return_value = b'{"embeddings": []}'
        mock_response.__enter__.return_value = mock_response

        with patch("urllib.request.urlopen", return_value=mock_response):
            with self.assertLogs(level="ERROR") as log_ctx:
                result = embedding_client.get_embedding("test")
            self.assertIsNone(result)
            self.assertTrue(
                any("keine Vektoren" in msg for msg in log_ctx.output),
                f"Expected 'keine Vektoren' in log, got: {log_ctx.output}",
            )


class EnsureModelLoadedTests(unittest.TestCase):
    """Tests for ensure_model_loaded()."""

    def test_no_error_when_model_responds_ok(self):
        valid_response = json.dumps({
            "embeddings": [[float(i) for i in range(768)]]
        }).encode("utf-8")

        mock_response = MagicMock()
        mock_response.read.return_value = valid_response
        mock_response.__enter__.return_value = mock_response

        with patch("urllib.request.urlopen", return_value=mock_response):
            # Should not raise
            embedding_client.ensure_model_loaded()

    def test_no_error_when_server_unreachable(self):
        """Graceful: even if Ollama is down, ensure_model_loaded() shouldn't raise."""
        with patch("urllib.request.urlopen") as mock_urlopen:
            mock_urlopen.side_effect = urllib.error.URLError("Connection refused")
            # Should not raise – graceful degradation
            embedding_client.ensure_model_loaded()


class PackUnpackTests(unittest.TestCase):
    """Tests for pack_embedding() / unpack_embedding() roundtrip."""

    def test_roundtrip_preserves_values(self):
        original = [float(i) / 100.0 for i in range(768)]
        packed = embedding_client.pack_embedding(original)
        unpacked = embedding_client.unpack_embedding(packed)
        self.assertEqual(len(unpacked), 768)
        for i, (o, u) in enumerate(zip(original, unpacked)):
            self.assertAlmostEqual(o, u, places=5,
                                   msg=f"Roundtrip mismatch at index {i}")

    def test_pack_wrong_dims_raises_value_error(self):
        with self.assertRaises(ValueError):
            embedding_client.pack_embedding([1.0, 2.0])

    def test_unpack_wrong_size_raises_value_error(self):
        with self.assertRaises(ValueError):
            embedding_client.unpack_embedding(b"too short")


if __name__ == "__main__":
    unittest.main()