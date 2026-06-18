---
title: "Arbeitsauftrag: Phase 10 – Whisper (1/5) – Model, Config & PluginDataLoader"
quelle: "roadmap.md → Phase 10 – Öffentliche & Flüster-Unterhaltung"
related-roadmap: "Plannung/whisper.md"
created: "2026-06-18"
status: in-progress
---

# Arbeitsauftrag: Phase 10 – Whisper (1/5) – Model, Config & PluginDataLoader

**Quelle:** roadmap.md → Phase 10

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Fundament für Phase 10 legen: Neue `ConversationVisibility`-Enum-Klasse anlegen, `AIRequest` um `conversationVisibility`-Feld erweitern, `conversation.visibility`-Sektion in `config.yml` einfügen und `PluginDataLoader` um das Einlesen dieser Werte ergänzen.

## Aktuelles Ergebnis
- Es gibt keine Visibility-Kontrolle; alle Gespräche sind implizit privat (nur der Spieler sieht die Antworten).
- `AIRequest` hat kein Visibility-Feld.
- `config.yml` hat keine `conversation.visibility`-Sektion.
- `PluginDataLoader` kennt keine Visibility-Config.

## Ursachenverdacht
Neues Feature – kein Fehler, sondern Erweiterung.

## Betroffene Schichten & Dateien
| Datei | Rolle | Größe |
|---|---|---|
| `src/main/java/de/ajsch/villagerai/model/ConversationVisibility.java` | **NEU** – Enum PUBLIC, WHISPER | ~20 Zeilen |
| `src/main/java/de/ajsch/villagerai/model/AIRequest.java` | Feld `String conversationVisibility` ergänzen | ~1,9 KB |
| `src/main/resources/config.yml` | Neue Sektion `conversation.visibility` | ~1 KB Zusatz |
| `src/main/java/de/ajsch/villagerai/config/PluginDataLoader.java` | Config-Werte einlesen, an Felder binden | ~25 KB |

## Erbetene Hilfe
1. **Neue Enum-Klasse `ConversationVisibility.java` anlegen:**
   - Package `de.ajsch.villagerai.model`
   - Werte: `PUBLIC`, `WHISPER`
   - Wie `ConversationRole` aufgebaut (einfaches Enum, keine komplexe Logik)

2. **`AIRequest.java` erweitern:**
   - Neues Feld `String conversationVisibility` (Werte "PUBLIC" oder "WHISPER")
   - Im Record-Konstruktor ergänzen (Default-Wert nicht nötig, wird immer gesetzt)
   - Alle Aufrufstellen von `new AIRequest(...)` müssen später (in Karte 02/04) ergänzt werden – erstmal kompiliert das nicht; das ist OK und wird in den Folgeschritten gefixt.

3. **`config.yml` erweitern:**
   - Neue Top-Level-Sektion `conversation:` mit Unter-Sektion `visibility:` einfügen
   - Felder gemäß `Plannung/whisper.md` Abschnitt 6:
     ```yaml
     conversation:
       visibility:
         default-mode: PUBLIC
         public-radius-blocks: 50
         public-player-prefix: "sagt"
         whisper-player-prefix: "flüsterst"
         public-chief-prefix: "sagt"
         whisper-chief-prefix: "flüstert"
         particles:
           enabled: true
           public-particle: VILLAGER_HAPPY
           whisper-particle: SOUL
           particle-count: 4
           particle-interval-ticks: 8
     ```
   - Nach `ai:`-Sektion einfügen, vor `memory:` (oder ans Ende, wenn memory nicht existiert)

4. **`PluginDataLoader.java` einlesen:**
   - Neue `volatile`-Felder für jeden Config-Wert (siehe bestehende Muster wie `conversationTimeoutMinutes`, `questCooldownMinutesThreshold` etc.)
   - Im `loadConfigAndInitialize()`-Block die neuen Pfade auslesen mit `config.getString(...)`, `config.getInt(...)`, `config.getBoolean(...)`
   - Getter-Methoden für `ConversationService` bereitstellen:
     - `getConversationDefaultVisibility()` → `String` ("PUBLIC" oder "WHISPER")
     - `getConversationPublicRadiusBlocks()` → `int`
     - `getConversationPublicPlayerPrefix()` → `String`
     - `getConversationWhisperPlayerPrefix()` → `String`
     - `getConversationPublicChiefPrefix()` → `String`
     - `getConversationWhisperChiefPrefix()` → `String`
     - `getConversationParticlesEnabled()` → `boolean`
     - `getConversationPublicParticle()` → `String` (für `Particle.valueOf()`)
     - `getConversationWhisperParticle()` → `String`
     - `getConversationParticleCount()` → `int`
   - Kein Reload-Kommando nötig; Werte werden beim Serverstart geladen.

5. **Build: `.\gradlew.bat compileJava`**
   - Wird fehlschlagen wegen fehlendem `conversationVisibility` in AIRequest-Aufrufen – das ist erwartet und wird in Karte 02/04 behoben.
   - Wenn andere Fehler auftreten, sofort beheben.

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy (nur nach letzter Karte 05):**
  1. `scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"`
  2. YAML-Configs kopieren: `config.yml` → Server
  3. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` (KEIN Plugin-Reload)
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, Plannung/roadmap.md