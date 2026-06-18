$ErrorActionPreference = "Stop"
Set-Location "C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI"

function Replace-InFile($file, $old, $new) {
    $content = [IO.File]::ReadAllText($file)
    $content = $content.Replace($old, $new)
    [IO.File]::WriteAllText($file, $content)
    Write-Output "  $file : $old -> $new"
}

Write-Output "=== 1/4 ConversationService.java ==="
$f = "src\main\java\de\ajsch\villagerai\service\ConversationService.java"
Replace-InFile $f "import de.ajsch.villagerai.model.Chief;" "import de.ajsch.villagerai.model.Speaker;"
Replace-InFile $f "request.chiefId()" "request.speakerId()"
Replace-InFile $f "request.chiefName()" "request.displayName()"
Replace-InFile $f "request.chiefRole()" "request.role()"
Replace-InFile $f "request.chiefPersonality()" "request.personality()"
Replace-InFile $f "request.chiefTone()" "request.speechTone()"
Replace-InFile $f "request.chiefBehaviorHint()" "request.behaviorHint()"
Replace-InFile $f "request.chiefGreeting()" "request.greeting()"

Write-Output "=== 2/4 ChiefCommand.java ==="
$f = "src\main\java\de\ajsch\villagerai\command\ChiefCommand.java"
Replace-InFile $f "snapshot.speakerId()" "snapshot.chiefId()"

Write-Output "=== 3/4 VillagerDebugOverlayService.java ==="
$f = "src\main\java\de\ajsch\villagerai\service\VillagerDebugOverlayService.java"
Replace-InFile $f "snapshot.speakerId()" "snapshot.chiefId()"

Write-Output "=== 4/4 VillageChiefPlugin.java ==="
$f = "src\main\java\de\ajsch\villagerai\VillageChiefPlugin.java"
Replace-InFile $f "conversationSettings.npcBusyMessage()" "conversationSettings.chiefBusyMessage()"

Write-Output "Done."