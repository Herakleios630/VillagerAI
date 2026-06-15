"""Unit-Tests für FactsWorker (Intent + Extraction + Dedup) mit gemockten qwen-Responses."""
import json
import sys
import os as _os
import unittest
from unittest.mock import patch, MagicMock

_PROJECT_ROOT = _os.path.dirname(_os.path.dirname(_os.path.abspath(__file__)))
if _PROJECT_ROOT not in sys.path:
    sys.path.insert(0, _PROJECT_ROOT)

from chief_ai_service.worker import FactsWorker, _FALLBACK_MEMORY_TRIGGER_RE


class TestFallbackRegex(unittest.TestCase):
    """Tests for the regex fallback when qwen is unreachable."""

    def test_trigger_erinner(self):
        self.assertTrue(bool(_FALLBACK_MEMORY_TRIGGER_RE.search("Kannst du dich an mich erinnern?")))

    def test_trigger_weisst_du_noch(self):
        self.assertTrue(bool(_FALLBACK_MEMORY_TRIGGER_RE.search("Weisst du noch, was wir gestern gemacht haben?")))

    def test_trigger_name(self):
        self.assertTrue(bool(_FALLBACK_MEMORY_TRIGGER_RE.search("Wie ist mein Name eigentlich?")))

    def test_trigger_damals(self):
        self.assertTrue(bool(_FALLBACK_MEMORY_TRIGGER_RE.search("Damals in der Hohle war es lustig.")))

    def test_no_trigger(self):
        self.assertFalse(bool(_FALLBACK_MEMORY_TRIGGER_RE.search("Hallo Chef, hast du eine Quest fuer mich?")))

    def test_no_trigger_neutral(self):
        self.assertFalse(bool(_FALLBACK_MEMORY_TRIGGER_RE.search("Das Wetter ist schoen heute.")))


class TestFactsWorkerIntentExtraction(unittest.TestCase):
    """Tests for the full pipeline with mocked qwen responses."""

    def setUp(self):
        self.worker = FactsWorker(max_retries=1, maxlen=10)

    # ------------------------------------------------------------------
    # Intent classification (with mock)
    # ------------------------------------------------------------------
    @patch("chief_ai_service.worker._load_prompt")
    @patch("chief_ai_service.qwen_client.send_prompt")
    def test_classify_intent_has_new_facts(self, mock_send, mock_load):
        mock_load.return_value = "prompt template {message}"
        mock_send.return_value = {
            "has_new_facts": True,
            "new_facts": [
                {"type": "name", "value": "Alex", "importance": 1.0},
                {"type": "location", "value": "Dorf am Fluss", "importance": 0.9},
            ],
            "seeks_facts": False,
            "query_text": "",
        }
        result = self.worker._classify_intent("Ich heisse Alex und wohne im Dorf am Fluss.")
        self.assertIsNotNone(result)
        self.assertTrue(result["has_new_facts"])
        self.assertEqual(len(result["new_facts"]), 2)
        self.assertEqual(result["new_facts"][0]["type"], "name")

    @patch("chief_ai_service.worker._load_prompt")
    @patch("chief_ai_service.qwen_client.send_prompt")
    def test_classify_intent_seeks_facts(self, mock_send, mock_load):
        mock_load.return_value = "prompt template {message}"
        mock_send.return_value = {
            "has_new_facts": False,
            "new_facts": [],
            "seeks_facts": True,
            "query_text": "mein Name",
        }
        result = self.worker._classify_intent("Weisst du noch, wie ich heisse?")
        self.assertIsNotNone(result)
        self.assertTrue(result["seeks_facts"])
        self.assertEqual(result["query_text"], "mein Name")

    @patch("chief_ai_service.worker._load_prompt")
    @patch("chief_ai_service.qwen_client.send_prompt")
    def test_classify_intent_no_facts(self, mock_send, mock_load):
        mock_load.return_value = "prompt template {message}"
        mock_send.return_value = {
            "has_new_facts": False,
            "new_facts": [],
            "seeks_facts": False,
            "query_text": "",
        }
        result = self.worker._classify_intent("Hallo, wie geht es dir?")
        self.assertIsNotNone(result)
        self.assertFalse(result["has_new_facts"])
        self.assertFalse(result["seeks_facts"])

    # ------------------------------------------------------------------
    # Intent fallback (no qwen)
    # ------------------------------------------------------------------
    @patch("chief_ai_service.worker._load_prompt")
    @patch("chief_ai_service.qwen_client.send_prompt")
    def test_classify_intent_fallback_on_qwen_error(self, mock_send, mock_load):
        mock_load.return_value = "prompt template {message}"
        mock_send.return_value = {"error": True, "raw_response": "timeout"}
        result = self.worker._classify_intent("Erinnerst du dich an mich?")
        self.assertIsNotNone(result)
        self.assertTrue(result.get("_fallback"))
        self.assertTrue(result["has_new_facts"])

    @patch("chief_ai_service.worker._load_prompt")
    @patch("chief_ai_service.qwen_client.send_prompt")
    def test_classify_intent_fallback_on_exception(self, mock_send, mock_load):
        mock_load.return_value = "prompt template {message}"
        mock_send.side_effect = ConnectionError("unreachable")
        result = self.worker._classify_intent("Damals war alles besser.")
        self.assertIsNotNone(result)
        self.assertTrue(result.get("_fallback"))

    @patch("chief_ai_service.worker._load_prompt")
    @patch("chief_ai_service.qwen_client.send_prompt")
    def test_classify_intent_fallback_no_trigger(self, mock_send, mock_load):
        mock_load.return_value = "prompt template {message}"
        mock_send.return_value = {"error": True, "raw_response": "timeout"}
        result = self.worker._classify_intent("Welche Quests hast du?")
        self.assertIsNotNone(result)
        self.assertTrue(result.get("_fallback"))
        self.assertFalse(result["has_new_facts"])
        self.assertFalse(result["seeks_facts"])

    # ------------------------------------------------------------------
    # Fact extraction (with mock)
    # ------------------------------------------------------------------
    @patch("chief_ai_service.worker._load_prompt")
    @patch("chief_ai_service.qwen_client.send_prompt")
    def test_extract_facts_valid(self, mock_send, mock_load):
        mock_load.return_value = "prompt template {message}"
        mock_send.return_value = {
            "facts": [
                {"type": "profession", "value": "Schmied", "importance": 0.8},
                {"type": "preference", "value": "mag Eisen", "importance": 0.6},
            ]
        }
        result = self.worker._extract_facts("Ich bin Schmied von Beruf und mag Eisen.")
        self.assertIsNotNone(result)
        self.assertEqual(len(result["facts"]), 2)
        self.assertEqual(result["facts"][0]["type"], "profession")

    @patch("chief_ai_service.worker._load_prompt")
    @patch("chief_ai_service.qwen_client.send_prompt")
    def test_extract_facts_empty(self, mock_send, mock_load):
        mock_load.return_value = "prompt template {message}"
        mock_send.return_value = {"facts": []}
        result = self.worker._extract_facts("Hallo Chef.")
        self.assertIsNotNone(result)
        self.assertEqual(len(result["facts"]), 0)

    @patch("chief_ai_service.worker._load_prompt")
    @patch("chief_ai_service.qwen_client.send_prompt")
    def test_extract_facts_qwen_error(self, mock_send, mock_load):
        mock_load.return_value = "prompt template {message}"
        mock_send.return_value = {"error": True, "raw_response": "crash"}
        result = self.worker._extract_facts("Test")
        self.assertIsNone(result)

    # ------------------------------------------------------------------
    # Dedup decider (with mock)
    # ------------------------------------------------------------------
    def test_dedup_no_existing_facts(self):
        """When no existing facts match, _ask_dedup_decider returns False."""
        result = self.worker._ask_dedup_decider(
            existing_fact=None,
            candidate_type="name",
            candidate_value="Bob",
            candidate_evidence="Ich heisse Bob.",
        )
        self.assertFalse(result)

    @patch("chief_ai_service.worker._load_prompt")
    @patch("chief_ai_service.qwen_client.send_prompt")
    def test_dedup_decider_ja(self, mock_send, mock_load):
        mock_load.return_value = "dedup prompt {type_a} {value_a} {evidence_a} {type_b} {value_b} {evidence_b}"
        mock_send.return_value = {"response": "ja"}
        result = self.worker._ask_dedup_decider(
            existing_fact={"id": 1, "fact_type": "name", "fact_value": "Bob", "evidence_text": "Ich bin Bob"},
            candidate_type="name",
            candidate_value="Bob",
            candidate_evidence="Ich heisse Bob.",
        )
        self.assertTrue(result)

    @patch("chief_ai_service.worker._load_prompt")
    @patch("chief_ai_service.qwen_client.send_prompt")
    def test_dedup_decider_nein(self, mock_send, mock_load):
        mock_load.return_value = "dedup prompt {type_a} {value_a} {evidence_a} {type_b} {value_b} {evidence_b}"
        mock_send.return_value = {"response": "nein"}
        result = self.worker._ask_dedup_decider(
            existing_fact={"id": 1, "fact_type": "location", "fact_value": "Dorf A", "evidence_text": "Ich wohne in Dorf A"},
            candidate_type="location",
            candidate_value="Dorf B",
            candidate_evidence="Ich wohne in Dorf B.",
        )
        self.assertFalse(result)

    @patch("chief_ai_service.worker._load_prompt")
    @patch("chief_ai_service.qwen_client.send_prompt")
    def test_dedup_decider_qwen_error_conservative(self, mock_send, mock_load):
        mock_load.return_value = "dedup prompt {type_a} {value_a} {evidence_a} {type_b} {value_b} {evidence_b}"
        mock_send.return_value = {"error": True, "raw_response": "timeout"}
        result = self.worker._ask_dedup_decider(
            existing_fact={"id": 1, "fact_type": "name", "fact_value": "Alex", "evidence_text": "Ich bin Alex"},
            candidate_type="name",
            candidate_value="Alex",
            candidate_evidence="Mein Name ist Alex.",
        )
        # Conservative: treat as duplicate on error
        self.assertTrue(result)

    # ------------------------------------------------------------------
    # Full pipeline integration (mock qwen + mock db)
    # ------------------------------------------------------------------
    @patch("chief_ai_service.worker.FactsWorker._mark_pending_retrieval")
    @patch("chief_ai_service.worker.FactsWorker._dedup_and_store")
    @patch("chief_ai_service.worker.FactsWorker._extract_facts")
    @patch("chief_ai_service.worker.FactsWorker._classify_intent")
    def test_analyze_facts_full_flow_with_facts(
        self, mock_intent, mock_extract, mock_dedup, mock_pending
    ):
        mock_intent.return_value = {
            "has_new_facts": True,
            "new_facts": [
                {"type": "name", "value": "Alex", "importance": 1.0},
            ],
            "seeks_facts": True,
            "query_text": "mein Name",
        }
        mock_extract.return_value = {
            "facts": [
                {"type": "name", "value": "Alex", "importance": 1.0},
            ]
        }
        mock_dedup.return_value = True

        self.worker._analyze_facts("uuid-1", "ChiefTest", "Ich heisse Alex.")

        mock_intent.assert_called_once_with("Ich heisse Alex.")
        mock_extract.assert_called_once_with("Ich heisse Alex.")
        mock_dedup.assert_called_once()
        # seeks_facts with query_text should trigger pending retrieval
        mock_pending.assert_called_once_with("uuid-1", "ChiefTest", "mein Name")

    @patch("chief_ai_service.worker.FactsWorker._mark_pending_retrieval")
    @patch("chief_ai_service.worker.FactsWorker._dedup_and_store")
    @patch("chief_ai_service.worker.FactsWorker._extract_facts")
    @patch("chief_ai_service.worker.FactsWorker._classify_intent")
    def test_analyze_facts_no_new_facts_skip_extraction(
        self, mock_intent, mock_extract, mock_dedup, mock_pending
    ):
        mock_intent.return_value = {
            "has_new_facts": False,
            "new_facts": [],
            "seeks_facts": False,
            "query_text": "",
        }

        self.worker._analyze_facts("uuid-2", "ChiefTest", "Hallo Chef.")

        mock_intent.assert_called_once()
        # Extraction should NOT be called when has_new_facts=False
        mock_extract.assert_not_called()
        mock_dedup.assert_not_called()
        mock_pending.assert_not_called()


if __name__ == "__main__":
    unittest.main()
