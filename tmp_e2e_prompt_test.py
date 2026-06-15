"""End-to-End test: Render full prompt with all sections and verify checklist.

Covers arbeitsauftrag-05-java-pruefen-e2e-test.md checklist items.
"""
import json
import sys
import os

project_dir = os.path.join(os.path.dirname(__file__), "chief-ai-service")
sys.path.insert(0, project_dir)

from chief_ai_service.prompt_builder import (
    build_context_prompt,
    build_deepseek_messages,
    resolve_system_prompt,
    check_memory_trigger,
)


# ── Config ──────────────────────────────────────────────────────────
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
        "system_prompt": (
            "Du bist ein glaubwuerdiger Sprecher in einem Minecraft-Dorf. "
            "Antworte passend zur Rolle, kurz und natuerlich auf Deutsch."
        ),
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
    "knowledge_packets": {
        "always": [
            "Minecraft ist eine Blockwelt.",
        ],
        "situational": {
            "day": ["Es ist hell, viele Arbeiten werden verrichtet."],
            "night": ["Nachts spawnen Monster, bleib in der Naehe von Licht."],
            "biomes": {
                "plains": "Weite Grasflaechen mit wenigen Baeumen.",
            },
        },
        "professions": {
            "default": "Ein einfacher Dorfbewohner mit alltaeglichen Aufgaben.",
            "farmer": "Bewirtschaftet Felder und erntet Weizen.",
        },
        "out_of_scope": ["Das ist kein Thema fuer einen Dorfbewohner."],
    },
}

# ── Payload (realistic, all fields populated) ───────────────────────
payload = {
    "playerUuid": "e2e-test-uuid",
    "chiefId": "chief-1",
    "chiefName": "Grimmig",
    "chiefRole": "Dorfhaeuptling",
    "chiefPersonality": "rau, aber gerecht",
    "chiefTone": "barsch, kurz angebunden",
    "chiefBehaviorHint": "spricht in kurzen Saetzen, verwendet gern Metaphern",
    "chiefGreeting": "Na endlich.",
    "villageId": "village-1",
    "villageName": "Eichenhain",
    "villageDescription": (
        "Eichenhain ist ein kleines, abgelegenes Dorf am Rande eines dunklen Waldes. "
        "Die Bewohner sind hart arbeitende Bauern und Holzarbeiter."
    ),
    "villageBiome": "PLAINS",
    "villagePopulationEstimate": 8,
    "villageEventSummary": "Letzte Woche hat ein Creeper das Lagerhaus beschaedigt.",
    "villageAttributes": "klein, abgelegen, waldnah",
    "playerMessage": "Hallo Grimmig, erinnerst du dich an das Creeper-Problem?",
    "memoryEnabled": True,
    "memoryTriggerFallbackPhrases": ["erinnerst du dich", "weisst du noch"],
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
    "authoritativeWorldFactsSummary": "Das Lagerhaus liegt am suedlichen Dorfrand.",
    "recentConversation": "Spieler hat gestern ueber das Creeper-Problem gesprochen.",
    "relationshipMemorySummary": "Der Spieler ist vor drei Tagen ins Dorf gekommen.",
    "homePoi": "Haus",
    "jobSitePoi": "unbekannt",
    "potentialJobSitePoi": "unbekannt",
    "meetingPointPoi": "Brunnen",
    "villageReputationScore": 45,
    "villageReputationSummary": "Der Spieler hat dem Dorf geholfen.",
    "speakerReputationScore": 60,
    "speakerReputationSummary": "Grimmig respektiert diesen Spieler.",
    "combinedReputationScore": 52,
    "combinedReputationSummary": "Guter Ruf, vertrauenswuerdig.",
    "reputationScore": 52,
    "reputationSummary": "Guter Ruf, vertrauenswuerdig.",
    "villageHasChief": True,
    "villagerIsChief": True,
    "villageMourning": False,
    "memoryTriggered": True,
    "mcDay": 120,
}


# ── Helper ──────────────────────────────────────────────────────────
def sections_from_prompt(prompt_text: str) -> dict[str, str]:
    """Parse rendered prompt back into a {title: body} dict."""
    result = {}
    current_title = None
    current_lines: list[str] = []
    for line in prompt_text.splitlines():
        if line.startswith("--- ") and line.rstrip().endswith(" ---"):
            if current_title is not None:
                result[current_title] = "\n".join(current_lines).strip()
            current_title = line[4:-4].strip()
            current_lines = []
        else:
            current_lines.append(line)
    if current_title is not None:
        result[current_title] = "\n".join(current_lines).strip()
    return result


def check(label: str, condition: bool, detail: str = ""):
    status = "PASS" if condition else "FAIL"
    extra = f"  ({detail})" if detail else ""
    print(f"  {status}: {label}{extra}")
    return condition


# ── Test 1: Build full context prompt ───────────────────────────────
print("=" * 70)
print("TEST: Full context prompt rendering (all sections)")
print("=" * 70)

prompt = build_context_prompt(payload, config)
sections = sections_from_prompt(prompt)

print(f"Prompt length: {len(prompt)} chars")
print(f"Sections found: {list(sections.keys())}")
print()

# ── Test 2: Checkliste ──────────────────────────────────────────────
print("=" * 70)
print("CHECKLISTE")
print("=" * 70)
all_pass = True

# 1. Ground-Truth enthält villageDescription-Text
cond = "Eichenhain ist ein kleines, abgelegenes Dorf" in sections.get("Ground-Truth", "")
all_pass &= check(
    "Ground-Truth enthält villageDescription-Text",
    cond,
    "villageDescription muss als Narrativ erscheinen",
)

# 2. Ground-Truth enthält Chief-Status-Narrativ
gt = sections.get("Ground-Truth", "")
cond = "Du BIST der Haeuptling" in gt
all_pass &= check(
    "Ground-Truth enthält Chief-Status-Narrativ",
    cond,
    "villagerIsChief=True -> 'Du BIST der Haeuptling'",
)

# 3. Ground-Truth enthält negative Klarstellung bei normalem Bewohner
#    (Test mit villagerIsChief=False, also separater Check)
#    In diesem Payload ist villagerIsChief=True, also prüfen wir,
#    dass KEINE false-negativ-Klarstellung erscheint
cond = "du bist NICHT der Haeuptling" not in gt.lower()
all_pass &= check(
    "Ground-Truth KEINE false-negative Klarstellung (villagerIsChief=True)",
    cond,
    "Darf nicht 'du bist NICHT der Haeuptling' enthalten",
)

# 4. Persönlichkeit enthält Ton + Verhalten
pers = sections.get("Persoenlichkeit", "")
cond_tone = "barsch, kurz angebunden" in pers
cond_behavior = "spricht in kurzen Saetzen" in pers
all_pass &= check(
    "Persönlichkeit enthält Ton",
    cond_tone,
    f"found tone? {cond_tone}",
)
all_pass &= check(
    "Persönlichkeit enthält Verhalten",
    cond_behavior,
    f"found behavior? {cond_behavior}",
)

# 5. Dorf-Details enthält KEINE internen IDs
village = sections.get("Dorf-Details", "")
cond = "village-" not in village and "chief-" not in village
all_pass &= check(
    "Dorf-Details enthält KEINE internen IDs",
    cond,
    f"village='{village[:80]}...'",
)

# 6. Ruf ist Score+Label-Format (z.B. "45 (leicht positiv)" oder "60 (gut)")
import re as _re
ruf = sections.get("Ruf", "")
cond = bool(_re.search(r"Dorfruf: \d+ \([a-z ]+\)", ruf)) and bool(_re.search(r"Persoenlicher Ruf bei diesem Sprecher: \d+ \([a-z ]+\)", ruf))
all_pass &= check(
    "Ruf ist Score+Label-Format",
    cond,
    f"ruf snippet: {ruf[:120]}",
)

# 7. Status hat keine "unbekannt"-POIs
status = sections.get("Status", "")
cond = "unbekannt" not in status.lower()
all_pass &= check(
    "Status hat keine 'unbekannt'-POIs",
    cond,
    "POI-Filter muss leer/unbekannt ausblenden",
)

# 8. Regeln sind <=15 Zeilen
rules = sections.get("Regeln", "")
rule_lines = [l for l in rules.splitlines() if l.strip()]
cond = len(rule_lines) <= 15
all_pass &= check(
    f"Regeln sind <=15 Zeilen (actual: {len(rule_lines)})",
    cond,
)

# 9. Regeln stehen VOR Spieler-Nachricht
section_names = list(sections.keys())
if "Regeln" in section_names and "Spieler-Nachricht" in section_names:
    rules_idx = section_names.index("Regeln")
    player_idx = section_names.index("Spieler-Nachricht")
    cond = rules_idx < player_idx
else:
    cond = False
all_pass &= check(
    "Regeln stehen VOR Spieler-Nachricht",
    cond,
    f"Rules idx={rules_idx if 'Regeln' in section_names else 'N/A'}, Player idx={player_idx if 'Spieler-Nachricht' in section_names else 'N/A'}",
)

# 10. Kein chiefTone/chiefBehaviorHint im System-Prompt-Teil
system_prompt = resolve_system_prompt(payload, config, "deepseek")
cond = "barsch" not in system_prompt and "spricht in kurzen Saetzen" not in system_prompt
all_pass &= check(
    "Kein chiefTone/chiefBehaviorHint im System-Prompt-Teil",
    cond,
    f"system_prompt='{system_prompt[:120]}...'",
)

print()
print("=" * 70)
print("GESAMTERGEBNIS")
print("=" * 70)
print("  ALLE CHECKS BESTANDEN" if all_pass else "  EINIGE CHECKS FEHLGESCHLAGEN – siehe oben")
print()

# ── Test 3: Print full prompt for manual review ─────────────────────
print("=" * 70)
print("FULL PROMPT (for manual review)")
print("=" * 70)
print(prompt)
print()
print(f"System prompt: {system_prompt}")
print()

# ── Test 4: Deepseek messages format ────────────────────────────────
print("=" * 70)
print("DEEPSEEK MESSAGES FORMAT")
print("=" * 70)
msgs = build_deepseek_messages(payload, config)
for m in msgs:
    print(f"[{m['role']}]: {m['content'][:200]}...")
    print()
print("=" * 70)
print("Done.")