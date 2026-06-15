"""Final fix: ChiefCommand.java remaining 26 errors."""
import pathlib

path = pathlib.Path("src/main/java/de/ajsch/villagerai/command/ChiefCommand.java")
c = path.read_text(encoding="utf-8")

# ---- 1. markChief-Zuweisung (Zeilen 193‑195): markChief returns Chief, variable must be Chief ----
c = c.replace(
    "Speaker speaker = (args.length >= 2 && !args[1].isBlank())\n                ? chiefService.markChief(villager, args[1])\n                : chiefService.markChief(villager);",
    "Chief chief = (args.length >= 2 && !args[1].isBlank())\n                ? chiefService.markChief(villager, args[1])\n                : chiefService.markChief(villager);"
)

# ---- 2. findStoredChief replacement (Zeile 222) ----
# Wurde schon zu "Speaker speaker = speakerService.getSpeaker(villager).orElse(null);" geändert,
# aber die nächste Zeile if (chief != null) existiert noch. Variablenname anpassen.
c = c.replace(
    "Speaker speaker = speakerService.getSpeaker(villager).orElse(null);\n        if (chief != null) {",
    "Speaker speaker = speakerService.getSpeaker(villager).orElse(null);\n        if (speaker != null) {"
)

# ---- 3. chief.crownedAt() (Zeile 288) ----
c = c.replace(
    'chief.crownedAt()',
    'chiefRepository.findByEntityUuid(villager.getUniqueId()).map(a -> a.crownedAt()).orElse(0L)'
)

# ---- 4. foundChief -> foundSpeaker (Zeilen 349‑353) ----
c = c.replace('foundChief.', 'foundSpeaker.')

# foundSpeaker.village*() -> villageIdentityService.resolve(villager).village*()
for method in ["villageDescription", "villageAttributes", "villageBiome",
               "villagePopulationEstimate", "villageEventSummary"]:
    c = c.replace(
        f"foundSpeaker.{method}()",
        f"villageIdentityService.resolve(villager).{method}()"
    )

# ---- 5. Chief speaker = ... (Zeile 484) ----
c = c.replace(
    "Chief speaker = speakerService.getSpeaker(villager).orElse(null);",
    "Speaker speaker = speakerService.getSpeaker(villager).orElse(null);"
)

# ---- 6. All questService.activate* and questOfferService.acceptOffer calls: chief -> speaker ----
# Diese Kaskade: "activateDeliverQuest(player.getUniqueId(), chief," -> "...speaker,"
# Die Variable heißt im Scope jetzt "speaker", muss konsistent werden.
# Wir ersetzen jedes Vorkommen von ", chief," durch ", speaker," (betrifft nur Quest-Aufrufe)
c = c.replace(', chief,', ', speaker,')

# ---- 7. chief); (letzter Parameter) -> speaker); ----
# Beispiel: questOfferService.acceptOffer(player, chief, offer);
c = c.replace('(chief,', '(speaker,')

# ---- 8. chief am Zeilenende vor ; oder ) ----
# z.B. "questService.activateBuildQuest(player.getUniqueId(), chief, material, amount);"
# schon durch Schritt 6 abgedeckt.

# ---- 9. chief alleinstehend nach questService.activate... (ohne Komma davor) ----
# Nicht nötig, die Muster sind immer ", chief,".

# ---- 10. Uebrige "chief." -> "speaker." (aber nicht chiefService oder chiefRepository) ----
import re
# Ersetze chief. NUR wenn NICHT vorne Service oder Repository steht
c = re.sub(r'(?<!\w)(chief)\.(?!getClass|markChief|unmarkChief|reloadProfiles)', 'speaker.', c)

path.write_text(c, encoding='utf-8')
print(f"Final fix applied. Written {len(c)} chars.")
