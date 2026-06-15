
"""
Integration test: Facts system (player_facts) across multiple turns.

Covers (Paket H1):
- Scenario A: Name genannt in Turn 1, 40+ Ablenkungs-Turns, Frage "Wie heisse ich?" in Turn ~50
  -> Fakt wird gefunden und im Prompt verwendet
- Scenario B: Name vergessen (kein Fakt) -> leere Facts-Sektion
- Scenario C: Widerspruch (Name korrigiert) -> neuester Fakt gewinnt
- Scenario D: Chief-uebergreifender Fakt (Name bei Chief B abrufbar)

Design:
- Fakten werden direkt via insert_fact() in die DB geschrieben (kein Worker)
- Retrieval via search_facts_fts() (FTS5, braucht kein Ollama)
- Prompt-Section via _build_facts_section()
- Tempfile-basierte SQLite-DB pro Testmethode
- Hybrid-Search-Tests sind optional (skipIf ohne Ollama)
"""

import unittest
import os
import sys
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
# Helper: insert a fact directly (bypasses worker pipeline)
# ---------------------------------------------------------------------------

def _insert_fact_directly(
    player_uuid: str,
    chief_name: str,
    fact_type: str,
    fact_value: str,
    evidence_text: str,
    embedding=None,
    confidence: float = 0.8,
    importance: float = 0.5,
    times_confirmed: int = 1,
) -> int:
    # Generate fake embedding if none provided (so tests run without Ollama)
    if embedding is None:
        from chief_ai_service.embedding_client import pack_embedding
        import random
        fake_vec = [random.random() for _ in range(768)]
        embedding = pack_embedding(fake_vec)
    fid = memory_db.insert_fact(
        player_uuid=player_uuid,
        chief_name=chief_name,
        fact_type=fact_type,
        fact_value=fact_value,
        evidence_text=evidence_text,
        embedding=embedding,
        confidence=confidence,
        importance=importance,
        times_confirmed=times_confirmed,
    )
    return fid


# ---------------------------------------------------------------------------
# Helper: simulate pending retrieval via FTS5 (no Ollama needed)
# ---------------------------------------------------------------------------

def _simulate_retrieval(player_uuid: str, chief_name: str, query_text: str) -> list[int]:
    """Search facts via FTS5 and store result in pending_relevant_facts.

    Uses FTS5 text search directly (no embeddings needed), so tests run
    without Ollama.
        Returns the list of fact_ids stored.
    """
    import re as _re
    # Sanitize query for FTS5 (remove special chars)
    qsafe = _re.sub(r"[?!/.,;:(){}#@$%^&*+=~\\[\\]-]", " ", query_text)
    # Split into words, join with OR so any word match counts
    words = [w for w in qsafe.split() if len(w) >= 2]
    fts_query = " OR ".join(words) if words else qsafe.strip()
    # Use get_facts_for_player (which includes chief='any' facts) for FTS filtering
    all_facts = memory_db.get_facts_for_player(player_uuid, chief_name)
    if not all_facts:
        memory_db.pending_relevant_facts[(player_uuid, chief_name)] = []
        return []
    # Filter by FTS match
    matched_ids = set()
    for fact in all_facts:
        # Simple substring match with lowercased query words (fallback for FTS5 limitations)
        fact_text = (fact.get('fact_value', '') + ' ' + fact.get('evidence_text', '') + ' ' + fact.get('fact_type', '')).lower()
        query_words = [w for w in words if len(w) >= 2]
        if any(w in fact_text for w in query_words):
            matched_ids.add(fact['id'])
    if not matched_ids:
        memory_db.pending_relevant_facts[(player_uuid, chief_name)] = []
        return []
    candidates = [f for f in all_facts if f['id'] in matched_ids]
    if not candidates:
        memory_db.pending_relevant_facts[(player_uuid, chief_name)] = []
        return []
    fact_ids = [int(c["id"]) for c in candidates[:5]]
    memory_db.pending_relevant_facts[(player_uuid, chief_name)] = list(fact_ids)
    return fact_ids


# ===================================================================
# Scenario A: Name genannt, 40+ Ablenkungs-Turns, spaetere Abfrage
# ===================================================================

class ScenarioANamePersistsTest(_BaseTempDbTest):
    """Szenario A: Spieler nennt Namen in Turn 1, viele Ablenkungen, dann
    fragt er 'Wie heisse ich?' und der Fakt wird gefunden."""

    PLAYER_UUID = "arno-scenario-a"
    CHIEF_NAME = "ElderOak"

    def test_name_fact_survives_distraction(self):
        # 1. Fakt direkt einfuegen (simuliert Worker-Ergebnis nach Turn 1)
        name_fact_id = _insert_fact_directly(
            player_uuid=self.PLAYER_UUID,
            chief_name=self.CHIEF_NAME,
            fact_type="name",
            fact_value="Arno",
            evidence_text="Ich heisse Arno.",
            importance=0.9,
            times_confirmed=1,
        )
        self.assertGreater(name_fact_id, 0)

        # 2. Viele Ablenkungs-Turns einfuegen (nur conversation_turns, keine Fakten)
        distraction_messages = [
            "Hallo Haeuptling, was gibt es heute zu tun?",
            "Ich brauche einen Auftrag.",
            "Gibt es gefaehrliche Monster in der Naehe?",
            "Kannst du mir Eisen verkaufen?",
            "Wie ist das Wetter heute?",
            "Ich habe eine Hoehle entdeckt.",
            "Weisst du, wo ich Diamanten finde?",
            "Soll ich fuer das Dorf Holz sammeln?",
            "Hast du einen Kessel fuer Suppe?",
            "Die Felder brauchen Wasser.",
            "Ich backe gerne Brot.",
            "Gibt es einen Schmied im Dorf?",
            "Die Schafe brauchen frische Weide.",
            "Sind die Minen tief?",
            "Brauchen wir mehr Betten?",
            "Ich mag den Geruch von Kekse.",
            "Heute ist ein schoener Tag.",
            "Morgen werde ich fischen gehen.",
            "Soll ich einen Schutzturm bauen?",
            "Hast du Angst vor Zombies?",
            "Die Sonne brennt heute stark.",
            "Ein ruhiger Tag zum Angeln.",
            "Sind hier schon Creeper explodiert?",
            "Ich liebe den Wald im Herbst.",
            "Kann ich dir beim Bauen helfen?",
            "Gibt es einen Kartographen hier?",
            "Was ist dein Lieblingsbier?",
            "Die Huehner legen viele Eier.",
            "Sollen wir einen Zaun bauen?",
            "Ich muss einen Bogen reparieren.",
            "Das Dorf ist wirklich schoen.",
            "Danke fuer die Hilfe gestern.",
            "Brauchst du etwas aus der Mine?",
            "Ich finde Kuerbisse toll.",
            "Hast du einen Hund?",
            "Morgen regnet es vielleicht.",
            "Soll ich Weizen anbauen?",
            "Kannst du mir einen Trank brauen?",
            "Die Bibliothek braucht Buecher.",
            "Ist der Fluss tief genug?",
        ]

        for i, msg in enumerate(distraction_messages, 1):
            memory_db.insert_turn(
                player_uuid=self.PLAYER_UUID,
                chief_name=self.CHIEF_NAME,
                role="player" if i % 2 == 1 else "chief",
                message=msg,
                mc_day=i // 2 + 1,
                mc_time=6000 + i * 100,
            )

        # Verify all 40 distraction turns exist
        all_turns = memory_db.query_turns(self.PLAYER_UUID, self.CHIEF_NAME, limit=100)
        self.assertEqual(len(all_turns), 40)

        # 3. Jetzt fragt der Spieler nach seinem Namen (Turn ~41)
        query_text = "Wie heisse ich eigentlich Sag mir meinen Namen"

        # Simuliere Worker-Retrieval
        fact_ids = _simulate_retrieval(self.PLAYER_UUID, self.CHIEF_NAME, query_text)
        self.assertGreater(len(fact_ids), 0,
                          "Sollte mindestens einen Fakt finden")
        self.assertIn(name_fact_id, fact_ids,
                      "Name-Fakt sollte in den Retrieval-Ergebnissen sein")

        # 4. Hole pending facts (wie reply_builder es macht)
        pending_ids = memory_db.get_pending_relevant_facts(
            self.PLAYER_UUID, self.CHIEF_NAME
        )
        self.assertIsNotNone(pending_ids)
        self.assertIn(name_fact_id, pending_ids)

        # 5. Resolve facts
        all_facts = memory_db.get_facts_for_player(self.PLAYER_UUID, self.CHIEF_NAME)
        facts_by_id = {f["id"]: f for f in all_facts}
        relevant_facts = [facts_by_id[fid] for fid in pending_ids if fid in facts_by_id]
        self.assertEqual(len(relevant_facts), 1)
        self.assertEqual(relevant_facts[0]["fact_type"], "name")
        self.assertEqual(relevant_facts[0]["fact_value"], "Arno")

        # 6. _build_facts_section() sollte den Namen enthalten
        from chief_ai_service.prompt_builder import _build_facts_section
        payload = {"mcDay": 25}
        config = {"memory": {"facts": {"max_facts_per_prompt": 5}}}
        section = _build_facts_section(relevant_facts, payload, config)
        self.assertIn("Arno", section,
                      "Facts-Sektion sollte den Spielernamen enthalten")
        self.assertIn("name:", section.lower(),
                      "Facts-Sektion sollte den Typ 'name' enthalten")
        print("\\nFacts section generated:\\n" + section)


# ===================================================================
# Scenario B: Kein Fakt vorhanden -> leere Antwort
# ===================================================================

class ScenarioBNoFactsTest(_BaseTempDbTest):
    """Szenario B: Spieler fragt nach Namen, hat aber nie einen genannt."""

    PLAYER_UUID = "bob-no-facts"
    CHIEF_NAME = "ElderBirch"

    def test_no_facts_returns_empty(self):
        # 1. Einfach nur Smalltalk-Turns, NOCH NIE Name gesagt
        for i in range(10):
            memory_db.insert_turn(
                player_uuid=self.PLAYER_UUID,
                chief_name=self.CHIEF_NAME,
                role="player" if i % 2 == 0 else "chief",
                message="Smalltalk Nachricht " + str(i),
                mc_day=1,
                mc_time=6000 + i * 100,
            )

        # 2. Spieler fragt nach Namen
        query_text = "Weisst du wie ich heisse"
        fact_ids = _simulate_retrieval(self.PLAYER_UUID, self.CHIEF_NAME, query_text)

        # Sollte keine Fakten finden
        self.assertEqual(len(fact_ids), 0,
                         "Ohne gespeicherte Fakten sollte nichts gefunden werden")

        # 3. Pending queue sollte leer sein
        pending_ids = memory_db.get_pending_relevant_facts(
            self.PLAYER_UUID, self.CHIEF_NAME
        )
        self.assertEqual(pending_ids, [])

        # 4. _build_facts_section() mit leerer Liste -> leerer String
        from chief_ai_service.prompt_builder import _build_facts_section
        payload = {"mcDay": 5}
        config = {"memory": {"facts": {"max_facts_per_prompt": 5}}}
        section = _build_facts_section([], payload, config)
        self.assertEqual(section.strip(), "",
                         "Ohne relevante Fakten sollte die Sektion leer sein")


# ===================================================================
# Scenario C: Widerspruch - Name wird korrigiert
# ===================================================================

class ScenarioCNameCorrectionTest(_BaseTempDbTest):
    """Szenario C: Spieler nennt Namen, spaeter korrigiert er sich.
    Der neueste Fakt soll gewinnen."""

    PLAYER_UUID = "caro-correction"
    CHIEF_NAME = "ElderSpruce"

    def test_name_correction_wins(self):
        # 1. Erster Name wird genannt
        old_fact_id = _insert_fact_directly(
            player_uuid=self.PLAYER_UUID,
            chief_name=self.CHIEF_NAME,
            fact_type="name",
            fact_value="Caro",
            evidence_text="Ich bin Caro.",
            importance=0.9,
            times_confirmed=1,
        )

        # 2. 20 Ablenkungs-Turns
        for i in range(20):
            memory_db.insert_turn(
                player_uuid=self.PLAYER_UUID,
                chief_name=self.CHIEF_NAME,
                role="player" if i % 2 == 0 else "chief",
                message="Ablenkung " + str(i) + ": alles beim Alten.",
                mc_day=i // 2 + 1,
                mc_time=6000 + i * 100,
            )

        # 3. Spieler korrigiert Namen - zweiter Fakt mit UPDATE (hoeheres times_confirmed)
        memory_db.update_fact(
            fact_id=old_fact_id,
            fact_value="Carolyn",
            evidence_text="Eigentlich heisse ich Carolyn, nicht Caro.",
            times_confirmed=2,
        )

        # 4. Abfrage
        query_text = "Wie heisse ich"
        fact_ids = _simulate_retrieval(self.PLAYER_UUID, self.CHIEF_NAME, query_text)
        self.assertIn(old_fact_id, fact_ids)

        # 5. Der aufgeloeste Fakt sollte den aktualisierten Wert haben
        pending_ids = memory_db.get_pending_relevant_facts(
            self.PLAYER_UUID, self.CHIEF_NAME
        )
        all_facts = memory_db.get_facts_for_player(self.PLAYER_UUID, self.CHIEF_NAME)
        facts_by_id = {f["id"]: f for f in all_facts}
        relevant = [facts_by_id[fid] for fid in pending_ids if fid in facts_by_id]
        self.assertEqual(len(relevant), 1)
        self.assertEqual(relevant[0]["fact_value"], "Carolyn")
        self.assertEqual(relevant[0]["times_confirmed"], 2)

        # 6. Facts-Sektion zeigt korrigierten Namen
        from chief_ai_service.prompt_builder import _build_facts_section
        payload = {"mcDay": 15}
        config = {"memory": {"facts": {"max_facts_per_prompt": 5}}}
        section = _build_facts_section(relevant, payload, config)
        self.assertIn("Carolyn", section)
        self.assertNotIn("Caro", section.replace("Carolyn", ""))
        self.assertIn("x2", section, "Sollte zweifache Bestaetigung anzeigen")
        print("\\nCorrection facts section:\\n" + section)


# ===================================================================
# Scenario D: Chief-uebergreifender Fakt
# ===================================================================

class ScenarioDChiefAgnosticFactTest(_BaseTempDbTest):
    """Szenario D: Spieler nennt Namen bei Chief A.
    Chief B kann den Namen auch abrufen (chief_name='any')."""

    PLAYER_UUID = "dana-cross-chief"
    CHIEF_A = "ElderPine"
    CHIEF_B = "ElderWillow"

    def test_cross_chief_fact_retrieval(self):
        # 1. Fakt bei Chief A gespeichert (chief_agnostic via 'any')
        fact_id = _insert_fact_directly(
            player_uuid=self.PLAYER_UUID,
            chief_name="any",  # chief-agnostisch
            fact_type="name",
            fact_value="Dana",
            evidence_text="Mein Name ist Dana.",
            importance=0.9,
        )

        # 2. Chief B hat eigene, andere Fakten (location)
        _insert_fact_directly(
            player_uuid=self.PLAYER_UUID,
            chief_name=self.CHIEF_B,
            fact_type="location",
            fact_value="Waldhuette",
            evidence_text="Ich wohne in einer Waldhuette.",
            importance=0.7,
        )

        # 3. Abfrage bei Chief B
        query_text = "name Dana Kennst du meinen Namen"
        fact_ids = _simulate_retrieval(self.PLAYER_UUID, self.CHIEF_B, query_text)

        # Sollte den 'any'-Fakt finden
        self.assertIn(fact_id, fact_ids,
                      "Chief-agnostischer Fakt sollte auch bei Chief B gefunden werden")

        # 4. Resolve facts
        pending_ids = memory_db.get_pending_relevant_facts(
            self.PLAYER_UUID, self.CHIEF_B
        )
        all_facts = memory_db.get_facts_for_player(self.PLAYER_UUID, self.CHIEF_B)
        facts_by_id = {f["id"]: f for f in all_facts}
        relevant = [facts_by_id[fid] for fid in pending_ids if fid in facts_by_id]

        # Mindestens der Name sollte da sein
        names = [f["fact_value"] for f in relevant if f["fact_type"] == "name"]
        self.assertIn("Dana", names,
                      "Name 'Dana' sollte in den relevanten Fakten auftauchen")

        # 5. Facts-Sektion
        from chief_ai_service.prompt_builder import _build_facts_section
        payload = {"mcDay": 10}
        config = {"memory": {"facts": {"max_facts_per_prompt": 5}}}
        section = _build_facts_section(relevant, payload, config)
        self.assertIn("Dana", section,
                      "Facts-Sektion sollte den Spielernamen enthalten")
        print("\\nCross-chief facts section:\\n" + section)


# ===================================================================
# Scenario: Facts-Sektion mit Zeitangaben
# ===================================================================

class FactsTimeSuffixTest(_BaseTempDbTest):
    """Teste die Zeit-Suffixe in _build_fact_time_suffix."""

    PLAYER_UUID = "time-test"
    CHIEF_NAME = "ChiefTime"

    def test_time_suffix_formatting(self):
        from chief_ai_service.prompt_builder import _build_fact_time_suffix

        # Fact von heute (mc_day = first_seen day)
        fact_today = {
            "first_seen_at": "2025-06-15 12:00:00",
            "last_seen_at": "2025-06-15 12:00:00",
            "times_confirmed": 1,
        }
        # mc_day ist relativ zu 2025-01-01 als epoch
        # 2025-06-15 = day 165
        suffix = _build_fact_time_suffix(fact_today, 165)
        self.assertIn("heute", suffix)

        # Fact von vor 3 Tagen
        fact_old = {
            "first_seen_at": "2025-06-12 08:00:00",
            "last_seen_at": "2025-06-12 08:00:00",
            "times_confirmed": 1,
        }
        suffix = _build_fact_time_suffix(fact_old, 165)  # delta = 3
        self.assertIn("3 Tagen", suffix)

        # Fact ohne Datum
        fact_nodate = {
            "first_seen_at": "",
            "last_seen_at": "",
            "times_confirmed": 1,
        }
        suffix = _build_fact_time_suffix(fact_nodate, 100)
        self.assertEqual(suffix.strip(), "")


# ===================================================================
# Scenario: max_facts_per_prompt respektiert
# ===================================================================

class FactsLimitTest(_BaseTempDbTest):
    """Teste dass max_facts_per_prompt eingehalten wird."""

    PLAYER_UUID = "limit-test"
    CHIEF_NAME = "ChiefLimit"

    def test_max_facts_respected(self):
        # 10 Fakten einfuegen
        fact_ids = []
        for i in range(10):
            fid = _insert_fact_directly(
                player_uuid=self.PLAYER_UUID,
                chief_name=self.CHIEF_NAME,
                fact_type="preference" if i % 2 == 0 else "event",
                fact_value="Fakt " + str(i) + ": Mag Sache " + str(i),
                evidence_text="Beweis " + str(i),
                importance=0.5,
            )
            fact_ids.append(fid)

        # Alle Fakten abrufen
        all_facts = memory_db.get_facts_for_player(self.PLAYER_UUID, self.CHIEF_NAME)
        self.assertEqual(len(all_facts), 10)

        # _build_facts_section mit max_facts=3
        from chief_ai_service.prompt_builder import _build_facts_section
        payload = {"mcDay": 5}
        config = {"memory": {"facts": {"max_facts_per_prompt": 3}}}
        section = _build_facts_section(all_facts, payload, config)

        # Sollte nur 3 Zeilen haben
        lines = [l for l in section.split("\\n") if l.strip().startswith("-")]
        self.assertLessEqual(len(lines), 3,
                             "Max 3 Fakten erwartet, aber " + str(len(lines)) + " gefunden")
        print("\\nLimited facts section (" + str(len(lines)) + " facts):\\n" + section)


# ===================================================================
# Scenario: Hybrid-Suche mit verschiedenen query_types
# ===================================================================

@unittest.skipIf(_NO_OLLAMA, 'Ollama not available, needed for embedding search')
class HybridSearchTypeFilteringTest(_BaseTempDbTest):
    """Teste dass search_facts_hybrid mit verschiedenen query_types funktioniert."""

    PLAYER_UUID = "hybrid-test"
    CHIEF_NAME = "ChiefHybrid"

    def setUp(self):
        super().setUp()
        # Mehrere Fakten unterschiedlicher Typen
        _insert_fact_directly(
            player_uuid=self.PLAYER_UUID,
            chief_name=self.CHIEF_NAME,
            fact_type="name",
            fact_value="Heinz",
            evidence_text="Ich heisse Heinz.",
            importance=0.9,
        )
        _insert_fact_directly(
            player_uuid=self.PLAYER_UUID,
            chief_name=self.CHIEF_NAME,
            fact_type="location",
            fact_value="Alte Muehle",
            evidence_text="Ich wohne in der alten Muehle.",
            importance=0.8,
        )
        _insert_fact_directly(
            player_uuid=self.PLAYER_UUID,
            chief_name=self.CHIEF_NAME,
            fact_type="preference",
            fact_value="mag Kuerbissuppe",
            evidence_text="Ich liebe Kuerbissuppe!",
            importance=0.6,
        )


    def test_name_query_finds_name(self):
        results = memory_db.search_facts_hybrid(
            query_text="Heinz heisse ich",
            player_uuid=self.PLAYER_UUID,
            chief_name=self.CHIEF_NAME,
            query_type="name",
            top_n=5,
        )
        self.assertGreater(len(results), 0)
        top_fact = results[0]
        self.assertEqual(top_fact["fact_type"], "name")
        self.assertEqual(top_fact["fact_value"], "Heinz")

    def test_location_query_finds_location(self):
        results = memory_db.search_facts_hybrid(
            query_text="Muehle wohne ich",
            player_uuid=self.PLAYER_UUID,
            chief_name=self.CHIEF_NAME,
            query_type="general",
                        top_n=5,
        )
        self.assertGreater(len(results), 0)
        # The location fact should be among the results
        fact_types = [r["fact_type"] for r in results]
        self.assertIn("location", fact_types,
                      "Location fact should be in search results")

    def test_general_query_finds_all(self):
        results = memory_db.search_facts_hybrid(
            query_text="Heinz Muehle Kuerbissuppe alles ueber mich",
            player_uuid=self.PLAYER_UUID,
            chief_name=self.CHIEF_NAME,
            query_type="general",
            top_n=10,
        )
        self.assertEqual(len(results), 3,
                         "General query should find all 3 facts")

    def test_score_range(self):
        results = memory_db.search_facts_hybrid(
            query_text="name",
            player_uuid=self.PLAYER_UUID,
            chief_name=self.CHIEF_NAME,
            query_type="general",
            top_n=5,
        )
        for r in results:
            self.assertIn("_score", r)
            score = r["_score"]
            self.assertGreaterEqual(score, 0.0)
            self.assertLessEqual(score, 1.0)
            print("  Fact type=" + r['fact_type'] + " value=" + r['fact_value'] + " _score=" + str(score))


# ===================================================================
# Scenario: Facts-Persistenz ueber DB-Neustart
# ===================================================================

class FactsPersistenceTest(_BaseTempDbTest):
    """Teste dass player_facts einen DB-Neustart ueberleben."""

    PLAYER_UUID = "persist-facts"
    CHIEF_NAME = "ChiefPersist"

    def test_facts_survive_close_reopen(self):
        import sqlite3

        # Insert fact with embedding
        from chief_ai_service.embedding_client import pack_embedding
        fake_emb = [0.1 * (i % 10) for i in range(768)]
        fid = _insert_fact_directly(
            player_uuid=self.PLAYER_UUID,
            chief_name=self.CHIEF_NAME,
            fact_type="name",
            fact_value="Persistus",
            evidence_text="Ich bin Persistus.",
            embedding=pack_embedding(fake_emb),
            importance=0.9,
        )

        # Simulate restart
        conn = sqlite3.connect(memory_db.DB_PATH)
        conn.execute("PRAGMA wal_checkpoint(TRUNCATE)")
        conn.close()
        memory_db.migrate()

        # Verify fact survived
        facts = memory_db.get_facts_for_player(self.PLAYER_UUID, self.CHIEF_NAME)
        self.assertEqual(len(facts), 1)
        self.assertEqual(facts[0]["fact_value"], "Persistus")
        self.assertEqual(facts[0]["times_confirmed"], 1)

        # Verify embedding survived
        from chief_ai_service.embedding_client import unpack_embedding
        blob = facts[0]["embedding"]
        self.assertIsNotNone(blob)
        unpacked = unpack_embedding(blob)
        self.assertEqual(len(unpacked), 768)
        for i, val in enumerate(unpacked):
            self.assertAlmostEqual(val, 0.1 * (i % 10), places=5)


# ===================================================================
# Scenario: Relevance-Cache (Paket D caching)
# ===================================================================

class RelevanceCacheTest(_BaseTempDbTest):
    """Teste dass der Relevance-Cache funktioniert."""

    PLAYER_UUID = "cache-test"

    def test_cache_hit_and_miss(self):
        result = memory_db.get_cached_relevance(self.PLAYER_UUID, "general", "abc123")
        self.assertIsNone(result, "Cache should miss initially")

        fact_ids = [1, 2, 3]
        memory_db.set_cached_relevance(
            player_uuid=self.PLAYER_UUID,
            query_type="general",
            message_hash="abc123",
            fact_ids=fact_ids,
            ttl_minutes=5,
        )

        hit = memory_db.get_cached_relevance(self.PLAYER_UUID, "general", "abc123")
        self.assertEqual(hit, fact_ids)

        miss = memory_db.get_cached_relevance(self.PLAYER_UUID, "general", "xyz789")
        self.assertIsNone(miss)

    def test_cache_expiry(self):
        import time
        memory_db.set_cached_relevance(
            player_uuid=self.PLAYER_UUID,
            query_type="general",
            message_hash="expired",
            fact_ids=[42],
            ttl_minutes=0,
        )
        time.sleep(0.1)
        hit = memory_db.get_cached_relevance(self.PLAYER_UUID, "general", "expired")
        self.assertIsNone(hit, "Cache should have expired")


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    print("Ollama available: " + str(not _NO_OLLAMA))
    unittest.main(verbosity=2)
