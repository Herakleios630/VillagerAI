---
title: "Arbeitsauftrag: Regeln komprimieren"
quelle: "Ad-hoc -> Prompt-Konzept Karte 4"
related-roadmap: "Plannung/prompt-redesign/konzept.md"
created: "2025-12-18"
status: done
---

# Arbeitsauftrag: Regeln komprimieren

**Quelle:** Ad-hoc -> Prompt-Konzept Abschnitt 5, Karte 4

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI

## Auftrag
`_build_rules_section()` von aktuell 45 Zeilen auf 15 Kernregeln komprimieren.
Dopplungen entfernen, Constraints straffen, `reputation_guidance` auf eine Zeile pro Stufe kurzen.
Die Sektion wandert ans Ende (wurde bereits in Karte 1 umgestellt).

## Aktuelles Ergebnis
- 45 Zeilen Regeln, teils redundant:
  - "Antworte direkt" kommt 3x in Varianten vor
  - "Kein Vorwort, keine Einleitung" doppelt
  - "Bleibe in deiner Rolle" doppelt
  - Reputation-Guidance ist bei positiven Scores trotzdem ein langer Block
- Regeln konkurrieren mit Ground-Truth (z.B. Chief-Existenz in Regeln UND Daten)

## Ursachenverdacht
Regeln sind historisch gewachsen (organische Anhaufung), nie systematisch auf Dopplungen gepruft.
Uberlange Regeln ermuden das LLM -> oberflachliche Befolgung.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `chief-ai-service/chief_ai_service/prompt_builder.py` | `_build_rules_section()` |

## Erbetene Hilfe

1. **`_build_rules_section()` ersetzen durch die kompakte Version aus dem Konzept (Abschnitt 3.2, Regeln)**

2. **Gestrichene Regeln (gegenuber aktuell):**
   - "Wenn ein Spieler nach dem Hauptling fragt..." -> STREICHEN, steht in Ground-Truth
   - "Bekannter-Spieler-Hinweis nutzen" -> STREICHEN, als Feld im Ruf-Template selbsterklarend
   - "Nutze das kuratierte Wissenspaket..." -> STREICHEN, Knowledge-Sektion spricht fur sich
   - Alle Dopplungen zu "Kein Vorwort", "Direkt antworten", "Nicht wiederholen"

3. **Test:** Trockentest:
   - Maximal 15 Zeilen (inkl. "Regeln:"-Uberschrift)
   - `reputation_guidance` erscheint als eine Zeile
   - Keine Dopplung zur Ground-Truth

4. Build & Deployment (Bridge):
   - `python -m compileall chief-ai-service/`
   - Kopieren + `sudo systemctl restart villagerai-chief`
