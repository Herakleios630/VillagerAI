# Stabilisierungskonzept – Chief-Villager-Umbau

> Erstellt auf Basis der Gap-Analyse vom 2025-07-17.
> Dieses Dokument beschreibt WAS zu tun ist und WARUM.
> Arbeitskarten werden daraus in einem zweiten Schritt abgeleitet.

---

## 1. Ausgangslage

Der Umbau von Chief-zentrischer zu Speaker-zentrischer Architektur ist zu ca. 85 % umgesetzt.  
Der Code ist jedoch in einem **inkonsistenten Mischzustand**: Neue Speaker-Modelle existieren parallel zu Legacy-Chief-Logik, mehrere Refactor-Schritte wurden nie vollständig abgeschlossen, und die Planungskarten lügen über den tatsächlichen Status (done ≠ fertig).

**Symptome:**
- NullPointerException bei Chat-Eingabe (`villager is null`), weil `VillagerInteractListener` null als Speaker durchreichen kann
- `ConversationRole.CHIEF` existiert weiterhin, obwohl durch `NPC` ersetzt werden sollte
- Bridge (Python) und Plugin (Java) nutzen unterschiedliche Feldnamen (`chiefName` vs. `displayName`)
- Prompt-Hoheit ist ungeklärt – Plugin und Bridge konkurrieren um System-Prompt-Inhalte
- Zwei getrennte Persistenzdateien: `chiefs.yml` (Loader) vs. `chief-attributes.yml` (Repository)

**Ursache:**
Kein Einzelbug, sondern ein unvollständig abgeschlossener Architektur-Umbau mit mangelhafter Planungspflege.

---

## 2. Problem-Cluster (was hängt zusammen)

### Cluster A: Null-Safety im Chat-Einstiegspfad
- **Betrifft:** `VillagerInteractListener` → `ConversationService.startConversation()` → `VillageIdentityService.resolveOrRegisterVillageId()`
- **Problem:** Der Listener übergibt potenziell null als Speaker/Villager an die nachgelagerten Services
- **Impact:** Crash bei jeder Chat-Eingabe, wenn kein gültiger Speaker ermittelt werden kann

### Cluster B: Rollenmodell-Reste (CHIEF → NPC)
- **Betrifft:** `ConversationRole` Enum, `ConversationService` (alle Stellen mit `ConversationRole.CHIEF`), `AIRequest`-Mapping
- **Problem:** Karte 04 (`conversationrole-npc`) steht auf done, aber CHIEF wird noch aktiv geschrieben und gelesen
- **Impact:** Falsche Rollen im Prompt, konzeptionelle Verwirrung zwischen Chief und NPC

### Cluster C: Bridge-Mapping-Inkonsistenz
- **Betrifft:** `reply_builder.py`, `http_app.py`, `prompt_builder.py` vs. Java-seitige Payload-Erzeugung
- **Problem:** Python nutzt teils `chiefName`, teils `displayName`; Plugin sendet nicht immer das, was die Bridge erwartet
- **Impact:** Fehlende oder falsche Namensanzeige, ggf. falsche Prompt-Generierung

### Cluster D: Prompt-Hoheit
- **Betrifft:** `prompt_builder.py` (Bridge) vs. `ConversationService.buildChiefPrompt()` (Plugin)
- **Problem:** Beide Seiten bauen System-Prompts – unklar, wer die endgültige Hoheit hat
- **Impact:** Memory/Fakten können untergraben werden, widersprüchliche Anweisungen an die KI

### Cluster E: Persistenz-Drift
- **Betrifft:** `PluginDataLoader` (schreibt `chiefs.yml`) vs. `YamlChiefRepository` (liest/schreibt `chief-attributes.yml`)
- **Problem:** Zwei Dateien, unterschiedliche Formate, keine klare Migrationsstrategie
- **Impact:** Datenverlust-Risiko, inkonsistente Zustände nach Reload

### Cluster F: Planungs-Disziplin
- **Betrifft:** Alle Karten in `chief-villager-umbau`, `bugfixes`, `ad-hoc`, `village-fixes`, `4 fixes`
- **Problem:** done-Markierungen stimmen nicht mit Code-Zustand überein; mehrere Karten hängen in in-progress ohne Fortschritt
- **Impact:** Kein belastbarer Überblick, welche Arbeiten tatsächlich noch ausstehen

---

## 3. Priorisierte Fix-Reihenfolge

Die Reihenfolge ist strikt nach Abhängigkeiten sortiert.  
Kein Cluster wird begonnen, bevor der jeweils vorherige abgeschlossen ist.

| # | Cluster | Priorität | Warum zuerst? |
|---|---------|-----------|---------------|
| 1 | **A: Null-Safety** | 🔴 Kritisch | Crash verhindert jeden Test des restlichen Systems |
| 2 | **F: Planungs-Disziplin** | 🟡 Hoch | Ohne korrekte Karten-Stati keine belastbare Arbeitsgrundlage |
| 3 | **B: Rollenmodell** | ✅ done | CHIEF-Reste durchziehen das ganze System; danach ist die Architektur "sauber" |
| 4 | **C: Bridge-Mapping** | 🟡 Hoch | Erst wenn Rollen klar sind, kann das Mapping korrekt vereinheitlicht werden |
| 5 | **D: Prompt-Hoheit** | 🟡 Hoch | Hängt von B und C ab – erst wenn Felder und Rollen stimmen, kann Hoheit geklärt werden |
| 6 | **E: Persistenz** | 🟢 Mittel | Kosmetisch/stabilisierend, aber kein Blocker für Funktionalität |

---

## 4. Vorgehen pro Cluster (grob)

### Cluster A – Null-Safety
1. `VillagerInteractListener` prüfen: Darf `startConversation` jemals mit null-Speaker aufgerufen werden?
2. Wenn nein: Guard einbauen, der vor dem Aufruf abbricht und sinnvoll logged.
3. `VillageIdentityService.resolveOrRegisterVillageId`: null-Check für den Villager-Parameter, bevor `getPersistentDataContainer()` aufgerufen wird.
4. Optional: `@NotNull`-Annotationen im gesamten Einstiegspfad ergänzen.

### Cluster F – Planungs-Disziplin
1. JEDE Karte in allen Planungsordnern öffnen und Status gegen den tatsächlichen Code prüfen.
2. Karten, die wirklich done sind, so belassen.
3. Karten, die fälschlich done sind, auf `in-progress` oder `todo` zurücksetzen und eine Notiz ergänzen, WAS konkret noch fehlt.
4. Offene Working-Cards identifizieren, die direkt zu den Clustern B–E passen (z.B. 04, 10, 13, 14c, ad-hoc 09e, 09f, a3, village-fixes 05, 08).
5. Diese Karten als "Baustellen" markieren, aus denen die Arbeitskarten für B–E abgeleitet werden.

### Cluster B – Rollenmodell
1. `ConversationRole`-Enum: CHIEF entfernen oder auf NPC mappen.
2. `ConversationService`: Alle Vorkommen von `ConversationRole.CHIEF` identifizieren und durch `NPC` ersetzen.
3. `AIRequest`-Mapping: Prüfen, ob dort noch CHIEF/Chief-spezifische Felder referenziert werden.
4. `ChiefCommand.java`: Prüfen, ob dort Rollen-Strings hartkodiert sind, die auf CHIEF verweisen.

### Cluster C – Bridge-Mapping
1. Mapping-Tabelle erstellen: Welches Feld heißt im Plugin wie, welches in der Bridge?
2. Auf EINEN einheitlichen Satz Feldnamen festlegen (Vorschlag: Orientierung am Speaker-Modell).
3. `reply_builder.py`, `http_app.py`, `prompt_builder.py` auf die einheitlichen Namen umstellen.
4. Java-seitige Payload-Erzeugung (`ConversationService`, `AIRequest`) gegen die Tabelle prüfen und ggf. korrigieren.

### Cluster D – Prompt-Hoheit
1. Entscheiden: Baut die Bridge den System-Prompt, oder liefert das Plugin ihn mit?
2. Wenn Bridge: `prompt_builder.py` ist die Single Source of Truth; Plugin sendet nur Fakten/Rohdaten.
3. Wenn Plugin: `ConversationService.buildChiefPrompt()` ist die Hoheit; Bridge übernimmt den Prompt unverändert.
4. Die jeweils andere Seite von Prompt-Logik bereinigen.
5. Dokumentieren, warum die Entscheidung so gefallen ist.

### Cluster E – Persistenz
1. ✅ Entscheiden: `chief-attributes.yml` (via YamlChiefRepository) + `speakers.yml` (via YamlSpeakerRepository) – `chiefs.yml` war der ungenutzte Zombie.
2. ✅ `chiefs.yml` aus Resources und `saveBundledResources()` entfernt.
3. ✅ `PluginDataLoader` bereinigt – kopiert keine ungenutzte Persistenzdatei mehr.
4. ✅ Build + Deploy erfolgreich, Server-Log zeigt keine Persistenz-Warnungen.

---

## 5. Abhängigkeiten als Graph

```
A (Null-Safety) ──── kein Blocker für andere, aber muss zuerst behoben werden
     │
     └──► F (Planung) ──── schafft die Grundlage für alle weiteren Cluster
               │
               ├──► B (Rollenmodell) ──── Voraussetzung für C und D
               │         │
               │         ├──► C (Bridge-Mapping) ──── Voraussetzung für D
               │         │         │
               │         │         └──► D (Prompt-Hoheit) ──── letzter inhaltlicher Cluster
               │         │
               │         └──► E (Persistenz) ──── unabhängig von C/D, kann parallel laufen
               │
               └──► (E kann nach F jederzeit parallel bearbeitet werden)
```

---

## 6. Explizite Nicht-Ziele für diese Stabilisierung

- Keine neuen Features (Quests, Trade, neue KI-Modelle)
- Kein weiterer Umbau über das hinaus, was die Karten bereits fordern
- Keine Optimierungen, die nicht direkt mit den Inkonsistenzen zusammenhängen
- Keine Änderungen an `chief-ai-service/memory_db.py` oder `worker.py` (außer sie sind direkt von Cluster C/D betroffen)

---

## 7. Erfolgskriterien

Nach Abschluss aller Cluster muss gelten:

1. **Kein Crash** bei Chat-Eingabe mit beliebigem Speaker-Zustand
2. **`ConversationRole.CHIEF` existiert nicht mehr** im gesamten Codebase
3. **Einheitliche Feldnamen** zwischen Plugin und Bridge, dokumentiert in einer Mapping-Tabelle
4. **Genau eine Stelle**, die den System-Prompt baut – dokumentiert und begründet
5. **Genau eine Persistenzdatei** für Chief/Speaker-Daten
6. **Alle Planungskarten** spiegeln den tatsächlichen Code-Zustand wider (Status-Flag korrekt)
7. **Alle als done markierten Karten** sind nachweislich und vollständig umgesetzt

---

## 8. Nächste Schritte

1. Dieses Konzept vom Nutzer abnehmen lassen.
2. Aus jedem Cluster (A–F) eine oder mehrere konkrete Arbeitskarten nach Template-Vorlage ableiten.
3. Arbeitskarten in der priorisierten Reihenfolge abarbeiten.
4. Nach jedem Cluster: Erfolgskriterien prüfen, bevor der nächste Cluster begonnen wird.

---

> **Status dieses Dokuments:** Entwurf, wartet auf Abnahme durch den Nutzer.
> **Nächster Schritt:** Ableitung von Arbeitskarten nach Freigabe.