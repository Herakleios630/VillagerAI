---
title: "Arbeitsauftrag: Abnahme-Szenarien Phase 4b"
quelle: "roadmap-memory.md → Phase 4b, Abnahme-Szenarien A1–A3"
related-roadmap: "Plannung/roadmap-memory.md#phase-4b"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: Abnahme-Szenarien Phase 4b

**Quelle:** roadmap-memory.md → Phase 4b, Abnahme-Szenarien 4b-A1 bis 4b-A3

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3 (Bridge), Java 21 (Plugin)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Manuelle Abnahme aller 3 Szenarien der Phase 4b auf dem Live-Server:

- **4b-A1** Turns mit 3 MC-Tagen Abstand → Prompt zeigt `[vor 3 Tagen]`
- **4b-A2** Turn am Abend (mcTime=20000) → Prompt zeigt `[vor 3 Tagen, am Abend]`
- **4b-A3** Gleicher Tag (mcDay-Differenz 0) → Prompt zeigt `[heute, am Morgen]`

## Aktuelles Ergebnis
- Phase 4b ist implementiert (4b-1 bis 4b-6).
- Abnahme steht noch aus.

## Betroffene Dateien
Keine Code-Änderungen – reine Abnahme.

## Erbetene Hilfe
1. Szenario 4b-A1: 2 Turns mit 3 Tagen Abstand einstellen (mcDay manuell setzen), Trigger-Frage → Zeitangabe prüfen
2. Szenario 4b-A2: Turn mit mcTime=20000 → Prompt zeigt Abend-Phrase
3. Szenario 4b-A3: 2 Turns am selben Tag → Prompt zeigt "heute"
4. Fehler protokollieren und ggf. Fix-Tickets erstellen
5. **Sync nach Abschluss:** docs/handover.md, Plannung/roadmap.md (Phase 4b abhaken)

## Technische Randbedingungen (wiederverwendbar)
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md