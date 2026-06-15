"""Read specific lines from ChiefCommand.java."""
import pathlib

path = pathlib.Path("src/main/java/de/ajsch/villagerai/command/ChiefCommand.java")
lines = path.read_text(encoding="utf-8").splitlines()

# Fehlerzeilen laut Build
fehler_lines = [193, 194, 195, 222, 223, 225, 250, 252, 279, 281, 282, 283, 284, 286, 288,
                349, 350, 351, 352, 353, 484, 627, 692, 739, 788, 831, 874, 923, 1045, 1087, 1094, 1164, 1216, 1282]

for ln in sorted(set(fehler_lines)):
    if ln < 1 or ln > len(lines):
        continue
    start = max(0, ln - 3)
    end = min(len(lines), ln + 3)
    print(f"=== around line {ln} ===")
    for i in range(start, end):
        marker = ">>>" if i == ln - 1 else "   "
        print(f"{marker} {i+1:5d}: {lines[i]}")
    print()
