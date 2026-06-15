"""Quick smoke test for memory trigger parser (4a-8a)."""
import sys
import os as _os
_par = _os.path.dirname(_os.path.abspath(__file__))
sys.path.insert(0, _par)

from chief_ai_service.prompt_builder import check_memory_trigger

# Load triggers from config.json
import json
cfg_path = _os.path.join(_par, "config.json")
with open(cfg_path, "r", encoding="utf-8") as fh:
    cfg = json.load(fh)
triggers = cfg.get("memory", {}).get("trigger_phrases", [])

print(f"Loaded {len(triggers)} trigger phrases.\n")

test_cases = [
    # (message, expected_bool, description)
    ("erinnerst du dich an mich?", True, "core trigger: erinnerst du dich"),
    ("Weißt du noch, was ich gesagt habe?", True, "core trigger: weißt du noch"),
    ("damals war alles anders", True, "core trigger: damals"),
    ("vorhin hast du noch anders geredet", True, "core trigger: vorhin"),
    ("hast du das vergessen?", True, "core trigger: vergessen"),
    ("was habe ich gesagt beim letzten Mal?", True, "extended: was habe ich gesagt"),
    ("was hab ich gesagt als wir uns das letzte mal sahen?", True, "extended: was hab ich gesagt"),
    ("neulich war hier noch keiner", True, "extended: neulich"),
    ("kennst du mich noch?", True, "extended: kennst du mich noch"),
    ("weißt du wer ich bin?", True, "extended: weißt du wer ich bin"),
    ("schonmal einen Diamond gefunden?", True, "extended: schonmal"),
    ("schon mal was von Enderman gehoert?", True, "extended: schon mal with space"),
    ("unser letztes gespraech war nicht so gut", True, "extended: unser letztes gespraech"),
    ("kuerzlich war ich im Nether", True, "extended: kuerzlich"),
    ("irgendwann muss das ja mal klappen", True, "extended: irgendwann"),
    ("vorige woche hast du noch anders geredet", True, "extended: vorige woche"),
    ("gibst du mir eine erinnerung daran?", True, "extended: erinnerung"),
    ("besinnst du dich noch an gestern?", True, "extended: besinnst"),
    ("besinnst du dich noch?", True, "extended: besinnst"),
    ("hast du mir das schon erzaehlt?", True, "extended: schon erzaehlt"),
    ("hast du das mal gesagt?", True, "extended: mal gesagt"),
    ("Hallo, wie geht es dir?", False, "no trigger: greeting"),
    ("was kostet ein Brot?", False, "no trigger: trade query"),
    ("gib mir einen Auftrag", False, "no trigger: quest request"),
    ("das Wetter ist heute gut", False, "no trigger: weather chat"),
    ("", False, "no trigger: empty string"),
    ("was kann ich hier kaufen?", False, "no trigger: shopping"),
    ("zeig mir den Weg zum Brunnen", False, "no trigger: directions"),
]

failed = 0
for msg, expected, desc in test_cases:
    result = check_memory_trigger(msg, triggers)
    status = "PASS" if result == expected else "FAIL"
    if result != expected:
        failed += 1
    print(f"[{status}] {desc}")
    if result != expected:
        print(f"        message: {msg!r}")
        print(f"        expected: {expected}, got: {result}")

print(f"\n{failed} of {len(test_cases)} tests failed.")
if failed:
    sys.exit(1)
else:
    print("All trigger tests passed!")
    sys.exit(0)