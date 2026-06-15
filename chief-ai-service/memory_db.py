"""Memory DB: SQLite CRUD & Schema-Migration for conversation_turns and memory_summaries.

Usage:
    from memory_db import (
        insert_turn, query_turns, query_turns_with_embeddings,
        query_turns_with_embeddings_detailed,
        delete_turns_for_player, delete_summaries_for_player,
        insert_summary, get_latest_summary,
        update_embedding, migrate, create_tables, get_connection,
        search_facts_hybrid,
        get_pending_relevant_facts, clear_pending_relevant_facts,
        get_cached_relevance, set_cached_relevance,
    )
"""

import hashlib
import sqlite3
import logging
import os.path
import re
import time
from typing import Optional, List

DB_PATH = os.path.join(os.path.dirname(__file__), "memory.db")
logger = logging.getLogger("chief_ai_service.memory_db")

# ----- Caches (module-level) -----
pending_relevant_facts: dict[tuple[str, str], list[int]] = {}  # (player_uuid, chief_name) -> [fact_id, ...]
_relevance_cache: dict[tuple[str, str, str], tuple[float, list[int]]] = {}  # (player_uuid, query_type, msg_hash) -> (expiry_ts, [fact_id, ...])


def get_pending_relevant_facts(player_uuid: str, chief_name: str) -> list[int]:
    """Return and clear pending relevant fact IDs for a player+chief pair."""
    key = (str(player_uuid), str(chief_name))
    result = pending_relevant_facts.pop(key, [])
    if result:
        logger.info(
            "get_pending_relevant_facts: player_uuid=%s chief_name=%s found=%d",
            player_uuid, chief_name, len(result),
        )
    return result


def clear_pending_relevant_facts(player_uuid: str, chief_name: str) -> None:
    """Clear pending facts without reading them (on errors)."""
    key = (str(player_uuid), str(chief_name))
    pending_relevant_facts.pop(key, None)


def get_cached_relevance(player_uuid: str, query_type: str, message_hash: str) -> Optional[list[int]]:
    """Check relevance cache for a prior decision.

    Returns list of fact_ids if a non-expired entry exists, else None.
    """
    key = (str(player_uuid), str(query_type), str(message_hash))
    entry = _relevance_cache.get(key)
    if entry is None:
        return None
    expiry_ts, fact_ids = entry
    if time.time() > expiry_ts:
        _relevance_cache.pop(key, None)
        return None
    logger.info("get_cached_relevance cache HIT for %s/%s", player_uuid, query_type)
    return list(fact_ids)


def set_cached_relevance(
    player_uuid: str,
    query_type: str,
    message_hash: str,
    fact_ids: list[int],
    ttl_minutes: int = 5,
) -> None:
    """Store a relevance decision in the cache."""
    key = (str(player_uuid), str(query_type), str(message_hash))
    expiry_ts = time.time() + ttl_minutes * 60
    _relevance_cache[key] = (expiry_ts, list(fact_ids))
    logger.info(
        "set_cached_relevance: player_uuid=%s query_type=%s fact_ids=%s ttl=%dm",
        player_uuid, query_type, fact_ids, ttl_minutes,
    )


# Latest expected schema – used by migrate() to add missing columns.
EXPECTED_COLUMNS = {
    "conversation_turns": {
        "id":      "INTEGER PRIMARY KEY AUTOINCREMENT",
        "player_uuid": "TEXT NOT NULL",
        "chief_name":  "TEXT NOT NULL",
        "role":     "TEXT NOT NULL CHECK(role IN ('player', 'chief'))",
        "message":  "TEXT NOT NULL",
        "embedding": "BLOB",
        "mc_day":   "INTEGER DEFAULT 0",
        "mc_time":  "INTEGER DEFAULT 0",
        "is_archived": "INTEGER DEFAULT 0 CHECK(is_archived IN (0, 1))",
        "created_at":  "TEXT DEFAULT (datetime('now'))",
    },
    "memory_summaries": {
        "id":                   "INTEGER PRIMARY KEY AUTOINCREMENT",
        "player_uuid":          "TEXT NOT NULL",
        "chief_name":           "TEXT NOT NULL",
        "summary_text":         "TEXT NOT NULL",
        "turn_range_start":     "INTEGER",
        "turn_range_end":       "INTEGER",
        "reputation_at_summary": "INTEGER DEFAULT 0",
        "embedding":            "BLOB",
        "created_at":           "TEXT DEFAULT (datetime('now'))",
    },
    "player_facts": {
        "id":              "INTEGER PRIMARY KEY AUTOINCREMENT",
        "player_uuid":     "TEXT NOT NULL",
        "chief_name":      "TEXT NOT NULL DEFAULT 'any'",
        "fact_type":       "TEXT NOT NULL",
        "fact_value":      "TEXT NOT NULL",
        "evidence_text":   "TEXT NOT NULL",
        "embedding":       "BLOB",
        "confidence":      "REAL DEFAULT 0.8",
        "importance":      "REAL DEFAULT 0.5",
        "times_confirmed": "INTEGER DEFAULT 1",
        "first_seen_at":   "TEXT DEFAULT (datetime('now'))",
        "last_seen_at":    "TEXT DEFAULT (datetime('now'))",
        "source_turn_id":  "INTEGER",
        "is_deleted":      "INTEGER DEFAULT 0",
    },
}


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def get_connection() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA foreign_keys=ON")
    return conn


def _row_to_dict(row: sqlite3.Row) -> dict:
    """Convert an sqlite3.Row to a plain dict."""
    return dict(zip(row.keys(), row))


# ---------------------------------------------------------------------------
# Schema creation (idempotent)
# ---------------------------------------------------------------------------

def create_tables() -> None:
    """Create tables and indexes if they don't exist yet."""
    conn = get_connection()
    try:
        conn.executescript("""
            CREATE TABLE IF NOT EXISTS conversation_turns (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid TEXT NOT NULL,
                chief_name TEXT NOT NULL,
                role TEXT NOT NULL CHECK(role IN ('player', 'chief')),
                message TEXT NOT NULL,
                embedding BLOB,
                mc_day INTEGER DEFAULT 0,
                mc_time INTEGER DEFAULT 0,
                is_archived INTEGER DEFAULT 0 CHECK(is_archived IN (0, 1)),
                created_at TEXT DEFAULT (datetime('now'))
            );

            CREATE TABLE IF NOT EXISTS memory_summaries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid TEXT NOT NULL,
                chief_name TEXT NOT NULL,
                summary_text TEXT NOT NULL,
                turn_range_start INTEGER,
                turn_range_end INTEGER,
                reputation_at_summary INTEGER DEFAULT 0,
                embedding BLOB,
                created_at TEXT DEFAULT (datetime('now'))
            );

            CREATE TABLE IF NOT EXISTS player_facts (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid     TEXT NOT NULL,
                chief_name      TEXT NOT NULL DEFAULT 'any',
                fact_type       TEXT NOT NULL,
                fact_value      TEXT NOT NULL,
                evidence_text   TEXT NOT NULL,
                embedding       BLOB,
                confidence      REAL DEFAULT 0.8,
                importance      REAL DEFAULT 0.5,
                times_confirmed INTEGER DEFAULT 1,
                first_seen_at   TEXT DEFAULT (datetime('now')),
                last_seen_at    TEXT DEFAULT (datetime('now')),
                source_turn_id  INTEGER,
                is_deleted      INTEGER DEFAULT 0
            );

            CREATE VIRTUAL TABLE IF NOT EXISTS facts_fts USING fts5(
                fact_type,
                fact_value,
                evidence_text,
                content='player_facts',
                content_rowid='id'
            );

            -- FTS5 sync triggers
            CREATE TRIGGER IF NOT EXISTS facts_ai AFTER INSERT ON player_facts BEGIN
                INSERT INTO facts_fts(rowid, fact_type, fact_value, evidence_text)
                VALUES (new.id, new.fact_type, new.fact_value, new.evidence_text);
            END;

            CREATE TRIGGER IF NOT EXISTS facts_ad AFTER DELETE ON player_facts BEGIN
                INSERT INTO facts_fts(facts_fts, rowid, fact_type, fact_value, evidence_text)
                VALUES ('delete', old.id, old.fact_type, old.fact_value, old.evidence_text);
            END;

            CREATE TRIGGER IF NOT EXISTS facts_au AFTER UPDATE ON player_facts BEGIN
                INSERT INTO facts_fts(facts_fts, rowid, fact_type, fact_value, evidence_text)
                VALUES ('delete', old.id, old.fact_type, old.fact_value, old.evidence_text);
                INSERT INTO facts_fts(rowid, fact_type, fact_value, evidence_text)
                VALUES (new.id, new.fact_type, new.fact_value, new.evidence_text);
            END;

            CREATE INDEX IF NOT EXISTS idx_turns_player
                ON conversation_turns(player_uuid, chief_name);
            CREATE INDEX IF NOT EXISTS idx_turns_role
                ON conversation_turns(role);
            CREATE INDEX IF NOT EXISTS idx_summaries_player
                ON memory_summaries(player_uuid, chief_name);
            CREATE INDEX IF NOT EXISTS idx_player_facts_lookup
                ON player_facts(player_uuid, chief_name, fact_type);
            CREATE INDEX IF NOT EXISTS idx_player_facts_last_seen
                ON player_facts(last_seen_at);
        """)
        conn.commit()
    finally:
        conn.close()


# ---------------------------------------------------------------------------
# Migration – add missing columns to existing tables
# ---------------------------------------------------------------------------

def migrate() -> None:
    """
    Ensure that all columns from EXPECTED_COLUMNS exist in the actual DB file.
    Calls create_tables() first (idempotent) and then ALTER TABLE for any
    missing columns.
    """
    create_tables()  # guarantees tables + base indexes exist

    conn = get_connection()
    try:
        for table, desired in EXPECTED_COLUMNS.items():
            existing = _get_table_columns(conn, table)
            for col_name, col_def in desired.items():
                if col_name not in existing:
                    safe_def = _sanitize_for_alter(col_def)
                    try:
                        conn.execute(
                            f"ALTER TABLE {table} ADD COLUMN {col_name} {safe_def}"
                        )
                    except sqlite3.OperationalError as exc:
                        # Column might have been added by a concurrent process
                        if "duplicate column name" not in str(exc).lower():
                            raise

        # Index on mc_day – created here (not in create_tables()) because
        # legacy databases may not have the column yet.
        conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_turns_mc_day"
            " ON conversation_turns(mc_day)"
        )

        conn.commit()
    finally:
        conn.close()


def _sanitize_for_alter(col_def: str) -> str:
    """
    SQLite ALTER TABLE ADD COLUMN does not support:
      - CHECK constraints
      - Non-constant defaults like '('datetime')'now''
    Strip these from the column definition so migration can succeed.
    """
    # Remove CHECK clause and everything after it (avoids nested-paren issues)
    match = re.search(r'\bCHECK\s*\(', col_def, re.IGNORECASE)
    if match:
        col_def = col_def[:match.start()].strip()
    # Remove parenthesized DEFAULT (e.g. DEFAULT (datetime('now')) or
    # DEFAULT (expr)) – use balanced-paren matching, not a simple regex.
    match = re.search(r'\bDEFAULT\s*\(', col_def, re.IGNORECASE)
    if match:
        start = match.start()
        i = match.end() - 1  # position of opening '('
        depth = 0
        end = i
        for j in range(i, len(col_def)):
            if col_def[j] == '(':
                depth += 1
            elif col_def[j] == ')':
                depth -= 1
                if depth == 0:
                    end = j + 1
                    break
        col_def = (col_def[:start] + col_def[end:]).strip()
    return col_def


def _get_table_columns(conn: sqlite3.Connection, table: str) -> set:
    """Return a set of existing column names for *table*."""
    cur = conn.execute(f"PRAGMA table_info({table})")
    return {row[1] for row in cur.fetchall()}


# ---------------------------------------------------------------------------
# CRUD: conversation_turns
# ---------------------------------------------------------------------------

def insert_turn(
    player_uuid: str,
    chief_name: str,
    role: str,
    message: str,
    embedding: Optional[bytes] = None,
    mc_day: int = 0,
    mc_time: int = 0,
) -> int:
    """
    Insert a single conversation turn and return its new row id.
    """
    conn = get_connection()
    try:
        cur = conn.execute(
            """INSERT INTO conversation_turns
               (player_uuid, chief_name, role, message, embedding, mc_day, mc_time)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            (player_uuid, chief_name, role, message, embedding, mc_day, mc_time),
        )
        conn.commit()
        return cur.lastrowid
    finally:
        conn.close()


def query_turns(
    player_uuid: str,
    chief_name: str,
    limit: int = 20,
    offset: int = 0,
    include_archived: bool = False,
) -> List[dict]:
    """
    Return recent turns for a player+chief, newest first.
    By default archived turns are excluded unless *include_archived* is True.
    """
    conn = get_connection()
    try:
        sql = """SELECT id, player_uuid, chief_name, role, message, embedding,
                        mc_day, mc_time, is_archived, created_at
                 FROM conversation_turns
                 WHERE player_uuid = ? AND chief_name = ?"""
        params: list = [player_uuid, chief_name]
        if not include_archived:
            sql += " AND is_archived = 0"
        sql += " ORDER BY id DESC LIMIT ? OFFSET ?"
        params.extend([limit, offset])
        cur = conn.execute(sql, params)
        return [_row_to_dict(row) for row in cur.fetchall()]
    finally:
        conn.close()


def query_turns_with_embeddings(
    player_uuid: str,
    chief_name: str,
    limit: int = 500,
) -> int:
    """
    Return the COUNT of turns with non-NULL embeddings for a player+chief.
    Includes archived turns (they may still be semantically relevant).
    """
    conn = get_connection()
    try:
        cur = conn.execute(
            """SELECT COUNT(*) AS cnt
               FROM conversation_turns
               WHERE player_uuid = ?
                 AND chief_name = ?
                 AND embedding IS NOT NULL
               LIMIT ?""",
            (player_uuid, chief_name, limit),
        )
        row = cur.fetchone()
        count = row["cnt"] if row else 0
        logger.info(
            "query_turns_with_embeddings: player_uuid=%s chief_name=%s count=%d limit=%d",
            player_uuid,
            chief_name,
            count,
            limit,
        )
        return count
    finally:
        conn.close()


def query_turns_with_embeddings_detailed(
    player_uuid: str,
    chief_name: str,
    limit: int = 500,
) -> List[dict]:
    """
    Return turns with non-NULL embeddings, newest first.
    Includes archived turns (they may still be semantically relevant).
    Limited to *limit* rows for performance.

    Use this variant when you need the actual turn data (e.g. for
    semantic search / cosine comparison), not just a count.
    """
    conn = get_connection()
    try:
        cur = conn.execute(
            """SELECT id, player_uuid, chief_name, role, message, embedding,
                      mc_day, mc_time, created_at
               FROM conversation_turns
               WHERE player_uuid = ?
                 AND chief_name = ?
                 AND embedding IS NOT NULL
               ORDER BY id DESC
               LIMIT ?""",
            (player_uuid, chief_name, limit),
        )
        rows = [_row_to_dict(row) for row in cur.fetchall()]
        logger.info(
            "query_turns_with_embeddings_detailed: player_uuid=%s chief_name=%s found=%d limit=%d",
            player_uuid,
            chief_name,
            len(rows),
            limit,
        )
        return rows
    finally:
        conn.close()


# ---------------------------------------------------------------------------
# Semantic search
# ---------------------------------------------------------------------------

def search_by_embedding(
    query_text: str,
    player_uuid: str,
    chief_name: str,
    top_n: int = 5,
    min_similarity: float = 0.5,
) -> list[dict]:
    """
    Semantically search for turns similar to *query_text*.

    1. Generate embedding for query_text via embedding_client.
    2. Fetch all turns with non-NULL embeddings for this player+chief.
    3. Compute cosine similarity for each.
    4. Return top_n dicts with "role" and "message" keys (most similar first).

    Falls gracefully: returns empty list on any error or no matches.
    """
    import sys
    import os as _os
    _parent = _os.path.dirname(_os.path.abspath(__file__))
    if _parent not in sys.path:
        sys.path.insert(0, _parent)

    try:
        from chief_ai_service.embedding_client import get_embedding, unpack_embedding, cosine_similarity
    except ImportError:
        # Fallback: running from project root, adjust import
        from embedding_client import get_embedding, unpack_embedding, cosine_similarity  # type: ignore[no-redef]

    logger.info(
        "search_by_embedding start: player_uuid=%s chief_name=%s top_n=%d min_similarity=%.4f query='%s'",
        player_uuid,
        chief_name,
        top_n,
        min_similarity,
        (query_text or "").replace("\n", " ")[:120],
    )

    try:
        query_embedding = get_embedding(query_text)
    except Exception as exc:
        logger.info("search_by_embedding: embedding generation raised error: %r", exc)
        return []

    if query_embedding is None:
        logger.info("search_by_embedding: embedding generation failed (None)")
        return []

    logger.info("search_by_embedding: embedding generated OK dims=%d", len(query_embedding))

    turns = query_turns_with_embeddings_detailed(player_uuid, chief_name)
    if not turns:
        logger.info("search_by_embedding: no turns with embeddings available")
        return []

    scored: list[tuple[float, str]] = []
    all_similarities: list[float] = []
    for turn in turns:
        blob = turn.get("embedding")
        if not blob:
            continue
        try:
            turn_emb = unpack_embedding(blob)
            sim = cosine_similarity(query_embedding, turn_emb)
            all_similarities.append(sim)
            if sim >= min_similarity:
                scored.append((sim, str(turn.get("message", ""))))
        except Exception:
            continue

    if all_similarities:
        top_scores = sorted(all_similarities, reverse=True)[:3]
        logger.info(
            "search_by_embedding: top_similarities=%s min_similarity=%.4f",
            [round(value, 4) for value in top_scores],
            min_similarity,
        )
    else:
        logger.info("search_by_embedding: no valid similarity values computed")

    # Sort by similarity descending
    scored.sort(key=lambda pair: pair[0], reverse=True)

    # Return unique messages with role context, preserving order
    seen: set[str] = set()
    result: list[dict] = []
    role_cache: dict[str, str] = {}
    # Build role lookup from original turns before sorting
    for turn in turns:
        msg = str(turn.get("message", ""))
        if msg and msg not in role_cache:
            role_cache[msg] = str(turn.get("role", "player"))
    for _sim, msg in scored:
        if msg not in seen:
            seen.add(msg)
            result.append({"role": role_cache.get(msg, "player"), "message": msg})
            if len(result) >= top_n:
                break

    logger.info("search_by_embedding: returning %d memory hits", len(result))

    return result


def delete_turns_for_player(player_uuid: str) -> int:
    """
    Delete all conversation_turns for a player.
    Returns the number of deleted rows.
    """
    conn = get_connection()
    try:
        cur = conn.execute(
            "DELETE FROM conversation_turns WHERE player_uuid = ?", (player_uuid,)
        )
        conn.commit()
        return cur.rowcount
    finally:
        conn.close()


def delete_summaries_for_player(player_uuid: str) -> int:
    """
    Delete all memory_summaries for a player.
    Returns the number of deleted rows.
    """
    conn = get_connection()
    try:
        cur = conn.execute(
            "DELETE FROM memory_summaries WHERE player_uuid = ?", (player_uuid,)
        )
        conn.commit()
        return cur.rowcount
    finally:
        conn.close()


def update_embedding(turn_id: int, embedding_blob: bytes) -> None:
    """
    Set (or overwrite) the embedding BLOB for a specific turn.
    """
    conn = get_connection()
    try:
        conn.execute(
            "UPDATE conversation_turns SET embedding = ? WHERE id = ?",
            (embedding_blob, turn_id),
        )
        conn.commit()
    finally:
        conn.close()


# ---------------------------------------------------------------------------
# CRUD: memory_summaries
# ---------------------------------------------------------------------------

def insert_summary(
    player_uuid: str,
    chief_name: str,
    summary_text: str,
    turn_range_start: Optional[int] = None,
    turn_range_end: Optional[int] = None,
    reputation: int = 0,
) -> int:
    """
    Store a summary and return its new row id.
    """
    conn = get_connection()
    try:
        cur = conn.execute(
            """INSERT INTO memory_summaries
               (player_uuid, chief_name, summary_text,
                turn_range_start, turn_range_end, reputation_at_summary)
               VALUES (?, ?, ?, ?, ?, ?)""",
            (player_uuid, chief_name, summary_text,
             turn_range_start, turn_range_end, reputation),
        )
        conn.commit()
        return cur.lastrowid
    finally:
        conn.close()


def update_summary_embedding(summary_id: int, embedding_blob: bytes) -> None:
    """
    Set (or overwrite) the embedding BLOB for a specific summary.
    """
    conn = get_connection()
    try:
        conn.execute(
            "UPDATE memory_summaries SET embedding = ? WHERE id = ?",
            (embedding_blob, summary_id),
        )
        conn.commit()
    finally:
        conn.close()


def get_latest_summary(
    player_uuid: str, chief_name: str
) -> Optional[dict]:
    """
    Return the most recent summary for the given player+chief, or None.
    """
    conn = get_connection()
    try:
        cur = conn.execute(
            """SELECT id, player_uuid, chief_name, summary_text,
                      turn_range_start, turn_range_end,
                      reputation_at_summary, created_at
               FROM memory_summaries
               WHERE player_uuid = ? AND chief_name = ?
               ORDER BY id DESC
               LIMIT 1""",
            (player_uuid, chief_name),
        )
        row = cur.fetchone()
        return _row_to_dict(row) if row else None
    finally:
        conn.close()


def query_summaries_with_embeddings(
    player_uuid: str,
    chief_name: str,
) -> List[dict]:
    """
    Return all summaries with non-NULL embeddings for a player+chief pair.
    """
    conn = get_connection()
    try:
        cur = conn.execute(
            """SELECT id, player_uuid, chief_name, summary_text,
                      turn_range_start, turn_range_end,
                      reputation_at_summary, embedding, created_at
               FROM memory_summaries
               WHERE player_uuid = ?
                 AND chief_name = ?
                 AND embedding IS NOT NULL
               ORDER BY id DESC""",
            (player_uuid, chief_name),
        )
        return [_row_to_dict(row) for row in cur.fetchall()]
    finally:
        conn.close()


def count_unsupervised_turns(player_uuid: str, chief_name: str) -> int:
    """
    Count how many non-archived turns exist after the last summary's
    turn_range_end (or all turns if no summary exists yet).

    Returns the count as int.
    """
    conn = get_connection()
    try:
        # Find the highest turn_range_end for this player+chief
        last_end = conn.execute(
            """SELECT MAX(turn_range_end) AS max_end
               FROM memory_summaries
               WHERE player_uuid = ? AND chief_name = ?""",
            (player_uuid, chief_name),
        ).fetchone()

        threshold = last_end["max_end"] if last_end and last_end["max_end"] else 0

        cur = conn.execute(
            """SELECT COUNT(*) AS cnt
               FROM conversation_turns
               WHERE player_uuid = ?
                 AND chief_name = ?
                 AND is_archived = 0
                 AND id > ?""",
            (player_uuid, chief_name, threshold),
        )
        row = cur.fetchone()
        return row["cnt"] if row else 0
    finally:
        conn.close()


def get_unsupervised_turns(
    player_uuid: str, chief_name: str
) -> List[dict]:
    """
    Return all non-archived turns that are not yet covered by any summary.
    Ordered by id ASC (oldest first), suitable for summary generation.
    """
    conn = get_connection()
    try:
        last_end = conn.execute(
            """SELECT MAX(turn_range_end) AS max_end
               FROM memory_summaries
               WHERE player_uuid = ? AND chief_name = ?""",
            (player_uuid, chief_name),
        ).fetchone()

        threshold = last_end["max_end"] if last_end and last_end["max_end"] else 0

        cur = conn.execute(
            """SELECT id, player_uuid, chief_name, role, message, mc_day, mc_time, created_at
               FROM conversation_turns
               WHERE player_uuid = ?
                 AND chief_name = ?
                 AND is_archived = 0
                 AND id > ?
               ORDER BY id ASC""",
            (player_uuid, chief_name, threshold),
        )
        return [_row_to_dict(row) for row in cur.fetchall()]
    finally:
        conn.close()


# ---------------------------------------------------------------------------
# CRUD: player_facts
# ---------------------------------------------------------------------------

def insert_fact(
    player_uuid: str,
    chief_name: str,
    fact_type: str,
    fact_value: str,
    evidence_text: str,
    embedding: Optional[bytes] = None,
    confidence: float = 0.8,
    importance: float = 0.5,
    times_confirmed: int = 1,
    source_turn_id: Optional[int] = None,
) -> int:
    """Insert a new fact and return its row id."""
    conn = get_connection()
    try:
        cur = conn.execute(
            """INSERT INTO player_facts
               (player_uuid, chief_name, fact_type, fact_value, evidence_text,
                embedding, confidence, importance, times_confirmed, source_turn_id)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (player_uuid, chief_name, fact_type, fact_value, evidence_text,
             embedding, confidence, importance, times_confirmed, source_turn_id),
        )
        conn.commit()
        return cur.lastrowid
    finally:
        conn.close()


def update_fact(
    fact_id: int,
    fact_value: Optional[str] = None,
    evidence_text: Optional[str] = None,
    embedding: Optional[bytes] = None,
    confidence: Optional[float] = None,
    importance: Optional[float] = None,
    times_confirmed: Optional[int] = None,
    is_deleted: Optional[int] = None,
) -> None:
    """Partial update of a fact row. Only non-None fields are changed.

    The last_seen_at timestamp is always refreshed to datetime('now').
    """
    conn = get_connection()
    try:
        fields: list[str] = []
        params: list = []
        if fact_value is not None:
            fields.append("fact_value = ?")
            params.append(fact_value)
        if evidence_text is not None:
            fields.append("evidence_text = ?")
            params.append(evidence_text)
        if embedding is not None:
            fields.append("embedding = ?")
            params.append(embedding)
        if confidence is not None:
            fields.append("confidence = ?")
            params.append(confidence)
        if importance is not None:
            fields.append("importance = ?")
            params.append(importance)
        if times_confirmed is not None:
            fields.append("times_confirmed = ?")
            params.append(times_confirmed)
        if is_deleted is not None:
            fields.append("is_deleted = ?")
            params.append(is_deleted)

        # Always refresh last_seen_at
        fields.append("last_seen_at = datetime('now')")
        params.append(fact_id)

        if not fields:
            return

        conn.execute(
            f"UPDATE player_facts SET {', '.join(fields)} WHERE id = ?",
            params,
        )
        conn.commit()
    finally:
        conn.close()


def query_facts_by_type(
    player_uuid: str,
    chief_name: str,
    fact_type: str,
    include_deleted: bool = False,
) -> List[dict]:
    """Return all facts of a given type for a player+chief pair."""
    conn = get_connection()
    try:
        sql = """SELECT id, player_uuid, chief_name, fact_type, fact_value,
                        evidence_text, embedding, confidence, importance, times_confirmed,
                        first_seen_at, last_seen_at, source_turn_id, is_deleted
                 FROM player_facts
                 WHERE player_uuid = ? AND chief_name = ? AND fact_type = ?"""
        if not include_deleted:
            sql += " AND is_deleted = 0"
        sql += " ORDER BY last_seen_at DESC"
        cur = conn.execute(sql, (player_uuid, chief_name, fact_type))
        return [_row_to_dict(row) for row in cur.fetchall()]
    finally:
        conn.close()


def search_facts_fts(
    query: str,
    player_uuid: Optional[str] = None,
    chief_name: Optional[str] = None,
    limit: int = 10,
) -> List[dict]:
    """Full-text search in player_facts via facts_fts.

    The *query* string is passed directly to FTS5, so it can include
    FTS5 syntax like "name OR title" etc.

    Results are joined back to player_facts so callers get the full
    row data including confidence, importance, etc.
    """
    conn = get_connection()
    try:
        where = ["pf.is_deleted = 0"]
        params: list = []
        if player_uuid:
            where.append("pf.player_uuid = ?")
            params.append(player_uuid)
        if chief_name:
            where.append("pf.chief_name = ?")
            params.append(chief_name)

        sql = f"""SELECT pf.id, pf.player_uuid, pf.chief_name, pf.fact_type,
                        pf.fact_value, pf.evidence_text, pf.embedding,
                        pf.confidence, pf.importance, pf.times_confirmed,
                        pf.first_seen_at, pf.last_seen_at, pf.source_turn_id,
                        pf.is_deleted
                 FROM facts_fts f
                 JOIN player_facts pf ON f.rowid = pf.id
                 WHERE facts_fts MATCH ?
                   AND {' AND '.join(where)}
                 ORDER BY rank
                 LIMIT ?"""
        params.insert(0, query)
        params.append(limit)
        cur = conn.execute(sql, params)
        return [_row_to_dict(row) for row in cur.fetchall()]
    finally:
        conn.close()


def get_facts_for_player(
    player_uuid: str,
    chief_name: Optional[str] = None,
    include_deleted: bool = False,
) -> List[dict]:
    """Return all (non-deleted) facts for a player.

    If *chief_name* is given, includes both chief-specific facts and
    facts marked as 'any' (chief-agnostic).  If *chief_name* is None,
    returns all facts for the player regardless of chief.
    """
    conn = get_connection()
    try:
        if chief_name is not None:
            sql = """SELECT id, player_uuid, chief_name, fact_type, fact_value,
                            evidence_text, embedding, confidence, importance, times_confirmed,
                            first_seen_at, last_seen_at, source_turn_id, is_deleted
                     FROM player_facts
                     WHERE player_uuid = ?
                       AND (chief_name = ? OR chief_name = 'any')"""
            params: list = [player_uuid, chief_name]
        else:
            sql = """SELECT id, player_uuid, chief_name, fact_type, fact_value,
                            evidence_text, embedding, confidence, importance, times_confirmed,
                            first_seen_at, last_seen_at, source_turn_id, is_deleted
                     FROM player_facts
                     WHERE player_uuid = ?"""
            params = [player_uuid]

        if not include_deleted:
            sql += " AND is_deleted = 0"
        sql += " ORDER BY last_seen_at DESC"
        cur = conn.execute(sql, params)
        return [_row_to_dict(row) for row in cur.fetchall()]
    finally:
        conn.close()


def search_facts_embedding(
    query_text: str,
    player_uuid: str,
    chief_name: Optional[str] = None,
    top_n: int = 5,
    min_similarity: float = 0.5,
) -> list[dict]:
    """Semantically search for facts similar to *query_text*.

    Uses the same embedding_client + cosine similarity approach as
    search_by_embedding(), but operates on player_facts instead of
    conversation_turns.
    """
    import sys
    import os as _os
    _parent = _os.path.dirname(_os.path.abspath(__file__))
    if _parent not in sys.path:
        sys.path.insert(0, _parent)

    try:
        from chief_ai_service.embedding_client import get_embedding, unpack_embedding, cosine_similarity
    except ImportError:
        from embedding_client import get_embedding, unpack_embedding, cosine_similarity  # type: ignore[no-redef]

    logger.info(
        "search_facts_embedding start: player_uuid=%s chief_name=%s top_n=%d min_sim=%.4f",
        player_uuid, chief_name, top_n, min_similarity,
    )

    try:
        query_embedding = get_embedding(query_text)
    except Exception as exc:
        logger.info("search_facts_embedding: embedding failed: %r", exc)
        return []
    if query_embedding is None:
        return []

    facts = get_facts_for_player(player_uuid, chief_name)
    if not facts:
        return []

    scored: list[tuple[float, dict]] = []
    for fact in facts:
        blob = fact.get("embedding")
        if not blob:
            continue
        try:
            emb = unpack_embedding(blob)
            sim = cosine_similarity(query_embedding, emb)
            if sim >= min_similarity:
                scored.append((sim, fact))
        except Exception:
            continue

    scored.sort(key=lambda pair: pair[0], reverse=True)
    return [fact for _sim, fact in scored[:top_n]]


def search_facts_hybrid(
    query_text: str,
    player_uuid: str,
    chief_name: Optional[str] = None,
    query_type: str = "general",
    top_n: int = 10,
    min_similarity: float = 0.3,
) -> list[dict]:
    """Hybrid fact search combining FTS5 and embedding, with scored ranking.

    Strategy depends on *query_type*:
      - name:       FTS5 primary, embedding secondary
      - location:   embedding primary, FTS5 secondary
      - event:      embedding only
      - preference: embedding primary, FTS5 secondary
      - general:    both combined equally

    Each result dict includes a '_score' key (0.0–1.0) and all player_facts columns.
    """
    import sys
    import os as _os
    _parent = _os.path.dirname(_os.path.abspath(__file__))
    if _parent not in sys.path:
        sys.path.insert(0, _parent)
    try:
        from chief_ai_service.embedding_client import get_embedding, unpack_embedding, cosine_similarity
    except ImportError:
        from embedding_client import get_embedding, unpack_embedding, cosine_similarity  # type: ignore[no-redef]

    logger.info(
        "search_facts_hybrid start: player_uuid=%s chief_name=%s query_type=%s top_n=%d query='%s'",
        player_uuid, chief_name, query_type, top_n, (query_text or "")[:120].replace("\n", " "),
    )

    # Pre-fetch all facts for embedding comparison
    all_facts = get_facts_for_player(player_uuid, chief_name)
    if not all_facts:
        logger.info("search_facts_hybrid: no facts available for player")
        return []

    # Generate query embedding once (needed for all strategies except FTS5-only)
    query_embedding = None
    if query_type != "name":
        try:
            query_embedding = get_embedding(query_text)
        except Exception as exc:
            logger.warning("search_facts_hybrid: embedding generation failed: %s", exc)

    # ---- Primary search ----
    fts_candidates: dict[int, dict] = {}
    emb_candidates: dict[int, float] = {}

    if query_type in ("name", "general"):
                # FTS5 primary - sanitize query to avoid FTS5 syntax errors
        try:
            query_safe = str(query_text)
            # Remove FTS5 special characters that cause syntax errors
            query_safe = str(query_text)
            # Remove special chars and build OR query for FTS5
            query_safe = re.sub(r"[?!.,;(){}#@\$%^&*+=~\[\]-]", " ", query_safe)
            words = [w for w in query_safe.split() if len(w) >= 2]
            fts_query = " OR ".join(words) if words else "unknown"
            fts_results = search_facts_fts(fts_query, player_uuid, chief_name, limit=top_n * 3)
            for f in fts_results:
                fts_candidates[f["id"]] = f
            logger.info("search_facts_hybrid: FTS found %d candidates", len(fts_candidates))
        except Exception as exc:
            logger.warning("search_facts_hybrid: FTS search failed: %s", exc)

    if query_type in ("location", "event", "preference", "general") and query_embedding is not None:
        # Embedding similarity against all facts
        for fact in all_facts:
            blob = fact.get("embedding")
            if not blob:
                continue
            try:
                emb = unpack_embedding(blob)
                sim = cosine_similarity(query_embedding, emb)
                if sim >= min_similarity:
                    emb_candidates[fact["id"]] = sim
            except Exception:
                continue
        logger.info("search_facts_hybrid: embedding found %d candidates", len(emb_candidates))

    # ---- Merge and compute scores ----
    merged: dict[int, tuple[dict, float]] = {}  # fact_id -> (fact_dict, cosine_sim)

    # Add FTS hits (cosine_sim=0.0 if not also an embedding hit)
    for fid, fact in fts_candidates.items():
        sim = emb_candidates.get(fid, 0.0)
        merged[fid] = (fact, sim)

    # Add embedding hits not already in FTS results
    for fid, sim in emb_candidates.items():
        if fid not in merged:
            # Find the fact dict
            fact = next((f for f in all_facts if f["id"] == fid), None)
            if fact is not None:
                merged[fid] = (fact, sim)

    if not merged:
        logger.info("search_facts_hybrid: no merged candidates after min_similarity filter")
        return []

    # ---- Score calculation ----
    max_confirmed = max(
        (f.get("times_confirmed", 1) or 1) for f, _ in merged.values()
    ) or 1

    scored_results: list[dict] = []
    for fid, (fact, cosine_sim) in merged.items():
        times_c = float(fact.get("times_confirmed", 1) or 1)
        importance_v = float(fact.get("importance", 0.5) or 0.5)
        score = cosine_sim * 0.6 + (times_c / max_confirmed) * 0.2 + importance_v * 0.2
        result = dict(fact)
        result["_score"] = round(score, 4)
        result["_cosine_sim"] = round(cosine_sim, 4)
        scored_results.append(result)

    # Sort by score descending
    scored_results.sort(key=lambda r: r["_score"], reverse=True)
    top = scored_results[:top_n]
    logger.info(
        "search_facts_hybrid: returning %d results (top_score=%.4f)",
        len(top),
        top[0]["_score"] if top else 0.0,
    )
    return top


def delete_facts_for_player(player_uuid: str) -> int:
    """Delete all player_facts for a player (hard delete).
    Returns the number of deleted rows.
    """
    conn = get_connection()
    try:
        cur = conn.execute(
            "DELETE FROM player_facts WHERE player_uuid = ?", (player_uuid,)
        )
        conn.commit()
        return cur.rowcount
    finally:
        conn.close()


# ---------------------------------------------------------------------------
# CLI smoke test
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    import sys

    if len(sys.argv) > 1 and sys.argv[1] == "migrate":
        migrate()
        print("Migration complete.")
    else:
        create_tables()
        print(f"memory.db created at {DB_PATH}")

        # Smoke tests (non-destructive)
        tid = insert_turn("test-uuid", "TestChief", "player", "Hello!", mc_day=1, mc_time=6000)
        print(f"inserted turn id={tid}")

        turns = query_turns("test-uuid", "TestChief")
        print(f"query_turns returned {len(turns)} rows")

        sid = insert_summary("test-uuid", "TestChief", "A test summary", turn_range_start=1, turn_range_end=1)
        print(f"inserted summary id={sid}")

        summary = get_latest_summary("test-uuid", "TestChief")
        print(f"latest summary: {summary['summary_text']}")

        # Clean up test data
        delete_turns_for_player("test-uuid")
        print("test data cleaned up")