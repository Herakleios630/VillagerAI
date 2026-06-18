# Payload-Felder: Plugin ↔ Bridge

Dokumentiert die JSON-Feldnamen, die das Java-Plugin (`HttpAIService`)
als Payload an die Python-Bridge (`chief-ai-service`) sendet.

Stand: 2025-07-17 (erstellt im Rahmen Stabilisierung S04, Cluster C)

## Serialisierungsregeln

- Java: `HttpAIService.HttpRequestPayload` wird via Gson serialisiert.
- Field-Naming: Standardmäßig camelCase (Java-Feldname = JSON-Feldname).
- Ausnahmen via `@SerializedName`:
  - `memoryEnabled` → `memory_enabled`
  - `memoryTriggerFallbackPhrases` → `memory_trigger_fallback_phrases`

---

## Gesendete Felder (Java → Bridge)

| JSON-Feldname | Java-Quelle (AIRequest-Feld) | Typ | Bemerkung |
|---|---|---|---|
| `systemPrompt` | (wird in `HttpAIService.buildSystemPrompt()` aus mehreren Feldern gebaut) | String | Enthält Rollen-Narrative, Smalltalk-Guard |
| `speakerId` | `speakerId` | String | Eindeutige ID des Speakers |
| `villageId` | `villageId` | String | |
| `villageName` | `villageName` | String | |
| `villageDescription` | `villageDescription` | String | |
| `villageAttributes` | `villageAttributes` | String | |
| `villageBiome` | `villageBiome` | String | |
| `villagePopulationEstimate` | `villagePopulationEstimate` | int | |
| `villageEventSummary` | `villageEventSummary` | String | |
| `displayName` | `displayName` | String | Name des Speakers (früher chiefName) |
| `role` | `role` | String | z. B. "Häuptling", "Bauer" |
| `personality` | `personality` | String | |
| `speechTone` | `speechTone` | String | |
| `behaviorHint` | `behaviorHint` | String | |
| `greeting` | `greeting` | String | |
| `villagerProfession` | `villagerProfession` | String | |
| `villagerType` | `villagerType` | String | |
| `currentBiome` | `currentBiome` | String | |
| `worldName` | `worldName` | String | |
| `isDay` | `isDay` | boolean | |
| `isRaining` | `isRaining` | boolean | |
| `isThundering` | `isThundering` | boolean | |
| `currentHealth` | `currentHealth` | double | |
| `maxHealth` | `maxHealth` | double | |
| `healthRatio` | `healthRatio` | double | |
| `ateRecently` | `ateRecently` | boolean | |
| `tradeSummary` | `tradeSummary` | String | |
| `confinementSummary` | `confinementSummary` | String | |
| `authoritativeWorldFactsSummary` | `authoritativeWorldFactsSummary` | String | |
| `recentConversation` | `recentConversation` | String | |
| `relationshipMemorySummary` | `relationshipMemorySummary` | String | |
| `homePoi` | `homePoi` | String | |
| `jobSitePoi` | `jobSitePoi` | String | |
| `potentialJobSitePoi` | `potentialJobSitePoi` | String | |
| `meetingPointPoi` | `meetingPointPoi` | String | |
| `villageReputationScore` | `villageReputationScore` | int | |
| `villageReputationSummary` | `villageReputationSummary` | String | |
| `speakerReputationScore` | `speakerReputationScore` | int | |
| `speakerReputationSummary` | `speakerReputationSummary` | String | |
| `combinedReputationScore` | `combinedReputationScore` | int | |
| `combinedReputationSummary` | `combinedReputationSummary` | String | |
| `reputationScore` | `reputationScore` | int | Legacy-Feld (identisch zu combinedReputationScore) |
| `reputationSummary` | `reputationSummary` | String | Legacy-Feld |
| `villageHasChief` | `villageHasChief` | boolean | |
| `villageMourning` | `villageMourning` | boolean | |
| `chiefLocation` | (gebaut in `ConversationService.buildChiefLocation()`) | String | |
| `speakerStatus` | `speakerStatus` | String | Enum-Name, z. B. "AKTIV_CHIEF" |
| `chiefAttributes` | (via `findChiefAttributes()`) | Object/nullable | |
| `playerUuid` | `playerUuid` (UUID als String) | String | |
| `playerMessage` | `playerMessage` | String | |
| `memory_enabled` | `memoryEnabled` | boolean | @SerializedName |
| `memory_trigger_fallback_phrases` | `memoryTriggerFallbackPhrases` | List\<String\> | @SerializedName |

---

## Bridge-Empfänger (Python liest diese Felder)

### `prompt_builder.py`

| Sektion | Gelesene Felder |
|---|---|
| `_build_ground_truth_section` | `displayName`, `role`, `speakerStatus`, `villageName`, `villageDescription`, `chiefNarrative` ❌, `combinedReputationScore`/`reputationScore` |
| `_build_personality_section` | `displayName`, `role`, `personality`, `speechTone`, `behaviorHint`, `greeting` |
| `_build_village_section` | `villageBiome`, `villagePopulationEstimate`, `villageEventSummary`, `villageAttributes`, `chiefLocation` |
| `_build_reputation_section` | `villageReputationScore`/`reputationScore`, `speakerReputationScore`/`reputationScore`, `combinedReputationScore`/`reputationScore`, `relationshipMemorySummary` |
| `_build_status_section` | `villagerType`, `currentBiome`, `worldName`, `isDay`, `isRaining`, `isThundering`, `currentHealth`, `maxHealth`, `healthRatio`, `ateRecently`, `tradeSummary`, `confinementSummary`, `authoritativeWorldFactsSummary`, `recentConversation`, `homePoi`, `jobSitePoi`, `potentialJobSitePoi`, `meetingPointPoi` |
| `_build_rules_section` | `combinedReputationScore`/`reputationScore` |
| `_build_knowledge_section` | `villagerProfession`, `isDay`, `villageBiome`, `currentBiome`, `playerMessage` |
| `_build_facts_section` | `mcDay` ❌ |
| `resolve_system_prompt` | `systemPrompt` (ersetzt Config-Prompt wenn gesetzt) |

### `reply_builder.py`

| Funktion | Gelesene Felder |
|---|---|
| `_load_memory_context` | `playerUuid`, `chiefName` ❌, `playerMessage` |
| `build_reply` (Dummy) | `playerMessage` |

### `http_app.py`

| Funktion | Gelesene Felder |
|---|---|
| `_store_turns_background` | `playerUuid`, `chiefName` ❌, `playerMessage`, `mcDay` ❌, `mcTime` ❌ |
| `do_POST` | `playerMessage` (Trigger-Check) |

---

## Bekannte Diskrepanzen (❌)

| Bridge-Feld | Problem | Lösung |
|---|---|---|
| `chiefName` | Java sendet nur `displayName` | Bridge auf `displayName` umstellen, mit Fallback "Dorfbewohner" |
| `chiefNarrative` | Java sendet kein separates Feld; Narrative steckt im `systemPrompt` | Bridge: `chiefNarrative` nicht selbst bauen, sondern `_build_ground_truth_section` vereinfachen (Nutze `systemPrompt` bereits) – ODER Java sendet `chiefNarrative` separat |
| `mcDay` | Java `AIRequest` hat kein mcDay-Feld | In `AIRequest` und `HttpAIService.HttpRequestPayload` ergänzen; `ConversationService` muss den Wert aus der Weltzeit berechnen |
| `mcTime` | Java `AIRequest` hat kein mcTime-Feld | Wie mcDay |

---

## Beschluss

1. **`chiefName` → `displayName`**: Bridge stellt ALLE `chiefName`-Zugriffe auf `displayName` um.
2. **`chiefNarrative`**: Java sendet zusätzlich `chiefNarrative` als eigenes Feld (wird bereits in `ConversationService.buildChiefNarrative()` berechnet, aber nur für `systemPrompt` verwendet).
3. **`mcDay`/`mcTime`**: Werden im `AIRequest` ergänzt und von Java befüllt. Bridge bleibt unverändert (liest sie bereits korrekt).
4. Mapping-Tabelle wird in diesem Dokument gepflegt.