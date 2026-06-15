# Fix ChiefCommand.java: remaining Chief->Speaker patterns

$path = 'src/main/java/de/ajsch/villagerai/command/ChiefCommand.java'
$content = [System.IO.File]::ReadAllText($path)

# 1. speaker.chatName() -> speaker.displayName()
$content = $content -replace 'speaker\.chatName\(\)', 'speaker.displayName()'

# 2. Add SpeakerService import if not present
if ($content -notmatch 'import de.ajsch.villagerai.service.SpeakerService;') {
    $content = $content -replace 'import de.ajsch.villagerai.service.ChiefService;', 'import de.ajsch.villagerai.service.ChiefService;`nimport de.ajsch.villagerai.service.SpeakerService;'
}

# 3. Add SpeakerService field
if ($content -notmatch 'private final SpeakerService speakerService;') {
    $content = $content -replace 'private final ChiefService chiefService;', 'private final ChiefService chiefService;`n    private final SpeakerService speakerService;'
}

# 4. Add speakerService param to constructor and assignment
$content = $content -replace 'ChiefService chiefService,', 'ChiefService chiefService,`n            SpeakerService speakerService,'
$content = $content -replace 'this.chiefService = chiefService;', 'this.chiefService = chiefService;`n        this.speakerService = speakerService;'

# 5. Fix Chief chief variable where markChief returns Chief (keep Chief there!)
# Replace all OTHER Chief chief = ... with Speaker speaker =
# Strategy: replace all 'Chief chief =' with 'Speaker speaker =', then revert markChief/unmarkChief lines
$content = $content -replace 'Chief chief = chiefService\.markChief', 'Chief chief = chiefService.markChief'  # ensure these stay
$content = $content -replace 'Chief chief = chiefService\.unmarkChief', 'Chief chief = chiefService.unmarkChief'
$content = $content -replace 'Chief chief =', 'Speaker speaker ='
$content = $content -replace 'Speaker speaker = chiefService\.markChief', 'Chief chief = chiefService.markChief'
$content = $content -replace 'Speaker speaker = chiefService\.unmarkChief', 'Chief chief = chiefService.unmarkChief'

# 6. Replace Optional<Chief> -> Optional<Speaker> where used with SpeakerService
$content = $content -replace 'Optional<Chief>', 'Optional<Speaker>'

# 7. Fix chiefService.getConversationSpeaker -> speakerService.getSpeaker(villager).orElse(null)
$content = $content -replace 'chiefService\.getConversationSpeaker\(villager\)', 'speakerService.getSpeaker(villager).orElse(null)'

# 8. Fix chiefService.createConversationProfile -> speakerService.createOrRefreshProfile
$content = $content -replace 'chiefService\.createConversationProfile\(villager\)', 'speakerService.createOrRefreshProfile(villager)'

# 9. Fix chiefService.getChief(villager) -> speakerService.getSpeaker(villager)
$content = $content -replace 'chiefService\.getChief\(villager\)', 'speakerService.getSpeaker(villager)'

# 10. Fix chiefService.isChief(villager) -> speakerService.getSpeaker(villager).map(Speaker::isChief).orElse(false)
$content = $content -replace 'chiefService\.isChief\(villager\)', 'speakerService.getSpeaker(villager).map(Speaker::isChief).orElse(false)'

# 11. Fix chiefService.findChiefByVillageId -> speakerService.findActiveChiefByVillageId
$content = $content -replace 'chiefService\.findChiefByVillageId', 'speakerService.findActiveChiefByVillageId'

# 12. Fix optionalChief -> optionalSpeaker (already done for type, fix variable name)
$content = $content -replace 'optionalChief', 'optionalSpeaker'

# 13. Fix Chief chief = chiefService.findStoredChief
# (findStoredChief returns ChiefAttributes? Let's check - leave it for now)

# 14. Fix var chief = chiefService.getChief -> var speaker = speakerService.getSpeaker
$content = $content -replace 'var chief = chiefService\.getChief\(villager\)', 'var speaker = speakerService.getSpeaker(villager)'

# 15. Fix various chief.villageId() -> speaker.villageId() etc - use safe replacement
$content = $content -replace 'chief\.villageId\(\)', 'speaker.villageId()'
$content = $content -replace 'chief\.displayName\(\)', 'speaker.displayName()'
$content = $content -replace 'chief\.role\(\)', 'speaker.role()'
$content = $content -replace 'chief\.personality\(\)', 'speaker.personality()'
$content = $content -replace 'chief\.greeting\(\)', 'speaker.greeting()'
$content = $content -replace 'chief\.entityUuid\(\)', 'speaker.entityUuid()'
$content = $content -replace 'chief\.world\(\)', 'speaker.world()'
$content = $content -replace 'chief\.x\(\)', 'speaker.x()'
$content = $content -replace 'chief\.y\(\)', 'speaker.y()'
$content = $content -replace 'chief\.z\(\)', 'speaker.z()'
$content = $content -replace 'chief\.villageName\(\)', 'speaker.villageName()'
$content = $content -replace 'chief\.crownedAt\(\)', 'speaker.crownedAt()'  # Speaker doesn't have crownedAt, but handle via ChiefAttributes later

[System.IO.File]::WriteAllText($path, $content)
Write-Host 'ChiefCommand fixes applied.'"
