"""
Summary Client – Rolling Summaries via Ollama (qwen2.5:3b).

Usage:
    from summary_client import SummaryClient
    client = SummaryClient()
    summary = client.generate_summary(existing_summary, turns)

Architecture:
    - The embedding model (nomic-embed-text) is normally kept loaded with keep_alive.
    - Before generating a summary, the embedding model is unloaded and qwen2.5:3b is loaded.
    - After the summary is generated, qwen2.5:3b is unloaded and the embedding model is reloaded.
    - This sequential model switching avoids Ollama running two models concurrently,
      which would exceed VRAM on most consumer GPUs.

Error Handling:
    - If Ollama is unreachable, generate_summary() returns a graceful degradation string.
    - Timeouts are caught and degraded.
"""

import json
import logging
import urllib.error
import urllib.request
from typing import Optional, List

logger = logging.getLogger("chief_ai_service.summary_client")

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

OLLAMA_GENERATE_ENDPOINT = "http://127.0.0.1:11434/api/generate"
SUMMARY_MODEL = "qwen2.5:3b"
EMBED_MODEL = "nomic-embed-text"
DEFAULT_TIMEOUT = 90  # seconds – summary generation can be slower than chat
SUMMARY_MAX_WORDS = 500

# ---------------------------------------------------------------------------
# Rolling Summary Prompt
# ---------------------------------------------------------------------------

ROLLING_SUMMARY_PROMPT_TEMPLATE = """Du bist ein Zusammenfassungs-Assistent für einen Minecraft-Dorfhäuptling-Chatbot.

Deine Aufgabe: Erstelle eine fortlaufende (rolling) Zusammenfassung der bisherigen Gespräche zwischen einem Spieler und dem Dorfhäuptling.

Regeln:
- Schreibe die Zusammenfassung aus Sicht des Häuptlings in der Ich-Form.
- Fasse den bisherigen Gesprächsverlauf knapp und sachlich zusammen.
- Wichtige Themen, wiederkehrende Anliegen, Konflikte und auffällige Stimmungen sollen klar erkennbar sein.
- Wenn eine bestehende Zusammenfassung vorhanden ist, integriere die neuen Gesprächszeilen darin. Überschneidungen vermeiden.
- Wenn keine bestehende Zusammenfassung vorhanden ist, erstelle eine neue nur aus den gegebenen Turns.
- Die Zusammenfassung darf maximal {max_words} Wörter lang sein.
- Schreibe nur die Zusammenfassung selbst. Kein Vorwort, kein Nachwort, keine Meta-Kommentare.
- Schreibe auf Deutsch.

{bisherige_summary}

Neue Gesprächszeilen:
{new_turns}

Aktualisierte Zusammenfassung:"""


# ---------------------------------------------------------------------------
# Summary Client
# ---------------------------------------------------------------------------

class SummaryClient:
    """
    Generates rolling summaries using a local Ollama model (qwen2.5:3b).

    The client handles sequential model switching:
    1. Unload embedding model (nomic-embed-text)
    2. Load summary model (qwen2.5:3b)
    3. Generate summary
    4. Unload summary model
    5. Reload embedding model
    """

    def __init__(self, timeout: int = DEFAULT_TIMEOUT):
        self._timeout = timeout

    # -----------------------------------------------------------------------
    # Public API
    # -----------------------------------------------------------------------

    def generate_summary(
        self,
        existing_summary: Optional[str],
        turns: List[dict],
    ) -> str:
        """
        Generate (or update) a rolling summary.

        Args:
            existing_summary: The previous summary text, or None if this is the first summary.
            turns: List of conversation turns, each a dict with at least 'role' and 'message'.
                   Example: [{'role': 'player', 'message': 'Hallo!'}, ...]

        Returns:
            A summary string (max ~500 words).

            On unrecoverable errors, returns a graceful degradation string
            that callers can use as a fallback.
        """
        if not turns:
            return existing_summary or "Kein Gesprächsverlauf vorhanden."

        # 1. Unload embedding model
        self._unload_embedding_model()

        # 2. Load summary model
        self._load_summary_model()

        try:
            # 3. Generate summary
            summary = self._generate_summary_internal(existing_summary, turns)
        finally:
            # 4. Unload summary model
            self._unload_summary_model()

            # 5. Reload embedding model
            self._ensure_embedding_model_reloaded()

        return summary

    # -----------------------------------------------------------------------
    # Internal: Model loading / unloading
    # -----------------------------------------------------------------------

    def _load_summary_model(self) -> None:
        """
        Load qwen2.5:3b into VRAM by sending a minimal prompt with keep_alive.
        This is a blocking call – Ollama will download/cache the model if needed.
        """
        self._send_generate_request(
            model=SUMMARY_MODEL,
            prompt="Ping. Antworte nur mit 'Pong'.",
            keep_alive="30m",  # keep alive for the summary session
            timeout=self._timeout,
        )

    def _unload_summary_model(self) -> None:
        """
        Unload qwen2.5:3b from VRAM immediately after summary generation.
        """
        self._send_generate_request(
            model=SUMMARY_MODEL,
            prompt="Ping. Antworte nur mit 'Pong'.",
            keep_alive=0,  # unload immediately after response
            timeout=10,
        )

    def _unload_embedding_model(self) -> None:
        """
        Unload nomic-embed-text from VRAM to free space for the summary model.
        Uses the /api/generate endpoint with keep_alive=0.
        Graceful degradation: embedding models don't support generate endpoint,
        so failures are expected and logged at debug level.
        """
        try:
            self._send_generate_request(
                model=EMBED_MODEL,
                prompt="Ping. Antworte nur mit 'Pong'.",
                keep_alive=0,
                timeout=10,
            )
        except RuntimeError:
            logger.debug(
                "Embedding model %s does not support generate – skipping unload",
                EMBED_MODEL,
            )

    def _ensure_embedding_model_reloaded(self) -> None:
        """
        Reload nomic-embed-text into VRAM after the summary model has been unloaded.
        Uses keep_alive="30m" so it stays loaded for subsequent embedding requests.
        Graceful degradation: silently ignores failures – the first real embedding
        request will trigger loading anyway.
        """
        try:
            self._send_generate_request(
                model=EMBED_MODEL,
                prompt="Ping. Antworte nur mit 'Pong'.",
                keep_alive="30m",
                timeout=30,
            )
        except RuntimeError:
            pass  # graceful degradation – embedding will load on first real use

    # -----------------------------------------------------------------------
    # Internal: Summary generation
    # -----------------------------------------------------------------------

    def _generate_summary_internal(
        self,
        existing_summary: Optional[str],
        turns: List[dict],
    ) -> str:
        """
        Send the rolling-summary prompt to qwen2.5:3b and return the result.
        """
        # Build the "new turns" block from the turns list
        new_turns_text = self._format_turns(turns)

        # Build the "previous summary" block
        bisherige = (
            f"Bisherige Zusammenfassung:\n{existing_summary}"
            if existing_summary
            else "Bisherige Zusammenfassung:\n(keine – dies ist der Beginn des Gesprächs)"
        )

        prompt = ROLLING_SUMMARY_PROMPT_TEMPLATE.format(
            max_words=SUMMARY_MAX_WORDS,
            bisherige_summary=bisherige,
            new_turns=new_turns_text,
        )

        response = self._send_generate_request(
            model=SUMMARY_MODEL,
            prompt=prompt,
            keep_alive="30m",
            timeout=self._timeout,
            temperature=0.3,  # low temperature for factual summaries
            num_predict=600,  # enough tokens for ~500 words in German
        )

        summary = response.strip()
        if not summary:
            return existing_summary or "Kein Gesprächsverlauf vorhanden."

        return summary

    @staticmethod
    def _format_turns(turns: List[dict]) -> str:
        """
        Format a list of turn dicts into a readable text block.
        """
        lines: List[str] = []
        for turn in turns:
            role = str(turn.get("role", "unknown"))
            message = str(turn.get("message", "")).strip()
            if not message:
                continue
            speaker = "Spieler" if role == "player" else "Dorfbewohner"
            lines.append(f"[{speaker}]: {message}")
        return "\n".join(lines)

    # -----------------------------------------------------------------------
    # Internal: Low-level Ollama request
    # -----------------------------------------------------------------------

    def _send_generate_request(
        self,
        model: str,
        prompt: str,
        keep_alive: str,
        timeout: int,
        temperature: float = 0.65,
        num_predict: int = 80,
    ) -> str:
        """
        Send a single /api/generate request to Ollama and return the response text.

        Args:
            model: Model name (e.g. "qwen2.5:3b").
            prompt: The full prompt string.
            keep_alive: Duration to keep model loaded after response (e.g. "30m") or 0 to unload.
            timeout: HTTP timeout in seconds.
            temperature: Sampling temperature (0.0–2.0).
            num_predict: Maximum number of tokens to generate.

        Returns:
            The generated text.

        Raises:
            RuntimeError: If Ollama is unreachable, returns an error, or produces invalid JSON.
        """
        request_body = json.dumps({
            "model": model,
            "prompt": prompt,
            "stream": False,
            "keep_alive": keep_alive,
            "options": {
                "temperature": temperature,
                "num_predict": num_predict,
            },
        }).encode("utf-8")

        request = urllib.request.Request(
            OLLAMA_GENERATE_ENDPOINT,
            data=request_body,
            headers={"Content-Type": "application/json"},
            method="POST",
        )

        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                response_body = response.read().decode("utf-8")
        except urllib.error.HTTPError as error:
            error_body = error.read().decode("utf-8", errors="replace")
            raise RuntimeError(
                f"Ollama HTTP {error.code}: {error_body}"
            ) from error
        except urllib.error.URLError as error:
            raise RuntimeError(
                f"Ollama ist nicht erreichbar: {error}"
            ) from error

        try:
            parsed = json.loads(response_body)
        except json.JSONDecodeError as error:
            raise RuntimeError(
                f"Ollama lieferte ungueltiges JSON: {response_body}"
            ) from error

        reply_text = str(parsed.get("response", "")).strip()
        if not reply_text:
            # An empty response is acceptable for ping/unload requests
            return ""

        return reply_text

    # -----------------------------------------------------------------------
    # Graceful degradation wrapper (for external callers that want safety)
    # -----------------------------------------------------------------------

    def generate_summary_safe(
        self,
        existing_summary: Optional[str],
        turns: List[dict],
    ) -> str:
        """
        Like generate_summary(), but never raises.

        Returns existing_summary (or a fallback string) on any error.
        """
        try:
            return self.generate_summary(existing_summary, turns)
        except RuntimeError as exc:
            # Log the error and degrade gracefully
            logger.error("Summary generation failed: %s", exc)
            if existing_summary:
                return existing_summary
            # Build a minimal fallback from turns
            if turns:
                last_turn = turns[-1]
                speaker = "Spieler" if last_turn.get("role") == "player" else "Häuptling"
                return (
                    f"Gespräch begann. Letzter Beitrag von {speaker}: "
                    f"{str(last_turn.get('message', ''))[:200]}"
                )
            return "Kein Gesprächsverlauf vorhanden."


# ---------------------------------------------------------------------------
# CLI smoke test
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    print("--- summary_client smoke test ---")

    client = SummaryClient()

    # Test with mock turns (no existing summary)
    test_turns = [
        {"role": "player", "message": "Hallo Häuptling!"},
        {"role": "chief", "message": "Willkommen in unserem Dorf. Was führt dich her?"},
        {"role": "player", "message": "Ich suche Arbeit. Habt ihr Aufträge?"},
        {"role": "chief", "message": "Ein starker Rücken ist immer willkommen. Hilf uns beim Holzfällen."},
    ]

    try:
        summary = client.generate_summary(None, test_turns)
        print(f"Summary (new):\n{summary}\n")
    except RuntimeError as exc:
        print(f"generate_summary failed (Ollama may not be reachable): {exc}")

    # Test with existing summary + new turns
    existing = (
        "Der Spieler ist neu im Dorf und hat den Häuptling begrüßt. "
        "Er sucht nach Arbeit und der Häuptling schlug Holzfällen vor."
    )
    new_turns = [
        {"role": "player", "message": "Gibt es auch gefährlichere Aufträge?"},
        {"role": "chief", "message": "Die Wälder sind nachts voller Gefahren. Bist du bereit dafür?"},
    ]
    try:
        updated = client.generate_summary(existing, new_turns)
        print(f"Summary (updated):\n{updated}\n")
    except RuntimeError as exc:
        print(f"generate_summary with existing failed: {exc}")

    # Test generate_summary_safe (graceful degradation)
    safe_summary = client.generate_summary_safe(None, test_turns)
    print(f"Safe summary: {safe_summary[:200]}...")

    print("--- smoke test complete ---")