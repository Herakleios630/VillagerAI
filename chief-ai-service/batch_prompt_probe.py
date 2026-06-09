import argparse
import copy
import json
from pathlib import Path
from urllib import error, request

from chief_ai_service.config import load_config
from chief_ai_service.prompt_builder import build_ollama_prompt
from chief_ai_service.reply_builder import build_reply


PROFILES = [
    {
        "key": "farmer",
        "chiefName": "Edda",
        "chiefRole": "Dorfhaeuptling und Baeuerin",
        "chiefPersonality": "warmherzig, pragmatisch und leicht trocken",
        "chiefGreeting": "Willkommen zwischen unseren Feldern.",
        "villagerProfession": "FARMER",
        "villagerType": "PLAINS",
        "currentBiome": "PLAINS",
    },
    {
        "key": "librarian",
        "chiefName": "Borin",
        "chiefRole": "Dorfhaeuptling und Gelehrter",
        "chiefPersonality": "scharfzuengig, klug und stolz",
        "chiefGreeting": "Sprich deutlich, ich verschwende ungern Zeit.",
        "villagerProfession": "LIBRARIAN",
        "villagerType": "TAIGA",
        "currentBiome": "TAIGA",
    },
    {
        "key": "armorer",
        "chiefName": "Raska",
        "chiefRole": "Dorfhaeuptling und Schmied",
        "chiefPersonality": "rau, direkt und verlaesslich",
        "chiefGreeting": "Wenn du etwas willst, komm zur Sache.",
        "villagerProfession": "ARMORER",
        "villagerType": "SAVANNA",
        "currentBiome": "SAVANNA",
    },
    {
        "key": "cleric",
        "chiefName": "Mira",
        "chiefRole": "Dorfhaeuptling und Heilerin",
        "chiefPersonality": "ruhig, beobachtend und streng",
        "chiefGreeting": "Der Glockenschlag hoert alles, also sprich ehrlich.",
        "villagerProfession": "CLERIC",
        "villagerType": "DESERT",
        "currentBiome": "DESERT",
    },
    {
        "key": "butcher",
        "chiefName": "Torv",
        "chiefRole": "Dorfhaeuptling und Fleischer",
        "chiefPersonality": "derb, humorvoll und schnell gereizt",
        "chiefGreeting": "Wenn du Aerger willst, nimm dir eine Nummer.",
        "villagerProfession": "BUTCHER",
        "villagerType": "SNOW",
        "currentBiome": "SNOWY_PLAINS",
    },
]

REPUTATIONS = [
    {"key": "trusted", "score": 24, "summary": "im Dorf angesehen und willkommen"},
    {"key": "liked", "score": 12, "summary": "eher geachtet und grundsaetzlich willkommen"},
    {"key": "neutral", "score": 0, "summary": "weitgehend neutral und noch ohne klaren Ruf"},
    {"key": "suspect", "score": -16, "summary": "misstrauisch beaeugt und eher unerwuenscht"},
    {"key": "hostile", "score": -32, "summary": "offen verhasst und als Gefahr bekannt"},
]

MESSAGES = [
    {"key": "greeting", "text": "Hallo Haeuptling."},
    {"key": "quest", "text": "Hast du einen Auftrag fuer mich?"},
    {"key": "wellbeing", "text": "Wie geht es dir heute?"},
    {"key": "insult", "text": "Du fuehrst dieses Dorf wie ein Idiot."},
]


def main() -> None:
    parser = argparse.ArgumentParser(description="Run repeatable 100-case prompt or reply probes without Minecraft.")
    parser.add_argument("--mode", choices=("prompt", "local", "http"), default="prompt")
    parser.add_argument("--count", type=int, default=100)
    parser.add_argument("--endpoint", default="http://127.0.0.1:8080/v1/chief/reply")
    parser.add_argument("--output", default="batch-probe-output.jsonl")
    parser.add_argument("--provider", choices=("dummy", "ollama"), default=None)
    args = parser.parse_args()

    config = load_config()
    if args.provider is not None:
        config = copy.deepcopy(config)
        config["provider"] = args.provider

    cases = build_cases(args.count)
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    with output_path.open("w", encoding="utf-8") as handle:
        for case in cases:
            result = execute_case(case["payload"], config, args.mode, args.endpoint)
            record = case | {"result": result}
            handle.write(json.dumps(record, ensure_ascii=True) + "\n")

    print(f"Wrote {len(cases)} cases to {output_path}")
    print(f"Mode: {args.mode}")
    print("Matrix: 5 profiles x 5 reputation states x 4 messages")


def build_cases(count: int) -> list[dict]:
    cases = []
    case_number = 1
    for profile in PROFILES:
        for reputation in REPUTATIONS:
            for message in MESSAGES:
                payload = build_payload(profile, reputation, message, case_number)
                cases.append({
                    "caseId": f"case-{case_number:03d}",
                    "profile": profile["key"],
                    "reputation": reputation["key"],
                    "reputationScore": reputation["score"],
                    "messageType": message["key"],
                    "payload": payload,
                })
                case_number += 1
                if len(cases) >= count:
                    return cases
    return cases


def build_payload(profile: dict, reputation: dict, message: dict, case_number: int) -> dict:
    return {
        "systemPrompt": "Du bist der Haeuptling eines Minecraft-Dorfes.",
        "chiefId": f"chief-{profile['key']}",
        "villageId": f"world:{profile['key']}:0",
        "villageName": f"Testdorf-{profile['key']}",
        "chiefName": profile["chiefName"],
        "chiefRole": profile["chiefRole"],
        "chiefPersonality": profile["chiefPersonality"],
        "chiefGreeting": profile["chiefGreeting"],
        "villagerProfession": profile["villagerProfession"],
        "villagerType": profile["villagerType"],
        "currentBiome": profile["currentBiome"],
        "worldName": "world",
        "isDay": case_number % 2 == 1,
        "isRaining": case_number % 3 == 0,
        "isThundering": case_number % 10 == 0,
        "currentHealth": 18.0 if reputation["score"] >= 0 else 13.0,
        "maxHealth": 20.0,
        "healthRatio": 0.9 if reputation["score"] >= 0 else 0.65,
        "ateRecently": reputation["score"] >= 0,
        "tradeSummary": "Der Spieler hat kuerzlich Brot gegen Emeralds gehandelt.",
        "confinementSummary": "kein klarer Hinweis auf Einschluss oder Vernachlaessigung",
        "recentConversation": "PLAYER: Danke fuer die letzte Hilfe. | CHIEF: Enttaeusche das Dorf nicht.",
        "homePoi": "Haus am Brunnen",
        "jobSitePoi": "Werkbank am Markt",
        "potentialJobSitePoi": "Scheune hinter dem Glockenturm",
        "meetingPointPoi": "Glocke auf dem Dorfplatz",
        "reputationScore": reputation["score"],
        "reputationSummary": reputation["summary"],
        "playerUuid": f"00000000-0000-0000-0000-{case_number:012d}",
        "playerMessage": message["text"],
    }


def execute_case(payload: dict, config: dict, mode: str, endpoint: str) -> str:
    if mode == "prompt":
        return build_ollama_prompt(payload, config)
    if mode == "local":
        return build_reply(payload, config)
    return call_http(payload, endpoint)


def call_http(payload: dict, endpoint: str) -> str:
    encoded = json.dumps(payload, ensure_ascii=True).encode("utf-8")
    http_request = request.Request(
        endpoint,
        data=encoded,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with request.urlopen(http_request, timeout=30) as response:
            body = json.loads(response.read().decode("utf-8"))
    except error.URLError as exc:
        raise RuntimeError(f"HTTP probe failed for {endpoint}: {exc}") from exc
    return str(body.get("replyText", ""))


if __name__ == "__main__":
    main()