$file = "src\main\java\de\ajsch\villagerai\service\ConversationService.java"
$content = Get-Content $file -Raw -Encoding UTF8

# === 2.1 Update startConversation() call site ===
$oldStart = @'
        ConversationSession session = new ConversationSession(
                UUID.randomUUID(),
                speaker,
                villager.getUniqueId(),
                villager.hasAI(),
            villagerContextService.resolve(villager, player.getUniqueId()));
'@

$newStart = @'
        String defaultVisibility = plugin.getPluginDataLoader().conversationVisibilityDefaultMode();
        Set<UUID> participants = new HashSet<>();
        participants.add(player.getUniqueId());
        ConversationSession session = new ConversationSession(
                UUID.randomUUID(),
                speaker,
                villager.getUniqueId(),
                villager.hasAI(),
            villagerContextService.resolve(villager, player.getUniqueId()),
            defaultVisibility,
            participants);
'@

if ($content.Contains($oldStart)) {
    $content = $content.Replace($oldStart, $newStart)
    Write-Host "[OK] startConversation() updated"
} else {
    Write-Host "[FAIL] startConversation() old string not found"
}

# === 2.4 Update handlePlayerChat() - add player message broadcast ===
# Find the block after session null check and before handleConversationExitRequest
$oldPlayerChatBlock = @'
        if (handleConversationExitRequest(playerUuid, session, message)) {
            return;
        }

        if (session.awaitingReply().get()) {
            queuePlayerMessage(session, message);
            notifyPlayer(playerUuid, waitingMessage + " Deine letzte Nachricht wird direkt danach vorgemerkt.", NamedTextColor.GRAY);
            return;
        }
'@

$newPlayerChatBlock = @'
        // Broadcast player message based on visibility
        if ("PUBLIC".equalsIgnoreCase(session.visibility())) {
            Villager villager = resolveVillagerFromSession(session);
            if (villager != null) {
                String playerPrefix = plugin.getPluginDataLoader().getConversationPublicPlayerPrefix();
                Player onlinePlayer = Bukkit.getPlayer(playerUuid);
                if (onlinePlayer != null) {
                    Component playerMsg = Component.text("[" + onlinePlayer.getName() + "] ", NamedTextColor.GREEN)
                            .append(Component.text(playerPrefix + " ", NamedTextColor.WHITE))
                            .append(Component.text(message, NamedTextColor.WHITE));
                    broadcastToNearby(session, villager, playerMsg);
                }
            }
        } else {
            String whisperPrefix = plugin.getPluginDataLoader().getConversationWhisperPlayerPrefix();
            Player onlinePlayer = Bukkit.getPlayer(playerUuid);
            if (onlinePlayer != null) {
                onlinePlayer.sendMessage(Component.text("[Du] ", NamedTextColor.GREEN)
                        .append(Component.text(whisperPrefix + " ", NamedTextColor.GRAY))
                        .append(Component.text(message, NamedTextColor.GRAY)));
            }
        }

        if (handleConversationExitRequest(playerUuid, session, message)) {
            return;
        }

        if (session.awaitingReply().get()) {
            queuePlayerMessage(session, message);
            notifyPlayer(playerUuid, waitingMessage + " Deine letzte Nachricht wird direkt danach vorgemerkt.", NamedTextColor.GRAY);
            return;
        }
'@

if ($content.Contains($oldPlayerChatBlock)) {
    $content = $content.Replace($oldPlayerChatBlock, $newPlayerChatBlock)
    Write-Host "[OK] handlePlayerChat() updated"
} else {
    Write-Host "[FAIL] handlePlayerChat() old string not found"
    # Try to find where the block is
    $lines = $content -split "`n"
    for ($i=0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match "handleConversationExitRequest\(playerUuid, session, message\)") {
            Write-Host "  Found handleConversationExitRequest at line $($i+1)"
        }
    }
}

$content | Set-Content $file -Encoding UTF8 -NoNewline
Write-Host "Done."
