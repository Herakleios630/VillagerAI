"""Dry-run test for reputation label + status conditionality."""
import json
import sys
import os

# Ensure the package is importable
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "chief-ai-service"))
from chief_ai_service.prompt_builder import (
    _reputation_label,
    _build_reputation_section,
    _build_status_section,
)

def test_reputation_labels():
    """Verify label thresholds."""
    cases = [
        (85, "hervorragend"),
        (80, "hervorragend"),
        (60, "gut"),
        (50, "gut"),
        (30, "leicht positiv"),
        (10, "leicht positiv"),
        (0, "neutral"),
        (-9, "neutral"),
        (-10, "schlecht"),
        (-29, "schlecht"),
        (-30, "sehr schlecht"),
        (-79, "sehr schlecht"),
        (-80, "extrem schlecht"),
        (-100, "extrem schlecht"),
    ]
    for score, expected in cases:
        actual = _reputation_label(score)
        status = "OK" if actual == expected else f"FEHLER (expected {expected!r})"
        print(f"  score={score:4d} -> {actual!r} {status}")

def test_reputation_section():
    """Compact Score+Label format."""
    payload = {
        "villageReputationScore": 85,
        "speakerReputationScore": 15,
        "combinedReputationScore": 50,
        "relationshipMemorySummary": "Noch weitgehend neu.",
    }
    result = _build_reputation_section(payload)
    print("Reputation section:")
    print(result)
    print()

    # Verify key pieces
    assert "Dorfruf: 85 (hervorragend)" in result, "Missing village score+label"
    assert "Persoenlicher Ruf bei diesem Sprecher: 15 (leicht positiv)" in result, "Missing speaker score+label"
    assert "Gesamteindruck: 50 (gut)" in result, "Missing combined score+label"
    assert "Bekannter-Hinweis:" in result, "Missing relationship hint"
    assert "Dorfruf des Spielers: Score" not in result, "Old format still present"
    assert "Einschaetzung:" not in result, "Old summary still present"
    print("  -> ALLE REPUTATION CHECKS OK")

def test_status_full_health_clear_weather():
    """Health=100%, no rain -> Health/Wetter lines absent."""
    payload = {
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
        "tradeSummary": "Keine Trades",
        "confinementSummary": "Frei",
        "authoritativeWorldFactsSummary": "",
        "recentConversation": "Neu",
        "homePoi": "unbekannt",
        "jobSitePoi": "unbekannt",
        "potentialJobSitePoi": "",
        "meetingPointPoi": "",
    }
    result = _build_status_section(payload)
    print("Status (full health, clear weather):")
    print(result)
    print()

    assert "Minecraft-Beruf:" not in result, "villagerProfession should be removed"
    assert "Wetter:" not in result, "Weather should be absent when clear"
    assert "Lebenspunkte:" not in result, "Health should be absent at 100% with ateRecently"
    assert "Hat kuerzlich gegessen:" not in result, "ateRecently should be absent when True"
    assert "Home-POI:" not in result, "Home-POI with 'unbekannt' should be absent"
    assert "Job-Site-POI:" not in result, "Job-Site with 'unbekannt' should be absent"
    assert "Pluginseitig bestaetigte Weltfakten:" not in result, "Empty facts should be absent"
    print("  -> ALLE FULL HEALTH / CLEAR WEATHER CHECKS OK")

def test_status_injured_raining():
    """Health=50%, raining -> Health and Weather lines appear."""
    payload = {
        "villagerType": "DESERT",
        "currentBiome": "DESERT",
        "worldName": "world_nether",
        "isDay": False,
        "isRaining": True,
        "isThundering": False,
        "currentHealth": 10.0,
        "maxHealth": 20.0,
        "healthRatio": 0.5,
        "ateRecently": False,
        "tradeSummary": "Viele Trades",
        "confinementSummary": "Eng",
        "authoritativeWorldFactsSummary": "Nebel ueberall",
        "recentConversation": "Lang",
        "homePoi": "Dorfbrunnen",
        "jobSitePoi": "unbekannt",
        "potentialJobSitePoi": "Schmiede",
        "meetingPointPoi": "",
    }
    result = _build_status_section(payload)
    print("Status (injured, raining, POIs):")
    print(result)
    print()

    assert "Minecraft-Beruf:" not in result, "villagerProfession should be removed"
    assert "Wetter: Regen" in result, "Rain should appear"
    assert "Lebenspunkte: 10.0/20.0 (50%)" in result, "Health should appear"
    assert "Hat kuerzlich gegessen: nein oder unbekannt" in result, "ateRecently false should appear"
    assert "Home-POI: Dorfbrunnen" in result, "Known POI should appear"
    assert "Job-Site-POI:" not in result, "Unknown POI should be absent"
    assert "Potenzielle Job-Site: Schmiede" in result, "Known POI should appear"
    assert "Meeting-Point:" not in result, "Empty POI should be absent"
    assert "Pluginseitig bestaetigte Weltfakten: Nebel ueberall" in result, "Facts should appear"
    assert "Tageszeit: Nacht" in result, "Night should show"
    print("  -> ALLE INJURED / RAINING CHECKS OK")

def test_status_thunder():
    """Thundering -> 'Gewitter' label."""
    payload = {
        "villagerType": "TAIGA",
        "currentBiome": "TAIGA",
        "worldName": "world",
        "isDay": True,
        "isRaining": True,
        "isThundering": True,
        "currentHealth": 20.0,
        "maxHealth": 20.0,
        "healthRatio": 1.0,
        "ateRecently": True,
        "tradeSummary": "-",
        "confinementSummary": "-",
        "authoritativeWorldFactsSummary": "",
        "recentConversation": "-",
        "homePoi": "",
        "jobSitePoi": "",
        "potentialJobSitePoi": "",
        "meetingPointPoi": "",
    }
    result = _build_status_section(payload)
    print("Status (thunder):")
    print(result)
    print()
    assert "Wetter: Gewitter" in result, "Thunder should show Gewitter"
    print("  -> THUNDER CHECK OK")

if __name__ == "__main__":
    print("=== Reputation Labels ===")
    test_reputation_labels()
    print("\n=== Reputation Section ===")
    test_reputation_section()
    print("\n=== Status: Full Health + Clear Weather ===")
    test_status_full_health_clear_weather()
    print("\n=== Status: Injured + Raining + POIs ===")
    test_status_injured_raining()
    print("\n=== Status: Thunder ===")
    test_status_thunder()
    print("\n=== ALLE TESTS BESTANDEN ===")