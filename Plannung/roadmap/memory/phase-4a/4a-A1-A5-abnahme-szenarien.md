---
title: "Arbeitsauftrag: Abnahme-Szenarien Phase 4a"
quelle: "roadmap-memory.md → Phase 4a, Abnahme-Szenarien A1–A5"
related-roadmap: "Plannung/roadmap-memory.md#phase-4a"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: Abnahme-Szenarien Phase 4a

**Quelle:** roadmap-memory.md → Phase 4a, Abnahme-Szenarien 4a-A1 bis 4a-A5

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3 (Bridge), Java 21 (Plugin)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Manuelle Abnahme aller 5 Szenarien der Phase 4a auf dem Live-Server:

- **4a-A1** Normales Gespräch (<20 Turns): Nur Rohtext im Prompt, noch keine Summary
- **4a-A2** Erinnerungsfrage: Semantische Suche findet ähnliche alte Nachrichten
- **4a-A3** Erinnerungsfrage ohne Match: NPC lehnt ab („Daran erinnere ich mich nicht genau.")
- **4a-A4** Server-Neustart: SQLite überlebt, alle Daten sofort wieder da
- **4a-A5** `/chief forget` → alle Einträge des Spielers gelöscht, Gespräch beginnt bei Null

## Aktuelles Ergebnis
- Alle Memory-Komponenten aus Phase 4a sind implementiert.
- Abnahme steht noch aus.

## Betroffene Dateien
Keine Code-Änderungen – reine Abnahme.

## Erbetene Hilfe
1. Szenario 4a-A1 durchspielen: <20 Turns, Prompt-Log auf Summary-Sektion prüfen
2. Szenario 4a-A2 durchspielen: Turn über Diamanten, später Trigger-Frage, Chief erinnert sich
3. Szenario 4a-A3 durchspielen: Trigger-Frage zu nie besprochenem Thema → Ablehnung
4. Szenario 4a-A4: `sudo systemctl restart villagerai-chief` → DB-Inhalt prüfen
5. Szenario 4a-A5: `/chief forget` ausführen, danach neues Gespräch → keine alten Turns
6. Fehler protokollieren und ggf. Fix-Tickets erstellen
7. **Sync nach Abschluss:** docs/handover.md, Plannung/roadmap.md (Phase 4a abhaken)

## Technische Randbedingungen (wiederverwendbar)
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md