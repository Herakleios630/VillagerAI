"""qwen2.5 Client-Wrapper für strukturierte JSON-Antworten via Ollama."""

import json
import logging
import re
import urllib.error
import urllib.request

logger = logging.getLogger("chief_ai_service.qwen_client")

_JSON_BLOCK_RE = re.compile(r"\{[^{}]*\}", re.DOTALL)


def send_prompt(prompt_text: str, model: str, config: dict | None = None) -> dict:
    """Sendet einen Prompt an Ollama und parsed eine JSON-Antwort.

    Args:
        prompt_text: Der Prompt-Text (muss keine Ollama-Syntax haben).
        model: Modellname, z.B. "qwen2.5:3b".
        config: Bridge-Konfiguration (wenn None, wird load_config importiert).

    Returns:
        Ein dict mit den geparsten JSON-Feldern oder {"error": True, "raw_response": "…"}.
    """
    if config is None:
        from .config import load_config
        config = load_config()

    ollama_config = config.get("ollama", {})
    endpoint = str(ollama_config.get("endpoint", "http://127.0.0.1:11434/api/generate"))
    timeout_seconds = int(ollama_config.get("timeout_seconds", 60))

    request_body = json.dumps({
        "model": model,
        "prompt": prompt_text,
        "stream": False,
        "options": {
            "temperature": 0.0,       # Determinismus für Klassifikation/Parsing
            "top_p": 1.0,
            "num_predict": 200,
        },
    }).encode("utf-8")

    request = urllib.request.Request(
        endpoint,
        data=request_body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            response_body = response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        error_body = error.read().decode("utf-8", errors="replace")
        logger.error("qwen HTTP %s: %s", error.code, error_body)
        return {"error": True, "raw_response": f"HTTP {error.code}: {error_body}"}
    except urllib.error.URLError as error:
        logger.error("qwen nicht erreichbar: %s", error)
        return {"error": True, "raw_response": str(error)}

    try:
        parsed = json.loads(response_body)
    except json.JSONDecodeError:
        logger.error("qwen lieferte ungültiges JSON: %s", response_body)
        return {"error": True, "raw_response": response_body}

    raw_text = str(parsed.get("response", "")).strip()
    if not raw_text:
        logger.error("qwen lieferte leere response")
        return {"error": True, "raw_response": ""}

    # Versuch 1: Direktes JSON-Parsing
    try:
        parsed = json.loads(raw_text)
        if isinstance(parsed, dict):
            return parsed
        # Qwen returned a JSON array – wrap it so callers stay safe
        if isinstance(parsed, list):
            logger.debug("qwen returned JSON array of %d elements – wrapping in dict", len(parsed))
            return {"facts": parsed}
    except json.JSONDecodeError:
        pass

    # Versuch 2: JSON-Block per Regex extrahieren
    match = _JSON_BLOCK_RE.search(raw_text)
    if match:
        try:
            candidate = json.loads(match.group(0))
            if isinstance(candidate, dict):
                return candidate
            if isinstance(candidate, list):
                logger.debug("qwen regex-extracted JSON array – wrapping in dict")
                return {"facts": candidate}
        except json.JSONDecodeError:
            pass

    # Totalversagen
    logger.error("qwen konnte kein JSON aus der Antwort parsen: %s", raw_text[:300])
    return {"error": True, "raw_response": raw_text}