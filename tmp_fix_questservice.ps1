$path = 'src/main/java/de/ajsch/villagerai/service/QuestService.java'
$content = [System.IO.File]::ReadAllText($path)

# Replace `Chief chief` parameter type with `Speaker speaker` in method signatures
# But NOT the class name ChiefService, ChiefRepository etc.
$content = $content -replace 'Chief chief\)', 'Speaker speaker)'
$content = $content -replace 'Chief chief,', 'Speaker speaker,'
$content = $content -replace 'Chief chief\.', 'Speaker speaker.'

# Fix chief.chatName() -> speaker.displayName() inside QuestService
$content = $content -replace 'chief\.chatName\(\)', 'speaker.displayName()'

# Fix chief.chiefId() -> speaker.speakerId() (already done via bulk rename but check)
$content = $content -replace 'chief\.chiefId\(\)', 'speaker.speakerId()'

# Fix chief.villageId() etc - these should be fine as Speaker has same getters

[System.IO.File]::WriteAllText($path, $content)
Write-Host 'QuestService Chief->Speaker migration done.'"
