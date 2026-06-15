---
title: \"Arbeitsauftrag: AIRequest an Speaker-Felder anpassen\"
quelle: \"konzept-aufteilung-chief-villager.md → Schritt 5\"
created: \"2025-01-16\"
status: done
---

# Arbeitsauftrag: AIRequest an Speaker-Felder anpassen

**Quelle:** konzept-aufteilung-chief-villager.md → Schritt 5

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin \"VillagerAI\"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\\Users\\ajsch\\OneDrive\\Documents\\Coding\\Minecraft\\VillagerAI`

## Auftrag
`model/AIRequest.java` so umbauen, dass es statt der alten `Chief`-Felder die neuen `Speaker`-Felder enthält. Zusätzlich ein optionales (`@Nullable`) `ChiefAttributes`-Feld hinzufügen, das nur gefüllt wird, wenn der Speaker wirklich ein aktiver Chief ist.

## Aktuelles Ergebnis
AIRequest hat vermutlich Felder wie `chiefName`, `chiefRole`, `chiefPersonality` etc. Diese stammen aus dem alten `Chief`-Record.

## Ursachenverdacht
Entfällt – reiner Umbau.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/model/AIRequest.java` | HAUPTDATEI – Felder umbauen |

## Erbetene Hilfe
1. Lies `AIRequest.java` mit `filesystem_read_text_file`
2. Ersetze alle Chief-bezogenen Felder durch Speaker-Felder: `displayName`, `role`, `personality`, `speechTone`, `behaviorHint`, `greeting`, `speakerStatus`, `villageId`, `villageName`
3. Füge `@Nullable ChiefAttributes chiefAttributes` hinzu
4. Passe den `toJson()`- oder Serialisierungs-Teil so an, dass `speakerStatus` als String serialisiert wird
5. Entferne den Import von `Chief` (alt), füge Import von `Speaker` und `ChiefAttributes` hinzu
6. Build mit `Set-Location \"C:\\Users\\ajsch\\OneDrive\\Documents\\Coding\\Minecraft\\VillagerAI\"; .\\gradlew.bat compileJava`
7. Kein Deploy nötig
