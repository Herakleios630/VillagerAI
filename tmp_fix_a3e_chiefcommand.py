"""Final fix for remaining 20 ChiefCommand errors."""
import pathlib

path = pathlib.Path("src/main/java/de/ajsch/villagerai/command/ChiefCommand.java")
c = path.read_text(encoding="utf-8")

# 1. Add Chief import if missing
if "import de.ajsch.villagerai.model.Chief;" not in c:
    c = c.replace(
        "import de.ajsch.villagerai.model.Speaker;",
        "import de.ajsch.villagerai.model.Chief;\nimport de.ajsch.villagerai.model.Speaker;"
    )

# 2. Fix line 198-200: speaker.speakerId() / villageId() after Chief chief declaration
# The previous script changed Chief chief back to Chief chief (markChief returns Chief).
# But the following lines still use Speaker methods which Chief also has. The issue is 
# that the variable is now named "chief", not "speaker". Rename usage.
c = c.replace(
    '                .append(Component.text(speaker.speakerId(), NamedTextColor.WHITE))\n                .append(Component.text(" von Dorf ", NamedTextColor.GRAY))\n                .append(Component.text(speaker.villageId(), NamedTextColor.WHITE)));',
    '                .append(Component.text(chief.speakerId(), NamedTextColor.WHITE))\n                .append(Component.text(" von Dorf ", NamedTextColor.GRAY))\n                .append(Component.text(chief.villageId(), NamedTextColor.WHITE)));'
)

# 3. Fix line 345: variable "speaker" not found (map foundChief -> map foundSpeaker)
# Ersetze im Methodenbody die Deklaration and Verwendung
def fix_speaker_in_handle_my_chief(content):
    """Fix 'speaker' variable in handleMyChief and handleDebugChief methods."""
    # Replace: speaker.map(foundChief -> foundSpeaker.xxx)
    # -> chief.map(foundChief -> foundChief.xxx) # wobei chief ist Chief type
    # Actually these are Optional<Chief> from chiefService.
    # The previous scripts mangled the variable names. Let's restore them.
    
    # Pattern: "var villageIdentity = speaker.<...>map(foundChief ->" 
    # Needs: "var villageIdentity = speaker.<...>map(foundChief ->"
    # where speaker is undefined. The original was "Optional<Chief> chief = ..."
    # Let's find the exact block and fix it.
    
    # Fix line 345
    content = content.replace(
        "        var villageIdentity = speaker.<de.ajsch.villagerai.model.VillageIdentity>map(foundChief ->",
        "        var villageIdentity = chief.<de.ajsch.villagerai.model.VillageIdentity>map(foundChief ->"
    )
    
    # Fix line 359
    content = content.replace(
        "            || speaker.map(foundChief -> foundSpeaker.speakerId().equals(activeQuest.speakerId())).orElse(false));",
        "            || chief.map(foundChief -> foundChief.speakerId().equals(activeQuest.speakerId())).orElse(false));"
    )
    
    # Fix lines 347-348: foundSpeaker -> foundChief
    content = content.replace('                    foundSpeaker.villageId(),', '                    foundChief.villageId(),')
    content = content.replace('                    foundSpeaker.villageName(),', '                    foundChief.villageName(),')
    
    return content

c = fix_speaker_in_handle_my_chief(c)

# 4. Fix lines 374, 378, 387, 394, 399, 408: speaker.map(Speaker::speakerId) -> chief.map(Chief::speakerId)
# These are in handleDebugChiefInfoShow method
c = c.replace('                speaker.map(Speaker::speakerId).orElseGet(() -> \nspeakerService.createOrRefreshProfile(villager).speakerId()));',
              '                chief.map(Chief::speakerId).orElseGet(() -> \nspeakerService.createOrRefreshProfile(villager).speakerId()));')
c = c.replace('                speaker.map(Speaker::speakerId).orElseGet(() -> \nspeakerService.createOrRefreshProfile(villager).speakerId()))',
              '                chief.map(Chief::speakerId).orElseGet(() -> \nspeakerService.createOrRefreshProfile(villager).speakerId()))')
c = c.replace('                        speaker.map(Speaker::speakerId).orElseGet(() ->',
              '                        chief.map(Chief::speakerId).orElseGet(() ->')
c = c.replace('                speaker.map(Speaker::speakerId).orElseGet(() ->',
              '                chief.map(Chief::speakerId).orElseGet(() ->')

# 5. Fix remaining "chief," in quest method calls (lines 627, 1087, 1164, 1216, 1282)
# These are ", chief," and should be replaced with ", chief," (but they are already chief!)
# Actually the build shows errors at lines 627, 1087, 1164, 1216, 1282 for variable chief
# That means the variable IS declared somewhere but not in scope, or the old chief variable was renamed.
# Let's just use a simple unique placeholder find-and-replace on each line's context.

# Read the file again to check exact current content at those lines
# We'll use a targeted approach: find the method name and replace within its scope

# For now, try global replace for the most common patterns that cause these:
# questService.activate* calls use ", speaker," (from earlier replace), but some still have ", chief,"
c = c.replace('activateDeliverQuest(player.getUniqueId(), chief,', 'activateDeliverQuest(player.getUniqueId(), speaker,')
c = c.replace('activateFetchQuest(player, chief,', 'activateFetchQuest(player, speaker,')
c = c.replace('activateBrewQuest(player.getUniqueId(), chief,', 'activateBrewQuest(player.getUniqueId(), speaker,')
c = c.replace('activateRepairQuest(player.getUniqueId(), chief,', 'activateRepairQuest(player.getUniqueId(), speaker,')
c = c.replace('activateBuildQuest(player.getUniqueId(), chief,', 'activateBuildQuest(player.getUniqueId(), speaker,')
c = c.replace('activateBreedQuest(player.getUniqueId(), chief,', 'activateBreedQuest(player.getUniqueId(), speaker,')
c = c.replace('activateKillQuest(player.getUniqueId(), chief,', 'activateKillQuest(player.getUniqueId(), speaker,')
c = c.replace('activateVisitQuest(player.getUniqueId(), chief,', 'activateVisitQuest(player.getUniqueId(), speaker,')
c = c.replace('activateSecureQuest(player.getUniqueId(), chief,', 'activateSecureQuest(player.getUniqueId(), speaker,')
c = c.replace('activateExploreQuest(player.getUniqueId(), chief,', 'activateExploreQuest(player.getUniqueId(), speaker,')
c = c.replace('acceptOffer(player, chief,', 'acceptOffer(player, speaker,')

# Special cases: ", chief," or "(chief," without specific method prefix
c = c.replace('                    chief, themeMaterial,', '                    speaker, themeMaterial,')
c = c.replace('                chief,', '                speaker,')

path.write_text(c, encoding='utf-8')
print("Final fix applied.")
