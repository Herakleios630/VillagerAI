---
title: "Bug: '[Villager] Der None ...' – Berufsname 'None' in Todes-Log"
quelle: "ad-hoc – Log-Beobachtung"
created: "2025-07-20"
status: obsolet
---

# Bug: „Der None Bela ist von einem player erschlagen worden"

**Quelle:** Ad-hoc (Log-Beobachtung vom 2025-07-20)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI" + Bridge-Dienst
- **Quellsprachen:**    Java 21, Python 3
- **Build-Tool:**       Gradle (Kotlin DSL) + pip
- **Server:**           Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service

## Beobachtung

Beim Tod eines Villagers (der zufällig ein Chief ist) erscheint im Server-Log:

```
[Villager] Der None Bela ist von einem player erschlagen worden.
```

Das `None` ist auffällig – dort steht offenbar der Beruf (Profession) des Villagers.  
Da Chiefs aktuell oft den Beruf `NONE` haben, erscheint das als „Der None …".

Die korrekte Ausgabe sollte stattdessen den Rollennamen des Chiefs (z. B. „Häuptling" oder den konkreten Beruf) verwenden,
oder zumindest auf „Der Dorfbewohner" / „Der Häuptling" normalisieren.

## Aktuelles Ergebnis
- Unschönes Log: `Der None Bela ist von einem player erschlagen worden`
- Betrifft Villager mit Profession `NONE` (= Chiefs ohne zugewiesenen Job)

## Ursachenverdacht
1. Ein Logging-Statement irgendwo im Code (Plugin ODER Bridge) baut einen String aus Profession + CustomName zusammen
2. Die Stelle verwendet `profession.name()` oder eine normalisierte Form ohne Fallback für `NONE`
3. Quelle unklar – `grep` nach „erschlagen", „ist von einem", „[Villager]" im Plugin-Repo findet keinen Treffer  
   → Vermutlich stammt die Zeile aus dem **Bridge-Python-Code** (`chief-ai-service/`) oder einer anderen Server-Komponente

## Betroffene Schichten & Dateien (zu klären)
| Datei | Rolle |
|---|---|
| `chief-ai-service/server.py` oder `reply_builder.py` o.ä. | Mögliche Quelle des Log-Strings, falls Bridge die Todes-Nachricht generiert |
| `src/main/java/.../VillageChiefPlugin.java` | Falls doch Plugin-seitig – zentraler Logger |
| Paper/Spigot-Server-Log | Könnte auch ein Vanilla-/Paper-Core-Log sein (unwahrscheinlich, da Präfix `[Villager]`) |

## Erbetene Hilfe
1. **Quelle identifizieren:**
   - `grep -r "erschlagen" *` im gesamten Repo (Plugin + Bridge) → negativ
   - `grep -r "ist von einem" *` → negativ
   - `grep -r "\[Villager\]" *` → negativ
   - `grep -r "Der " *` im Bridge-Ordner, um Strings mit „Der " zu finden
   - Im Plugin-Code nach `getProfession()`-Aufrufen suchen, die mit einem Logger verknüpft sind
   - Den Bridge-Code auf Logging-Statements untersuchen, die Todesereignisse verarbeiten
2. **Fix umsetzen:**
   - Wenn Quelle gefunden: Fallback für `NONE`-Profession einbauen (z. B. auf „Häuptling" oder „Dorfbewohner" normalisieren)
   - Alternativ: Berufs-String aus senior Role-Attribut (Chief/ConversationProfile) statt aus Enum `Profession` beziehen
3. **Ggf. Plugin-seitig absichern:**
   - Unabhängig von der Quelle: `ChiefService.markChief()` könnte die Profession des zugehörigen Villagers auf `NONE` lassen oder auf einen sinnvollen Wert setzen – Entscheidung dokumentieren
4. Build & Deploy wie üblich