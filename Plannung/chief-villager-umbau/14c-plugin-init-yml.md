---
title: "Arbeitsauftrag: plugin.yml und VillageChiefPlugin.java initialisieren"
quelle: "konzept-aufteilung-chief-villager.md → Schritt 14 (Teil c)"
created: "2025-01-16"
status: done
---

# Arbeitsauftrag: plugin.yml und VillageChiefPlugin.java initialisieren

**Quelle:** konzept-aufteilung-chief-villager.md → Schritt 14 → Teil c

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Zwei Dateien anpassen:
1. `src/main/resources/plugin.yml` – Neue Listener registrieren, alte entfernen, `VillagerProfileListener` raus
2. `VillageChiefPlugin.java` – Service-Initialisierung umstellen: statt altem `ChiefService`-Monolith jetzt `SpeakerService`, verschlankten `ChiefService`, `SpeakerRepository`, neues `ChiefRepository` verdrahten

---

### Teil 1: plugin.yml

**Hauptdatei:** `src/main/resources/plugin.yml`

1. Lies `plugin.yml` mit `filesystem_read_text_file`
2. Entferne `VillagerProfileListener` aus der Listener-Liste
3. Prüfe dass `SpeakerService` NICHT als Listener registriert wird (ist ein Service, kein Listener)
4. Alle anderen Listener-Namen bleiben gleich (sie wurden nur intern umgebaut)

---

### Teil 2: VillageChiefPlugin.java

**Hauptdatei:** `VillageChiefPlugin.java`

1. Lies `VillageChiefPlugin.java` mit `filesystem_read_text_file`
2. Entferne Initialisierung von:
   - `VillagerProfileRepository` / `YamlVillagerProfileRepository`
   - `VillagerProfileListener`
3. Füge Initialisierung hinzu (in dieser Reihenfolge):
   - `SpeakerRepository speakerRepo = new YamlSpeakerRepository(this)`
   - `SpeakerService speakerService = new SpeakerService(this, speakerRepo, villageIdentityService)`
   - `ChiefAttributesRepository chiefAttributesRepo = new YamlChiefAttributesRepository(this)` ← umbenannt von ChiefRepository
   - `ChiefService chiefService = new ChiefService(this, speakerService, chiefAttributesRepo, mourningService)` ← verschlankt
4. Injiziere `speakerService` in:
   - `VillagerInteractListener`
   - `ConversationService`
   - `ChiefCommand`
5. Injiziere `chiefService` (verschlankt) in:
   - `ChiefDeathHandler`
   - `ChiefCommand`
   - `ChiefAutoAssignmentService`
6. Injiziere `chiefAttributesRepo` in:
   - `ChiefVisualService`
   - `MourningService`
   - `ChiefMeetingObserver`
7. Entferne alle Imports für gelöschte Klassen
8. Build mit `Set-Location "C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI"; .\gradlew.bat compileJava`
"9. Kein Deploy nötig

---

## Ergebnis

| Teil | Status | Details |
|------|--------|---------|
| plugin.yml | ✅ Keine Änderung nötig | `VillagerProfileListener` bereits entfernt, keine Listener-Sektion vorhanden |
| VillageChiefPlugin.java | ✅ Zirkuläre Abhängigkeit aufgelöst | `MourningService` → `ChiefService`-Parameter aus Konstruktor entfernt, `setChiefService()`-Setter eingeführt; `chiefService`-Feld nicht mehr `final` |
| Build | ✅ Erfolgreich | `compileJava` und `shadowJar` sauber (nach Reboot wegen Build-Lock) |

**Zusätzlich behoben (nicht in Original-Karte):** Entkopplung der zirkulären Abhängigkeit `MourningService` ↔ `ChiefService`. Initialisierungsreihenfolge in `onEnable()` jetzt: `MourningService` (ohne ChiefService) → `ChiefService` (mit mourningService) → `mourningService.setChiefService(chiefService)`."
