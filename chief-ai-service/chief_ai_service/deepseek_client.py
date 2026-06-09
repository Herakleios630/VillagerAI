import json
import urllib.error
import urllib.request

from .prompt_builder import build_deepseek_messages
from .reply_sanitizer import sanitize_reply_text


def request_deepseek_reply(payload: dict, config: dict) -> str:
    message = str(payload.get("playerMessage", "")).strip()
    if not message:
        return "Sprich klar, damit ich dich verstehen kann."

    deepseek_config = config.get("deepseek", {})
    api_key = str(deepseek_config.get("api_key", "")).strip()
    if not api_key:
        raise RuntimeError(
            "DeepSeek API-Key fehlt. Setze den Key in config.json unter deepseek.api_key oder als Umgebungsvariable aus deepseek.api_key_env."
        )

    request_body = json.dumps({
        "model": deepseek_config.get("model", "deepseek-chat"),
        "messages": build_deepseek_messages(payload, config),
        "temperature": float(deepseek_config.get("temperature", 0.55)),
        "top_p": float(deepseek_config.get("top_p", 0.9)),
        "max_tokens": int(deepseek_config.get("max_tokens", 120)),
        "stream": False,
    }).encode("utf-8")

    request = urllib.request.Request(
        str(deepseek_config.get("endpoint", "https://api.deepseek.com/chat/completions")),
        data=request_body,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="POST",
    )

    timeout_seconds = int(deepseek_config.get("timeout_seconds", 60))

    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            response_body = response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        error_body = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"DeepSeek HTTP {error.code}: {error_body}") from error
    except urllib.error.URLError as error:
        raise RuntimeError(f"DeepSeek ist nicht erreichbar: {error}") from error

    try:
        parsed = json.loads(response_body)
    except json.JSONDecodeError as error:
        raise RuntimeError(f"DeepSeek lieferte ungueltiges JSON: {response_body}") from error

    choices = parsed.get("choices") or []
    if not choices:
        raise RuntimeError("DeepSeek lieferte keine Auswahl zurueck.")

    first_choice = choices[0] or {}
    message_payload = first_choice.get("message") or {}
    reply_text = str(message_payload.get("content", "")).strip()
    if not reply_text:
        raise RuntimeError("DeepSeek lieferte keine Antwort.")

    return sanitize_reply_text(reply_text)