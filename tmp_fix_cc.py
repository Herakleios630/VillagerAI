import re

path = r'src/main/java/de/ajsch/villagerai/command/ChiefCommand.java'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

# 1. Add SpeakerService import
if 'import de.ajsch.villagerai.service.SpeakerService;' not in c:
    c = c.replace(
        'import de.ajsch.villagerai.service.ChiefService;',
        'import de.ajsch.villagerai.service.ChiefService;\nimport de.ajsch.villagerai.service.SpeakerService;')

# 2. Add SpeakerService field
if 'private final SpeakerService speakerService;' not in c:
    c = c.replace(
        'private final ChiefService chiefService;',
        'private final ChiefService chiefService;\n    private final SpeakerService speakerService;')

# 3. Add SpeakerService param to constructor
c = c.replace(
    'ChiefService chiefService,',
    'ChiefService chiefService,\n            SpeakerService speakerService,')

# 4. Add assignment
c = c.replace(
    'this.chiefService = chiefService;',
    'this.chiefService = chiefService;\n        this.speakerService = speakerService;')

# 5. Replace method calls
c = c.replace('chiefService.getConversationSpeaker(villager)', 'speakerService.getSpeaker(villager).orElse(null)')
c = c.replace('chiefService.createConversationProfile(villager)', 'speakerService.createOrRefreshProfile(villager)')
c = c.replace('chiefService.getChief(villager)', 'speakerService.getSpeaker(villager)')
c = c.replace('chiefService.isChief(villager)', 'speakerService.getSpeaker(villager).map(Speaker::isChief).orElse(false)')
c = c.replace('chiefService.findChiefByVillageId', 'speakerService.findActiveChiefByVillageId')

# 6. Replace Optional<Chief> with Optional<Speaker>
c = c.replace('Optional<Chief>', 'Optional<Speaker>')

# 7. Replace variable names
c = c.replace('optionalChief', 'optionalSpeaker')

# 8. Replace .chatName() with .displayName()
c = c.replace('.chatName()', '.displayName()')

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)

print('ChiefCommand fixes applied.')
