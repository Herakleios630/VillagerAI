"""Patch prompt_builder.py resolve_system_prompt and test."""

# Fix prompt_builder.py
path_pb = "chief-ai-service/chief_ai_service/prompt_builder.py"
with open(path_pb, "r", encoding="utf-8") as f:
    text = f.read()

old = """    # Payload-SystemPrompt ist NUR Rollentext-Additiv, kein Ersatz für den gesamten Kontext
    payload_prompt = str(payload.get("systemPrompt", "")).strip()
    if payload_prompt and payload_prompt not in base_prompt:
        return base_prompt + "\\n\\n" + payload_prompt

    return base_prompt"""

new = """    # Payload-SystemPrompt ersetzt den Config-Prompt, wenn gesetzt
    payload_prompt = str(payload.get("systemPrompt", "")).strip()
    if payload_prompt:
        return payload_prompt

    return base_prompt"""

if old in text:
    text = text.replace(old, new)
    print("Patched resolve_system_prompt in prompt_builder.py")
else:
    print("WARNING: old resolve_system_prompt snippet not found!")
    # Try to find line-by-line
    for i, line in enumerate(text.splitlines(), 1):
        if "Payload-SystemPrompt ist NUR Rollentext-Additiv" in line:
            print(f"  Found comment at line {i}")
            # Replace next few lines
            lines = text.splitlines(True)
            for j in range(i-1, min(i+5, len(lines))):
                print(f"    {j+1}: {lines[j].rstrip()}")

with open(path_pb, "w", encoding="utf-8") as f:
    f.write(text)

# Fix test for empty strings: flat strings now get "Unbekannt: " prefix
path_test = "chief-ai-service/tests/test_prompt_builder.py"
with open(path_test, "r", encoding="utf-8") as f:
    ttext = f.read()

old_test = 'self.assertIn(" - gültig", prompt)'
new_test = 'self.assertIn("- Unbekannt: gültig", prompt)'
ttext = ttext.replace(old_test, new_test)

old_test2 = 'self.assertIn(" - auch gültig", prompt)'
new_test2 = 'self.assertIn("- Unbekannt: auch gültig", prompt)'
ttext = ttext.replace(old_test2, new_test2)

with open(path_test, "w", encoding="utf-8") as f:
    f.write(ttext)

print("Done.")