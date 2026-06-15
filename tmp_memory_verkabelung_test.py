"""Quick compile-check for memory wiring changes."""
import sys
import os

project_dir = os.path.join(os.path.dirname(__file__), "chief-ai-service")
sys.path.insert(0, project_dir)

try:
    from memory_db import search_by_embedding
    print("memory_db.search_by_embedding OK")
except Exception as e:
    print(f"memory_db FAILED: {e}")

try:
    from chief_ai_service.prompt_builder import build_deepseek_messages, build_ollama_prompt
    print("prompt_builder OK")
except Exception as e:
    print(f"prompt_builder FAILED: {e}")

try:
    from chief_ai_service.reply_builder import build_reply, _load_memory_context
    print("reply_builder OK")
except Exception as e:
    print(f"reply_builder FAILED: {e}")

try:
    from chief_ai_service.deepseek_client import request_deepseek_reply
    print("deepseek_client OK")
except Exception as e:
    print(f"deepseek_client FAILED: {e}")

try:
    from chief_ai_service.ollama_client import request_ollama_reply
    print("ollama_client OK")
except Exception as e:
    print(f"ollama_client FAILED: {e}")

print("\nAll imports done.")