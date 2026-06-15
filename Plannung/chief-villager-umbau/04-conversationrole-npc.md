---
title: "Arbeitsauftrag: ConversationRole.CHIEF → NPC"
quelle: "konzept-aufteilung-chief-villager.md → Schritt 4"
created: "2025-01-16"
status: done
---

# Arbeitsauftrag: ConversationRole.CHIEF → NPC

**Quelle:** konzept-aufteilung-chief-villager.md → Schritt 4

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
In `model/ConversationRole.java` den Enum-Wert `CHIEF` in `NPC` umbenennen. Das ist ein rein kosmetischer, aber wichtiger Schritt: Die Rolle kennzeichnet nur "der NPC hat gesagt", hat nichts mit Chief-Sein zu tun. Alle Referenzen auf `ConversationRole.CHIEF` im gesamten Codebase müssen auf `ConversationRole.NPC` umgestellt werden.

## Aktuelles Ergebnis
Enum existiert mit Vermutlich `PLAYER` und `CHIEF`. Alle Stellen, die `ConversationRole.CHIEF` verwenden, müssen gefunden und geändert werden.

## Ursachenverdacht
Entfällt.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/model/ConversationRole.java` | HAUPTDATEI – Enum umbenennen |
| Alle Dateien mit `ConversationRole.CHIEF`-Referenz | Werden per grep gefunden und angepasst |

## Erbetene Hilfe
1. Lies `ConversationRole.java` mit `filesystem_read_text_file`
2. Benenne `CHIEF` in `NPC` um (via `single_find_and_replace`)
3. Suche per grep nach `ConversationRole.CHIEF` im gesamten `src/`-Ordner
4. Ersetze ALLE Fundstellen von `ConversationRole.CHIEF` durch `ConversationRole.NPC`
5. Build mit `Set-Location "C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI"; .\gradlew.bat compileJava`
6. Kein Deploy nötig
