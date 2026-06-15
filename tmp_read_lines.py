#!/usr/bin/env python3
import pathlib
lines = pathlib.Path("src/main/java/de/ajsch/villagerai/service/ConversationService.java").read_text(encoding='utf-8').splitlines()
for i in range(470, min(520, len(lines))):
    print(f"{i+1}: {lines[i]}")