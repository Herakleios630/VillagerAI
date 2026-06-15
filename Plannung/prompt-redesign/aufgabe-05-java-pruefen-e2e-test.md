---
title: "Arbeitsauftrag: Java-Seite prufen + End-to-End-Test"
quelle: "Ad-hoc -> Prompt-Konzept Karte 5"
related-roadmap: "Plannung/prompt-redesign/konzept.md"
created: "2025-12-18"
status: done
---

# Arbeitsauftrag: Java-Seite prufen + End-to-End-Test

**Quelle:** Ad-hoc -> Prompt-Konzept Abschnitt 5, Karte 5

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21 + Python 3
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI

## Auftrag
1. `HttpAIService.buildSystemPrompt()` prufen: Ist der System-Prompt nach dem Umbau schlank genug?
   Werden `chiefTone`/`chiefBehaviorHint` jetzt doppelt gesendet (Java + Bridge)?
2. End-to-End-Trockentest: Kompletten Prompt mit realistischem Test-Payload rendern und auf
   alle 11 Sektionen + Korrektheit prufen
3. Deployment: Bridge + ggf. Plugin

## Aktuelles Ergebnis
- `buildSystemPrompt()` baut Rollentext inkl. Tone/BehaviorHint -> geht als `systemPrompt` an Bridge
- `resolve_system_prompt()` hangt diesen additiv an den Basis-Prompt
- Tone/BehaviorHint erscheinen jetzt ZUSATZLICH in der Ground-Truth/Personlichkeit -> mogliche Dopplung

## Ursachenverdacht
Durch den Prompt-Umbau (Karten 1-4) konnten Tone/BehaviorHint jetzt doppelt im Prompt stehen:
einmal als Teil des Java-SystemPrompts, einmal in der strukturierten Personlichkeitssektion.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/ai/HttpAIService.java` | `buildSystemPrompt()` prufen/kurzen |
| `chief-ai-service/chief_ai_service/prompt_builder.py` | `resolve_system_prompt()` prufen |
| `tmp_memory_verkabelung_dryrun.py` | Als Basis fur End-to-End-Test |

## Erbetene Hilfe

1. **`HttpAIService.buildSystemPrompt()` prufen und kurzen:**
   - `chiefTone` und `chiefBehaviorHint` aus dem Java-SystemPrompt entfernen
     (sind jetzt in der Bridge Ground-Truth/Personlichkeit)
   - Ziel: `buildSystemPrompt()` sendet nur noch Basis-Rollentext und Smalltalk-Erkennung
   - Tone/BehaviorHint-Zeilen auskommentieren oder entfernen

2. **Bridge `resolve_system_prompt()` prufen:**
   - Kein Anderungsbedarf erwartet
   - Nach Java-Kurzung kommt nur noch ein schlanker Rollentext

3. **End-to-End-Test mit `tmp_memory_verkabelung_dryrun.py`:**
   - Payload mit `chiefTone`, `chiefBehaviorHint`, `villageDescription` erweitern
   - Checkliste abarbeiten:
     - [ ] Ground-Truth enthalt `villageDescription`-Text
     - [ ] Ground-Truth enthalt Chief-Status-Narrativ
     - [ ] Ground-Truth enthalt negative Klarstellung bei normalem Bewohner
     - [ ] Personlichkeit enthalt Ton + Verhalten
     - [ ] Dorf-Details enthalt KEINE internen IDs
     - [ ] Ruf ist Score+Label-Format
     - [ ] Status hat keine "unbekannt"-POIs
     - [ ] Regeln sind <=15 Zeilen
     - [ ] Regeln stehen VOR Spieler-Nachricht
     - [ ] Kein `chiefTone`/`chiefBehaviorHint` im System-Prompt-Teil

4. **Deployment nach erfolgreichem Test:**
   - Java: `.\gradlew.bat shadowJar -x test`
   - Plugin-JAR kopieren
   - Bridge-Dateien kopieren
   - `ssh mc@10.0.0.86 "sudo systemctl restart villagerai-chief"`
   - `ssh mc@10.0.0.86 "sudo systemctl restart crafty"`
