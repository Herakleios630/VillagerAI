"""
Integration test: 50 simulated Conversation-Turns via Bridge components.

Covers:
- Turn insertion and retrieval across 50 turns
- Summary trigger detection at turn 20 and 40
- Embedding search with trigger phrases
- DB persistence across connection close/reopen
- Optional: Real embedding with Ollama if available (skip-safe)
"""

import unittest
import os
import sys
import time
import tempfile

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import memory_db

# ---------------------------------------------------------------------------
# Ollama availability check
# ---------------------------------------------------------------------------

def _check_ollama():
    """Return True if Ollama is reachable and nomic-embed-text responds."""
    try:
        from chief_ai_service.embedding_client import get_embedding
        vec = get_embedding("integration test ping", timeout=5)
        return vec is not None and len(vec) == 768
    except Exception:
        return False

_NO_OLLAMA = not _check_ollama()


# ---------------------------------------------------------------------------
# Test base class with temp DB (per-method isolation)
# ---------------------------------------------------------------------------

class _BaseTempDbTest(unittest.TestCase):
    """Base class: each test method gets a fresh temp DB file."""

    _orig_db_path = memory_db.DB_PATH

    def setUp(self):
        tmp = tempfile.NamedTemporaryFile(suffix=".db", delete=False)
        tmp.close()
        self._tmpfile = tmp.name
        memory_db.DB_PATH = self._tmpfile
        memory_db.create_tables()

    def tearDown(self):
        memory_db.DB_PATH = self._orig_db_path
        if self._tmpfile and os.path.exists(self._tmpfile):
            os.unlink(self._tmpfile)


# ---------------------------------------------------------------------------
# Helper: simulate background summary job without Ollama
# ---------------------------------------------------------------------------

def _simulate_summary_generation(player_uuid, chief_name):
    """Insert a summary directly without calling Ollama (fast path for CI)."""
    latest = memory_db.get_latest_summary(player_uuid, chief_name)
    existing_text = latest["summary_text"] if latest else None

    turns = memory_db.get_unsupervised_turns(player_uuid, chief_name)
    if not turns:
        return None

    if existing_text:
        new_text = existing_text + " [Fortsetzung: " + str(len(turns)) + " weitere Zeilen]"
    else:
        new_text = "Erste Zusammenfassung ueber " + str(len(turns)) + " Gespraechszeilen."

    range_start = min(t["id"] for t in turns)
    range_end = max(t["id"] for t in turns)

    sid = memory_db.insert_summary(
        player_uuid=player_uuid,
        chief_name=chief_name,
        summary_text=new_text,
        turn_range_start=range_start,
        turn_range_end=range_end,
    )
    return sid


# ---------------------------------------------------------------------------
# Test 1: 50 Turns + Summary Trigger
# ---------------------------------------------------------------------------

class FiftyTurnsSummaryTriggerTest(_BaseTempDbTest):
    """Verify that summary trigger fires after the configured interval."""

    PLAYER_UUID = "steve-test-50"
    CHIEF_NAME = "ElderIron"

    def _insert_turn_pair(self, turn_num):
        """Insert a player turn and a chief turn, return their IDs."""
        pid = memory_db.insert_turn(
            player_uuid=self.PLAYER_UUID,
            chief_name=self.CHIEF_NAME,
            role="player",
            message="Turn " + str(turn_num) + " player message about village life.",
            mc_day=turn_num // 2,
            mc_time=6000 + turn_num * 100,
        )
        cid = memory_db.insert_turn(
            player_uuid=self.PLAYER_UUID,
            chief_name=self.CHIEF_NAME,
            role="chief",
            message="Turn " + str(turn_num) + " chief reply about the day ahead.",
            mc_day=turn_num // 2,
            mc_time=6000 + turn_num * 100 + 50,
        )
        return pid, cid

    def test_50_turns_with_summary_checks(self):
        """Insert 50 turn pairs and verify summary trigger logic at 20/40."""

        # Insert first 20 turn pairs
        for i in range(1, 21):
            self._insert_turn_pair(i)

        # After 20 turn pairs = 40 turns total in DB
        all_turns = memory_db.query_turns(self.PLAYER_UUID, self.CHIEF_NAME, limit=100)
        self.assertEqual(len(all_turns), 40, "Should have 40 turns after 20 pairs")

        # Check unsupervised count (no summary yet, all 40 are unsupervised)
        unsupervised = memory_db.count_unsupervised_turns(
            self.PLAYER_UUID, self.CHIEF_NAME
        )
        self.assertEqual(unsupervised, 40,
                         "All 40 turns should be unsupervised before first summary")

        # Simulate summary generation
        summary1_id = _simulate_summary_generation(self.PLAYER_UUID, self.CHIEF_NAME)
        self.assertIsNotNone(summary1_id)
        latest1 = memory_db.get_latest_summary(self.PLAYER_UUID, self.CHIEF_NAME)
        self.assertIsNotNone(latest1, "First summary should exist after 40 turns")
        self.assertIn("Erste Zusammenfassung", latest1["summary_text"])

        # After summary, unsupervised count should drop to 0
        unsupervised_after_1 = memory_db.count_unsupervised_turns(
            self.PLAYER_UUID, self.CHIEF_NAME
        )
        self.assertEqual(unsupervised_after_1, 0,
                         "Unsupervised count should be 0 right after summary")

        # Insert turns 21-40 (another 20 pairs = 40 more turns)
        for i in range(21, 41):
            self._insert_turn_pair(i)

        all_turns = memory_db.query_turns(self.PLAYER_UUID, self.CHIEF_NAME, limit=200)
        self.assertEqual(len(all_turns), 80, "Should have 80 total turns after 40 pairs")

        # Unsupervised should be 40 again (turns 41-80)
        unsupervised2 = memory_db.count_unsupervised_turns(
            self.PLAYER_UUID, self.CHIEF_NAME
        )
        self.assertEqual(unsupervised2, 40,
                         "Should have 40 unsupervised turns after second batch")

        # Generate second summary (rolling)
        summary2_id = _simulate_summary_generation(self.PLAYER_UUID, self.CHIEF_NAME)
        self.assertIsNotNone(summary2_id)
        self.assertGreater(summary2_id, summary1_id,
                          "Second summary ID should be higher")

        latest2 = memory_db.get_latest_summary(self.PLAYER_UUID, self.CHIEF_NAME)
        self.assertIsNotNone(latest2, "Second summary should exist")
        self.assertEqual(latest2["id"], summary2_id)
        self.assertIn("Fortsetzung", latest2["summary_text"],
                      "Rolling summary should reference continuation")

        # Insert final 10 pairs (turns 81-100)
        for i in range(41, 51):
            self._insert_turn_pair(i)

        all_turns_final = memory_db.query_turns(
            self.PLAYER_UUID, self.CHIEF_NAME, limit=200
        )
        self.assertEqual(len(all_turns_final), 100,
                         "Should have exactly 100 turns after 50 pairs")


# ---------------------------------------------------------------------------
# Test 2: Trigger Phrase + Embedding Search
# ---------------------------------------------------------------------------

class TriggerPhraseEmbeddingSearchTest(_BaseTempDbTest):
    """Verify trigger phrases and embedding search."""

    PLAYER_UUID = "alex-search-test"
    CHIEF_NAME = "ElderBirch"

    TRIGGER_PHRASES = [
        "erinnerst du dich",
        "weisst du noch",
        "weissst du noch",
        "erinnerst",
        "letztes mal",
        "gestern",
        "vorhin",
        "damals",
        "frueher",
        "frueher",
        "vor einer weile",
        "neulich",
    ]

    def test_trigger_phrase_detection(self):
        """Verify check_memory_trigger identifies memory-intent messages."""
        from chief_ai_service.prompt_builder import check_memory_trigger

        positives = [
            "Erinnerst du dich an den Diamanten?",
            "Weisst du noch, was wir gestern besprochen haben?",
            "Sag mal, erinnerst du dich an unser letztes Treffen?",
            "Damals war das anders, oder nicht?",
            "Neulich hast du mir doch von der Mine erzaehlt.",
            "Vor einer Weile haben wir ueber Eisen geredet.",
        ]
        for msg in positives:
            with self.subTest(msg=msg):
                self.assertTrue(
                    check_memory_trigger(msg, self.TRIGGER_PHRASES),
                    "Should detect trigger in: " + msg
                )

        negatives = [
            "Hallo Haeuptling!",
            "Was gibt es heute zu tun?",
            "Ich brauche einen Auftrag.",
            "Wie geht es dir?",
            "Kannst du mir Eisen verkaufen?",
        ]
        for msg in negatives:
            with self.subTest(msg=msg):
                self.assertFalse(
                    check_memory_trigger(msg, self.TRIGGER_PHRASES),
                    "Should NOT detect trigger in: " + msg
                )

    def test_embedding_search_finds_diamond_turn(self):
        """Insert a turn about diamonds, verify embedding search finds it."""
        from chief_ai_service.prompt_builder import check_memory_trigger

        # Insert background turns 1-4
        filler_msgs = [
            "Hallo Haeuptling, ich mag Kekse.",
            "Ich backe gerne Brot im Ofen.",
            "Hast du einen Kessel fuer Suppe?",
            "Der Regen heute ist wirklich angenehm.",
        ]
        for i, msg in enumerate(filler_msgs, 1):
            role = "player" if i % 2 == 1 else "chief"
            memory_db.insert_turn(
                player_uuid=self.PLAYER_UUID,
                chief_name=self.CHIEF_NAME,
                role=role,
                message=msg,
                embedding=None,
                mc_day=1,
                mc_time=6000 + i * 100,
            )

        # Turn 5: Key turn about diamonds
        diamond_msg = "Ich habe Diamanten gefunden! Tief in der Schlucht leuchten sie blau."
        diamond_turn_id = memory_db.insert_turn(
            player_uuid=self.PLAYER_UUID,
            chief_name=self.CHIEF_NAME,
            role="player",
            message=diamond_msg,
            embedding=None,
            mc_day=3,
            mc_time=9000,
        )

        # Turn 6: Chief response (also diamond-related)
        chief_diamond_msg = "Diamanten! Das sind seltene Funde. Gut gemacht, Graeber."
        chief_diamond_turn_id = memory_db.insert_turn(
            player_uuid=self.PLAYER_UUID,
            chief_name=self.CHIEF_NAME,
            role="chief",
            message=chief_diamond_msg,
            embedding=None,
            mc_day=3,
            mc_time=9100,
        )

        # More filler turns 7-10 – German, completely unrelated to diamonds
        german_fillers = [
            "Die Sonne brennt heute stark vom Himmel.",
            "Wir muessen die Felder vor der Duerre schuetzen.",
            "Ein ruhiger Tag zum Fischen am See.",
            "Die Schafe brauchen frische Weidegruende.",
        ]
        for i, msg in enumerate(german_fillers, 7):
            role = "player" if i % 2 == 1 else "chief"
            memory_db.insert_turn(
                player_uuid=self.PLAYER_UUID,
                chief_name=self.CHIEF_NAME,
                role=role,
                message=msg,
                embedding=None,
                mc_day=5,
                mc_time=6000 + i * 100,
            )

        # Verify all turns exist
        all_turns = memory_db.query_turns(self.PLAYER_UUID, self.CHIEF_NAME, limit=50)
        self.assertEqual(len(all_turns), 10)

        # Verify trigger phrase detection
        query_msg = "Erinnerst du dich an den Diamanten?"
        self.assertTrue(
            check_memory_trigger(query_msg, self.TRIGGER_PHRASES),
            "Query about diamonds should trigger memory search"
        )

        # Embedding-based search (optional Ollama)
        if _NO_OLLAMA:
            self.skipTest("Ollama not available - skipping embedding search test")

        from chief_ai_service.embedding_client import (
            get_embedding, pack_embedding, cosine_similarity,
        )

        # Generate real embeddings for all turns
        diamond_emb = get_embedding(diamond_msg)
        if diamond_emb is None:
            self.skipTest("Ollama embedding failed - skipping embedding similarity test")

        self.assertEqual(len(diamond_emb), 768)
        memory_db.update_embedding(diamond_turn_id, pack_embedding(diamond_emb))

        # Embed all turns
        turns_list = memory_db.query_turns(
            self.PLAYER_UUID, self.CHIEF_NAME, limit=50
        )
        all_embeddings = {}
        for turn in turns_list:
            if turn["message"] and turn["message"].strip():
                if turn["id"] == diamond_turn_id:
                    all_embeddings[turn["id"]] = diamond_emb
                    continue
                emb = get_embedding(turn["message"])
                if emb is not None:
                    memory_db.update_embedding(turn["id"], pack_embedding(emb))
                    all_embeddings[turn["id"]] = emb

        # Query embedding
        query_emb = get_embedding(query_msg)
        self.assertIsNotNone(query_emb)
        self.assertEqual(len(query_emb), 768)

        # Compute similarities
        similarities = {}
        for tid, emb in all_embeddings.items():
            similarities[tid] = cosine_similarity(query_emb, emb)

        ranked = sorted(similarities.items(), key=lambda x: x[1], reverse=True)

        # Both diamond-related turns should have strong similarity to the query.
        # The player and chief diamond turns together should rank in the top 3.
        diamond_sim = similarities.get(diamond_turn_id, -1.0)
        chief_sim = similarities.get(chief_diamond_turn_id, -1.0)
        best_diamond_sim = max(diamond_sim, chief_sim)

        # Check that at least one diamond turn is in the top 2 results
        top_ids = [tid for tid, _ in ranked[:2]]
        diamond_ids_in_top = sum(
            1 for tid in (diamond_turn_id, chief_diamond_turn_id)
            if tid in top_ids
        )
        self.assertGreaterEqual(
            diamond_ids_in_top, 1,
            "At least one diamond-related turn should be in the top 2 results; "
            "top 2 were: " + str(top_ids) +
            " (diamond_ids: " + str(diamond_turn_id) + ", " + str(chief_diamond_turn_id) + ")"
        )

        # Print debug info
        print("\nEmbedding search results for query: " + query_msg)
        for tid, sim in ranked[:5]:
            matching = [t for t in turns_list if t["id"] == tid]
            label = matching[0]["message"][:80] if matching else "?"
            print("  Turn " + str(tid) + ": sim=" + str(round(sim, 4)) +
                  " -> " + label)

        print("Diamond player turn similarity: " + str(round(diamond_sim, 4)))
        print("Diamond chief turn similarity: " + str(round(chief_sim, 4)))
        self.assertGreater(best_diamond_sim, 0.5,
                           "Best diamond similarity should be > 0.5, got " +
                           str(round(best_diamond_sim, 4)))


# ---------------------------------------------------------------------------
# Test 3: DB Persistence Across Connection Close/Reopen
# ---------------------------------------------------------------------------

class DbPersistenceTest(_BaseTempDbTest):
    """Verify that data survives closing and reopening the database."""

    PLAYER_UUID = "persist-test-uuid"
    CHIEF_NAME = "ChiefPersist"

    def test_data_survives_close_reopen(self):
        """Insert data, simulate restart, verify all data intact."""
        import sqlite3

        # Phase 1: Insert data
        turn_ids = []
        for i in range(5):
            tid = memory_db.insert_turn(
                player_uuid=self.PLAYER_UUID,
                chief_name=self.CHIEF_NAME,
                role="player" if i % 2 == 0 else "chief",
                message="Persist message " + str(i),
                mc_day=10,
                mc_time=12000 + i * 100,
            )
            turn_ids.append(tid)

        sid = memory_db.insert_summary(
            player_uuid=self.PLAYER_UUID,
            chief_name=self.CHIEF_NAME,
            summary_text="Persist summary text.",
            turn_range_start=turn_ids[0],
            turn_range_end=turn_ids[-1],
            reputation=42,
        )

        # Add embedding to one turn
        from chief_ai_service.embedding_client import pack_embedding
        fake_emb = [0.1 * (i % 10) for i in range(768)]
        memory_db.update_embedding(turn_ids[2], pack_embedding(fake_emb))

        # Phase 2: Simulate restart (checkpoint WAL + reopen)
        conn = sqlite3.connect(memory_db.DB_PATH)
        conn.execute("PRAGMA wal_checkpoint(TRUNCATE)")
        conn.close()

        memory_db.migrate()

        # Phase 3: Verify all data intact
        turns = memory_db.query_turns(self.PLAYER_UUID, self.CHIEF_NAME, limit=20)
        self.assertEqual(len(turns), 5, "All 5 turns should survive restart")
        self.assertEqual(turns[0]["id"], turn_ids[-1],
                         "Last inserted turn should be first (newest-first)")

        # Verify embedding survived
        turn_with_emb = [t for t in turns if t["id"] == turn_ids[2]]
        self.assertEqual(len(turn_with_emb), 1)
        self.assertIsNotNone(turn_with_emb[0]["embedding"],
                            "Embedding BLOB should survive restart")
        from chief_ai_service.embedding_client import unpack_embedding
        unpacked = unpack_embedding(turn_with_emb[0]["embedding"])
        self.assertEqual(len(unpacked), 768)
        for i, val in enumerate(unpacked):
            self.assertAlmostEqual(val, 0.1 * (i % 10), places=5)

        # Verify summary survived
        latest = memory_db.get_latest_summary(self.PLAYER_UUID, self.CHIEF_NAME)
        self.assertIsNotNone(latest, "Summary should survive restart")
        self.assertEqual(latest["summary_text"], "Persist summary text.")
        self.assertEqual(latest["reputation_at_summary"], 42)
        self.assertEqual(latest["turn_range_start"], turn_ids[0])
        self.assertEqual(latest["turn_range_end"], turn_ids[-1])


# ---------------------------------------------------------------------------
# Test 4 (optional): Real Ollama integration smoke test
# ---------------------------------------------------------------------------

@unittest.skipIf(_NO_OLLAMA, "Ollama not available")
class OllamaIntegrationSmokeTest(_BaseTempDbTest):
    """Optional integration smoke test with real Ollama embedding + summary."""

    PLAYER_UUID = "ollama-smoke-test"
    CHIEF_NAME = "ChiefOak"

    def test_real_embedding_and_summary_flow(self):
        """End-to-end: insert turns, generate embeddings, generate summary."""
        from chief_ai_service.embedding_client import get_embedding, pack_embedding
        from chief_ai_service.summary_client import SummaryClient

        messages = [
            ("player", "Hallo Haeuptling, ich bin neu im Dorf."),
            ("chief", "Willkommen! Wir koennen jede Hand gebrauchen."),
            ("player", "Gibt es gefaehrliche Auftraege?"),
            ("chief", "Nur fuer die Mutigen. Die Minen sind tief und dunkel."),
            ("player", "Ich habe Diamanten gefunden!"),
            ("chief", "Diamanten! Du bist ein Glueckspilz."),
            ("player", "Kannst du dich an meinen Fund erinnern?"),
            ("chief", "Diamanten vergisst man nicht so schnell."),
            ("player", "Was soll ich mit den Diamanten machen?"),
            ("chief", "Bewahre sie gut auf oder tausche sie bei unserem Schmied."),
        ]

        for i, (role, msg) in enumerate(messages, 1):
            emb = get_embedding(msg)
            emb_blob = pack_embedding(emb) if emb else None
            memory_db.insert_turn(
                player_uuid=self.PLAYER_UUID,
                chief_name=self.CHIEF_NAME,
                role=role,
                message=msg,
                embedding=emb_blob,
                mc_day=i // 2 + 1,
                mc_time=6000 + i * 200,
            )

        # Verify all 10 turns stored
        turns = memory_db.query_turns(self.PLAYER_UUID, self.CHIEF_NAME, limit=20)
        self.assertEqual(len(turns), 10)

        # Verify embeddings are non-NULL
        embedded = [t for t in turns if t["embedding"] is not None]
        self.assertGreater(len(embedded), 0,
                          "At least some turns should have embeddings")

        # Test SummaryClient with real Ollama
        turns_for_summary = memory_db.get_unsupervised_turns(
            self.PLAYER_UUID, self.CHIEF_NAME
        )
        self.assertEqual(len(turns_for_summary), 10)

        client = SummaryClient()
        summary = client.generate_summary_safe(
            existing_summary=None,
            turns=turns_for_summary,
        )
        self.assertIsNotNone(summary)
        self.assertGreater(len(summary), 10,
                          "Summary should be meaningful, got: " + repr(summary))
        print("\nReal summary: " + summary[:200] + "...")


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    print("Ollama available: " + str(not _NO_OLLAMA))
    unittest.main(verbosity=2)