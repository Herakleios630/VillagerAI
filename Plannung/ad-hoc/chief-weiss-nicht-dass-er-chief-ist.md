---
title: "Arbeitsauftrag: Chief weiß nicht dass er Chief ist / falsche Rolle im Prompt"
quelle: "Ad-hoc (Nutzer-Chat)"
created: "2025-07-17"
status: in-progress
---

# Arbeitsauftrag: Chief-Rolle wird nicht korrekt in den Prompt übernommen

**Quelle:** Ad-hoc (Nutzer-Chat)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service

## Auftrag
Debuggen, warum ein gekrönter Häuptling (Rurik) im Prompt `role=Dorfbewohner` hat und nicht erkennt, dass ER SELBST der Häuptling ist. Gleichzeitig sehen normale Dorfbewohner (Maren) `chiefLocation=Das Dorf hat derzeit keinen Häuptling`, obwohl Rurik aktiver Chief ist.

## Aktuelles Ergebnis (aus Logs)
1. **Rurik IST Chief** laut Server-Logs:
   - `Ein neuer Häuptling erhebt sich in Ebendorf: Rurik!`
   - `Chief chief-807306e1 gekrönt in Dorf 67b973f3`
   - `auto-assigned chief villager-08c9d07a (Rurik) to village 67b973f3`

2. **Prompt für Rurik (CHIEF)** enthält:
   - `role=Dorfbewohner` (FALSCH – müsste `Häuptling` sein)
   - `villagerProfession=NONE` (müsste den Chief-Beruf zeigen)
   - `displayName=Rurik` (korrekt)
   - `chiefLocation=Der Häuptling Rurik (Dorfbewohner) steht bei -2377, 89, 1322` (technisch korrekt, aber er erkennt sich nicht selbst)

3. **Prompt für Maren (NORMALER VILLAGER)** enthält:
   - `role=Dorfbewohner` (korrekt)
   - `chiefLocation=Das Dorf hat derzeit keinen Häuptling.` (FALSCH – Rurik ist Chief!)

4. **AI-Antworten bestätigen die Fehlinformation:**
   - Rurik: "Nein, nein, der Häuptling steht da drüben beim Brunnen. Ich bin nur Rurik, der Schmied."
   - Maren: "Nein, nein, der Häuptling steht da drüben beim Brunnen. Ich bin nur Rurik, der Schmied."

## Ursachenverdacht
1. **role-Feld wird nicht auf `Häuptling` gesetzt, wenn der Speaker der Chief ist** – `ConversationService` oder `SpeakerService` prüft nicht, ob der sprechende Villager der aktive Chief ist.
2. **chiefLocation für Nicht-Chiefs wird falsch/nicht ermittelt** – `ChiefService.getChiefLocation()` o.ä. findet den Chief nicht, obwohl er existiert.
3. **chief-villager-umbau hat Chief-spezifische Prompt-Felder nicht migriert** – Früher gab es vielleicht ein `Chief`-Model mit eigener Rolle, jetzt wird `Speaker` verwendet und die Chief-Erkennung fehlt.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/ConversationService.java` | Baut den Prompt-String für den Speaker |
| `src/main/java/de/ajsch/villagerai/service/SpeakerService.java` | Lädt Speaker-Attribute (role, personality, etc.) |
| `src/main/java/de/ajsch/villagerai/service/ChiefService.java` | Verwaltet Chief-Zustand, liefert Chief-Info |
| `src/main/java/de/ajsch/villagerai/model/Speaker.java` | Speaker-Record mit role-Feld |
| `src/main/java/de/ajsch/villagerai/model/SpeakerStatus.java` | Enum für Speaker-Status |
| `src/main/resources/chief-profiles.yml` | Chief-Profile (Häuptlings-Attribute) |
| `src/main/resources/speakers.yml` | Speaker-Daten (rolle, persönlichkeit) |
| `chief-ai-service/chief_ai_service/prompt_builder.py` | Baut finalen Prompt-String (Bridge) |

## Erbetene Hilfe (ToDo-Liste)
1. `ConversationService.java` lesen: Wo wird `role=` gesetzt? Wird geprüft, ob Speaker == Chief?
2. `SpeakerService.java` lesen: Wie wird `role` ermittelt? Gibt es eine Chief-spezifische Logik?
3. `ChiefService.java` lesen: Wie wird `chiefLocation` ermittelt? Warum "kein Häuptling" für Maren?
4. `chief-profiles.yml` + `speakers.yml` lesen: Welche Rolle ist dort hinterlegt?
5. Fix identifizieren und umsetzen
6. Build mit `.\gradlew.bat shadowJar -x test`
7. Deployment via SCP + `sudo systemctl restart crafty`

## Fortschritt
- [ ] Schritt 1: ConversationService analysieren
- [ ] Schritt 2: SpeakerService analysieren
- [ ] Schritt 3: ChiefService analysieren
- [ ] Schritt 4: YAML-Configs prüfen
- [ ] Schritt 5: Fix umsetzen
- [ ] Schritt 6: Build & Deploy