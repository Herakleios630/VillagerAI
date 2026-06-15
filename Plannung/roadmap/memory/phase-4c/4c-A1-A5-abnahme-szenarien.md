---
title: "Arbeitsauftrag: Abnahme-Szenarien Phase 4c"
quelle: "roadmap-memory.md → Phase 4c, Abnahme-Szenarien A1–A5"
related-roadmap: "Plannung/roadmap-memory.md#phase-4c"
created: "2025-07-18"
status: in-progress
---

# Arbeitsauftrag: Abnahme-Szenarien Phase 4c

**Quelle:** roadmap-memory.md → Phase 4c, Abnahme-Szenarien 4c-A1 bis 4c-A5

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3 (Bridge)
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Manuelle Abnahme aller 5 Reputations-Szenarien der Phase 4c:

- **4c-A1** Ruf 85 → Summary: "Mhakari ist ein geschätzter Freund des Dorfes..."
- **4c-A2** Ruf 15 → Summary: "Mhakari war wieder im Dorf, die Stimmung war angespannt..."
- **4c-A3** Ruf 90 + Erinnerungsfrage → Chief: "Aber ja! Du hattest vor ein paar Tagen..."
- **4c-A4** Ruf 8 + gleiche Frage → Chief: "Ich erinnere mich vage. Aber eigentlich ist mir das egal."
- **4c-A5** Ruf -50 + Erinnerungsfrage → Chief verweigert Antwort: "Warum sollte ich dir das erzählen?"

## Aktuelles Ergebnis
- Phase 4c ist implementiert (4c-1 bis 4c-4). Abnahme steht aus.

## Erbetene Hilfe
1. Szenarien mit simulierten Reputation-Werten durchspielen (ggf. DB direkt editieren)
2. Summary-Tonfall prüfen (A1, A2)
3. Memory-Antwort-Tonfall prüfen (A3, A4, A5)
4. Fehler protokollieren und ggf. Fix-Tickets erstellen
5. **Sync nach Abschluss:** docs/handover.md, Plannung/roadmap.md (Phase 4c abhaken)

## Technische Randbedingungen (wiederverwendbar)
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md