$root = "C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI"

# ===== FIX 1: PluginDataLoader.java - Duplikate entfernen =====
$pdlFile = "$root\src\main\java\de\ajsch\villagerai\config\PluginDataLoader.java"
$pdlContent = Get-Content $pdlFile -Raw -Encoding UTF8

# Finde das zweite Vorkommen der Duplikate (nach debugVillageLightParticleMarker)
# Die Duplikate stehen zwischen debugVillageLightParticleMarker() und dem ConfinementSettings-Record
$duplicateBlock = @'
    public String conversationVisibilityDefaultMode() {
        return plugin.getConfig().getString("conversation.visibility.default-mode", "PUBLIC");
    }

    public double getConversationPublicRadiusBlocks() {
        return plugin.getConfig().getDouble("conversation.visibility.public-radius-blocks", 50);
    }

    public String getConversationPublicPlayerPrefix() {
        return plugin.getConfig().getString("conversation.visibility.public-player-prefix", "sagt");
    }

    public String getConversationWhisperPlayerPrefix() {
        return plugin.getConfig().getString("conversation.visibility.whisper-player-prefix", "flüsterst");
    }

    public String getConversationPublicChiefPrefix() {
        return plugin.getConfig().getString("conversation.visibility.public-chief-prefix", "sagt");
    }

    public String getConversationWhisperChiefPrefix() {
        return plugin.getConfig().getString("conversation.visibility.whisper-chief-prefix", "flüstert");
    }

    public boolean getConversationParticlesEnabled() {
        return plugin.getConfig().getBoolean("conversation.visibility.particles.enabled", true);
    }

    public String getConversationPublicParticle() {
        return plugin.getConfig().getString("conversation.visibility.particles.public-particle", "VILLAGER_HAPPY");
    }

    public String getConversationWhisperParticle() {
        return plugin.getConfig().getString("conversation.visibility.particles.whisper-particle", "SOUL");
    }

    public int getConversationParticleCount() {
        return plugin.getConfig().getInt("conversation.visibility.particles.particle-count", 4);
    }
'@

if ($pdlContent.Contains($duplicateBlock)) {
    # Entferne den Duplikat-Block
    $pdlContent = $pdlContent.Replace($duplicateBlock, "")
    # Bereinige doppelte Leerzeilen
    while ($pdlContent.Contains("`n`n`n")) {
        $pdlContent = $pdlContent.Replace("`n`n`n", "`n`n")
    }
    $pdlContent | Set-Content $pdlFile -Encoding UTF8 -NoNewline
    Write-Host "[OK] PluginDataLoader.java - Duplikate entfernt"
} else {
    Write-Host "[WARN] PluginDataLoader.java - Duplikatblock nicht gefunden (vielleicht schon entfernt?)"
}

# ===== FIX 2: ConversationService.java - plugin.getPluginDataLoader() ersetzen =====
$csFile = "$root\src\main\java\de\ajsch\villagerai\service\ConversationService.java"
$csContent = Get-Content $csFile -Raw -Encoding UTF8

# Alle plugin.getPluginDataLoader().XXX() durch plugin.getConfig().YYY ersetzen
$replacements = @(
    @{
        old = 'plugin.getPluginDataLoader().conversationVisibilityDefaultMode()'
        new = 'plugin.getConfig().getString("conversation.visibility.default-mode", "PUBLIC")'
    },
    @{
        old = 'plugin.getPluginDataLoader().getConversationPublicRadiusBlocks()'
        new = 'plugin.getConfig().getDouble("conversation.visibility.public-radius-blocks", 50)'
    },
    @{
        old = 'plugin.getPluginDataLoader().getConversationPublicPlayerPrefix()'
        new = 'plugin.getConfig().getString("conversation.visibility.public-player-prefix", "sagt")'
    },
    @{
        old = 'plugin.getPluginDataLoader().getConversationWhisperPlayerPrefix()'
        new = 'plugin.getConfig().getString("conversation.visibility.whisper-player-prefix", "flüsterst")'
    },
    @{
        old = 'plugin.getPluginDataLoader().getConversationPublicChiefPrefix()'
        new = 'plugin.getConfig().getString("conversation.visibility.public-chief-prefix", "sagt")'
    },
    @{
        old = 'plugin.getPluginDataLoader().getConversationWhisperChiefPrefix()'
        new = 'plugin.getConfig().getString("conversation.visibility.whisper-chief-prefix", "flüstert")'
    },
    @{
        old = 'plugin.getPluginDataLoader().getConversationParticlesEnabled()'
        new = 'plugin.getConfig().getBoolean("conversation.visibility.particles.enabled", true)'
    },
    @{
        old = 'plugin.getPluginDataLoader().getConversationPublicParticle()'
        new = 'plugin.getConfig().getString("conversation.visibility.particles.public-particle", "VILLAGER_HAPPY")'
    },
    @{
        old = 'plugin.getPluginDataLoader().getConversationWhisperParticle()'
        new = 'plugin.getConfig().getString("conversation.visibility.particles.whisper-particle", "SOUL")'
    },
    @{
        old = 'plugin.getPluginDataLoader().getConversationParticleCount()'
        new = 'plugin.getConfig().getInt("conversation.visibility.particles.particle-count", 4)'
    }
)

$changed = $false
foreach ($r in $replacements) {
    if ($csContent.Contains($r.old)) {
        $csContent = $csContent.Replace($r.old, $r.new)
        $changed = $true
        Write-Host "[OK] Ersetzt: $($r.old)"
    } else {
        Write-Host "[SKIP] Nicht gefunden: $($r.old)"
    }
}

if ($changed) {
    $csContent | Set-Content $csFile -Encoding UTF8 -NoNewline
    Write-Host "[OK] ConversationService.java aktualisiert"
}

Write-Host "Fertig."
