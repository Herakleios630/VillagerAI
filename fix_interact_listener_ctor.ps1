$file = 'C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI\src\main\java\de\ajsch\villagerai\VillageChiefPlugin.java'
$lines = [System.IO.File]::ReadAllLines($file, [System.Text.Encoding]::UTF8)

# Find the line index (0-based) of the VillagerInteractListener constructor call in registerListeners()
$targetLine = -1
for ($i = 0; $i -lt $lines.Length; $i++) {
    if ($lines[$i] -match "new VillagerInteractListener\(" -and $i -gt 300) {
        $targetLine = $i
        break
    }
}

if ($targetLine -eq -1) {
    Write-Host "FAIL: target line not found after line 300"
    exit 1
}

Write-Host "Found constructor at 0-based line $targetLine"
Write-Host "Current lines:"
for ($j = $targetLine; $j -lt $targetLine + 6; $j++) {
    Write-Host "  $($lines[$j])"
}

# Replace the 5-line block: line 394-398 needs additional params
# Line 0 (targetLine):     pluginManager.registerEvents(new VillagerInteractListener(
# Line 1:                     speakerService,
# Line 2:                     conversationService,
# Line 3:                     getConfig().getBoolean("interaction.allow-regular-villager-conversations", true),
# Line 4:                     getConfig().getBoolean("interaction.regular-villager-conversations-require-sneak", true)), this);

$indent = "                "
$lines[$targetLine + 0] = $lines[$targetLine + 0]  # unchanged: registerEvents line
$lines[$targetLine + 1] = $lines[$targetLine + 1]  # unchanged: speakerService
$lines[$targetLine + 2] = $lines[$targetLine + 2]  # unchanged: conversationService
# Insert 3 new lines after line 2
$newArray = [System.Collections.Generic.List[string]]::new()
for ($i = 0; $i -le $targetLine + 2; $i++) {
    $newArray.Add($lines[$i])
}
# Add the 3 new dependencies
$newArray.Add($indent + 'chiefService,')
$newArray.Add($indent + 'reputationService,')
$newArray.Add($indent + 'villageIdentityService,')
# Continue with the remaining original lines
$newArray.Add($lines[$targetLine + 3])  # getConfig line 1
$newArray.Add($lines[$targetLine + 4])  # getConfig line 2 + )), this);
for ($i = $targetLine + 5; $i -lt $lines.Length; $i++) {
    $newArray.Add($lines[$i])
}

[System.IO.File]::WriteAllLines($file, $newArray, [System.Text.Encoding]::UTF8)
Write-Host "SUCCESS: VillageChiefPlugin.java updated."