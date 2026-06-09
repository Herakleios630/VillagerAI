import json
import os
from pathlib import Path


CONFIG_PATH = Path(__file__).resolve().parent.parent / "config.json"
KNOWLEDGE_PACKETS_PATH = Path(__file__).resolve().parent.parent / "knowledge-packets.json"
KNOWLEDGE_PACKETS_DIR = Path(__file__).resolve().parent.parent / "knowledge-packets"
DEFAULT_CONFIG = {
    "host": "127.0.0.1",
    "port": 8080,
    "provider": "dummy",
    "village_name": "unser Dorf",
    "reply_prefix": "Der Haeuptling sagt",
    "ollama": {
        "endpoint": "http://127.0.0.1:11434/api/generate",
        "model": "qwen2.5:3b",
        "timeout_seconds": 60,
        "temperature": 0.65,
        "top_p": 0.9,
        "repeat_penalty": 1.12,
        "num_predict": 80,
        "system_prompt": "Du bist der Haeuptling des Dorfes Eichenhain in Minecraft. Antworte auf Deutsch, kurz, freundlich und glaubwuerdig. Sprich wie eine Figur in der Welt, nicht wie ein Assistent.",
    },
    "deepseek": {
        "endpoint": "https://api.deepseek.com/chat/completions",
        "model": "deepseek-chat",
        "timeout_seconds": 60,
        "temperature": 0.55,
        "top_p": 0.9,
        "max_tokens": 120,
        "api_key_env": "DEEPSEEK_API_KEY",
        "api_key": "",
        "system_prompt": "Du bist ein glaubwuerdiger Sprecher in einem Minecraft-Dorf. Antworte passend zur Rolle, kurz und natuerlich auf Deutsch.",
    },
}

DEFAULT_KNOWLEDGE_PACKETS = {
    "always": [
        "Villager kennen einfachen Dorfalltag: Arbeit am Tag, mehr Vorsicht in der Nacht, Monster sind nachts gefaehrlicher.",
        "Betten, Nahrung, Glocke, Felder, Werkstaetten und Handel gehoeren fuer Dorfbewohner zum normalen Leben."
    ],
    "situational": {
        "day": [
            "Tagsueber sprechen Dorfbewohner eher ueber Arbeit, Handel, Felder, Wege und Alltag."
        ],
        "night": [
            "Nachts sprechen Dorfbewohner eher ueber Vorsicht, Schlaf, Sicherheit und Monstergefahr."
        ],
        "biomes": {
            "plains": [
                "In Ebenen sind Felder, offene Wege und freie Sicht typisch."
            ],
            "desert": [
                "In Wueste und Trockenheit denkt man eher an Hitze, Sand, Wasserknappheit und harte Arbeit."
            ],
            "taiga": [
                "In kalten Waldgebieten spielen Holz, Kaelte und robuste Vorratshaltung eine groessere Rolle."
            ],
            "savanna": [
                "In Savannen wirken Hitze, trockenes Gras und weite Wege oft praegend fuer den Dorfalltag."
            ],
            "snow": [
                "In kalten und verschneiten Gegenden sind Waerme, Schutz und Vorrat besonders wichtig."
            ]
        }
    },
    "professions": {
        "DEFAULT": [
            "Dorfbewohner kennen vor allem ihr Dorf, ihre Arbeit, Handel, Nahrung, Wetter und einfache Gefahren."
        ],
        "LIBRARIAN": [
            "Bibliothekare kennen Buecher, Papier, Karten, Verzauberungstische, Lesen, Abschreiben und Dorfwissen.",
            "Bei grossen Weltgeheimnissen oder moderner Wissenschaft sollen sie Grenzen zugeben statt zu halluzinieren."
        ],
        "CARTOGRAPHER": [
            "Kartographen kennen Wege, Landmarken, Biome, Karten und grobe Orientierung in der Umgebung.",
            "Sie duerfen ueber Richtungen und typische Landmarken sprechen, aber keine erfundenen exakten Koordinaten behaupten."
        ],
        "ARMORER": [
            "Ruestungsschmiede kennen Schutz, Metallarbeit, Ausruestung und die Gefahr kaempferischer Reisen."
        ],
        "TOOLSMITH": [
            "Werkzeugschmiede kennen Werkzeuge, Rohstoffe, Reparaturen und robuste Alltagsarbeit."
        ],
        "WEAPONSMITH": [
            "Waffenschmiede kennen Waffen, Metallarbeit, Kohle, Oefen und die Gefahren von Monstern."
        ],
        "FARMER": [
            "Bauern kennen Felder, Saat, Ernte, Brot, Kompost, Wetter und Dorfalltag."
        ],
        "BUTCHER": [
            "Fleischer kennen Vieh, Nahrung, Vorratshaltung, Rauch, Messerarbeit und derben Alltagswitz."
        ],
        "FISHERMAN": [
            "Fischer kennen Wasser, Wetter, Fische, Geduld und einfache Uferarbeit."
        ],
        "CLERIC": [
            "Kleriker kennen Heilung, Tranke, Sorge um Menschen und einen eher sinnsuchenden Blick auf Ereignisse."
        ],
        "MASON": [
            "Steinmetze kennen Stein, Haeuser, Wege, Mauern und belastbare Bauarbeit."
        ],
        "FLETCHER": [
            "Pfeilmacher kennen Boegen, Pfeile, Holzarbeit und Jagdvorbereitung."
        ],
        "LEATHERWORKER": [
            "Lederarbeiter kennen Leder, Taschen, Zaeume und robuste Kleidung."
        ],
        "SHEPHERD": [
            "Schaefer kennen Wolle, Tiere, Weiden und einfache Dorfversorgung."
        ]
    },
    "out_of_scope": [
        "Bei Mathe, moderner Technik, Internet, Autos, Programmierung oder allgemeinem Trivia-Wissen soll der Villager knapp sagen, dass das nicht sein Gebiet ist.",
        "Ein Villager soll lieber glaubwuerdig Grenzen ziehen als sich Fachwissen ausserhalb von Minecraft-Dorfleben auszudenken."
    ]
}

DEFAULT_KNOWLEDGE_PACKET_FILES = {
    "always.json": {
        "always": DEFAULT_KNOWLEDGE_PACKETS["always"],
    },
    "situational.json": {
        "situational": DEFAULT_KNOWLEDGE_PACKETS["situational"],
    },
    "professions.json": {
        "professions": DEFAULT_KNOWLEDGE_PACKETS["professions"],
    },
    "out-of-scope.json": {
        "out_of_scope": DEFAULT_KNOWLEDGE_PACKETS["out_of_scope"],
    },
}


def _load_or_create_json(path: Path, default_value: dict) -> dict:
    if not path.exists():
        path.write_text(json.dumps(default_value, indent=2), encoding="utf-8")
        return dict(default_value)

    with path.open("r", encoding="utf-8") as file_handle:
        return json.load(file_handle)


def _deep_merge(base: dict, overlay: dict) -> dict:
    merged = dict(base)
    for key, value in overlay.items():
        if isinstance(value, dict) and isinstance(merged.get(key), dict):
            merged[key] = _deep_merge(merged[key], value)
        else:
            merged[key] = value
    return merged


def _load_knowledge_packets() -> dict:
    if KNOWLEDGE_PACKETS_DIR.exists() and KNOWLEDGE_PACKETS_DIR.is_dir():
        merged: dict = {}
        for path in sorted(KNOWLEDGE_PACKETS_DIR.glob("*.json")):
            with path.open("r", encoding="utf-8") as file_handle:
                merged = _deep_merge(merged, json.load(file_handle))
        if merged:
            return merged

    if KNOWLEDGE_PACKETS_PATH.exists():
        legacy_packets = _load_or_create_json(KNOWLEDGE_PACKETS_PATH, DEFAULT_KNOWLEDGE_PACKETS)
        KNOWLEDGE_PACKETS_DIR.mkdir(parents=True, exist_ok=True)
        for file_name, default_value in DEFAULT_KNOWLEDGE_PACKET_FILES.items():
            target = KNOWLEDGE_PACKETS_DIR / file_name
            if not target.exists():
                section_key = next(iter(default_value.keys()))
                payload = {section_key: legacy_packets.get(section_key, default_value[section_key])}
                target.write_text(json.dumps(payload, indent=2), encoding="utf-8")
        return legacy_packets

    KNOWLEDGE_PACKETS_DIR.mkdir(parents=True, exist_ok=True)
    merged: dict = {}
    for file_name, default_value in DEFAULT_KNOWLEDGE_PACKET_FILES.items():
        target = KNOWLEDGE_PACKETS_DIR / file_name
        target.write_text(json.dumps(default_value, indent=2), encoding="utf-8")
        merged = _deep_merge(merged, default_value)
    return merged


def load_config() -> dict:
    if not CONFIG_PATH.exists():
        CONFIG_PATH.write_text(json.dumps(DEFAULT_CONFIG, indent=2), encoding="utf-8")
        knowledge_packets = _load_knowledge_packets()
        merged_default = dict(DEFAULT_CONFIG)
        merged_default["knowledge_packets"] = knowledge_packets
        return merged_default

    with CONFIG_PATH.open("r", encoding="utf-8") as file_handle:
        loaded = json.load(file_handle)
    knowledge_packets = _load_knowledge_packets()

    merged = dict(DEFAULT_CONFIG)
    merged.update(loaded)
    merged["ollama"] = dict(DEFAULT_CONFIG["ollama"]) | dict(loaded.get("ollama", {}))
    merged["deepseek"] = dict(DEFAULT_CONFIG["deepseek"]) | dict(loaded.get("deepseek", {}))
    merged["knowledge_packets"] = knowledge_packets
    api_key_env = str(merged["deepseek"].get("api_key_env", "DEEPSEEK_API_KEY")).strip()
    api_key = str(merged["deepseek"].get("api_key", "")).strip()
    if not api_key and api_key_env:
        merged["deepseek"]["api_key"] = os.getenv(api_key_env, "")
    return merged