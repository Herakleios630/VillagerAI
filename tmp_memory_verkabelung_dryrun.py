"""Dry-run test for memory wiring in reply pipeline."""
import json
import sys
import os

project_dir = os.path.join(os.path.dirname(__file__), "chief-ai-service")
sys.path.insert(0, project_dir)

from chief_ai_service.reply_builder import build_reply
from chief_ai_service.prompt_builder import build_context_prompt, build_deepseek_messages


# Minimal config (no real API keys needed for prompt construction)
config = {
    "provider": "deepseek",
    "village_name": "Testdorf",
    "deepseek": {
        "model": "deepseek-chat",
        "temperature": 0.55,
        "top_p": 0.9,
        "max_tokens": 120,
        "endpoint": "https://api.deepseek.com/chat/completions",
        "api_key": "sk-test-placeholder",
    },
    "memory": {
        "enabled": True,
        "trigger_phrases": ["erinnerst du dich", "weisst du noch", "letztes mal", "gestern", "vorhin"],
        "embedding_search": True,
        "summary_search": True,
        "embedding_top_n": 5,
        "embedding_min_similarity": 0.5,
        "summary_interval_turns": 20,
    },
}

# Test payload with memoryTriggered=True
payload = {
    "playerUuid": "test-uuid-1234",
    "chiefId": "chief-1",
    "chiefName": "Grimmig",
    "chiefRole": "Dorfhaeuptling",
    "chiefPersonality": "rau",
    "chiefGreeting": "Na endlich.",
    "villageId": "village-1",
    "villageName": "Testdorf",
    "villageBiome": "PLAINS",
    "villagePopulationEstimate": 5,
    "villageEventSummary": "kein wichtiges Dorfereignis bekannt",
    "villageAttributes": "klein, abgelegen",
    "playerMessage": "Erinnerst du dich an gestern?",
    "memoryTriggered": True,
    "villagerProfession": "NONE",
    "villagerType": "PLAINS",
    "currentBiome": "PLAINS",
    "worldName": "world",
    "isDay": True,
    "isRaining": False,
    "isThundering": False,
    "currentHealth": 20.0,
    "maxHealth": 20.0,
    "healthRatio": 1.0,
    "ateRecently": True,
    "tradeSummary": "keine bekannten Trades mit diesem Spieler",
    "confinementSummary": "kein klarer Hinweis auf Einschluss oder Vernachlaessigung",
    "authoritativeWorldFactsSummary": "",
    "recentConversation": "Spieler hat gestern ueber Eisen gesprochen",
    "homePoi": "Haus",
    "jobSitePoi": "unbekannt",
    "potentialJobSitePoi": "unbekannt",
    "meetingPointPoi": "Brunnen",
    "villageReputationScore": 0,
    "villageReputationSummary": "neutraler Dorfruf",
    "speakerReputationScore": 0,
    "speakerReputationSummary": "persoenlich noch ohne Eindruck",
    "combinedReputationScore": 0,
    "combinedReputationSummary": "neutral",
    "relationshipMemorySummary": "Dieser Spieler ist fuer dich noch weitgehend neu.",
}

print("=" * 70)
print("Test 1: _load_memory_context (memory enabled, triggered)")
print("=" * 70)

from chief_ai_service.reply_builder import _load_memory_context
memories, summary_text = _load_memory_context(payload, config)
print(f"memories: {memories}")
print(f"summary_text: {summary_text}")
print()

print("=" * 70)
print("Test 2: build_context_prompt with memories/summary")
print("=" * 70)

if memories or summary_text:
    prompt = build_context_prompt(payload, config, memories=memories, summary_text=summary_text)
    print(prompt[:2000])
    print(f"\n... total prompt length: {len(prompt)} chars")
else:
    print("No memory data to inject (expected if memory.db is empty)")
    # Still show that build_context_prompt works without them
    prompt = build_context_prompt(payload, config)
    print(f"Fallback prompt length: {len(prompt)} chars")
print()

print("=" * 70)
print("Test 3: Memory section presence check")
print("=" * 70)

# Force fake memories/summary to verify they render
fake_memories = ["Spieler fragte nach Eisen", "Spieler handelte mit Brot"]
fake_summary = "Der Spieler hat gestern Eisen gesammelt und Brot gehandelt."
prompt_with = build_context_prompt(
    payload, config,
    memories=fake_memories,
    summary_text=fake_summary,
)
print(prompt_with)
print()

# Verify expected sections are present
assert "--- Memories ---" in prompt_with, "Memories section missing!"
assert "--- Summary ---" in prompt_with, "Summary section missing!"
assert "Spieler fragte nach Eisen" in prompt_with, "Memory content missing!"
assert "Der Spieler hat gestern" in prompt_with, "Summary content missing!"
print("ASSERTIONS PASSED: Both Memories and Summary sections render correctly.")
print()

print("=" * 70)
print("Test 4: Prompt without memories/summary (regression check)")
print("=" * 70)
prompt_without = build_context_prompt(payload, config)
assert "--- Memories ---" not in prompt_without, "Memories section should NOT appear!"
assert "--- Summary ---" not in prompt_without, "Summary section should NOT appear!"
print("ASSERTIONS PASSED: No memory sections leak when not requested.")

print("\nAll dry-run tests passed.\n")
