---
title: "Arbeitsauftrag: relationshipMemory-Text für Erstkontakt präzisieren"
quelle: "Ad-hoc → Log-Analyse Prompt-Redesign"
created: "2025-12-18"
status: obsolet
---

# Arbeitsauftrag: relationshipMemory-Text für Erstkontakt präzisieren

**Quelle:** Ad-hoc → Log-Analyse nach Prompt-Redesign-Deployment

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Beim allerersten Gespräch mit einem Villager antwortet das LLM mit "Ja, ich erinnere mich vage
an dich", obwohl es das erste Gespräch ist. Der `relationshipMemory`-Prompt-Text suggeriert
eine bestehende (wenn auch vage) Erinnerung.

## Aktuelles Ergebnis
Log zeigt beim ersten Turn:
```
relationshipMemory=Ihr habt erst kuerzlich miteinander gesprochen. Du kennst den Spieler erst
oberflaechlich und erinnerst dich nur an wenige direkte Worte.
```
Das LLM nimmt "erinnerst dich nur an wenige direkte Worte" wörtlich und produziert "ich erinnere
mich vage an dich".

## Ursachenverdacht
Der Text wird vom `ConversationService` oder direkt im Payload-Bau gesetzt. Die Formulierung
"erst kürzlich miteinander gesprochen" und "erinnerst dich nur an wenige" setzt eine
Vorgeschichte voraus, die es beim ersten Turn nicht gibt. Für leere Conversation-Histories
sollte der Text eindeutig sagen: "Dies ist euer erstes Gespräch."

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/ConversationService.java` | Baut `relationshipMemory`-String |
| `chief-ai-service/chief_ai_service/prompt_builder.py` | Rendert `Ruf`-Sektion mit `relationshipMemory` |

## Erbetene Hilfe

1. **Java-Seite prüfen:** In `ConversationService` die Logik finden, die `relationshipMemorySummary` setzt. Unterscheiden zwischen:
   - Keine vorherigen Turns → "Du hast diesen Spieler noch nie zuvor gesehen. Dies ist euer erstes Gespräch."
   - 1–2 Turns → "Ihr habt euch gerade erst kennengelernt."
   - 3+ Turns → bestehende Eskalation beibehalten
2. **Bridge-Seite prüfen:** `_build_reputation_section()` rendert den Text bereits korrekt unter "Bekannter-Hinweis:" – kein Änderungsbedarf erwartet
3. Build mit `.\gradlew.bat shadowJar -x test` (nur Plugin)
4. Deployment via SCP + `sudo systemctl restart crafty`

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** `.\gradlew.bat shadowJar -x test`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar`
- **Deploy:**
  1. `scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"`
  2. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"`
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, Plannung/roadmap.md
```

---