---
title: "Arbeitsauftrag: Unit-Test prompt_builder.py"
quelle: "roadmap-memory.md → Phase 4a, Aufgabe 4a-15"
related-roadmap: "Plannung/roadmap-memory.md#phase-4a"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: Unit-Test prompt_builder.py

**Quelle:** roadmap-memory.md → Phase 4a, Aufgabe 4a-15

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3 (Bridge), Java 21 (Plugin)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Unit-Tests für `prompt_builder.py` schreiben: Prompt mit und ohne Memory-Treffer testen, korrekte
Sektionsreihenfolge prüfen, Summary-Einbettung validieren, leere Sektionen werden nicht gerendert.

## Aktuelles Ergebnis
- `prompt_builder.py` implementiert (4a-4, 4a-8b, 4a-9).
- Keine Unit-Tests vorhanden.
- **Vorarbeiten:** `check_memory_trigger()` fehlte im Quellcode (von `http_app.py` referenziert, aber nicht implementiert). `build_context_prompt()` war monolithisch.

## Ergebnis nach Bearbeitung
- `check_memory_trigger()` in `prompt_builder.py` implementiert (Regex, case-insensitive, escaped).
- `build_context_prompt()` auf Sektionen-Rendering refaktoriert (Signatur: `build_context_prompt(… memories=None, summary_text=None)`).
- Sektionsreihenfolge: Regeln → Knowledge → Dorf-Info → Persönlichkeit → Ruf → Status → [Memories] → [Summary] → Spieler-Nachricht.
- Leere Sektionen werden übersprungen; Prompt-Länge wird geloggt.
- Rückwärtskompatibel: Alte 2-Arg-Aufrufer funktionieren weiter.
- **32 Unit-Tests** in `tests/test_prompt_builder.py` geschrieben, alle grün.
- Bestehende Tests (`test_trigger_parser.py`, `test_embedding_client.py`, `test_memory_db.py`) laufen weiter.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/tests/test_prompt_builder.py` | NEU anlegen |

## Erbetene Hilfe
1. Test: Prompt ohne Memory – Memories-Sektion fehlt komplett
2. Test: Prompt mit Memory-Treffer – 3 Erinnerungen eingefügt
3. Test: Prompt mit Summary – Summary-Sektion vorhanden
4. Test: Prompt ohne Summary – Summary-Sektion fehlt
5. Test: Sektionsreihenfolge exakt: System → Knowledge → … → Nachricht
6. Test: Leere Sektionen werden nicht gerendert (keine leeren Überschriften)
7. Test: Prompt-Gesamtlänge loggt
8. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file`
- **Große Java-Dateien (>300 Z.):** Mit `filesystem_read_text_file` lesen
- **Build:** `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar`
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, docs/handover.md, Plannung/roadmap.md