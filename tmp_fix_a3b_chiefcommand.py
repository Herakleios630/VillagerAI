"""Zweite Runde: ChiefCommand.java restliche Fehler beheben."""
import pathlib

path = pathlib.Path("src/main/java/de/ajsch/villagerai/command/ChiefCommand.java")
content = path.read_text(encoding="utf-8")

# 1. markChief-Zuweisung: Speaker speaker -> Chief chief (markChief returns Chief)
content = content.replace(
    "Speaker speaker = (args.length >= 2 && !args[1].isBlank())",
    "Chief chief = (args.length >= 2 && !args[1].isBlank())"
)
# Falls die zweite Variante (ohne Blank) existiert:
content = content.replace(
    "Speaker speaker = (args.length >= 2 && !args[1].isBlank())",
    "Chief chief = (args.length >= 2 && !args[1].isBlank())"
)

# 2. Chief speaker = speakerService.getSpeaker -> Speaker speaker = speakerService.getSpeaker
# (Zeile 484)
content = content.replace(
    "Chief speaker = speakerService.getSpeaker(villager).orElse(null);",
    "Speaker speaker = speakerService.getSpeaker(villager).orElse(null);"
)

# 3. Variable chief -> speaker in questService.activate*/questOfferService.acceptOffer Aufrufen
# Alle ", chief," -> ", speaker,"
content = content.replace(', chief,', ', speaker,')

# 4. Alle "chief." -> "speaker." (aber nicht chiefService!)
# Benutze Regex mit Word-Boundary um chiefService zu schuetzen
import re
content = re.sub(r'(?<!Service\.)(?<!\.)chief\.(?!getClass|markChief|unmarkChief|findBy|isVillageInMourning|reloadProfiles)', 
                 'speaker.', content)

# 5. foundChief -> foundSpeaker (Variable)
content = content.replace('foundChief.', 'foundSpeaker.')
content = content.replace('foundChief)', 'foundSpeaker)')
content = content.replace('foundChief))', 'foundSpeaker))')

# 6. foundSpeaker.villageDescription() -> villageIdentityService.resolve(villager).villageDescription()
# sowie die anderen Village-Methoden
for method in ['villageDescription', 'villageAttributes', 'villageBiome',
               'villagePopulationEstimate', 'villageEventSummary']:
    content = content.replace(
        f'foundSpeaker.{method}()',
        f'villageIdentityService.resolve(villager).{method}()'
    )

# 7. chief.crownedAt() in Zeile 288: muss aus ChiefRepository kommen
# Ersetze Text: Component.text("Gekroent seit: " + formatTimeAgo(chief.crownedAt())
# -> Component.text("Gekroent seit: " + formatTimeAgo(chiefRepository.findByEntityUuid(villager.getUniqueId()).map(c -> c.crownedAt()).orElse(0L))
# Einfacher: placeholder, spaeter manuell fixen. Zuerst provisorisch:
content = content.replace(
    'chief.crownedAt()',
    'chiefRepository.findByEntityUuid(villager.getUniqueId()).map(a -> a.crownedAt()).orElse(0L)'
)

path.write_text(content, encoding='utf-8')
print("Zweite Runde fixes applied.")