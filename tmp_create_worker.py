import os

worker_code = r'''
"""
Asynchronous worker queue for facts extraction.
Reads player messages from an internal queue and processes them in a daemon thread
so the synchronous reply path is never blocked.
"""
import logging
import threading
import time
from collections import deque
from datetime import datetime, timezone
from typing import Optional

logger = logging.getLogger("chief_ai_service.worker")


def _ts_utc() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds")


class FactsWorker:
    """Daemon-thread worker that drains a queue of player messages for facts analysis."""

    def __init__(self, max_retries: int = 3, maxlen: int = 100) -> None:
        self._queue: deque = deque(maxlen=maxlen)
        self._max_retries = max_retries
        self._lock = threading.Lock()
        self._thread: Optional[threading.Thread] = None
        self._running = False

    def start(self) -> None:
        """Ensure the worker thread is running (idempotent)."""
        with self._lock:
            if self._running:
                return
            self._running = True
            self._thread = threading.Thread(
                target=self._run_loop,
                daemon=True,
                name="facts-worker",
            )
            self._thread.start()
            logger.info("FactsWorker started ts=%s max_retries=%d", _ts_utc(), self._max_retries)

    def stop(self) -> None:
        """Gracefully ask the worker to stop (best-effort)."""
        with self._lock:
            self._running = False
        logger.info("FactsWorker stop requested ts=%s", _ts_utc())

    def enqueue(self, player_uuid: str, chief_name: str, player_message: str) -> None:
        """Drop a message into the worker queue. Never blocks.

        If the queue is full the oldest entry is silently discarded (deque maxlen).
        """
        entry = {
            "player_uuid": player_uuid,
            "chief_name": chief_name,
            "player_message": player_message,
            "retries": 0,
        }
        self._queue.append(entry)
        logger.debug(
            "FactsWorker enqueue ts=%s player_uuid=%s queue_depth=%d",
            _ts_utc(),
            player_uuid,
            len(self._queue),
        )
        if not self._running:
            self.start()

    @property
    def queue_depth(self) -> int:
        return len(self._queue)

    def _run_loop(self) -> None:
        logger.info("FactsWorker run-loop started ts=%s", _ts_utc())
        while self._running:
            try:
                self._drain()
            except Exception as exc:
                logger.error("FactsWorker unhandled error in drain: %s", exc)
            time.sleep(0.1)
        logger.info("FactsWorker run-loop finished ts=%s", _ts_utc())

    def _drain(self) -> None:
        try:
            entry = self._queue.popleft()
        except IndexError:
            return

        player_uuid = str(entry["player_uuid"])
        chief_name = str(entry["chief_name"])
        player_message = str(entry["player_message"])
        retries = int(entry["retries"])

        logger.debug(
            "FactsWorker processing ts=%s player_uuid=%s retry=%d",
            _ts_utc(),
            player_uuid,
            retries,
        )

        try:
            self._analyze_facts(player_uuid, chief_name, player_message)
            logger.info(
                "FactsWorker OK ts=%s player_uuid=%s queue_depth=%d",
                _ts_utc(),
                player_uuid,
                len(self._queue),
            )
        except Exception as exc:
            retries += 1
            if retries < self._max_retries:
                entry["retries"] = retries
                self._queue.append(entry)
                logger.warning(
                    "FactsWorker retry ts=%s player_uuid=%s retries=%d/%d error=%s queue_depth=%d",
                    _ts_utc(),
                    player_uuid,
                    retries,
                    self._max_retries,
                    exc,
                    len(self._queue),
                )
            else:
                logger.error(
                    "FactsWorker discard ts=%s player_uuid=%s after %d retries error=%s",
                    _ts_utc(),
                    player_uuid,
                    retries,
                    exc,
                )

    def _analyze_facts(self, player_uuid: str, chief_name: str, player_message: str) -> None:
        """Stub for the facts analysis pipeline (Paket C).

        This method is intentionally left as a no-op for now.
        Future tickets will connect it to the qwen2.5:3b model and
        store extracted facts in the database.
        """
        logger.debug(
            "FactsWorker _analyze_facts stub ts=%s player_uuid=%s message_len=%d",
            _ts_utc(),
            player_uuid,
            len(player_message),
        )
        # TODO: implement in Paket C
'''

target = os.path.join("chief-ai-service", "chief_ai_service", "worker.py")
with open(target, "w", encoding="utf-8") as f:
    f.write(worker_code)
print(f"Written {len(worker_code)} chars to {target}")