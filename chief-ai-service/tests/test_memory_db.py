"""Unit tests for memory_db.py.

Covers:
- create_tables() schema
- insert_turn() / query_turns() roundtrip
- insert_summary() / get_latest_summary() roundtrip
- update_embedding() BLOB storage
- delete_turns_for_player() isolation
- migrate() missing-column addition
"""

import unittest
import sqlite3
import os
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import memory_db


class MemoryDbTests(unittest.TestCase):
    """Tests against a temporary file-based SQLite DB.

    The module's DB_PATH is patched to a temp file so connections
    survive close() and behave exactly like production.
    """

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
            conn.execute("DROP TABLE IF EXISTS conversation_turns")
            conn.execute("DROP TABLE IF EXISTS memory_summaries")
            conn.commit()
        finally:
            conn.close()

    # ------------------------------------------------------------------
    # Test 1: create_tables() schema
    # ------------------------------------------------------------------
    def test_create_tables_produces_correct_schema(self):
        conn = memory_db.get_connection()
        try:
            # Verify both tables exist
            tables = conn.execute(
                "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"
            ).fetchall()
            table_names = {row[0] for row in tables}
            self.assertIn("conversation_turns", table_names)
            self.assertIn("memory_summaries", table_names)

            # Verify conversation_turns columns
            cols_turns = self._get_columns(conn, "conversation_turns")
            expected_turns = {
                "id", "player_uuid", "chief_name", "role", "message",
                "embedding", "mc_day", "mc_time", "is_archived", "created_at",
            }
            self.assertEqual(expected_turns, cols_turns)

            # Verify memory_summaries columns
            cols_summ = self._get_columns(conn, "memory_summaries")
            expected_summ = {
                "id", "player_uuid", "chief_name", "summary_text",
                "turn_range_start", "turn_range_end",
                "reputation_at_summary", "embedding", "created_at",
            }
            self.assertEqual(expected_summ, cols_summ)

            # Verify role CHECK constraint rejects bad values
            with self.assertRaises(sqlite3.IntegrityError):
                conn.execute(
                    "INSERT INTO conversation_turns"
                    " (player_uuid, chief_name, role, message)"
                    " VALUES (?, ?, ?, ?)",
                    ("uuid-1", "ChiefA", "invalid_role", "test"),
                )
        finally:
            conn.close()

    # ------------------------------------------------------------------
    # Test 2: insert_turn() + query_turns() roundtrip
    # ------------------------------------------------------------------
    def test_insert_and_query_turn_roundtrip(self):
        tid = memory_db.insert_turn(
            player_uuid="uuid-1",
            chief_name="Elder",
            role="player",
            message="Hello, Chief!",
            mc_day=42,
            mc_time=13000,
        )
        self.assertIsInstance(tid, int)
        self.assertGreater(tid, 0)

        turns = memory_db.query_turns("uuid-1", "Elder")
        self.assertEqual(len(turns), 1)

        turn = turns[0]
        self.assertEqual(turn["id"], tid)
        self.assertEqual(turn["player_uuid"], "uuid-1")
        self.assertEqual(turn["chief_name"], "Elder")
        self.assertEqual(turn["role"], "player")
        self.assertEqual(turn["message"], "Hello, Chief!")
        self.assertEqual(turn["mc_day"], 42)
        self.assertEqual(turn["mc_time"], 13000)
        self.assertIsNone(turn["embedding"])
        self.assertEqual(turn["is_archived"], 0)
        self.assertIsNotNone(turn["created_at"])

    def test_query_turns_excludes_archived_by_default(self):
        memory_db.insert_turn("uuid-1", "Chief", "player", "msg1")
        memory_db.insert_turn("uuid-1", "Chief", "player", "msg2")

        # Manually archive the first turn
        conn = memory_db.get_connection()
        try:
            conn.execute(
                "UPDATE conversation_turns SET is_archived=1 WHERE id=1"
            )
            conn.commit()
        finally:
            conn.close()

        turns = memory_db.query_turns("uuid-1", "Chief")
        self.assertEqual(len(turns), 1)
        self.assertEqual(turns[0]["id"], 2)

    def test_query_turns_include_archived_flag(self):
        memory_db.insert_turn("uuid-1", "Chief", "player", "msg1")
        conn = memory_db.get_connection()
        try:
            conn.execute(
                "UPDATE conversation_turns SET is_archived=1 WHERE id=1"
            )
            conn.commit()
        finally:
            conn.close()

        turns = memory_db.query_turns(
            "uuid-1", "Chief", include_archived=True
        )
        self.assertEqual(len(turns), 1)
        self.assertEqual(turns[0]["is_archived"], 1)

    def test_query_turns_limit_and_offset(self):
        for i in range(5):
            memory_db.insert_turn("uuid-1", "Chief", "player", f"msg{i}")

        turns = memory_db.query_turns("uuid-1", "Chief", limit=2)
        self.assertEqual(len(turns), 2)
        # Newest first -> id 5 and 4
        self.assertEqual(turns[0]["id"], 5)
        self.assertEqual(turns[1]["id"], 4)

        turns_page2 = memory_db.query_turns(
            "uuid-1", "Chief", limit=2, offset=2
        )
        self.assertEqual(len(turns_page2), 2)
        self.assertEqual(turns_page2[0]["id"], 3)
        self.assertEqual(turns_page2[1]["id"], 2)

    # ------------------------------------------------------------------
    # Test 3: insert_summary() + get_latest_summary() roundtrip
    # ------------------------------------------------------------------
    def test_insert_and_get_latest_summary(self):
        sid1 = memory_db.insert_summary(
            player_uuid="uuid-1",
            chief_name="Elder",
            summary_text="First summary.",
            turn_range_start=1,
            turn_range_end=10,
            reputation=50,
        )
        sid2 = memory_db.insert_summary(
            player_uuid="uuid-1",
            chief_name="Elder",
            summary_text="Second summary.",
            turn_range_start=11,
            turn_range_end=20,
            reputation=75,
        )
        self.assertGreater(sid2, sid1)

        latest = memory_db.get_latest_summary("uuid-1", "Elder")
        self.assertIsNotNone(latest)
        self.assertEqual(latest["id"], sid2)
        self.assertEqual(latest["summary_text"], "Second summary.")
        self.assertEqual(latest["turn_range_start"], 11)
        self.assertEqual(latest["turn_range_end"], 20)
        self.assertEqual(latest["reputation_at_summary"], 75)
        self.assertIsNotNone(latest["created_at"])

    def test_get_latest_summary_returns_none_when_empty(self):
        result = memory_db.get_latest_summary("no-such-uuid", "NoChief")
        self.assertIsNone(result)

    # ------------------------------------------------------------------
    # Test 4: update_embedding() – BLOB roundtrip
    # ------------------------------------------------------------------
    def test_update_embedding_blob_roundtrip(self):
        tid = memory_db.insert_turn(
            player_uuid="uuid-1",
            chief_name="Chief",
            role="chief",
            message="A response",
        )

        blob = bytes([0x01, 0x02, 0x03, 0xFF, 0xFE, 0xFD])
        memory_db.update_embedding(tid, blob)

        turns = memory_db.query_turns("uuid-1", "Chief")
        self.assertEqual(len(turns), 1)
        self.assertEqual(turns[0]["embedding"], blob)

    def test_update_embedding_overwrite(self):
        tid = memory_db.insert_turn("uuid-1", "Chief", "player", "msg")
        memory_db.update_embedding(tid, b"original")
        memory_db.update_embedding(tid, b"overwritten")

        turns = memory_db.query_turns("uuid-1", "Chief")
        self.assertEqual(turns[0]["embedding"], b"overwritten")

    # ------------------------------------------------------------------
    # Test 5: delete_turns_for_player() – isolation
    # ------------------------------------------------------------------
    def test_delete_turns_for_player_isolation(self):
        # Insert turns for two different players
        memory_db.insert_turn("uuid-A", "Chief", "player", "A1")
        memory_db.insert_turn("uuid-A", "Chief", "player", "A2")
        memory_db.insert_turn("uuid-B", "Chief", "player", "B1")

        deleted = memory_db.delete_turns_for_player("uuid-A")
        self.assertEqual(deleted, 2)

        # uuid-A should be empty
        turns_a = memory_db.query_turns("uuid-A", "Chief")
        self.assertEqual(len(turns_a), 0)

        # uuid-B should still have its turn
        turns_b = memory_db.query_turns("uuid-B", "Chief")
        self.assertEqual(len(turns_b), 1)
        self.assertEqual(turns_b[0]["player_uuid"], "uuid-B")

    # ------------------------------------------------------------------
    # Test 6: migrate() – adds missing columns
    # ------------------------------------------------------------------
    def test_migrate_adds_missing_columns(self):
        # Drop existing tables and create a legacy schema manually
        conn = memory_db.get_connection()
        try:
            conn.execute("DROP TABLE IF EXISTS conversation_turns")
            conn.execute("DROP TABLE IF EXISTS memory_summaries")
            # Legacy table: missing embedding, mc_day, mc_time, is_archived
            conn.execute("""
                CREATE TABLE conversation_turns (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    chief_name TEXT NOT NULL,
                    role TEXT NOT NULL,
                    message TEXT NOT NULL,
                    created_at TEXT DEFAULT (datetime('now'))
                )
            """)
            conn.execute("""
                CREATE TABLE memory_summaries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    chief_name TEXT NOT NULL,
                    summary_text TEXT NOT NULL
                )
            """)
            conn.commit()
        finally:
            conn.close()

        # Insert legacy data
        conn = memory_db.get_connection()
        try:
            conn.execute(
                "INSERT INTO conversation_turns"
                " (player_uuid, chief_name, role, message)"
                " VALUES (?, ?, ?, ?)",
                ("old-uuid", "OldChief", "player", "legacy message"),
            )
            conn.execute(
                "INSERT INTO memory_summaries"
                " (player_uuid, chief_name, summary_text)"
                " VALUES (?, ?, ?)",
                ("old-uuid", "OldChief", "legacy summary"),
            )
            conn.commit()
        finally:
            conn.close()

        # Run migration
        memory_db.migrate()

        # Verify all expected columns now exist
        conn = memory_db.get_connection()
        try:
            turns_cols = self._get_columns(conn, "conversation_turns")
            expected_turns = {
                "id", "player_uuid", "chief_name", "role", "message",
                "embedding", "mc_day", "mc_time", "is_archived", "created_at",
            }
            self.assertEqual(expected_turns, turns_cols)

            summ_cols = self._get_columns(conn, "memory_summaries")
            expected_summ = {
                "id", "player_uuid", "chief_name", "summary_text",
                "turn_range_start", "turn_range_end",
                "reputation_at_summary", "embedding", "created_at",
            }
            self.assertEqual(expected_summ, summ_cols)

            # Legacy data preserved and new columns have defaults
            row = conn.execute(
                "SELECT * FROM conversation_turns WHERE id=1"
            ).fetchone()
            self.assertEqual(row["player_uuid"], "old-uuid")
            self.assertEqual(row["message"], "legacy message")
            self.assertEqual(row["mc_day"], 0)
            self.assertEqual(row["mc_time"], 0)
            self.assertEqual(row["is_archived"], 0)
            self.assertIsNone(row["embedding"])

            row_s = conn.execute(
                "SELECT * FROM memory_summaries WHERE id=1"
            ).fetchone()
            self.assertEqual(row_s["player_uuid"], "old-uuid")
            self.assertEqual(row_s["summary_text"], "legacy summary")
            self.assertIsNone(row_s["turn_range_start"])
            self.assertEqual(row_s["reputation_at_summary"], 0)
            self.assertIsNone(row_s["embedding"])
        finally:
            conn.close()

    def test_migrate_is_idempotent(self):
        # Running migrate twice should not raise errors
        memory_db.migrate()
        memory_db.migrate()

        # Schema should still be valid
        conn = memory_db.get_connection()
        try:
            turns_cols = self._get_columns(conn, "conversation_turns")
            self.assertIn("mc_day", turns_cols)
            self.assertIn("mc_time", turns_cols)
            self.assertIn("is_archived", turns_cols)
        finally:
            conn.close()

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------
    @staticmethod
    def _get_columns(conn, table):
        cur = conn.execute(f"PRAGMA table_info({table})")
        return {row[1] for row in cur.fetchall()}


if __name__ == "__main__":
    unittest.main()