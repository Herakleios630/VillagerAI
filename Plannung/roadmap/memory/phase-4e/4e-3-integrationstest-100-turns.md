---
title: "Arbeitsauftrag: Vollständiger Integrationstest 100 Turns"
quelle: "roadmap-memory.md → Phase 4e, Aufgabe 4e-3"
related-roadmap: "Plannung/roadmap-memory.md#phase-4e"
created: "2025-07-18"
status: in-progress
---

# Arbeitsauftrag: Vollständiger Integrationstest 100 Turns

**Quelle:** roadmap-memory.md → Phase 4e, Aufgabe 4e-3

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3 (Bridge)
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Vollständiger Integrationstest über alle 4 Memory-Phasen: 100 simulierte Turns durchspielen und
folgende Aspekte prüfen:
- **Summary**: Nach 20, 40, 60, 80, 100 Turns je eine Rolling Summary erstellt
- **MC-Zeit**: Zeitstempel in Memory-Turns korrekt gespeichert und formatiert
- **Reputation**: Summary-Tonfall ändert sich mit variierendem Reputation-Score
- **Embedding-Suche**: Trigger-Phrase findet semantisch ähnliche alte Turns
- **Archivierung**: Turns älter als 30 Tage werden archiviert und nicht mehr durchsucht

## Aktuelles Ergebnis
- Alle Einzelkomponenten aus Phase 4a–4d sind implementiert.
- Kein übergreifender Integrationstest vorhanden.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/tests/test_integration_100_turns.py` | NEU anlegen |

## Erbetene Hilfe
1. Testskript mit 100 simulierten POST-Requests an Bridge
2. Alle 20 Turns: Summary-Erstellung prüfen
3. mcDay variieren (0, 5, 10, 35) → Zeitstempel prüfen
4. Reputation variieren (85, 15, -50) → Summary-Tonfall prüfen
5. Trigger-Fragen an verschiedenen Punkten → semantische Suche prüfen
6. Nach Test: DB auf archivierte Turns prüfen (mcDay < -30)
7. Fehler protokollieren und ggf. Fix-Tickets erstellen
8. Deployment via SCP + `sudo systemctl restart villagerai-chief` + `sudo systemctl restart crafty`
9. **Sync nach Abschluss:** README.md, docs/developer-guide.md, docs/handover.md, Plannung/roadmap.md (Phase 4 komplett abhaken)

## Technische Randbedingungen (wiederverwendbar)
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md