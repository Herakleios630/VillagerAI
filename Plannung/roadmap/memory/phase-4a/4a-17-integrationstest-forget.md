---
title: "Arbeitsauftrag: Integrationstest /chief forget"
quelle: "roadmap-memory.md → Phase 4a, Aufgabe 4a-17"
related-roadmap: "Plannung/roadmap-memory.md#phase-4a"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: Integrationstest /chief forget

**Quelle:** roadmap-memory.md → Phase 4a, Aufgabe 4a-17

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3 (Bridge), Java 21 (Plugin)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Integrationstest: DELETE /v1/chief/forget auf eine leere Datenbank senden. Muss HTTP 404 zurückgeben.
Danach 5 Turns einfügen, forget aufrufen, prüfen dass alle Turns gelöscht sind (HTTP 204). Erneuter
forget auf leere DB → wieder 404.

## Aktuelles Ergebnis
- DELETE-Endpoint implementiert (4a-12).
- Integrationstest `chief-ai-service/tests/test_integration_forget.py` erstellt mit 6 Tests, alle grün.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/tests/test_integration_forget.py` | NEU anlegen |

## Ergebnis
1. Test 1: DELETE auf leere DB → HTTP 404, Body: {"error": "no_entries", ...} ✅
2. Test 2: 5 Turns + Summary einfügen, DELETE → HTTP 204, beides gelöscht ✅
3. Test 3: Cross-Player-Isolation: nur eigener Spieler gelöscht, anderer bleibt ✅
4. Test 4: Erneuter DELETE → HTTP 404 ✅
5. Test 5: DELETE ohne player_uuid → HTTP 400 ✅
6. Test 6 (Bonus): DELETE mit leerem player_uuid → HTTP 400 ✅
7. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen (wiederverwendbar)
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md