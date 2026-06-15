"""Trockentest für die komprimierte _build_rules_section()."""
import sys
sys.path.insert(0, "chief-ai-service")

from chief_ai_service.prompt_builder import _build_rules_section

config = {}
payloads = [
    {"combinedReputationScore": -100, "reputationScore": 0},
    {"combinedReputationScore": -50, "reputationScore": 0},
    {"combinedReputationScore": -15, "reputationScore": 0},
    {"combinedReputationScore": 0, "reputationScore": 0},
    {"combinedReputationScore": 50, "reputationScore": 0},
]

print("=== Test: _build_rules_section() komprimiert ===\n")

for p in payloads:
    score = p["combinedReputationScore"]
    result = _build_rules_section(p, config)
    lines = result.strip().split("\n")
    line_count = len(lines)
    
    print(f"Score {score:>4}: {line_count} Zeilen")
    print(result)
    print("-" * 60)

    # Checks
    assert "Kein Vorwort" in result.lower() or True, f"Missing 'Kein Vorwort' for score {score}"
    assert line_count <= 15, f"ZU VIELE ZEILEN: {line_count} für Score {score}"
    assert "Wenn ein Spieler nach dem Haeuptling fragt" not in result, f"Ground-Truth-Dopplung gefunden bei Score {score}"
    assert "Nutze das kuratierte Wissenspaket" not in result, f"Knowledge-Dopplung gefunden bei Score {score}"
    assert "Bekannter-Spieler-Hinweis" not in result, f"Bekannter-Hinweis-Dopplung gefunden bei Score {score}"
    assert "Kein Vorwort" in result, f"Kein-Vorwort-Regel fehlt bei Score {score}"
    assert "IDs" in result, f"Technische-Details-Regel fehlt bei Score {score}"

print("\n=== Alle Checks bestanden! ===")