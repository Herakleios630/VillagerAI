---
title: "Arbeitsauftrag: ConversationService auf Speaker umbauen"
quelle: "konzept-aufteilung-chief-villager.md → Schritt 10"
created: "2025-01-16"
status: in-progress
---

# Arbeitsauftrag: ConversationService auf Speaker umbauen

**Quelle:** konzept-aufteilung-chief-villager.md → Schritt 10

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`service/ConversationService.java` komplett von `Chief` auf `Speaker` umstellen. Alle Variablen umbenennen (`chief` → `speaker`), Session-Maps speichern `Speaker` statt `Chief`, `startConversation()` nimmt `Speaker` entgegen. Beim Bauen des `AIRequest` die Speaker-Felder + optionale ChiefAttributes verwenden.

## Aktuelles Ergebnis
ConversationService arbeitet mit dem alten `Chief`-Record. Muss auf `Speaker` umgebaut werden.

## Ursachenverdacht
Entfällt – reiner Umbau.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/ConversationService.java` | HAUPTDATEI – komplett umbauen |

## Erbetene Hilfe
1. Lies `ConversationService.java` mit `filesystem_read_text_file`
2. Führe folgende Änderungen durch (viele kleine `single_find_and_replace`-Aufrufe):
   - Alle Variablen `chief` → `speaker`
   - Alle Typ-Referenzen `Chief` → `Speaker`
   - `Map<Player, Chief>` → `Map<Player, Speaker>` (Session-Maps)
   - `startConversation(Player, Villager, Chief)` → `startConversation(Player, Villager, Speaker)`
   - `sendChiefMessage(...)` → `sendNpcMessage(...)`
   - `chiefRequestOwners` → `npcRequestOwners`
   - Beim Bauen des AIRequest: Speaker-Felder verwenden (displayName, role, personality, speechTone, etc.), NICHT die alten Chief-Felder
   - `chiefAttributes` nur dann ins AIRequest setzen, wenn `speaker.speakerStatus() == SpeakerStatus.AKTIV_CHIEF` UND ChiefAttributes via ChiefRepository.findByEntityUuid() existiert
   - Wenn der Speaker ein Chief ist, hole die ChiefAttributes aus dem ChiefRepository und hänge sie ans AIRequest
   - Dorf-Identitätsfelder (villageDescription, etc.) NICHT aus Speaker nehmen (da nicht persistiert), sondern bei Gesprächsbeginn vom VillageIdentityService holen
   - `ConversationRole.CHIEF` → `ConversationRole.NPC` (falls nicht bereits in Schritt 04 global ersetzt)
3. Füge Abhängigkeit zu `SpeakerService` und `ChiefRepository` hinzu (per Constructor Injection)
4. Entferne den Import von `Chief` (alt)
5. Build mit `Set-Location "C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI"; .\gradlew.bat compileJava`
6. Erwarte Compile-Fehler in Listenern, die `startConversation()` aufrufen – die werden in Schritt 11 behoben
