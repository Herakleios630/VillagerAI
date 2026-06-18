# VillagerAI Refactoring: Core + Modules Architektur

> **Status:** Konzeptphase | **Erstellt:** 2025-01-19

---

## 1. Analyse: Migration der heutigen Services & Listener

### 1.1 Was bleibt im Core?

Der Core stellt **ausschliesslich Infrastruktur** bereit - kein aktives Gameplay. Kein Core-Service veraendert Vanilla-Verhalten eigenstaendig.

| Heutige Datei | Core-Rolle | Begruendung |
|---|---|---|
| `VillageChiefPlugin.java` | `CorePlugin` (Bootstrap, ModuleRegistry) | Plugin-Lifecycle, Module laden/entladen |
| `PluginDataLoader.java` | `CoreConfigService` | Zentrale Config-Ladung, YAML-Parsing |
| `Keys.java` | `CoreKeys` | NamespacedKey-Factory (benoetigen alle Module) |
| `AIService.java` (Interface) | `core/ai/AIService.java` | Interface fuer AI-Provider |
| `HttpAIService.java` | `core/ai/HttpAIService.java` | HTTP-Transport (kein Gameplay) |
| `DummyAIService.java` | `core/ai/DummyAIService.java` | Fallback |
| `AIReply.java`, `AIRequest.java` | `core/model/` | AI-DTOs |
| `EntityTargetingUtil.java` | `core/util/` | Hilfsfunktion fuer Entity-Raytrace |
| Alle `*Repository.java` Interfaces | `core/storage/api/` | Storage-Interfaces |
| Alle `Yaml*Repository.java` | `core/storage/yaml/` | YAML-Implementierungen |
| `DarkBlockCache.java` | `core/world/DarkBlockCache.java` | Cached Blockscans (wird von Quests+Perimeter gebraucht) |
| `LightLevelScanner.java` | `core/world/LightLevelScanner.java` | Light-Level-Scans (wiederverwendbar) |
| `VillagePerimeterService.java` | `core/world/VillagePerimeterService.java` | Perimeter-Berechnung (kein Gameplay) |
| `VillagerConfinementService.java` | `core/vanilla/VillagerConfinementService.java` | Vanilla-Schutz: erkennt feststeckende Villager |

### 1.2 Was wird zu Modulen? (4 statt 5: speaker+conversation zusammengelegt)

| Modul | Heutige Services/Listener | Aktiviert Features |
|---|---|---|
| **quests** | `QuestService`, `QuestOfferService`, `QuestRewardService`, `QuestDifficultyService`, `QuestUiService`, `QuestMarkerService`, `QuestGiverLocatorService`, `QuestLifecycleListener`, `QuestUiListener`, `LegendaryUnlockService` | Alle Quest-Typen (TALK, DELIVER, FETCH, KILL, BUILD, BREED, BREW, VISIT, EXPLORE, SECURE, RETINUE_*, LEGENDARY_*) |
| **reputation** | `ReputationService`, `ReputationListener`, `ReputationChangedEvent` | Reputation & Events |
| **interaction** | `SpeakerService`, `SpeakerLifecycleListener`, `VillagerInteractListener`, `ChiefAutoAssignmentService`, `ChiefMeetingObserver`, `ConversationService`, `ConversationHistoryRepository`, `PlayerChatListener`, `VillagerContextService`, `VillagerTradeService`, `VillagerTradeListener`, `VillagerDebugOverlayService` | Speaker-Management, Chief-Auto-Assignment, AI-Chat, Conversation-Lifecycle, Trade-Tracking, Debug-Overlay |
| **village** | `VillageIdentityService`, `VillagePerimeterDisplayService`, `VillageLightParticleMarkerService`, `MourningService`, `ChiefService`, `ChiefVisualService`, `ChiefDeathHandler`, `VillagePerimeter`, `VillageRecord`, `VillageReputation`, `Anchor`, `BiomeFamily`, `VillageIdentity` | Village-Identity, Perimeter-Display, Chief-Visuals, Trauer-System |

**Begruendung fuer Zusammenlegung speaker+conversation:** Beide sind extrem eng verzahnt (VillagerInteractListener ruft ConversationService auf). Ein gemeinsames `interaction`-Modul vermeidet EventBus-Overhead fuer interne Aufrufe und reduziert die Modul-Anzahl von 5 auf 4.

### 1.3 Aufgeteilte Dateien (>300 Zeilen)

| Datei | Zeilen (~) | Aufteilung in |
|---|---|---|
| `QuestService.java` | 900+ | `QuestCrudService` (CRUD), `QuestTypeHandlers` (pro Typ eine Klasse), `QuestProgressService` (Fortschritt) |
| `ConversationService.java` | 700+ | `ConversationOrchestrator`, `ConversationStateMachine`, `SpontaneousOfferEngine` |
| `ChiefCommand.java` | 800+ | `ChiefCommandRouter` + pro Subcommand eine Handler-Klasse |
| `QuestOfferService.java` | 500+ | `QuestOfferEngine`, `QuestTemplateResolver` |
| `VillageChiefPlugin.java` | 500+ | `CorePlugin` (Bootstrap), `ModuleRegistry`, `ModuleLifecycleManager` |

---

## 2. Package/Ordnerstruktur

```
src/main/java/de/ajsch/villagerai/
+-- core/                              # Leichtgewichtig, vanilla-kompatibel
|   +-- CorePlugin.java                # Ersetzt VillageChiefPlugin
|   +-- ModuleRegistry.java            # Registriert/verwaltet Module
|   +-- Module.java                    # Interface fuer alle Module
|   +-- config/
|   |   +-- CoreConfigService.java     # Globale + modul-spezifische Config
|   |   +-- PluginDataLoader.java      # YAML-Ladung (umbenannt aus config/)
|   +-- ai/
|   |   +-- AIService.java             # Interface (wie bisher)
|   |   +-- HttpAIService.java
|   |   +-- DummyAIService.java
|   +-- storage/
|   |   +-- api/
|   |   |   +-- ChiefRepository.java
|   |   |   +-- QuestRepository.java
|   |   |   +-- ReputationRepository.java
|   |   |   +-- SpeakerRepository.java
|   |   |   +-- VillageRepository.java
|   |   |   +-- VillagerTradeRepository.java
|   |   |   +-- ConversationHistoryRepository.java
|   |   +-- yaml/
|   |       +-- YamlChiefRepository.java
|   |       +-- YamlQuestRepository.java
|   |       +-- ... (alle Yaml*Implementierungen)
|   +-- world/
|   |   +-- DarkBlockCache.java
|   |   +-- LightLevelScanner.java
|   |   +-- VillagePerimeterService.java
|   |   +-- WorldScannerService.java   # Neu: vereinheitlicht Scans
|   |   +-- ParticleMarkerService.java # Neu: Partikel-Effekte
|   +-- vanilla/
|   |   +-- VillagerConfinementService.java  # Vanilla-Schutz
|   +-- event/
|   |   +-- CoreEventBus.java          # Lose Kopplung zwischen Modulen
|   +-- command/
|   |   +-- CommandRegistry.java       # Neu: SubCommand-Registrierung
|   +-- util/
|       +-- Keys.java
|       +-- EntityTargetingUtil.java
|
+-- api/                               # Oeffentliche API fuer Module & andere Plugins
|   +-- event/
|   |   +-- ReputationChangedEvent.java
|   |   +-- QuestCompletedEvent.java
|   |   +-- SpeakerAssignedEvent.java
|   |   +-- ... (weitere Events)
|   +-- model/                         # Kriterium: taucht in Event-Typ oder Core-Service-Interface auf
|       +-- Quest.java
|       +-- QuestStatus.java
|       +-- QuestType.java
|       +-- QuestCategory.java         # Neu: Kategorie-Enum
|       +-- Speaker.java
|       +-- VillageRecord.java
|       +-- ... (geteilte DTOs)
|
+-- model/                             # Modul-interne Modelle (nur innerhalb eines Moduls verwendet)
|   +-- ChiefAttributes.java
|   +-- ChiefVisualTier.java
|   +-- ConversationHistory.java
|   +-- ConversationRole.java
|   +-- ConversationTurn.java
|   +-- ConversationVisibility.java
|   +-- ReputationScope.java
|   +-- SpeakerReputation.java
|   +-- SpeakerStatus.java
|   +-- VillageIdentity.java
|   +-- ...
|
+-- modules/                           # Optional aktivierbare Module (4 Stueck)
    +-- quests/
    |   +-- QuestsModule.java
    |   +-- service/
    |   |   +-- QuestCrudService.java
    |   |   +-- QuestProgressService.java
    |   |   +-- QuestOfferService.java
    |   |   +-- QuestRewardService.java
    |   |   +-- QuestDifficultyService.java
    |   |   +-- QuestUiService.java
    |   |   +-- QuestMarkerService.java
    |   |   +-- QuestGiverLocatorService.java
    |   +-- handler/
    |   |   +-- TalkQuestHandler.java
    |   |   +-- DeliverQuestHandler.java
    |   |   +-- FetchQuestHandler.java
    |   |   +-- KillQuestHandler.java
    |   |   +-- BuildQuestHandler.java
    |   |   +-- BreedQuestHandler.java
    |   |   +-- BrewQuestHandler.java
    |   |   +-- VisitQuestHandler.java
    |   |   +-- ExploreQuestHandler.java
    |   |   +-- SecureQuestHandler.java
    |   |   +-- RetinueQuestHandler.java
    |   |   +-- LegendaryQuestHandler.java
    |   +-- listener/
    |   |   +-- QuestLifecycleListener.java
    |   |   +-- QuestUiListener.java
    |   +-- legendary/
    |       +-- LegendaryUnlockService.java
    |
    +-- reputation/
    |   +-- ReputationModule.java
    |   +-- service/
    |   |   +-- ReputationService.java
    |   +-- listener/
    |       +-- ReputationListener.java
    |
    +-- interaction/                    # speaker + conversation zusammengelegt
    |   +-- InteractionModule.java
    |   +-- speaker/
    |   |   +-- SpeakerService.java
    |   |   +-- ChiefAutoAssignmentService.java
    |   |   +-- SpeakerLifecycleListener.java
    |   |   +-- VillagerInteractListener.java
    |   |   +-- ChiefMeetingObserver.java
    |   +-- conversation/
    |   |   +-- ConversationService.java
    |   |   +-- ConversationStateMachine.java
    |   |   +-- SpontaneousOfferEngine.java
    |   |   +-- PlayerChatListener.java
    |   +-- context/
    |   |   +-- VillagerContextService.java
    |   |   +-- VillagerTradeService.java
    |   |   +-- VillagerTradeListener.java
    |   +-- chief/
    |   |   +-- ChiefService.java
    |   |   +-- ChiefVisualService.java
    |   |   +-- ChiefDeathHandler.java
    |   +-- debug/
    |       +-- VillagerDebugOverlayService.java
    |
    +-- village/
        +-- VillageModule.java
        +-- service/
        |   +-- VillageIdentityService.java
        |   +-- VillagePerimeterDisplayService.java
        |   +-- VillageLightParticleMarkerService.java
        |   +-- MourningService.java
        +-- listener/
            +-- (ChunkLoad-Listener fuer Mourning)

# Platzhalter fuer spaeteres living-world Modul
# modules/living-world/ - siehe Abschnitt 15

**Trennkriterium `api/model/` vs `model/`:**
- `api/model/` - Jede Klasse, die in einem Event-Typ ODER als Parameter/Rueckgabewert eines Core-Service-Interfaces auftaucht
- `model/` - Alles, was NUR innerhalb eines einzelnen Moduls verwendet wird
- Ein Model wandert von `model/` nach `api/model/`, sobald ein zweites Modul es importieren muss
```

---

## 3. Module-Interface

```java
package de.ajsch.villagerai.core;

import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

public interface Module {
    String id();
    String displayName();
    List<String> dependencies();
    void onEnable(ModuleContext context);
    void onDisable();
    void reload(ConfigurationSection moduleConfig);
    boolean isEnabled();
}
```

**`ModuleContext`** gibt Zugriff auf:
```java
public interface ModuleContext {
    CorePlugin plugin();
    Logger logger();
    CoreEventBus eventBus();
    CoreConfigService configService();
    CommandRegistry commandRegistry();
    AIService aiService();
    // Storage-Repos
    ChiefRepository chiefRepository();
    SpeakerRepository speakerRepository();
    QuestRepository questRepository();
    ReputationRepository reputationRepository();
    VillageRepository villageRepository();
    VillagerTradeRepository tradeRepository();
    ConversationHistoryRepository conversationRepository();
    // World-Services
    WorldScannerService worldScanner();
    ParticleMarkerService particleMarker();
    VillagePerimeterService perimeterService();
}
```

---

## 4. Phasenplan

### Phase 0: Analyse & Vorbereitung (1 Tag)
- [x] 0.1 Aktuellen Code-Stand dokumentieren (diese Analyse)
- [ ] 0.2 Alle Imports und Abhaengigkeiten zwischen Services kartografieren
- [ ] 0.3 Testfaelle fuer Regression dokumentieren
- [ ] 0.4 Feature-Flags in config.yml definieren (`modules.quests.enabled: true`)

**Test-Strategie (verbindlich fuer alle Phasen):**

| Test-Typ | Wann | Werkzeug | Verantwortlich |
|---|---|---|---|
| Compile-Test | Nach jedem Task | `gradlew compileJava` | Entwickler |
| Unit-Test | Neue Utility-Klassen (EventBus, Registry, Validator) | JUnit 5 + MockBukkit | Entwickler |
| Startup-Test | Nach jeder Phase | Plugin startet auf Crafty ohne Crash | Entwickler |
| Smoke-Test | Nach jeder Phase | Quest annehmen+abschliessen, Reputation pruefen | Entwickler / Tester |

**Unit-Test-Policy:**
- Jede neue Klasse in `core/` (EventBus, ModuleRegistry, ConfigValidator) MUSS Unit-Tests haben
- Modul-interne Handler KOeNNEN Unit-Tests haben (MockBukkit fuer Player/World)
- Legacy-Code (YamlRepositories) wird NUR getestet, wenn er refaktorisiert wird
- Testklassen liegen unter `src/test/java/de/ajsch/villagerai/` mit gleicher Package-Struktur

### Phase 1: Core-Extraktion (3-5 Tage)
- [ ] 1.1 `Module.java` Interface + `ModuleContext.java` + `CommandRegistry.java` erstellen
- [ ] 1.2 `CorePlugin.java` aus `VillageChiefPlugin.java` extrahieren (nur Lifecycle, ModuleRegistry)
- [ ] 1.3 `CoreEventBus.java` implementieren (register/unregister/post)
- [ ] 1.4 `CoreConfigService.java` - Config-Sections pro Modul laden
- [ ] 1.5 Storage-Interfaces nach `core/storage/api/` verschieben
- [ ] 1.6 World-Services nach `core/world/` verschieben, `WorldScannerService` definieren
- [ ] 1.7 `VillagerConfinementService` nach `core/vanilla/` (bleibt immer an)
- [ ] 1.8 Plugin kompiliert UND laeuft ohne Module (reiner Core, keine Features)

### Phase 2: Erstes Modul - Reputation (2-3 Tage)
- [ ] 2.1 `ReputationModule.java` implementieren
- [ ] 2.2 `ReputationService` + `ReputationListener` migrieren
- [ ] 2.3 `ReputationChangedEvent` in API-Event umwandeln
- [ ] 2.4 Modul ueber config.yml aktivierbar machen
- [ ] 2.5 Deployment + Test auf Production

### Phase 3: Quests-Modul (5-7 Tage) - groesster Block
- [ ] 3.1 `QuestsModule.java` mit Dependencies `reputation`
- [ ] 3.2 QuestService aufteilen: `QuestCrudService` + `QuestProgressService`
- [ ] 3.3 `QuestTypeRegistry` + `QuestHandler` Interface implementieren
- [ ] 3.4 `QuestCategory` Enum, bestehende QuestTypes kategorisieren
- [ ] 3.5 Pro QuestType einen Handler erstellen (12+ Handler a ~50-100 Zeilen)
- [ ] 3.6 `QuestOfferService` + `QuestRewardService` migrieren
- [ ] 3.7 `QuestMarkerService` + `QuestUiService` migrieren (nutzt ParticleMarkerService)
- [ ] 3.8 `LegendaryUnlockService` migrieren
- [ ] 3.9 Listener migrieren (QuestLifecycle, QuestUi)
- [ ] 3.10 Config-Validierung: `ConfigValidator` fuer Quests registrieren
- [ ] 3.11 QuestRepository um In-Memory-Index `Map<UUID,List<Quest>>` erweitern
- [ ] 3.12 Vollstaendiger Quest-Test (alle Typen)

### Phase 4: Interaction-Modul (4-6 Tage)
- [ ] 4.1 `InteractionModule.java` mit Dependencies `quests`, `reputation`
- [ ] 4.2 SpeakerService + ChiefAutoAssignment migrieren
- [ ] 4.3 VillagerInteractListener migrieren
- [ ] 4.4 ConversationService aufteilen (Orchestrator, StateMachine, SpontaneousOfferEngine)
- [ ] 4.5 VillagerContext + Trade migrieren
- [ ] 4.6 Chief-spezifische Services einhaengen (ChiefService, ChiefVisual, ChiefDeath)
- [ ] 4.7 DebugOverlayService migrieren
- [ ] 4.8 Integrationstest: Speaker, Chief, Chat, Trade funktionieren

### Phase 5: Village-Modul (3-4 Tage)
- [ ] 5.1 `VillageModule.java` mit Dependencies `interaction`, `reputation`
- [ ] 5.2 VillageIdentityService + PerimeterDisplay + Mourning migrieren
- [ ] 5.3 VillageLightParticleMarkerService auf ParticleMarkerService umstellen
- [ ] 5.4 `GlobalTickService` (1 BukkitTask/sec) einfuehren, Modul-Timer umhaengen
- [ ] 5.5 DarkBlockCache Invalidation via BlockPhysicsEvent
- [ ] 5.6 ConversationHistory-Pruning (async, via aiExecutor)
- [ ] 5.7 Abschluss-Test: Alle Module aktiv, alle Features funktionieren

### Phase 6: Monolith-Code entfernen (1-2 Tage)
- [ ] 6.1 `VillageChiefPlugin.java` komplett durch `CorePlugin.java` ersetzen
- [ ] 6.2 Alte Direkt-Abhaengigkeiten loeschen
- [ ] 6.3 `SubCommandHandler` Interface + `CommandRegistry` fertigstellen
- [ ] 6.4 ChiefCommand in SubCommand-Handler aufteilen, in Module verschieben
- [ ] 6.5 Finaler Regressionstest

---

## 5. Task Cards (vollstaendig, je 1-4 Stunden)

| ID | Phase | Aufgabe | Geschaetzt |
|---|---|---|---|
| T01 | 1 | `Module.java` + `ModuleContext.java` + `CommandRegistry.java` definieren | 1.5h |
| T02 | 1 | `CoreEventBus.java` implementieren (register/unregister/post) + Unit-Test | 2h |
| T03 | 1 | `CorePlugin.java`: Lifecycle, ModuleRegistry als Map, topologicalSort | 3h |
| T04 | 1 | `CoreConfigService.java`: `getModuleConfig(id)` -> ConfigurationSection | 2h |
| T05 | 1 | Storage-Interfaces nach `core/storage/api/` verschieben (7 Interfaces) | 1.5h |
| T06 | 1 | Yaml-Repos nach `core/storage/yaml/` verschieben (8 Implementierungen) | 1h |
| T07 | 1 | World-Services nach `core/world/` verschieben, `WorldScannerService` definieren | 2h |
| T08 | 1 | `ParticleMarkerService` im Core implementieren | 2h |
| T09 | 1 | `VillagerConfinementService` nach `core/vanilla/` | 1h |
| T10 | 1 | `config.yml` um `modules:` Sektion erweitern (Feature-Flags) | 0.5h |
| T11 | 1 | Build + Test: Plugin startet als reiner Core (alle Module disabled) | 2h |
| T12 | 2 | `ReputationModule.java` implementieren | 2h |
| T13 | 2 | `ReputationService` + `ReputationListener` entkoppeln, in Modul verschieben | 2h |
| T14 | 2 | `ReputationChangedEvent` in API-Event umwandeln, via EventBus | 1.5h |
| T15 | 2 | Build + Deploy + Test: Reputation-Modul standalone | 1h |
| T16 | 3 | `QuestTypeRegistry` + `QuestHandler` Interface implementieren | 2h |
| T17 | 3 | `QuestCategory` Enum definieren, bestehende QuestTypes kategorisieren | 1h |
| T18 | 3 | QuestService aufteilen: `QuestCrudService` + `QuestProgressService` | 3h |
| T19 | 3 | Ersten QuestHandler via Registry+YAML demonstrieren (z.B. TALK) | 2h |
| T20 | 3 | Alle 12 QuestHandler implementieren (je ~1 Handler/0.5h) | 4h |
| T21 | 3 | `QuestOfferService` + `QuestRewardService` migrieren | 2h |
| T22 | 3 | `QuestMarkerService` + `QuestUiService` auf ParticleMarkerService umstellen | 2h |
| T23 | 3 | `ConfigValidator` fuer Quests registrieren (Pflichtfelder pruefen) | 2h |
| T24 | 3 | QuestRepository um In-Memory-Index erweitern | 1.5h |
| T25 | 3 | Build + Deploy + Test: Alle Quest-Typen funktionieren | 3h |
| T26 | 4 | `InteractionModule.java` mit Dependencies `quests`, `reputation` | 2h |
| T27 | 4 | SpeakerService + VillagerInteractListener migrieren | 2h |
| T28 | 4 | ConversationService aufteilen (Orchestrator, StateMachine, SpontaneousEngine) | 3h |
| T29 | 4 | VillagerContext + Trade + DebugOverlay migrieren | 2h |
| T30 | 4 | ChiefService + ChiefVisual + ChiefDeath migrieren | 2h |
| T31 | 4 | Build + Deploy + Test: Interaction-Modul komplett | 2h |
| T32 | 5 | `VillageModule.java` mit Dependencies `interaction`, `reputation` | 2h |
| T33 | 5 | VillageIdentity + PerimeterDisplay + Mourning migrieren | 3h |
| T34 | 5 | `GlobalTickService` einfuehren, alle Modul-Timer umhaengen | 2h |
| T35 | 5 | DarkBlockCache Invalidation + ConversationHistory-Pruning | 2h |
| T36 | 5 | Build + Deploy + Test: Alle Module aktiv | 2h |
| T37 | 6 | `SubCommandHandler` Interface + ChiefCommand aufteilen | 3h |
| T38 | 6 | SubCommands in ihre Module verschieben | 3h |
| T39 | 6 | `VillageChiefPlugin.java` entfernen, `CorePlugin.java` finalisieren | 2h |
| T40 | 6 | Finaler Regressionstest + Deployment | 2h |

---

## 6. Detail-Tipps

### 6.1 Event-System fuer lose Kopplung

```java
public class CoreEventBus {
    private final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();

    public <T> void register(Class<T> eventType, Consumer<T> handler) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    @SuppressWarnings("unchecked")
    public <T> void post(T event) {
        List<Consumer<?>> handlers = listeners.getOrDefault(event.getClass(), List.of());
        for (Consumer<?> handler : handlers) {
            ((Consumer<T>) handler).accept(event);
        }
    }
}
```

**Verwendung:**
- Modul A posted `QuestCompletedEvent` -> Modul B (Reputation) updated Werte.
- Keine direkten Imports zwischen Modulen noetig.
- Events liegen in `api/event/`.

### 6.2 Config-Management

```yaml
# config.yml
modules:
  quests:
    enabled: true
    cooldown-seconds: 300
    markers-enabled: true
  reputation:
    enabled: true
    decay-rate: 0.01
  interaction:
    enabled: true
  village:
    enabled: true
```

Jedes Modul bekommt via `CoreConfigService.getModuleConfig("quests")` seine eigene `ConfigurationSection`. Module lesen NUR ihre eigene Section - keine Quer-Zugriffe.

### 6.3 Dependency Resolution

```java
public class ModuleRegistry {
    private final Map<String, Module> modules = new LinkedHashMap<>();

    public void register(Module module) {
        modules.put(module.id(), module);
    }

    public void enableAll(ModuleContext context) {
        Set<String> enabled = new HashSet<>();
        for (Module module : topologicalSort()) {
            if (!isEnabledInConfig(module.id())) {
                context.logger().info("Modul '" + module.id() + "' deaktiviert (config)");
                continue;
            }
            for (String dep : module.dependencies()) {
                if (!enabled.contains(dep)) {
                    throw new IllegalStateException(
                        "Modul '" + module.id() + "' benoetigt '" + dep + "', aber es ist nicht aktiv");
                }
            }
            module.onEnable(context);
            enabled.add(module.id());
        }
    }

    private List<Module> topologicalSort() {
        // Kahn-Algorithmus ueber modules.values() anhand dependencies()
    }
}
```

### 6.4 Vermeidung zyklischer Abhaengigkeiten

**Regel:** Module duerfen sich NUR ueber Events (nicht ueber direkte Methodenaufrufe) beeinflussen.

**Erlaubt:**
- `quests` -> posted `QuestCompletedEvent` -> `reputation` erhoeht Werte
- `interaction` -> posted `ConversationEndedEvent` -> `quests` prueft Quest-Uebergabe

**Verboten:**
- `QuestService` ruft direkt `ReputationService.addReputation()` auf
- Stattdessen: `QuestService` posted Event, `ReputationModule` subscribed

**Tool zur Pruefung:** `jdeps` oder manuelle Import-Analyse: Kein `modules/X/` import darf auf `modules/Y/` verweisen.

### 6.5 Core bleibt vanilla - aktive Features nur in Modulen

**Prinzip:** Wenn alle Module in `config.yml` auf `enabled: false` gesetzt sind, verhaelt sich der Server **exakt wie Vanilla Minecraft**.

**Checkliste fuer Nicht-Invasivitaet:**
- [ ] Kein NMS/Reflection im Core
- [ ] Keine AI-Goals im Core
- [ ] Kein Pathfinding im Core
- [ ] Keine Villager-Profession-Aenderungen im Core
- [x] Nur `VillagerConfinementService` laeuft im Core (erkennt feststeckende Villager via Bukkit-API, kein NMS)
- [x] Alle Features (Quests, Chief-Visuals, Reputation-Display, AI-Chat) sind Modul-Sache

### 6.6 Unified World Services (Core)

Diese Services liegen im **Core** unter `core/world/`, weil sie von mehreren Modulen verwendet werden.

| Service | Aufgabe | Wird genutzt von |
|---|---|---|
| `WorldScannerService` | Vereinheitlicht DarkBlockCache + LightLevelScanner -> ein `AreaScanResult` | Quests (SECURE, EXPLORE), Village (Perimeter-Validierung) |
| `ParticleMarkerService` | Zeigt zeitlich begrenzte Partikel-Effekte an Koordinaten | Quests (Marker), Village (Perimeter-Display), Interaction (Chief-Visuals) |
| `PathValidator` | Prueft, ob ein Ziel in der Spieler-Dimension erreichbar ist | Quests (VISIT, EXPLORE) |

### 6.7 Reserve: living-world Package-Slot

In der Package-Struktur ist `modules/living-world/` als Platzhalter vorgesehen. Das Modul wird NICHT in diesem Refactoring implementiert, aber folgende Voraussetzungen werden geschaffen:
- `CoreEventBus` unterstuetzt asynchrone Events (kein Umbau noetig)
- `GlobalTickService` (T34) kann zusaetzliche Timer aufnehmen
- `VillagerConfinementService` kann via Config deaktiviert werden, wenn Living World uebernimmt
- `WorldScannerService` ist erweiterbar fuer Biome-Scans

---

## 7. Zusammenfassung

| Metrik | Vorher | Nachher |
|---|---|---|
| Package-Tiefe | 1 (flach) | 3 (`core/`, `modules/quests/handler/`, ...) |
| Max. Dateigroesse | 900+ Zeilen (QuestService) | <400 Zeilen |
| Features abschaltbar | [ ] Nur global | [x] Pro Modul |
| Vanilla-kompatibel | [x] | [x] (verbessert - Core ohne Module = Vanilla) |
| API fuer andere Plugins | [ ] Keine | [x] `api/` Package |
| Query zwischen Modulen | Direkte Methodenaufrufe | Events via EventBus |
| Erweiterbarkeit | Neuer QuestType -> QuestService aendern | Neuer QuestType -> Neuer Handler in YAML + Klasse |
| Modulanzahl | 0 (Monolith) | 4 (quests, reputation, interaction, village) |
| SQLite-Backend | [ ] | [ ] Zukunftsbaustein (siehe roadmap) |

---

**Naechster Schritt:** Phase 0.2 (Abhaengigkeitsgraph kartografieren) + Task Card T01 (Module-Interface implementieren).
