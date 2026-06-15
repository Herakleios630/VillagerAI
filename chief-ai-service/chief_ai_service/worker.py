"""
Asynchronous worker queue for facts extraction.
Reads player messages from an internal queue and processes them in a daemon thread
so the synchronous reply path is never blocked.

Pipeline (Paket C2):
  1. Intent-Classifier via qwen2.5:3b
  2. Fakten-Extraktion (wenn has_new_facts)
  3. Embedding-Deduplizierung (Cosinus + qwen-Dedup-Entscheider)
  4. player_facts INSERT/UPDATE
  5. Fallback-Regex bei qwen-Fehlern
"""
import json
import logging
import os as _os
import re
import threading
import time
from collections import deque
from datetime import datetime, timezone
from typing import Optional

logger = logging.getLogger("chief_ai_service.worker")


_FALLBACK_MEMORY_TRIGGER_RE = re.compile(
    r"name|erinner|weisst\s+du\s+noch|fruher|letztes\s+mal|damals|vergessen|"
    r"gestern|vorgestern|neulich|beim\s+letzten\s+Mal|kennst\s+du\s+mich|"
    r"weisst\s+du\s+wer\s+ich\s+bin",
    re.IGNORECASE,
)

_PROMPTS_DIR_DEFAULT = _os.path.join(_os.path.dirname(_os.path.abspath(__file__)), "..", "prompts")


def _ts_utc() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds")


def _load_prompt(filename: str) -> str:
    path = _os.path.join(_PROMPTS_DIR_DEFAULT, filename)
    try:
        with open(path, "r", encoding="utf-8") as fh:
            return fh.read()
    except Exception as exc:
        logger.warning("Worker cannot load prompt %s: %s", filename, exc)
        return ""


class FactsWorker:
    """Daemon-thread worker that drains a queue of player messages for facts analysis."""

    def __init__(self, max_retries: int = 3, maxlen: int = 100, config: dict | None = None) -> None:
        self._queue: deque = deque(maxlen=maxlen)
        self._max_retries = max_retries
        self._config = config
        self._lock = threading.Lock()
        self._thread: Optional[threading.Thread] = None
        self._running = False

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------
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

    # ------------------------------------------------------------------
    # Internal
    # ------------------------------------------------------------------
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

    # ------------------------------------------------------------------
    # Pipeline: Intent -> Extraction -> Dedup -> Store
    # ------------------------------------------------------------------
    def _analyze_facts(self, player_uuid: str, chief_name: str, player_message: str) -> None:
        """Full facts analysis pipeline for a single player message."""
        logger.debug(
            "FactsWorker _analyze_facts ts=%s player_uuid=%s message_len=%d message='%s'",
            _ts_utc(),
            player_uuid,
            len(player_message),
            (player_message or "")[:120].replace("\n", " "),
        )

        # Step 1: Intent classification
        intent_result = self._classify_intent(player_message)
        if intent_result is None:
            logger.debug("FactsWorker intent classification returned None, skipping")
            return

        has_new_facts = bool(intent_result.get("has_new_facts", False))
        new_facts = intent_result.get("new_facts", []) if isinstance(intent_result.get("new_facts"), list) else []
        seeks_facts = bool(intent_result.get("seeks_facts", False))
        query_text = str(intent_result.get("query_text", "") or "")

        logger.debug(
            "FactsWorker intent result: has_new_facts=%s new_facts_count=%d seeks_facts=%s query_text='%s'",
            has_new_facts,
            len(new_facts),
            seeks_facts,
            query_text[:120] if query_text else "",
        )

        # Step 2: Extract facts if intent says so
        extracted_facts: list[dict] = []
        if has_new_facts:
            # Try structured extraction via qwen
            ext_result = self._extract_facts(player_message)
            if ext_result is not None and isinstance(ext_result.get("facts"), list):
                extracted_facts = ext_result["facts"]
            else:
                # Fallback: use intent-provided facts
                extracted_facts = new_facts

        logger.debug(
            "FactsWorker extracted %d facts for player_uuid=%s",
            len(extracted_facts),
            player_uuid,
        )

        # Step 3: Deduplicate and store each fact
        stored_count = 0
        for fact in extracted_facts:
            if not isinstance(fact, dict):
                continue
            fact_type = str(fact.get("type", "")).strip().lower()
            fact_value = str(fact.get("value", "")).strip()
            importance = float(fact.get("importance", 0.5))
            if not fact_type or not fact_value:
                continue
            if self._dedup_and_store(
                player_uuid=player_uuid,
                chief_name=chief_name,
                fact_type=fact_type,
                fact_value=fact_value,
                evidence_text=player_message,
                importance=importance,
            ):
                stored_count += 1

        logger.info(
            "FactsWorker stored %d new/updated facts for player_uuid=%s",
            stored_count,
            player_uuid,
        )

        # Step 4: If player seeks facts, mark for pending retrieval (task D)
        if seeks_facts and query_text:
            self._mark_pending_retrieval(player_uuid, chief_name, query_text)

    # ------------------------------------------------------------------
    # Intent Classification
    # ------------------------------------------------------------------
    def _classify_intent(self, player_message: str) -> Optional[dict]:
        """Run intent classification via qwen2.5:3b. Returns dict or None on failure."""
        from .qwen_client import send_prompt

        prompt_template = _load_prompt("intent_prompt.txt")
        if not prompt_template:
            logger.warning("FactsWorker intent_prompt.txt empty or missing, using fallback regex")
            return self._classify_intent_fallback(player_message)

        prompt_text = prompt_template.replace("{message}", player_message)

        try:
            result = send_prompt(prompt_text, model="qwen2.5:3b")
        except Exception as exc:
            logger.warning("FactsWorker intent qwen call failed: %s", exc)
            return self._classify_intent_fallback(player_message)

        if not isinstance(result, dict):
            logger.warning("FactsWorker intent qwen returned non-dict type=%s", type(result).__name__)
            return self._classify_intent_fallback(player_message)

        if result.get("error"):
            logger.warning("FactsWorker intent qwen returned error: %s", str(result.get("raw_response", ""))[:200])
            return self._classify_intent_fallback(player_message)

        return result

    def _classify_intent_fallback(self, player_message: str) -> Optional[dict]:
        """Regex-based fallback when qwen is unreachable."""
        triggered = bool(_FALLBACK_MEMORY_TRIGGER_RE.search(player_message))
        logger.debug(
            "FactsWorker intent fallback: triggered=%s message='%s'",
            triggered,
            player_message[:120],
        )
        return {
            "has_new_facts": triggered,
            "new_facts": [],
            "seeks_facts": triggered,
            "query_text": player_message if triggered else "",
            "_fallback": True,
        }

    # ------------------------------------------------------------------
    # Fact Extraction
    # ------------------------------------------------------------------
    def _extract_facts(self, player_message: str) -> Optional[dict]:
        """Run fact extraction via qwen2.5:3b. Returns dict or None on failure."""
        from .qwen_client import send_prompt

        prompt_template = _load_prompt("extraction_prompt.txt")
        if not prompt_template:
            logger.warning("FactsWorker extraction_prompt.txt empty or missing")
            return None

        prompt_text = prompt_template.replace("{message}", player_message)

        try:
            result = send_prompt(prompt_text, model="qwen2.5:3b")
        except Exception as exc:
            logger.warning("FactsWorker extraction qwen call failed: %s", exc)
            return None

        if not isinstance(result, dict):
            logger.warning("FactsWorker extraction qwen returned non-dict type=%s", type(result).__name__)
            return None

        if result.get("error"):
            logger.warning("FactsWorker extraction qwen returned error: %s", str(result.get("raw_response", ""))[:200])
            return None

        return result

    # ------------------------------------------------------------------
    # Deduplication + Store
    # ------------------------------------------------------------------
    def _dedup_and_store(
        self,
        player_uuid: str,
        chief_name: str,
        fact_type: str,
        fact_value: str,
        evidence_text: str,
        importance: float = 0.5,
    ) -> bool:
        """Check for duplicates via embedding similarity, then INSERT or UPDATE.

        Returns True if a new fact was stored or an existing one updated.
        """
        try:
            import sys
            _parent = _os.path.dirname(_os.path.dirname(_os.path.abspath(__file__)))
            if _parent not in sys.path:
                sys.path.insert(0, _parent)
            from memory_db import query_facts_by_type, insert_fact, update_fact
            from chief_ai_service.embedding_client import get_embedding, pack_embedding, unpack_embedding, cosine_similarity
        except ImportError:
            # Fallback when running from project root with chief_ai_service as package
            from chief_ai_service.embedding_client import get_embedding, pack_embedding, unpack_embedding, cosine_similarity  # type: ignore[no-redef]
            from memory_db import query_facts_by_type, insert_fact, update_fact  # type: ignore[no-redef]

        # Generate embedding for the candidate fact
        combined_text = f"{fact_type}: {fact_value}"
        try:
            candidate_emb = get_embedding(combined_text)
        except Exception as exc:
            logger.warning("FactsWorker dedup embedding failed: %s", exc)
            candidate_emb = None

        # Fetch existing facts of same type
        existing_facts = query_facts_by_type(player_uuid, chief_name, fact_type)

        # Dedup check
        best_similarity = 0.0
        best_match: Optional[dict] = None
        if candidate_emb is not None and existing_facts:
            for existing in existing_facts:
                blob = existing.get("embedding")
                if not blob:
                    continue
                try:
                    existing_emb = unpack_embedding(blob)
                    sim = cosine_similarity(candidate_emb, existing_emb)
                    if sim > best_similarity:
                        best_similarity = sim
                        best_match = existing
                except Exception:
                    continue

        logger.debug(
            "FactsWorker dedup: type=%s value='%s' best_sim=%.4f",
            fact_type,
            fact_value[:60],
            best_similarity,
        )

        # Decision logic: thresholds from config (or defaults 0.70/0.85)
        facts_cfg = (self._config or {}).get("facts", {}) if isinstance(self._config, dict) else {}
        dedup_high = float(facts_cfg.get("dedup_similarity_threshold", 0.85))
        dedup_low = float(facts_cfg.get("dedup_ask_model_threshold_min", 0.70))
        if best_similarity < dedup_low:
            # Clearly new -> INSERT
            action = "insert"
        elif best_similarity >= dedup_high:
            # Clearly duplicate -> UPDATE (confirm)
            action = "update"
        else:
            # Ambiguous (dedup_low-dedup_high) -> ask qwen dedup decider
            is_duplicate = self._ask_dedup_decider(
                existing_fact=best_match,
                candidate_type=fact_type,
                candidate_value=fact_value,
                candidate_evidence=evidence_text,
            )
            action = "update" if is_duplicate else "insert"

        # Execute
        try:
            if action == "insert":
                emb_blob = pack_embedding(candidate_emb) if candidate_emb is not None else None
                fact_id = insert_fact(
                    player_uuid=player_uuid,
                    chief_name=chief_name,
                    fact_type=fact_type,
                    fact_value=fact_value,
                    evidence_text=evidence_text,
                    embedding=emb_blob,
                    importance=importance,
                )
                logger.info(
                    "FactsWorker INSERT fact id=%s type=%s value='%s' player_uuid=%s",
                    fact_id,
                    fact_type,
                    fact_value[:60],
                    player_uuid,
                )
                return True
            else:
                if best_match is not None:
                    update_fact(
                        fact_id=best_match["id"],
                        fact_value=fact_value,
                        evidence_text=evidence_text,
                        embedding=pack_embedding(candidate_emb) if candidate_emb is not None else None,
                        times_confirmed=int(best_match.get("times_confirmed", 1)) + 1,
                    )
                    logger.info(
                        "FactsWorker UPDATE fact id=%s type=%s value='%s' player_uuid=%s",
                        best_match["id"],
                        fact_type,
                        fact_value[:60],
                        player_uuid,
                    )
                    return True
        except Exception as exc:
            logger.error("FactsWorker store failed: %s", exc)

        return False

    def _ask_dedup_decider(
        self,
        existing_fact: Optional[dict],
        candidate_type: str,
        candidate_value: str,
        candidate_evidence: str,
    ) -> bool:
        """Ask qwen dedup decider whether two facts are identical.

        Returns True if they are duplicates.
        """
        if existing_fact is None:
            return False

        from .qwen_client import send_prompt

        prompt_template = _load_prompt("dedup_prompt.txt")
        if not prompt_template:
            logger.warning("FactsWorker dedup_prompt.txt empty, using conservative decision")
            # Conservative: treat as duplicate if any existing match exists
            return True

        prompt_text = (
            prompt_template
            .replace("{type_a}", candidate_type)
            .replace("{value_a}", candidate_value)
            .replace("{evidence_a}", candidate_evidence)
            .replace("{type_b}", str(existing_fact.get("fact_type", "")))
            .replace("{value_b}", str(existing_fact.get("fact_value", "")))
            .replace("{evidence_b}", str(existing_fact.get("evidence_text", "")))
        )

        try:
            result = send_prompt(prompt_text, model="qwen2.5:3b")
        except Exception as exc:
            logger.warning("FactsWorker dedup qwen call failed: %s", exc)
            return True  # conservative

        if not isinstance(result, dict):
            logger.warning("FactsWorker dedup qwen returned non-dict type=%s", type(result).__name__)
            return True  # conservative

        if result.get("error"):
            logger.warning("FactsWorker dedup qwen error: %s", str(result.get("raw_response", ""))[:200])
            return True

        # The dedup prompt asks for "ja" or "nein"
        raw_text = str(result.get("response", "") or "").strip().lower()
        # Also check direct JSON fields if qwen returned structured
        for key in ("decision", "answer", "duplicate"):
            val = result.get(key)
            if val is not None:
                raw_text = str(val).strip().lower()
                break

        is_duplicate = raw_text.startswith("ja") or raw_text == "ja"
        logger.debug(
            "FactsWorker dedup decider: type=%s candidate='%s' existing='%s' -> %s",
            candidate_type,
            candidate_value[:60],
            str(existing_fact.get("fact_value", ""))[:60],
            "duplicate" if is_duplicate else "new",
        )
        return is_duplicate

    # ------------------------------------------------------------------
    # Retrieval + Relevance filter (task D)
    # ------------------------------------------------------------------
    def _mark_pending_retrieval(self, player_uuid: str, chief_name: str, query_text: str) -> None:
        """Execute hybrid fact search, optional relevance filter, and store result in pending cache.

        Pipeline:
        1. Compute message_hash for caching
        2. Check relevance cache (TTL-based)
        3. Run search_facts_hybrid()
        4. If more than N candidates: qwen relevance filter
        5. Store result in pending_relevant_facts dict
        """
        import hashlib
        from .config import load_config

        config = load_config()
        facts_cfg = config.get("facts", {}) if isinstance(config, dict) else {}
        top_n_candidates = int(facts_cfg.get("retrieval_top_n_candidates", 10))
        max_facts = int(facts_cfg.get("max_facts_per_prompt", 5))
        cache_minutes = int(facts_cfg.get("relevance_cache_minutes", 5))

        # 1. Message hash for cache key
        message_hash = hashlib.md5(query_text.encode("utf-8")).hexdigest()

        # 2. Check relevance cache
        try:
            import sys
            _parent = _os.path.dirname(_os.path.dirname(_os.path.abspath(__file__)))
            if _parent not in sys.path:
                sys.path.insert(0, _parent)
            from memory_db import get_cached_relevance, set_cached_relevance
            from memory_db import pending_relevant_facts
        except ImportError:
            # When running from chief-ai-service root
            from memory_db import get_cached_relevance, set_cached_relevance  # type: ignore[no-redef]
            from memory_db import pending_relevant_facts  # type: ignore[no-redef]

        # We don't know query_type here – intent result has it, but we only get query_text
        # Use "general" as fallback for caching
        cached = get_cached_relevance(player_uuid, "general", message_hash)
        if cached is not None:
            key = (str(player_uuid), str(chief_name))
            pending_relevant_facts[key] = list(cached)
            logger.info(
                "FactsWorker retrieval cache HIT: player_uuid=%s fact_ids=%s",
                player_uuid, cached,
            )
            return

        # 3. Hybrid search
        try:
            from memory_db import search_facts_hybrid
        except ImportError:
            from memory_db import search_facts_hybrid  # type: ignore[no-redef]

        candidates = search_facts_hybrid(
            query_text=query_text,
            player_uuid=player_uuid,
            chief_name=chief_name,
            query_type="general",  # intent result not threaded through yet; default to general
            top_n=top_n_candidates,
            min_similarity=0.3,
        )

        if not candidates:
            logger.info(
                "FactsWorker retrieval: no candidates for player_uuid=%s",
                player_uuid,
            )
            return

        logger.info(
            "FactsWorker retrieval: %d candidates (threshold=%d) for player_uuid=%s",
            len(candidates), max_facts, player_uuid,
        )

        # 4. Relevance filter if too many candidates
        if len(candidates) > max_facts:
            selected_ids = self._filter_relevance(
                candidates=candidates,
                question=query_text,
                max_facts=max_facts,
            )
        else:
            selected_ids = [int(c["id"]) for c in candidates]

        # 5. Store in pending cache
        key = (str(player_uuid), str(chief_name))
        pending_relevant_facts[key] = list(selected_ids)
        logger.info(
            "FactsWorker pending cache stored: player_uuid=%s chief_name=%s fact_ids=%s",
            player_uuid, chief_name, selected_ids,
        )

        # 5b. Also store in relevance cache
        set_cached_relevance(
            player_uuid=player_uuid,
            query_type="general",
            message_hash=message_hash,
            fact_ids=list(selected_ids),
            ttl_minutes=cache_minutes,
        )

    def _filter_relevance(
        self, candidates: list[dict], question: str, max_facts: int = 5
    ) -> list[int]:
        """Ask qwen to filter candidate facts down to those relevant to the question.

        Returns a list of fact IDs.
        """
        from .qwen_client import send_prompt

        prompt_template = _load_prompt("relevance_prompt.txt")
        if not prompt_template:
            logger.warning("FactsWorker relevance_prompt.txt missing, falling back to top-N by score")
            # Fallback: return top max_facts by _score
            return [int(c["id"]) for c in candidates[:max_facts]]

        # Build compact facts_list for the prompt
        facts_lines: list[str] = []
        for c in candidates:
            cid = c.get("id", "?")
            ctype = str(c.get("fact_type", "")).strip()
            cvalue = str(c.get("fact_value", "")).strip()
            cscore = float(c.get("_score", 0.0))
            facts_lines.append(
                f"[{cid}] {ctype}: {cvalue} (score={cscore:.2f})"
            )
        facts_list = "\n".join(facts_lines)

        prompt_text = (
            prompt_template
            .replace("{facts_list}", facts_list)
            .replace("{question}", question[:500])
        )

        try:
            result = send_prompt(prompt_text, model="qwen2.5:3b")
        except Exception as exc:
            logger.warning("FactsWorker relevance qwen call failed: %s", exc)
            return [int(c["id"]) for c in candidates[:max_facts]]

        if not isinstance(result, dict):
            logger.warning("FactsWorker relevance qwen returned non-dict type=%s", type(result).__name__)
            return [int(c["id"]) for c in candidates[:max_facts]]

        if result.get("error"):
            logger.warning(
                "FactsWorker relevance qwen error: %s",
                str(result.get("raw_response", ""))[:200],
            )
            return [int(c["id"]) for c in candidates[:max_facts]]

        # Parse the JSON array response
        raw_response = str(result.get("response", "") or "").strip()
        try:
            import json as _json
            parsed = _json.loads(raw_response)
            if isinstance(parsed, list):
                selected = [int(x) for x in parsed if isinstance(x, (int, float))]
                logger.info(
                    "FactsWorker relevance filter: %d candidates -> %d selected %s",
                    len(candidates), len(selected), selected,
                )
                # Return only those that are still in the candidate set (safety)
                candidate_ids = {int(c["id"]) for c in candidates}
                return [sid for sid in selected if sid in candidate_ids][:max_facts]
            elif isinstance(parsed, dict):
                # Sometimes qwen wraps in {"relevant_ids": [...]}
                for key in ("relevant_ids", "ids", "relevant", "selected"):
                    val = parsed.get(key)
                    if isinstance(val, list):
                        selected = [int(x) for x in val if isinstance(x, (int, float))]
                        candidate_ids = {int(c["id"]) for c in candidates}
                        return [sid for sid in selected if sid in candidate_ids][:max_facts]
        except Exception as exc:
            logger.warning("FactsWorker relevance JSON parse failed: %s raw='%s'", exc, raw_response[:200])

        # Fallback: top-N by score
        return [int(c["id"]) for c in candidates[:max_facts]]