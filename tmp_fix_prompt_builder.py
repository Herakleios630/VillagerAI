"""Patch prompt_builder.py: fix resolve_system_prompt + accept flat string memories."""
import re

path = "chief-ai-service/chief_ai_service/prompt_builder.py"
with open(path, "r", encoding="utf-8") as fh:
    text = fh.read()

# 1) Fix resolve_system_prompt: payload_prompt replaces, not appends
text = text.replace(
    "    # Payload-SystemPrompt ist NUR Rollentext-Additiv, kein Ersatz fuer den gesamten Kontext\n    payload_prompt = str(payload.get(\"systemPrompt\", \"\")).strip()\n    if payload_prompt and payload_prompt not in base_prompt:\n        return base_prompt + \"\\n\\n\" + payload_prompt\n\n    return base_prompt",
    "    # Payload-SystemPrompt ersetzt den Config-Prompt, wenn gesetzt\n    payload_prompt = str(payload.get(\"systemPrompt\", \"\")).strip()\n    if payload_prompt:\n        return payload_prompt\n\n    return base_prompt",
)

# 2) Fix memories section: accept both old-style flat strings and new-style dicts
old_mem_section = """    # ---- Memories (nur bei Treffern) ----
    if memories:
        role_de: dict[str, str] = {"player": "Spieler", "chief": "Haeuptling"}
        mem_lines: list[str] = []
        for mem_entry in memories:
            if not isinstance(mem_entry, dict) or not mem_entry.get("message"):
                continue
            role_label = role_de.get(str(mem_entry.get("role", "")), "Unbekannt")
            msg = str(mem_entry["message"]).strip()
            if msg:
                mem_lines.append(f"- {role_label}: {msg}")
        if mem_lines:
            sections.append(("Memories", "\\n".join(mem_lines)))"""

new_mem_section = """    # ---- Memories (nur bei Treffern) ----
    if memories:
        role_de: dict[str, str] = {"player": "Spieler", "chief": "Haeuptling"}
        mem_lines: list[str] = []
        for mem_entry in memories:
            if isinstance(mem_entry, dict):
                if not mem_entry.get("message"):
                    continue
                role_label = role_de.get(str(mem_entry.get("role", "")), "Unbekannt")
                msg = str(mem_entry["message"]).strip()
            else:
                # Flat string memory (backward compat)
                role_label = "Unbekannt"
                msg = str(mem_entry).strip()
            if msg:
                mem_lines.append(f"- {role_label}: {msg}")
        if mem_lines:
            sections.append(("Memories", "\\n".join(mem_lines)))"""

if old_mem_section in text:
    text = text.replace(old_mem_section, new_mem_section)
else:
    print("WARNING: could not find old memories section text. Check manually.")
    # Try with different line endings
    old_mem_section2 = old_mem_section.replace("\n", "\r\n")
    if old_mem_section2 in text:
        text = text.replace(old_mem_section2, new_mem_section)
    else:
        print("FAILED to find memories section!")

with open(path, "w", encoding="utf-8") as fh:
    fh.write(text)

print("Done patching prompt_builder.py")