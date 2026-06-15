---\ntitle: "Arbeitsauftrag: memory_db.py Schema finalisieren"
quelle: "roadmap-memory.md → Phase 4a, Aufgabe 4a-0"
related-roadmap: "Plannung/roadmap-memory.md#phase-4a"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: memory_db.py Schema finalisieren

**Quelle:** roadmap-memory.md → Phase 4a, Aufgabe 4a-0

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3 (Bridge), Java 21 (Plugin)
- **Build-Tool:**       Gradle (Kotlin DSL, Java); kein Build für Python
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service

## Auftrag
`memory_db.py` als neue Datei im Bridge-Dienst anlegen. SQLite-Datenbank `chief-ai-service/memory.db` mit folgenden Tabellen und Indexen erstellen:

**Tabelle `conversation_turns`:**
| Spalte | Typ | Bemerkung |
|---|---|---|
| id | INTEGER PRIMARY KEY AUTOINCREMENT | |
| player_uuid | TEXT NOT NULL | |
| chief_name | TEXT NOT NULL | |
| role | TEXT NOT NULL | 'player' oder 'chief' |
| message | TEXT NOT NULL | Rohtext |
| embedding | BLOB | Embedding-Vektor von nomic-embed-text |
| mc_day | INTEGER DEFAULT 0 | Minecraft-Tag seit Weltstart |
| mc_time | INTEGER DEFAULT 0 | Ticks des aktuellen Tages (0–23999) |
| is_archived | INTEGER DEFAULT 0 | 0=aktiv, 1=archiviert |
| created_at | TEXT DEFAULT (datetime('now')) | |

**Tabelle `memory_summaries`:**
| Spalte | Typ | Bemerkung |
|---|---|---|
| id | INTEGER PRIMARY KEY AUTOINCREMENT | |
| player_uuid | TEXT NOT NULL | |
| chief_name | TEXT NOT NULL | |
| summary_text | TEXT NOT NULL | Rolling Summary von qwen2.5:3b |
| turn_range_start | INTEGER | Erster Turn dieser Summary |
| turn_range_end | INTEGER | Letzter Turn dieser Summary |
| reputation_at_summary | INTEGER DEFAULT 0 | Reputation-Score zum Zeitpunkt der Summary |
| created_at | TEXT DEFAULT (datetime('now')) | |

**Indexe:** `idx_turns_player` (player_uuid, chief_name), `idx_turns_role`, `idx_turns_mc_day`, `idx_summaries_player`

**Weitere Anforderung:** Die bestehende leere `game-memory.db` im Projektroot löschen.

## Aktuelles Ergebnis
- Kein Langzeitspeicher in der Bridge. Conversation-History existiert nur flüchtig im RAM.
- `game-memory.db` existiert im Projektroot, ist aber leer.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/memory_db.py` | NEU anlegen – Schema, Create-Tables |
| `game-memory.db` | LÖSCHEN (leer) |

## Erbetene Hilfe – ToDo-Liste
1. `chief-ai-service/memory_db.py` anlegen mit `create_tables()`-Funktion
2. Beide Tabellen mit exakten Spaltendefinitionen, NOT NULL / DEFAULT Constraints
3. Alle vier Indexe anlegen
4. `game-memory.db` löschen
5. Test: `memory.db` wird im angegebenen Pfad erstellt
6. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file`
- **Große Java-Dateien (>300 Z.):** Mit `filesystem_read_text_file` lesen
- **Build:** `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar`
- **Deploy:** `scp` JAR + `sudo systemctl restart crafty`; Bridge zuerst
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, docs/handover.md, Plannung/roadmap.md