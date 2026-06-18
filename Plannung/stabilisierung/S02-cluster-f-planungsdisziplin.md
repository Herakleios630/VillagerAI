---
title: "Arbeitsauftrag: Stabilisierung S02 – Cluster F: Planungs-Disziplin wiederherstellen"
quelle: "Plannung/konzept-stabilisierung.md → Cluster F"
created: "2025-07-17"
status: done
---

# Arbeitsauftrag: Stabilisierung S02 – Cluster F: Planungs-Disziplin wiederherstellen

**Quelle:** Plannung/konzept-stabilisierung.md → Cluster F (Planungs-Disziplin)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin VillagerAI
- **Quellsprache:**     Java 21 (Plugin), Python 3.x (Bridge)
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI

## Auftrag

SAEMTLICHE Planungskarten in allen Ordnern auf ihren tatsaechlichen Status pruefen und korrigieren.

Ziel: Eine belastbare, ehrliche Planungsgrundlage schaffen, BEVOR die weiteren Stabilisierungs-Cluster (B–E) bearbeitet werden.

## Aktuelles Ergebnis

Die Gap-Analyse hat ergeben:
- Mehrere Karten in `chief-villager-umbau` stehen auf `done`, obwohl der Code noch Mischzustaende/Altlasten enthaelt (z.B. 04-conversationrole-npc, 10-conversationservice-umbau, 13-bridge-python-anpassen, 14c-plugin-init-yml)
- Bugfix-Karten haengen auf `in-progress` ohne dokumentierten Fortschritt (05-mourning, 10-chiefs-duplikate)
- Bugfix-Karten auf `todo` ohne Arbeitsbeginn (07-none-beruf)
- Ad-hoc-Karten auf `in-progress`, die exakt zu den gefundenen Inkonsistenzen passen (09e, 09f, a3)
- `village-fixes`-Karten teilweise in-progress (05, 08)
- `4 fixes` alle vier Karten auf in-progress

Ohne Korrektur dieser Stati ist keine zuverlaessige Arbeitsplanung moeglich.

## Ursachenverdacht

- Karten wurden waehrend der Arbeit nicht nachgehalten (Status nicht aktualisiert)
- Done wurde voreilig gesetzt, als die Grundstruktur stand, ohne die Feinheiten abzuschliessen
- Dateiverlust ("nicht gespeichert") hat den Code-Zustand verschlechtert, ohne dass die Karten zurueckgesetzt wurden

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `Plannung/chief-villager-umbau/*.md` (18 Karten) | Umbau-Karten – mehrere done-Markierungen fragwuerdig |
| `Plannung/bugfixes/*.md` (17 Karten) | Bugfix-Karten – mehrere noch offen oder in-progress |
| `Plannung/ad-hoc/*.md` (23+ Karten) | Ad-hoc-Karten – mehrere in-progress ohne Fortschritt |
| `Plannung/ad-hoc/village-fixes/*.md` (8 Karten) | Village-Fix-Karten – zwei in-progress |
| `Plannung/ad-hoc/4 fixes/*.md` (4 Karten) | Vier Fixes – alle in-progress |
| `Plannung/konzept-stabilisierung.md` | Referenzdokument – nach Abschluss Cluster F abhaken |

## Erbetene Hilfe

1. **Karten-Inventur:** JEDE Karte in den fuenf Ordnern oeffnen und `status:`-Feld mit dem tatsaechlichen Code-Zustand abgleichen.
2. **Status korrigieren:** Karten, die faelschlich `done` sind, auf `in-progress` zuruecksetzen. Im Abschnitt `## Notizen` dokumentieren, WAS konkret noch fehlt (Code-Stellen, Symptome).
3. **Echte done-Karten** belassen, aber kurz pruefen ob die Akzeptanzkriterien tatsaechlich erfuellt sind.
4. **Offene Working-Cards identifizieren**, die direkt zu den Stabilisierungs-Clustern B–E passen:
   - B (Rollenmodell): 04-conversationrole-npc, 10-conversationservice-umbau, 05-airequest-anpassen
   - C (Bridge-Mapping): 13-bridge-python-anpassen, ad-hoc/memory-enabled-payload
   - D (Prompt-Hoheit): ad-hoc/chief-weiss-nicht-dass-er-chief-ist, bugfix/aufgabe-09-prompt-hoheit
   - E (Persistenz): 14c-plugin-init-yml, 14b-villagerprofile-loeschen
5. **Cross-Referenz-Tabelle** erstellen und in `Plannung/stabilisierung/kartenmatrix.md` ablegen:
   - Pro Karte: Dateiname, aktueller Status, korrigierter Status, zugeordnet zu Cluster (A–F), naechste konkrete Aktion
6. Kein Coding. Nur Planungsdateien editieren.

## Akzeptanzkriterien

- JEDE Karte in allen Planungsordnern hat einen `status:`, der zum tatsaechlichen Code-Zustand passt
- Keine Karte steht auf `done`, wenn der Code noch Altlasten oder Inkonsistenzen enthaelt
- Zu jedem unfertigen Status existiert eine konkrete Notiz, WAS noch fehlt
- `Plannung/stabilisierung/kartenmatrix.md` existiert und enthaelt die Cross-Referenz aller Karten auf die Stabilisierungs-Cluster

## Technische Randbedingungen
- Kein Code – nur `.md`-Dateien editieren
- `filesystem_edit_file` oder `single_find_and_replace` fuer Status-Aenderungen verwenden
- Nach Abschluss: Plannung/konzept-stabilisierung.md (Cluster F abhaken)

"## Notizen (waehrend Bearbeitung)

### 2025-07-21: Karteninventur abgeschlossen

**65+ Karten in 5 Ordnern geprueft** (chief-villager-umbau, bugfixes, ad-hoc, village-fixes, 4 fixes).

**2 Falschmarkierungen korrigiert:**
- `04-conversationrole-npc.md`: done → in-progress (CHIEF noch im Enum)
- `13-bridge-python-anpassen.md`: done → in-progress (Bridge nutzt noch `chiefName` statt `displayName`)

**3 Deployment-Schulden identifiziert:** Aufgaben 09, 16, 17 – Code fertig und gebaut, nicht deployed.

**9 offene Working-Cards** fuer Cluster B–E identifiziert.

**Kartenmatrix:** `Plannung/stabilisierung/kartenmatrix.md` mit Cross-Referenz aller Karten.

### Akzeptanzkriterien-Check:
1. [x] JEDE Karte hat einen status der zum Code-Zustand passt
2. [x] Keine Karte steht auf done wenn Code Altlasten enthaelt (04, 13 korrigiert)
3. [x] Zu jedem unfertigen Status existiert eine konkrete Notiz
4. [x] kartenmatrix.md existiert

### Ready for Review. Cluster F kann nach Abnahme auf done gesetzt werden."
