---
title: "Arbeitsauftrag: Plugin-Anpassungen – Debug-Logging & Fallback-Regex"
quelle: "konzept-memory-langzeit-fakten.md → Paket F"
related-roadmap: "Plannung/konzept-memory-langzeit-fakten.md"
created: "2025-09-18"
status: done
---

# Arbeitsauftrag: Plugin-Anpassungen – Debug-Logging & Fallback-Regex

**Quelle:** konzept-memory-langzeit-fakten.md → Paket F (F1–F2)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Das Debug-Logging in `ConversationService` um die neue Facts-Sektion erweitern und den `likelyMemoryTrigger`-Regex als konfigurierbaren Fallback für die Bridge bereitstellen.

## Aktuelles Ergebnis
- `ConversationService.logChatDebugPrompt()` zeigt `recentConversation` und Memory-Sektionen an, aber keine Facts.
- `likelyMemoryTrigger` prüft hartkodiert auf `name|erinner|weisst du noch|früher|letztes mal|damals`.
- Kein Mechanismus, den Regex als Fallback an die Bridge zu übergeben.

## Ursachenverdacht
Nicht zutreffend – Erweiterung bestehender Debug-Infrastruktur.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/ConversationService.java` | `logChatDebugPrompt()` erweitern |
| `src/main/java/de/ajsch/villagerai/config/PluginDataLoader.java` | `likelyMemoryTrigger`-Regex exportierbar machen |
| `src/main/java/de/ajsch/villagerai/ai/AIRequest.java` | Ggf. `memoryTriggerFallbackRegex` Feld (optional) |
| `src/main/resources/config.yml` | Nur wenn Regex konfigurierbar gemacht wird |

## Erbetene Hilfe
1. **Debug-Logging erweitern:** `logChatDebugPrompt()` so anpassen, dass die Facts-Sektion (wenn im Bridge-Response-Debug enthalten) mit ausgegeben wird. Der genaue Mechanismus hängt davon ab, wie die Bridge die Facts zurückmeldet – vermutlich als Teil des Prompt-Debug-Strings.
2. **Fallback-Regex exportierbar machen:** `PluginDataLoader` soll den `likelyMemoryTrigger`-Regex aus `config.yml` lesen können (neuer Key `memory.trigger_phrases_fallback`), sodass er bei Bedarf erweitert/angepasst werden kann.
3. **Regex als Fallback an Bridge übergeben:** Entweder als eigenes Feld im `AIRequest`-DTO oder als Teil des `memory.enabled`-Payloads – die Bridge nutzt ihn, wenn qwen nicht erreichbar ist.
4. Build: `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`
5. Deployment: JAR kopieren + `sudo systemctl restart crafty`

## Technische Randbedingungen (wiederverwendbar)
- **Große Java-Dateien:** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Edits:** `single_find_and_replace` bevorzugen
- **Build:** `.\gradlew.bat compileJava` → `.\gradlew.bat shadowJar -x test`
- **Deploy:** `scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"` + `ssh mc@10.0.0.86 "sudo systemctl restart crafty"`
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, Plannung/roadmap.md