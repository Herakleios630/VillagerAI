"""
Integration test: DELETE /v1/chief/forget endpoint.

Covers:
- DELETE on empty DB -> HTTP 404
- DELETE with 5 turns + summaries -> HTTP 204, all data removed
- DELETE again on emptied DB -> HTTP 404
- DELETE without player_uuid -> HTTP 400
- Verification that only the specified player's data is removed (no cross-player impact)
"""

import unittest
import os
import sys
import tempfile
import threading
import time
import json
from http.server import ThreadingHTTPServer
from urllib.parse import urlencode
from urllib.request import Request, urlopen
from urllib.error import HTTPError

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import memory_db


# ---------------------------------------------------------------------------
# Helpers: start/stop test server on random port
# ---------------------------------------------------------------------------

def _start_test_server(handler_class):
    """Start a ThreadingHTTPServer on a random port and return (server, base_url)."""
    server = ThreadingHTTPServer(("127.0.0.1", 0), handler_class)
    port = server.server_address[1]
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server, f"http://127.0.0.1:{port}"


def _stop_test_server(server):
    server.shutdown()
    server.server_close()


# ---------------------------------------------------------------------------
# Test handler that routes to ChiefAIHandler.do_DELETE
# ---------------------------------------------------------------------------

class _ForgetTestHandler:
    """Handler factory: sets up ChiefAIHandler with a temp DB for tests."""

    @staticmethod
    def create():
        from chief_ai_service.http_app import ChiefAIHandler
        # Override config to avoid real config dependency
        ChiefAIHandler.config = {"provider": "dummy"}
        return ChiefAIHandler


# ---------------------------------------------------------------------------
# Test case with temp DB (per-method isolation)
# ---------------------------------------------------------------------------

class ForgetIntegrationTest(unittest.TestCase):
    """Integration tests for DELETE /v1/chief/forget."""

    PLAYER_UUID = "00000000-0000-0000-0000-000000000001"
    OTHER_PLAYER = "00000000-0000-0000-0000-000000000002"
    CHIEF_NAME = "ChiefTest"

    _orig_db_path = memory_db.DB_PATH

    @classmethod
    def setUpClass(cls):
        # Start test server once for all tests in this class
        handler_cls = _ForgetTestHandler.create()
        cls._server, cls._base_url = _start_test_server(handler_cls)

    @classmethod
    def tearDownClass(cls):
        _stop_test_server(cls._server)

    def setUp(self):
        # Each test gets a fresh temp DB
        tmp = tempfile.NamedTemporaryFile(suffix=".db", delete=False)
        tmp.close()
        self._tmpfile = tmp.name
        memory_db.DB_PATH = self._tmpfile
        memory_db.create_tables()

    def tearDown(self):
        memory_db.DB_PATH = self._orig_db_path
        if self._tmpfile and os.path.exists(self._tmpfile):
            os.unlink(self._tmpfile)

    # -----------------------------------------------------------------------
    # Test 1: DELETE on empty DB -> HTTP 404
    # -----------------------------------------------------------------------

    def test_forget_on_empty_db_returns_404(self):
        """DELETE /v1/chief/forget on an empty database must return 404."""
        url = f"{self._base_url}/v1/chief/forget?player_uuid={self.PLAYER_UUID}"
        req = Request(url, method="DELETE")

        try:
            resp = urlopen(req)
            self.fail(f"Expected HTTPError 404, got {resp.status}")
        except HTTPError as e:
            self.assertEqual(e.code, 404, "Empty DB must return 404")
            body = json.loads(e.read().decode("utf-8"))
            self.assertIn("error", body)
            self.assertEqual(body["error"], "no_entries")
            self.assertIn("message", body)
            self.assertIn(self.PLAYER_UUID, body["message"])

    # -----------------------------------------------------------------------
    # Test 2: 5 turns + summary, DELETE -> HTTP 204, all data removed
    # -----------------------------------------------------------------------

    def test_forget_deletes_all_turns_and_summaries(self):
        """Insert 5 turns + 1 summary, DELETE must return 204 and clear all data."""
        # Insert 5 turns
        turn_ids = []
        for i in range(5):
            role = "player" if i % 2 == 0 else "chief"
            tid = memory_db.insert_turn(
                player_uuid=self.PLAYER_UUID,
                chief_name=self.CHIEF_NAME,
                role=role,
                message=f"Test message {i}",
                mc_day=1,
                mc_time=6000 + i * 100,
            )
            turn_ids.append(tid)

        # Insert a summary
        memory_db.insert_summary(
            player_uuid=self.PLAYER_UUID,
            chief_name=self.CHIEF_NAME,
            summary_text="Test summary covering 5 turns.",
            turn_range_start=turn_ids[0],
            turn_range_end=turn_ids[-1],
            reputation=10,
        )

        # Verify data is present before DELETE
        turns_before = memory_db.query_turns(self.PLAYER_UUID, self.CHIEF_NAME, limit=20)
        self.assertEqual(len(turns_before), 5, "Should have 5 turns before DELETE")
        summary_before = memory_db.get_latest_summary(self.PLAYER_UUID, self.CHIEF_NAME)
        self.assertIsNotNone(summary_before, "Should have a summary before DELETE")

        # Send DELETE
        url = f"{self._base_url}/v1/chief/forget?player_uuid={self.PLAYER_UUID}"
        req = Request(url, method="DELETE")
        resp = urlopen(req)

        self.assertEqual(resp.status, 204, "Successful deletion must return 204")

        # Verify all turns and summaries are gone
        turns_after = memory_db.query_turns(self.PLAYER_UUID, self.CHIEF_NAME, limit=20)
        self.assertEqual(len(turns_after), 0, "All turns must be deleted")
        summary_after = memory_db.get_latest_summary(self.PLAYER_UUID, self.CHIEF_NAME)
        self.assertIsNone(summary_after, "All summaries must be deleted")

    # -----------------------------------------------------------------------
    # Test 3: No cross-player impact
    # -----------------------------------------------------------------------

    def test_forget_only_deletes_specified_player(self):
        """DELETE for one player must not affect another player's data."""
        # Insert turns for PLAYER_UUID
        for i in range(3):
            memory_db.insert_turn(
                player_uuid=self.PLAYER_UUID,
                chief_name=self.CHIEF_NAME,
                role="player" if i % 2 == 0 else "chief",
                message=f"Player1 message {i}",
            )

        # Insert turns for OTHER_PLAYER
        for i in range(3):
            memory_db.insert_turn(
                player_uuid=self.OTHER_PLAYER,
                chief_name=self.CHIEF_NAME,
                role="player" if i % 2 == 0 else "chief",
                message=f"Player2 message {i}",
            )

        # Verify both exist
        self.assertEqual(
            len(memory_db.query_turns(self.PLAYER_UUID, self.CHIEF_NAME, limit=10)),
            3
        )
        self.assertEqual(
            len(memory_db.query_turns(self.OTHER_PLAYER, self.CHIEF_NAME, limit=10)),
            3
        )

        # DELETE only PLAYER_UUID
        url = f"{self._base_url}/v1/chief/forget?player_uuid={self.PLAYER_UUID}"
        resp = urlopen(Request(url, method="DELETE"))
        self.assertEqual(resp.status, 204)

        # PLAYER_UUID is empty, OTHER_PLAYER still has 3 turns
        self.assertEqual(
            len(memory_db.query_turns(self.PLAYER_UUID, self.CHIEF_NAME, limit=10)),
            0
        )
        self.assertEqual(
            len(memory_db.query_turns(self.OTHER_PLAYER, self.CHIEF_NAME, limit=10)),
            3
        )

    # -----------------------------------------------------------------------
    # Test 4: DELETE again on already emptied DB -> HTTP 404
    # -----------------------------------------------------------------------

    def test_forget_twice_returns_404_on_second_call(self):
        """First DELETE = 204, second DELETE on same empty DB = 404."""
        # Insert 1 turn
        memory_db.insert_turn(
            player_uuid=self.PLAYER_UUID,
            chief_name=self.CHIEF_NAME,
            role="player",
            message="Single message",
        )

        # First DELETE -> 204
        url = f"{self._base_url}/v1/chief/forget?player_uuid={self.PLAYER_UUID}"
        resp1 = urlopen(Request(url, method="DELETE"))
        self.assertEqual(resp1.status, 204)

        # Second DELETE -> 404
        try:
            urlopen(Request(url, method="DELETE"))
            self.fail("Second DELETE on empty DB must raise HTTPError 404")
        except HTTPError as e:
            self.assertEqual(e.code, 404)
            body = json.loads(e.read().decode("utf-8"))
            self.assertEqual(body["error"], "no_entries")

    # -----------------------------------------------------------------------
    # Test 5: DELETE without player_uuid -> HTTP 400
    # -----------------------------------------------------------------------

    def test_forget_missing_player_uuid_returns_400(self):
        """DELETE /v1/chief/forget without player_uuid parameter must return 400."""
        url = f"{self._base_url}/v1/chief/forget"
        req = Request(url, method="DELETE")

        try:
            resp = urlopen(req)
            self.fail(f"Expected HTTPError 400, got {resp.status}")
        except HTTPError as e:
            self.assertEqual(e.code, 400, "Missing player_uuid must return 400")
            body = json.loads(e.read().decode("utf-8"))
            self.assertIn("error", body)
            self.assertEqual(body["error"], "missing_player_uuid")

    # -----------------------------------------------------------------------
    # Test 6: DELETE with empty player_uuid -> HTTP 400
    # -----------------------------------------------------------------------

    def test_forget_empty_player_uuid_returns_400(self):
        """DELETE /v1/chief/forget?player_uuid= must return 400."""
        url = f"{self._base_url}/v1/chief/forget?player_uuid="
        req = Request(url, method="DELETE")

        try:
            resp = urlopen(req)
            self.fail(f"Expected HTTPError 400, got {resp.status}")
        except HTTPError as e:
            self.assertEqual(e.code, 400, "Empty player_uuid must return 400")
            body = json.loads(e.read().decode("utf-8"))
            self.assertIn("error", body)
            self.assertEqual(body["error"], "missing_player_uuid")


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    unittest.main(verbosity=2)