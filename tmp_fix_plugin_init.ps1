# Fix VillageChiefPlugin.java: add SpeakerService init, fix constructors, fix registerListeners/Commands

$path = 'src/main/java/de/ajsch/villagerai/VillageChiefPlugin.java'
$content = [System.IO.File]::ReadAllText($path)

# 1. Add imports for SpeakerService, SpeakerRepository, YamlSpeakerRepository
$importInsert = @'
import de.ajsch.villagerai.service.SpeakerService;
import de.ajsch.villagerai.storage.SpeakerRepository;
import de.ajsch.villagerai.storage.YamlSpeakerRepository;
'@
# Insert after ChiefService import
$content = $content -replace 'import de.ajsch.villagerai.service.ChiefService;', "import de.ajsch.villagerai.service.ChiefService;$importInsert"

# 2. Add SpeakerService and SpeakerRepository fields
$fieldInsert = @'
    private SpeakerRepository speakerRepository;
    private SpeakerService speakerService;
'@
$content = $content -replace '    private ChiefService chiefService;', "$fieldInsert    private ChiefService chiefService;"

# 3. Initialize SpeakerRepository and SpeakerService after villagerProfileRepository init
$initInsert = @'

        // Speaker-Infrastruktur
        this.speakerRepository = new YamlSpeakerRepository(this);
        this.speakerService = new SpeakerService(this, speakerRepository, villageIdentityService, getLogger());
'@
$content = $content -replace 'this.villageIdentityService.setVillageRepository\(new YamlVillageRepository\(this\)\);', "this.villageIdentityService.setVillageRepository(new YamlVillageRepository(this));$initInsert"

# 4. Fix ChiefService constructor: (keys, chiefRepository, villagerProfileRepository, ...) -> (keys, chiefRepository, villageIdentityService, chiefVisualService, speakerService, mourningService, getLogger())
$oldChiefInit = '        this.chiefService = new ChiefService(' + "`n" + '            keys,' + "`n" + '            chiefRepository,' + "`n" + '            villagerProfileRepository,' + "`n" + '            villageIdentityService,' + "`n" + '            chiefVisualService,' + "`n" + '            getLogger(),' + "`n" + '            dataLoader.loadChiefProfilesSection());'
$newChiefInit = '        this.chiefService = new ChiefService(' + "`n" + '            keys,' + "`n" + '            chiefRepository,' + "`n" + '            villageIdentityService,' + "`n" + '            chiefVisualService,' + "`n" + '            speakerService,' + "`n" + '            mourningService,' + "`n" + '            getLogger());'
$content = $content -replace [regex]::Escape($oldChiefInit), $newChiefInit

# 5. Fix VillagerInteractListener: chiefService -> speakerService
$content = $content -replace 'new VillagerInteractListener\(\s*chiefService,', 'new VillagerInteractListener(' + "`n" + '                speakerService,'

# 6. Fix QuestLifecycleListener: chiefService -> speakerService
$oldQLL = '            chiefService,' + "`n" + '            questService,' + "`n" + '            questUiService,' + "`n" + '            villagerProfileRepository'
$newQLL = '            speakerService,' + "`n" + '            questService,' + "`n" + '            questUiService,' + "`n" + '            villagerProfileRepository'
$content = $content -replace [regex]::Escape($oldQLL), $newQLL

# 7. Fix ConversationService constructor: needs speakerService and villageIdentityService
# Check current ConversationService signature
$oldConv = '                chiefRepository,' + "`n" + '                conversationHistoryRepository,' + "`n" + '                villagerContextService,'
$newConv = '                speakerService,' + "`n" + '                villageIdentityService,' + "`n" + '                chiefRepository,' + "`n" + '                conversationHistoryRepository,' + "`n" + '                villagerContextService,'
$content = $content -replace [regex]::Escape($oldConv), $newConv

# 8. Fix registerCommands: add speakerService to ChiefCommand constructor
$oldCmd = '        ChiefCommand executor = new ChiefCommand(' + "`n" + '            this,' + "`n" + '            chiefService,'
$newCmd = '        ChiefCommand executor = new ChiefCommand(' + "`n" + '            this,' + "`n" + '            chiefService,' + "`n" + '            speakerService,'
$content = $content -replace [regex]::Escape($oldCmd), $newCmd

# 9. Fix refreshLoadedVillagerProfiles: chiefService -> speakerService
$content = $content -replace 'chiefService.refreshLoadedVillagerProfiles\(villagers\)', 'speakerService.refreshLoadedVillagerProfiles(villagers)'

# 10. Fix reloadProfiles call on chiefService (keep as ChiefService still has that? Let's check)
# Actually chiefService might still have reloadProfiles - leave it for now

[System.IO.File]::WriteAllText($path, $content)
Write-Host 'Plugin init fixes applied.'"
