---
title: "Arbeitsauftrag: Bridge-Python anpassen"
quelle: "konzept-aufteilung-chief-villager.md → Schritt 13"
created: "2025-01-16"
status: done
---

# Arbeitsauftrag: Bridge-Python anpassen

**Quelle:** konzept-aufteilung-chief-villager.md → Schritt 13

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3 (Bridge)
- **Build-Tool:**       Gradle (nur für Plugin)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Drei Python-Dateien im Bridge-Dienst prüfen und auf die neuen Feldnamen umstellen: `prompt_builder.py` (speakerStatus auswerten), `reply_builder.py` (NPC-Erkennung), `summary_client.py` (chiefId → speakerId).

## Aktuelles Ergebnis
Die Bridge arbeitet mit den JSON-Feldern, die das Plugin im AIRequest sendet. Da das Plugin jetzt `speakerStatus` statt `isChief`, `speakerId` statt `chiefId` sendet, müssen die Python-Dateien die neuen Feldnamen verstehen.

## Ursachenverdacht
Entfällt – reine Feldumbenennung.

---

### 13a. prompt_builder.py

**Hauptdatei:** `chief-ai-service/chief_ai_service/prompt_builder.py`

**Auftrag:** `speakerStatus` auswerten statt `isChief`. Prompt-Rolle je nach Status setzen.

1. Lies `chief-ai-service/chief_ai_service/prompt_builder.py` mit `filesystem_read_text_file`
2. Finde die Stelle, wo `isChief` geprüft wird
3. Ersetze `isChief` → `speakerStatus` mit den Werten:
   - `"AKTIV_CHIEF"` → Chief-Prompt-Rolle
   - `"NORMALER_DORFBEWOHNER"` → einfacher Dorfbewohner
   - `"GEWESENER_CHIEF"` → ignorieren (Trauer-Flavour kommt von MourningService)
4. Falls `chiefId` referenziert wird → `speakerId`
5. Build: `python -c "import py_compile; py_compile.compile('chief-ai-service/chief_ai_service/prompt_builder.py', doraise=True)"`

---

### 13b. reply_builder.py

**Hauptdatei:** `chief-ai-service/chief_ai_service/reply_builder.py`

**Auftrag:** NPC-Erkennung prüfen. Falls `chiefId` verwendet → `speakerId`.

1. Lies `chief-ai-service/chief_ai_service/reply_builder.py` mit `filesystem_read_text_file`
2. `chiefId` → `speakerId`
3. Prüfe auf `ConversationRole.CHIEF` (wird als String vom Plugin gesendet) → `NPC`
4. Build: `python -c "import py_compile; py_compile.compile('chief-ai-service/chief_ai_service/reply_builder.py', doraise=True)"`

---

### 13c. summary_client.py

**Hauptdatei:** `chief-ai-service/chief_ai_service/summary_client.py`

**Auftrag:** `chiefId` → `speakerId` in Zusammenfassungs-Referenzen.

1. Lies `chief-ai-service/chief_ai_service/summary_client.py` mit `filesystem_read_text_file`
2. `chiefId` → `speakerId`
3. Build: `python -c "import py_compile; py_compile.compile('chief-ai-service/chief_ai_service/summary_client.py', doraise=True)"`

---

## Abschließende Prüfung
`python -c "import py_compile; [py_compile.compile(f'chief-ai-service/chief_ai_service/{f}', doraise=True) for f in ['prompt_builder.py', 'reply_builder.py', 'summary_client.py']]"`

## Deployment
SCP der geänderten Python-Dateien auf den Server:
- `scp "chief-ai-service\chief_ai_service\prompt_builder.py" mc@10.0.0.86:"/opt/villagerai/chief-ai-service/chief_ai_service/prompt_builder.py"`
- `scp "chief-ai-service\chief_ai_service\reply_builder.py" mc@10.0.0.86:"/opt/villagerai/chief-ai-service/chief_ai_service/reply_builder.py"`
- `scp "chief-ai-service\chief_ai_service\summary_client.py" mc@10.0.0.86:"/opt/villagerai/chief-ai-service/chief_ai_service/summary_client.py"`
- `ssh mc@10.0.0.86 "sudo systemctl restart villagerai-chief"`
