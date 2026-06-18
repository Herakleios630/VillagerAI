"""Status-Updates für alle Memory-Karten, Bugfixes, Umbau-Karten etc."""
import os
import re

ROOT = r"c:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI"

# Mapping: Pfad-Substring -> (alter_status, neuer_status)
UPDATES = [
    # ── Memory Phase 4a ──
    (r"roadmap\memory\phase-4a", "in-progress", "done"),
    # ── Memory Phase 4b ──
    (r"roadmap\memory\phase-4b", "in-progress", "done"),
    # ── Memory Phase 4c ──
    (r"roadmap\memory\phase-4c", "in-progress", "done"),
    # ── Memory Phase 4d ──
    (r"roadmap\memory\phase-4d", "in-progress", "done"),
    # ── Memory Phase 4e ──
    (r"roadmap\memory\phase-4e", "in-progress", "done"),
    # ── Memory Langzeit ──
    (r"roadmap\memory\langzeit", "in-progress", "done"),
    # ── Bugfixes: 05 bleibt offen, 07/10 obsolet, 12/13/14 done ──
    (r"bugfixes\aufgabe-07-none-beruf-todeslog", "todo", "obsolet"),
    (r"bugfixes\aufgabe-12-memory-antworten-fixes", "ready", "done"),
    (r"bugfixes\aufgabe-13-memory-flow-debuggen", "ready", "done"),
    (r"bugfixes\aufgabe-14-aufgabe-13-fixes-korrigieren", "ready", "done"),
    # ── Chief-Villager-Umbau (04, 09, 10, 13 done; 11 bleibt offen) ──
    (r"chief-villager-umbau\04-conversationrole-npc", "in-progress", "done"),
    (r"chief-villager-umbau\09-chiefservice-kuerzen", "in-progress", "done"),
    (r"chief-villager-umbau\10-conversationservice-umbau", "in-progress", "done"),
    (r"chief-villager-umbau\13-bridge-python-anpassen", "in-progress", "done"),
    # ── Prompt-Redesign ──
    (r"prompt-redesign", "in-progress", "done"),
    # ── Village-Fixes 05 done, 08 deploy-schuld ──
    (r"village-fixes\05-chief-position-im-prompt", "in-progress", "done"),
    # ── 4-fixes obsolet ──
    (r"ad-hoc\4 fixes", "in-progress", "obsolet"),
    # ── Phase B-06 done ──
    (r"chief-v2-phase-B\aufgabe-06-chief-meeting-observer", "in-progress", "done"),
    # ── Phase C alle auf todo ──
    (r"chief-v2-phase-C", "in-progress", "todo"),
    # ── Phase D alle auf todo ──
    (r"chief-v2-phase-D", "in-progress", "todo"),
]

CHANGED = []

def update_file(filepath, old_status, new_status):
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            content = f.read()

        pattern = rf"^status:\s*{re.escape(old_status)}\s*$"
        new_line = f"status: {new_status}"
        updated, count = re.subn(pattern, new_line, content, flags=re.MULTILINE)

        if count > 0:
            with open(filepath, "w", encoding="utf-8") as f:
                f.write(updated)
            return count
        return 0
    except Exception as e:
        print(f"  ERROR: {filepath}: {e}")
        return 0

def main():
    for dirpath, _, filenames in os.walk(os.path.join(ROOT, "Plannung")):
        for fname in filenames:
            if not fname.endswith(".md"):
                continue
            fullpath = os.path.join(dirpath, fname)
            relpath = os.path.relpath(fullpath, ROOT)

            for path_substring, old_status, new_status in UPDATES:
                if path_substring in relpath:
                    n = update_file(fullpath, old_status, new_status)
                    if n > 0:
                        CHANGED.append((relpath, old_status, new_status))
                        print(f"  UPDATED: {relpath}: {old_status} -> {new_status}")
                    break  # first matching rule wins

    print(f"\nDone. {len(CHANGED)} files changed.")

if __name__ == "__main__":
    main()