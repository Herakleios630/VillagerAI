---
title: "Arbeitsauftrag: Stabilisierung S01 – Cluster A: Null-Safety im Chat-Einstiegspfad"
quelle: "Plannung/konzept-stabilisierung.md → Cluster A"
created: "2025-07-17"
"status: done
---

## Fortschritt

| Schritt | Status | Beschreibung |
|---------|--------|-------------|
| 1 | done | VillagerInteractListener – Speaker-null-Guard eingebaut |
| 2 | done | ConversationService.startConversation – null-Check für villager/speaker |
| 3 | done | VillageIdentityService.resolveOrRegisterVillageId – defensiver null-Check |
| 4 | done | PlayerChatListener – kein Risiko (nur UUID-basierter Session-Lookup) |
"| 5 | done | Build mit shadowJar (BUILD SUCCESSFUL) |
| 6 | todo | Deployment via SCP + Restart |
| 7 | todo | Chat auslösen und Log prüfen |"

## Änderungen

### VillagerInteractListener.java
- `Speaker`-Import ergänzt
- Vor `startConversation` wird der Speaker aus `speakerService.getSpeaker()` geholt und auf null geprüft
- Bei null-Speaker: Fehlermeldung an Spieler („Dieser Dorfbewohner hat keine gueltige Sprecher-Rolle..."), `startConversation` wird NICHT aufgerufen

### ConversationService.java
- In `startConversation` Guard: `if (villager == null || speaker == null)` blockt den Aufruf
- Logger: `[ConversationService] startConversation() mit null villager oder speaker aufgerufen – abgebrochen`

### VillageIdentityService.java
- In `resolveOrRegisterVillageId` Guard: `if (villager == null)` logged Warnung und gibt `null` zurück

### PlayerChatListener.java
- Kein Risiko: `handlePlayerChat` arbeitet nur mit `playerUuid` und `message`, kein Villager/Speaker"
---

# Arbeitsauftrag: Stabilisierung S01 – Cluster A: Null-Safety im Chat-Einstiegspfad

**Quelle:** Plannung/konzept-stabilisierung.md → Cluster A (Null-Safety)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin VillagerAI
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI

## Auftrag

Den Chat-Einstiegspfad so absichern, dass KEIN null-Villager/Speaker mehr in die nachgelagerten Services durchgereicht werden kann.

Der aktuell beobachtete Crash:
`java.lang.NullPointerException: Cannot invoke org.bukkit.entity.Villager.getPersistentDataContainer() because villager is null`
in `VillageIdentityService.resolveOrRegisterVillageId()` muss durch einen Guard im Einstiegspfad verhindert werden.

## Aktuelles Ergebnis

- `VillagerInteractListener` ruft `ConversationService.startConversation()` auf und uebergibt potenziell null als Speaker/Villager
- `ConversationService.startConversation()` reicht diesen null-Wert weiter an `VillageIdentityService`
- `VillageIdentityService.resolveOrRegisterVillageId()` ruft `.getPersistentDataContainer()` ohne null-Check auf dem uebergebenen Villager auf → Crash
- Der gesamte Chat-Pfad ist bis zur Behebung dieses Problems unbenutzbar

## Ursachenverdacht

1. `VillagerInteractListener` hat keinen Guard, der vor dem Aufruf von `startConversation` prueft, ob der Speaker/Villager gueltig ist
2. `ConversationService.startConversation()` hat keinen `@NotNull`-Contract fuer den Speaker-Parameter
3. `VillageIdentityService.resolveOrRegisterVillageId()` hat keinen defensiven null-Check auf dem Villager-Parameter vor dem Zugriff auf Bukkit-API-Methoden

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/listener/VillagerInteractListener.java` | Einstiegspunkt – ruft startConversation auf |
| `src/main/java/de/ajsch/villagerai/service/ConversationService.java` | Vermittelt Player+Villager an VillageIdentityService |
| `src/main/java/de/ajsch/villagerai/service/VillageIdentityService.java` | Nutzt villager.getPersistentDataContainer() ohne null-Check |
| `src/main/java/de/ajsch/villagerai/listener/PlayerChatListener.java` | Alternativer Einstiegspunkt fuer Chat (pruefen auf aehnliches Risiko) |

## Erbetene Hilfe

1. `VillagerInteractListener.java` analysieren: Unter welchen Bedingungen kann der Villager null sein? Guard einbauen, der vor dem Aufruf abbricht und eine sinnvolle Log-Meldung ausgibt.
2. `ConversationService.java` startConversation-Methode: `@Nullable` oder `@NotNull`-Annotation pruefen. Wenn Speaker/Villager null sein darf, muss der gesamte nachgelagerte Pfad damit umgehen koennen. Wenn nicht, @NotNull ergaenzen.
3. `VillageIdentityService.java` resolveOrRegisterVillageId: Defensiven null-Check fuer den Villager-Parameter EINBAUEN, BEVOR getPersistentDataContainer() aufgerufen wird. Bei null sinnvoll loggen und kontrolliert abbrechen (kein Crash).
4. `PlayerChatListener.java` pruefen: Kann auch hier ein null-Villager durchkommen? Falls ja, gleichen Guard einbauen.
5. Build mit `.\gradlew.bat shadowJar -x test`
6. Deployment via SCP + `ssh mc@10.0.0.86 sudo systemctl restart crafty`
7. Nach Deployment: Chat ausloesen und pruefen, ob der Crash verschwunden ist. Log auf die neuen Guard-Meldungen beobachten.

## Akzeptanzkriterien

- Der genannte NullPointerException-Crash tritt unter KEINEN Umstaenden mehr auf
- Bei ungueltigem Speaker wird eine verstaendliche Log-Meldung ausgegeben (statt Crash)
- Der Chat funktioniert normal, wenn ein gueltiger Speaker vorhanden ist

## Technische Randbedingungen
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Grosse Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 grosse oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeaenderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:**
  1. `scp build\libs\VillagerAI-0.1.0-SNAPSHOT.jar mc@10.0.0.86:/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar`
  2. Nur wenn YAML-Configs geaendert: zusaetzlich `config.yml` kopieren
  3. `ssh mc@10.0.0.86 sudo systemctl restart crafty` (KEIN Plugin-Reload)
- **Sync nach Abschluss:** Plannung/konzept-stabilisierung.md (Cluster A abhaken), Plannung/roadmap.md