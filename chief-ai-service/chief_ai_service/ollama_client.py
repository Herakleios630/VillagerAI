import json
import urllib.error
import urllib.request

from .prompt_builder import build_ollama_prompt
from .reply_sanitizer import sanitize_reply_text


def request_ollama_reply(
    payload: dict,
    config: dict,
    memories: list[str] | None = None,
    summary_text: str | None = None,
    relevant_facts: list[dict] | None = None,
) -> str:
    message = str(payload.get("playerMessage", "")).strip()
    if not message:
        return "Sprich klar, damit ich dich verstehen kann."

    ollama_config = config.get("ollama", {})
    request_body = json.dumps({
        "model": ollama_config.get("model", "qwen2.5:3b"),
        "prompt": build_ollama_prompt(payload, config, memories=memories, summary_text=summary_text, relevant_facts=relevant_facts),
        "stream": False,
        "options": {
            "temperature": float(ollama_config.get("temperature", 0.65)),
            "top_p": float(ollama_config.get("top_p", 0.9)),
            "repeat_penalty": float(ollama_config.get("repeat_penalty", 1.12)),
            "num_predict": int(ollama_config.get("num_predict", 80)),
        },
    }).encode("utf-8")

    request = urllib.request.Request(
        str(ollama_config.get("endpoint", "http://127.0.0.1:11434/api/generate")),
        data=request_body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    timeout_seconds = int(ollama_config.get("timeout_seconds", 60))

    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            response_body = response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        error_body = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Ollama HTTP {error.code}: {error_body}") from error
    except urllib.error.URLError as error:
        raise RuntimeError(f"Ollama ist nicht erreichbar: {error}") from error

    try:
        parsed = json.loads(response_body)
    except json.JSONDecodeError as error:
        raise RuntimeError(f"Ollama lieferte ungueltiges JSON: {response_body}") from error

    reply_text = str(parsed.get("response", "")).strip()
    if not reply_text:
        raise RuntimeError("Ollama lieferte keine Antwort.")

    return sanitize_reply_text(reply_text)