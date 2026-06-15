"""Korrigiert beschaedigte ChiefCommand.java nach Regex-Panne."""
import pathlib

path = pathlib.Path("src/main/java/de/ajsch/villagerai/command/ChiefCommand.java")
c = path.read_text(encoding="utf-8")

# 1. Repariere kaputte Methoden-Namen, die durch Chief:: -> Speaker:: entstanden sind
# chiefService.findSpeakerByVillageId -> chiefService.findChiefByVillageId
c = c.replace("findSpeakerByVillageId", "findChiefByVillageId")
# chiefService.isSpeaker -> chiefService.isChief (existiert nicht mehr, aber war vorher isChief)
# chiefService.markSpeaker -> chiefService.markChief
c = c.replace("markSpeaker", "markChief")
# SpeakerRepository -> ChiefRepository
c = c.replace("SpeakerRepository", "ChiefRepository")
# SpeakerService (wo eigentlich ChiefService gemeint war)
# Aber Achtung: es gibt jetzt auch ein echtes SpeakerService-Feld!
# Wir muessen nur die faelschlich ersetzten zurueckaendern
# Methode: alles was vorher ChiefService hiess und jetzt SpeakerService heisst...
# Schwierig. Besser: wir schauen nur auf die kaputten Aufrufe:
# chiefService.findChiefByVillageId (war vorher korrekt, wurde zu findSpeakerByVillageId, jetzt wieder korrekt)
# chiefService.isChief (existiert nicht mehr -> muss speakerService.isChief sein? Nein, kommt spaeter)
# Erstmal nur die offensichtlichen Fixes

# 2. Fuege Chief-Import hinzu falls fehlt
if "import de.ajsch.villagerai.model.Chief;" not in c:
    c = c.replace(
        "import de.ajsch.villagerai.model.Speaker;",
        "import de.ajsch.villagerai.model.Chief;\nimport de.ajsch.villagerai.model.Speaker;"
    )

# 3. Ersetze verbliebene ", chief," durch ", speaker," (aber NUR in Methodenaufrufen)
# Diese sind in Zeilen wie activateBreedQuest(player.getUniqueId(), chief, entityType, amount)
# Wir ersetzen ALLE ", chief," die NICHT Teil einer Deklaration sind
c = c.replace(", chief,", ", speaker,")

# 4. Die markChief-Zeilen (193-195) muessen mit variable 'chief' arbeiten, weil markChief Chief zurueckgibt.
# Die Variable wurde durch Skript zu 'speaker' umbenannt, wir muessen sie zurueckbenennen:
c = c.replace(
    "Speaker speaker = (args.length >= 2 && !args[1].isBlank())",
    "Chief chief = (args.length >= 2 && !args[1].isBlank())"
)

# 5. Zeilen 198/200: nach dem markChief-Block wird auf speaker.speakerId() zugegriffen,
# aber die Variable heisst jetzt 'chief'. Ersetze speaker. -> chief. in diesen Zeilen
# Nur innerhalb des unmittelbaren Blocks nach markChief
c = c.replace(
    '.append(Component.text(speaker.speakerId(), NamedTextColor.WHITE))',
    '.append(Component.text(chief.speakerId(), NamedTextColor.WHITE))'
)
c = c.replace(
    '.append(Component.text(speaker.villageId(), NamedTextColor.WHITE)));',
    '.append(Component.text(chief.villageId(), NamedTextColor.WHITE)));'
)

path.write_text(c, encoding='utf-8')
print(f"Reparaturen angewendet. {len(c)} Zeichen geschrieben.")
