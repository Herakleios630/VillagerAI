"""Fix ChiefCommand.java: alle verbliebenen Chief-Referenzen durch Speaker ersetzen."""
import pathlib
import re

path = pathlib.Path("src/main/java/de/ajsch/villagerai/command/ChiefCommand.java")
content = path.read_text(encoding="utf-8")

# 1. Methoden-Parameter: Chief speaker -> Speaker speaker
content = content.replace(
    "private boolean handleDebugSetVillage(Player player, Villager villager, Chief speaker, int score)", 
    "private boolean handleDebugSetVillage(Player player, Villager villager, Speaker speaker, int score)")
content = content.replace(
    "private boolean handleDebugSetVillager(Player player, Chief speaker, int score)",
    "private boolean handleDebugSetVillager(Player player, Speaker speaker, int score)")

# 2. findStoredChief -> speakerService.getSpeaker
content = content.replace(
    "Chief chief = chiefService.findStoredChief(villager.getUniqueId()).orElse(null);",
    "Speaker speaker = speakerService.getSpeaker(villager).orElse(null);"
)

# 3. Alle "Chief chief =" (Variablen-Deklaration) -> "Speaker speaker ="
content = re.sub(r'(?<!\w)Chief chief\b(?!\.)', 'Speaker speaker', content)

# 4. "Chief chief," (Methodenaufrufe) -> "Speaker speaker,"
content = re.sub(r'(?<!\w)Chief chief,', 'Speaker speaker,', content)

# 5. Optional<Chief> -> Optional<Speaker>
content = content.replace("Optional<Chief>", "Optional<Speaker>")

# 6. Variable optionalChief -> optionalSpeaker
content = content.replace("optionalChief", "optionalSpeaker")

# 7. .chiefId() -> .speakerId()
content = content.replace(".chiefId()", ".speakerId()")

# 8. Chief:: -> Speaker::
content = content.replace("Chief::", "Speaker::")

# 9. Village-Felder von Speaker auf villageIdentityService.resolve(villager) umleiten
for method in ["villageDescription", "villageAttributes", "villageBiome", 
               "villagePopulationEstimate", "villageEventSummary"]:
    content = content.replace(
        f"speaker.{method}()",
        f"villageIdentityService.resolve(villager).{method}()"
    )

path.write_text(content, encoding="utf-8")
print("ChiefCommand.java fixes applied.")