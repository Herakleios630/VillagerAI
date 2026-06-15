"\"\"\"Converts ChiefCommand.java from Chief to Speaker type.\"\"\"
import re

with open('src/main/java/de/ajsch/villagerai/command/ChiefCommand.java', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Replace import de.ajsch.villagerai.model.Chief -> Speaker (already done)
# 2. Add SpeakerService import
if 'import de.ajsch.villagerai.service.SpeakerService;' not in content:
    content = content.replace(
        'import de.ajsch.villagerai.model.Speaker;',
        'import de.ajsch.villagerai.model.Speaker;\nimport de.ajsch.villagerai.service.SpeakerService;')

# 3. Add SpeakerService field
if 'private final SpeakerService speakerService;' not in content:
    content = content.replace(
        'private final ChiefService chiefService;',
        'private final ChiefService chiefService;\n    private final SpeakerService speakerService;')

# 4. Update constructor parameter list: add SpeakerService speakerService,
content = content.replace(
    'public ChiefCommand(\n            VillageChiefPlugin plugin,\n            ChiefService chiefService,',
    'public ChiefCommand(\n            VillageChiefPlugin plugin,\n            ChiefService chiefService,\n            SpeakerService speakerService,')

# 5. Add speakerService assignment in constructor
content = content.replace(
    'this.chiefService = chiefService;',
    'this.chiefService = chiefService;\n        this.speakerService = speakerService;')

# 6. Replace getConversationSpeaker calls
# Pattern: chiefService.getConversationSpeaker(villager)
# Replace with: speakerService.getSpeaker(villager).orElse(null)
content = re.sub(
    r'Chief (\w+) = chiefService\.getConversationSpeaker\((\w+)\);',
    r'Speaker \1 = speakerService.getSpeaker(\2).orElse(null);',
    content)

# 7. Replace remaining .chiefId() -> .speakerId() on Speaker type (already done via bulk replace)
# 8. Replace .chatName() -> .displayName() on Speaker
content = content.replace('speaker.chatName()', 'speaker.displayName()')
content = content.replace('chief.chatName()', 'speaker.displayName()')

# 9. Replace Chief type declarations where Speaker is used
content = re.sub(r'Chief (?!markChief|unmarkChief|mournChief)chief', r'Speaker speaker', content)

# 10. Fix leftover chiefService.createConversationProfile(villager).speakerId()
content = re.sub(
    r'chiefService\.createConversationProfile\((\w+)\)\.speakerId\(\)',
    r'speakerService.createOrRefreshProfile(\1).speakerId()',
    content)
content = re.sub(
    r'chiefService\.createConversationProfile\((\w+)\)',
    r'speakerService.createOrRefreshProfile(\1)',
    content)

# 11. Replace chiefService.getChief(villager) -> speakerService.getSpeaker(villager)
content = re.sub(
    r'chiefService\.getChief\((\w+)\)',
    r'speakerService.getSpeaker(\1)',
    content)

# 12. Replace chiefService.isChief(villager) -> speakerService.getSpeaker(villager).map(Speaker::isChief).orElse(false)
content = content.replace(
    'chiefService.isChief(villager)',
    'speakerService.getSpeaker(villager).map(Speaker::isChief).orElse(false)')

# 13. Replace chiefService.findStoredChief -> chiefRepository call via chiefService.findStoredChief()
# (keep as-is for now since ChiefService still has findStoredChief)

# 14. Replace chiefService.findChiefByVillageId -> speakerService.findActiveChiefByVillageId
content = re.sub(
    r'chiefService\.findChiefByVillageId\(',
    r'speakerService.findActiveChiefByVillageId(',
    content)

# 15. Replace Chief variable type in handleDebug
content = re.sub(r'var chief = chiefService\.getChief', r'var speaker = speakerService.getSpeaker', content)
content = re.sub(r'Optional<Chief> optionalChief = chiefService\.getChief', r'Optional<Speaker> optionalSpeaker = speakerService.getSpeaker', content)

# 16. Replace Chief type for return values (markChief returns Chief, keep those)
# But replace chief variable type declarations
content = re.sub(r'(Optional)<Chief> (\w+) = speakerService\.getSpeaker', r'\1<Speaker> \2 = speakerService.getSpeaker', content)

with open('src/main/java/de/ajsch/villagerai/command/ChiefCommand.java', 'w', encoding='utf-8') as f:
    f.write(content)

print('ChiefCommand migration script completed.')
"