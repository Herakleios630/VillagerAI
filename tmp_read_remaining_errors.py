"""Read exact error lines from ChiefCommand.java."""
import pathlib, re

path = pathlib.Path("tmp_errors_a3e.txt")
text = path.read_text(encoding="utf-8")

# Extract line numbers from ChiefCommand.java errors
for m in re.finditer(r'ChiefCommand\.java:(\d+): Fehler: (.+)', text):
    print(f"ChiefCommand:{m.group(1)}: {m.group(2)}")

for m in re.finditer(r'VillageChiefPlugin\.java:(\d+): Fehler: (.+)', text):
    print(f"VillageChiefPlugin:{m.group(1)}: {m.group(2)}")
