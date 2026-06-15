"""Unit tests for player_facts CRUD in memory_db.py.

Covers:
- Table + FTS5 + triggers creation via create_tables()
- insert_fact() / update_fact() roundtrip
- query_facts_by_type()
- search_facts_fts()
- get_facts_for_player() with chief_name / any logic
- delete_facts_for_player() isolation
- migrate() adds player_facts columns to existing DB
"""

import unittest
import sqlite3
import os
import sys
import tempfile
import logging

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# Suppress noisy logger output during tests
logging.getLogger("chief_ai_service.memory_db").setLevel(logging.WARNING)

import memory_db


class PlayerFactsTests(unittest.TestCase):
    """Tests against a temporary file-based SQLite DB."""

    @classmethod
    def setUpClass(cls):
        tmp = tempfile.NamedTemporaryFile(suffix=".db", delete=False)
        tmp.close()
        cls._tmpfile = tmp.name
        cls._orig_db_path = memory_db.DB_PATH
        memory_db.DB_PATH = cls._tmpfile

    @classmethod
    def tearDownClass(cls):
        memory_db.DB_PATH = cls._orig_db_path
        if cls._tmpfile and os.path.exists(cls._tmpfile):
            os.unlink(cls._tmpfile)

    def setUp(self):
        memory_db.create_tables()

    def tearDown(self):
        conn = memory_db.get_connection()
        try:
            conn.execute("DROP TABLE IF EXISTS player_facts")
            conn.execute("DROP TABLE IF EXISTS facts_fts")
            conn.execute("DROP TABLE IF EXISTS conversation_turns")
            conn.execute("DROP TABLE IF EXISTS memory_summaries")
            conn.commit()
        finally:
            conn.close()

    # ------------------------------------------------------------------
    # Test 1: create_tables() schema for player_facts + FTS5
    # ------------------------------------------------------------------
    def test_create_tables_includes_player_facts_and_fts(self):
        conn = memory_db.get_connection()
        try:
            tables = conn.execute(
                "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"
            ).fetchall()
            table_names = {row[0] for row in tables}
            self.assertIn("player_facts", table_names)
            self.assertIn("facts_fts", table_names)

            # Verify player_facts columns
            cols = self._get_columns(conn, "player_facts")
            expected = {
                "id", "player_uuid", "chief_name", "fact_type", "fact_value",
                "evidence_text", "embedding", "confidence", "importance",
                "times_confirmed", "first_seen_at", "last_seen_at",
                "source_turn_id", "is_deleted",
            }
            self.assertEqual(expected, cols)

            # Verify FTS triggers exist
            triggers = conn.execute(
                "SELECT name FROM sqlite_master WHERE type='trigger' ORDER BY name"
            ).fetchall()
            trigger_names = {row[0] for row in triggers}
            self.assertIn("facts_ai", trigger_names)
            self.assertIn("facts_ad", trigger_names)
            self.assertIn("facts_au", trigger_names)

            # Verify indexes
            indexes = conn.execute(
                "SELECT name FROM sqlite_master WHERE type='index' ORDER BY name"
            ).fetchall()
            index_names = {row[0] for row in indexes}
            self.assertIn("idx_player_facts_lookup", index_names)
            self.assertIn("idx_player_facts_last_seen", index_names)
        finally:
            conn.close()

    # ------------------------------------------------------------------
    # Test 2: insert_fact() + get_facts_for_player() roundtrip
    # ------------------------------------------------------------------
    def test_insert_and_get_fact(self):
        fid = memory_db.insert_fact(
            player_uuid="uuid-arno",
            chief_name="ElderGrimm",
            fact_type="name",
            fact_value="Arno",
            evidence_text="ich heisse Arno",
            confidence=0.9,
            importance=0.95,
            times_confirmed=1,
            source_turn_id=42,
        )
        self.assertIsInstance(fid, int)
        self.assertGreater(fid, 0)

        facts = memory_db.get_facts_for_player("uuid-arno", "ElderGrimm")
        self.assertEqual(len(facts), 1)

        f = facts[0]
        self.assertEqual(f["id"], fid)
        self.assertEqual(f["player_uuid"], "uuid-arno")
        self.assertEqual(f["chief_name"], "ElderGrimm")
        self.assertEqual(f["fact_type"], "name")
        self.assertEqual(f["fact_value"], "Arno")
        self.assertEqual(f["evidence_text"], "ich heisse Arno")
        self.assertEqual(f["confidence"], 0.9)
        self.assertEqual(f["importance"], 0.95)
        self.assertEqual(f["times_confirmed"], 1)
        self.assertEqual(f["source_turn_id"], 42)
        self.assertEqual(f["is_deleted"], 0)
        self.assertIsNotNone(f["first_seen_at"])
        self.assertIsNotNone(f["last_seen_at"])

    def test_insert_fact_with_defaults(self):
        fid = memory_db.insert_fact(
            player_uuid="uuid-1",
            chief_name="Chief",
            fact_type="preference",
            fact_value="mag Karotten",
            evidence_text="Ich liebe Karotten",
        )
        facts = memory_db.get_facts_for_player("uuid-1", "Chief")
        self.assertEqual(len(facts), 1)
        self.assertEqual(facts[0]["confidence"], 0.8)
        self.assertEqual(facts[0]["importance"], 0.5)
        self.assertEqual(facts[0]["times_confirmed"], 1)
        self.assertIsNone(facts[0]["source_turn_id"])
        self.assertIsNone(facts[0]["embedding"])

    # ------------------------------------------------------------------
    # Test 3: update_fact() partial update
    # ------------------------------------------------------------------
    def test_update_fact_partial(self):
        fid = memory_db.insert_fact(
            player_uuid="uuid-1",
            chief_name="Chief",
            fact_type="name",
            fact_value="Arno",
            evidence_text="ich heisse Arno",
            confidence=0.8,
            times_confirmed=1,
        )

        # Update only some fields
        memory_db.update_fact(
            fid,
            times_confirmed=5,
            confidence=0.95,
        )

        facts = memory_db.get_facts_for_player("uuid-1", "Chief")
        self.assertEqual(facts[0]["confidence"], 0.95)
        self.assertEqual(facts[0]["times_confirmed"], 5)
        # Unchanged fields stay the same
        self.assertEqual(facts[0]["fact_value"], "Arno")

    def test_update_fact_soft_delete(self):
        fid = memory_db.insert_fact(
            player_uuid="uuid-1",
            chief_name="Chief",
            fact_type="name",
            fact_value="Arno",
            evidence_text="ich heisse Arno",
        )
        memory_db.update_fact(fid, is_deleted=1)

        # Default get should exclude deleted
        facts = memory_db.get_facts_for_player("uuid-1", "Chief")
        self.assertEqual(len(facts), 0)

        # With include_deleted=True it should appear
        facts_all = memory_db.get_facts_for_player("uuid-1", "Chief", include_deleted=True)
        self.assertEqual(len(facts_all), 1)
        self.assertEqual(facts_all[0]["is_deleted"], 1)

    def test_update_fact_refreshes_last_seen_at(self):
        fid = memory_db.insert_fact(
            player_uuid="uuid-1",
            chief_name="Chief",
            fact_type="name",
            fact_value="Arno",
            evidence_text="ich heisse Arno",
        )
        facts_before = memory_db.get_facts_for_player("uuid-1", "Chief")
        original_last_seen = facts_before[0]["last_seen_at"]

        memory_db.update_fact(fid, times_confirmed=2)

        facts_after = memory_db.get_facts_for_player("uuid-1", "Chief")
        # last_seen_at should have changed (datetime string comparison)
        self.assertIsNotNone(facts_after[0]["last_seen_at"])

    # ------------------------------------------------------------------
    # Test 4: query_facts_by_type()
    # ------------------------------------------------------------------
    def test_query_facts_by_type_filters_correctly(self):
        memory_db.insert_fact("uuid-1", "Chief", "name", "Arno", "heisse Arno")
        memory_db.insert_fact("uuid-1", "Chief", "location", "im Wald", "wohne im Wald")
        memory_db.insert_fact("uuid-1", "Chief", "preference", "Karotten", "mag Karotten")

        names = memory_db.query_facts_by_type("uuid-1", "Chief", "name")
        self.assertEqual(len(names), 1)
        self.assertEqual(names[0]["fact_value"], "Arno")

        locs = memory_db.query_facts_by_type("uuid-1", "Chief", "location")
        self.assertEqual(len(locs), 1)
        self.assertEqual(locs[0]["fact_value"], "im Wald")

    def test_query_facts_by_type_excludes_deleted(self):
        memory_db.insert_fact("uuid-1", "Chief", "name", "Arno", "heisse Arno")
        fid = memory_db.insert_fact("uuid-1", "Chief", "name", "Arnold", "heisse Arnold")
        memory_db.update_fact(fid, is_deleted=1)

        names = memory_db.query_facts_by_type("uuid-1", "Chief", "name")
        self.assertEqual(len(names), 1)
        self.assertEqual(names[0]["fact_value"], "Arno")

    # ------------------------------------------------------------------
    # Test 5: search_facts_fts()
    # ------------------------------------------------------------------
    def test_search_facts_fts_finds_value(self):
        memory_db.insert_fact(
            "uuid-1", "Chief", "name", "Arno", "Hallo ich heisse Arno",
        )
        memory_db.insert_fact(
            "uuid-1", "Chief", "location", "Wald", "Ich wohne im Wald",
        )

        results = memory_db.search_facts_fts("Arno", player_uuid="uuid-1")
        self.assertEqual(len(results), 1)
        self.assertEqual(results[0]["fact_value"], "Arno")

        results = memory_db.search_facts_fts("Wald", player_uuid="uuid-1")
        self.assertEqual(len(results), 1)
        self.assertEqual(results[0]["fact_type"], "location")

    def test_search_facts_fts_case_insensitive(self):
        memory_db.insert_fact(
            "uuid-1", "Chief", "name", "Arno", "heisse Arno",
        )
        results = memory_db.search_facts_fts("arno", player_uuid="uuid-1")
        self.assertEqual(len(results), 1)

    def test_search_facts_fts_no_match(self):
        memory_db.insert_fact(
            "uuid-1", "Chief", "name", "Arno", "heisse Arno",
        )
        results = memory_db.search_facts_fts("xyz_not_present", player_uuid="uuid-1")
        self.assertEqual(len(results), 0)

    def test_search_facts_fts_filters_by_uuid(self):
        memory_db.insert_fact("uuid-A", "Chief", "name", "Arno", "heisse Arno")
        memory_db.insert_fact("uuid-B", "Chief", "name", "Berta", "heisse Berta")

        results = memory_db.search_facts_fts("heisse", player_uuid="uuid-A")
        self.assertEqual(len(results), 1)
        self.assertEqual(results[0]["fact_value"], "Arno")

    def test_search_facts_fts_excludes_deleted(self):
        fid = memory_db.insert_fact("uuid-1", "Chief", "name", "Arno", "heisse Arno")
        memory_db.update_fact(fid, is_deleted=1)

        results = memory_db.search_facts_fts("Arno", player_uuid="uuid-1")
        self.assertEqual(len(results), 0)

    # ------------------------------------------------------------------
    # Test 6: get_facts_for_player() chief-agnostic logic
    # ------------------------------------------------------------------
    def test_get_facts_includes_any_chief(self):
        # A fact with chief_name='any'
        conn = memory_db.get_connection()
        try:
            conn.execute(
                """INSERT INTO player_facts
                   (player_uuid, chief_name, fact_type, fact_value, evidence_text)
                   VALUES ('uuid-1', 'any', 'name', 'Arno', 'heisse Arno')"""
            )
            conn.commit()
        finally:
            conn.close()

        # Query with a specific chief should still find 'any' facts
        facts = memory_db.get_facts_for_player("uuid-1", "Grimward")
        self.assertEqual(len(facts), 1)
        self.assertEqual(facts[0]["chief_name"], "any")

    def test_get_facts_mixed_any_and_specific(self):
        memory_db.insert_fact("uuid-1", "any", "name", "Arno", "heisse Arno")
        memory_db.insert_fact("uuid-1", "ChiefA", "event", "Karotten geliefert", "brachte Karotten")

        facts = memory_db.get_facts_for_player("uuid-1", "ChiefA")
        self.assertEqual(len(facts), 2)
        chiefs = {f["chief_name"] for f in facts}
        self.assertIn("any", chiefs)
        self.assertIn("ChiefA", chiefs)

    def test_get_facts_without_chief_returns_all(self):
        memory_db.insert_fact("uuid-1", "ChiefA", "name", "Arno", "heisse Arno")
        memory_db.insert_fact("uuid-1", "ChiefB", "location", "Wald", "wohne im Wald")
        memory_db.insert_fact("uuid-1", "any", "preference", "Karotten", "mag Karotten")

        facts = memory_db.get_facts_for_player("uuid-1", chief_name=None)
        self.assertEqual(len(facts), 3)

    # ------------------------------------------------------------------
    # Test 7: delete_facts_for_player() isolation
    # ------------------------------------------------------------------
    def test_delete_facts_for_player_isolation(self):
        memory_db.insert_fact("uuid-A", "Chief", "name", "Arno", "heisse Arno")
        memory_db.insert_fact("uuid-A", "Chief", "location", "Wald", "im Wald")
        memory_db.insert_fact("uuid-B", "Chief", "name", "Berta", "heisse Berta")

        deleted = memory_db.delete_facts_for_player("uuid-A")
        self.assertEqual(deleted, 2)

        self.assertEqual(len(memory_db.get_facts_for_player("uuid-A", "Chief")), 0)
        self.assertEqual(len(memory_db.get_facts_for_player("uuid-B", "Chief")), 1)

    # ------------------------------------------------------------------
    # Test 8: FTS5 trigger sync (INSERT / UPDATE / DELETE)
    # ------------------------------------------------------------------
    def test_fts_trigger_sync_on_insert(self):
        memory_db.insert_fact("uuid-1", "Chief", "name", "Arno", "heisse Arno")
        # FTS search should find it immediately
        results = memory_db.search_facts_fts("Arno", player_uuid="uuid-1")
        self.assertEqual(len(results), 1)

    def test_fts_trigger_sync_on_update(self):
        fid = memory_db.insert_fact("uuid-1", "Chief", "name", "Arno", "heisse Arno")
        memory_db.update_fact(fid, fact_value="Arnold", evidence_text="heisse Arnold")

        results = memory_db.search_facts_fts("Arnold", player_uuid="uuid-1")
        self.assertEqual(len(results), 1)
        # Old value should no longer match
        results_old = memory_db.search_facts_fts("Arno", player_uuid="uuid-1")
        self.assertEqual(len(results_old), 0)

    def test_fts_trigger_sync_on_delete(self):
        memory_db.insert_fact("uuid-1", "Chief", "name", "Arno", "heisse Arno")
        memory_db.delete_facts_for_player("uuid-1")

        results = memory_db.search_facts_fts("Arno", player_uuid="uuid-1")
        self.assertEqual(len(results), 0)

    # ------------------------------------------------------------------
    # Test 9: migrate() adds player_facts table + columns
    # ------------------------------------------------------------------
    def test_migrate_adds_player_facts(self):
        # Drop player_facts + FTS to simulate legacy DB
        conn = memory_db.get_connection()
        try:
            conn.execute("DROP TABLE IF EXISTS player_facts")
            conn.execute("DROP TABLE IF EXISTS facts_fts")
            conn.commit()
        finally:
            conn.close()

        memory_db.migrate()

        conn = memory_db.get_connection()
        try:
            tables = conn.execute(
                "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"
            ).fetchall()
            table_names = {row[0] for row in tables}
            self.assertIn("player_facts", table_names)
            self.assertIn("facts_fts", table_names)

            cols = self._get_columns(conn, "player_facts")
            self.assertIn("embedding", cols)
            self.assertIn("confidence", cols)
            self.assertIn("is_deleted", cols)
        finally:
            conn.close()

    # ------------------------------------------------------------------
    # Test 10: search_facts_embedding() – basic stub
    # ------------------------------------------------------------------
    def test_search_facts_embedding_returns_empty_when_no_embedding(self):
        """Without real embedding data, search_facts_embedding returns []."""
        memory_db.insert_fact(
            "uuid-1", "Chief", "name", "Arno", "heisse Arno",
            # embedding=None (default)
        )
        # Since there is no embedding BLOB, the search yields nothing.
        results = memory_db.search_facts_embedding(
            "wie heisse ich", "uuid-1", "Chief", top_n=5, min_similarity=0.5
        )
        self.assertEqual(len(results), 0)

    def test_search_facts_embedding_no_facts(self):
        results = memory_db.search_facts_embedding(
            "wie heisse ich", "no-such-uuid", "NoChief"
        )
        self.assertEqual(len(results), 0)

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------
    @staticmethod
    def _get_columns(conn, table):
        cur = conn.execute(f"PRAGMA table_info({table})")
        return {row[1] for row in cur.fetchall()}


if __name__ == "__main__":
    unittest.main()