"""Quick smoke test for ground-truth narrative."""
import sys
sys.path.insert(0, "chief-ai-service")
from chief_ai_service.prompt_builder import _build_ground_truth_section

# Test 1: Normal villager, village has chief
payload1 = {
    "chiefName": "Gerald",
    "chiefRole": "Dorfhaeuptling",
    "villageName": "Weidenfeld",
    "villageDescription": "Weidenfeld ist ein kleines, friedliches Dorf.",
    "villageHasChief": True,
    "villageMourning": False,
    "villagerIsChief": False,
    "combinedReputationScore": 15,
}
print("=== Test 1: Normaler Bewohner, Dorf hat Chief ===")
print(_build_ground_truth_section(payload1))
print()

# Test 2: Village in mourning
payload2 = {
    "chiefName": "Gerald",
    "chiefRole": "Dorfhaeuptling",
    "villageName": "Weidenfeld",
    "villageDescription": "",
    "villageHasChief": False,
    "villageMourning": True,
    "villagerIsChief": False,
    "combinedReputationScore": 0,
}
print("=== Test 2: Trauerfall ===")
print(_build_ground_truth_section(payload2))
print()

# Test 3: Speaker IS the chief
payload3 = {
    "chiefName": "Gerald",
    "chiefRole": "Dorfhaeuptling",
    "villageName": "Weidenfeld",
    "villageDescription": "Ein stolzes Dorf.",
    "villageHasChief": True,
    "villageMourning": False,
    "villagerIsChief": True,
    "combinedReputationScore": 62,
}
print("=== Test 3: Sprecher IST der Chief ===")
print(_build_ground_truth_section(payload3))
print()

# Test 4: No chief, no mourning
payload4 = {
    "chiefName": "unbekannt",
    "chiefRole": "Bewohner",
    "villageName": "Neues Dorf",
    "villageDescription": "Ein junges Dorf.",
    "villageHasChief": False,
    "villageMourning": False,
    "villagerIsChief": False,
    "combinedReputationScore": -50,
}
print("=== Test 4: Kein Chief, keine Trauer ===")
print(_build_ground_truth_section(payload4))
