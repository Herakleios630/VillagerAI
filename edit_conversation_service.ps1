$file = "src\main\java\de\ajsch\villagerai\service\ConversationService.java"
$content = Get-Content $file -Raw

# 1. Update the compact constructor - add visibility and participants params
$oldConstructor = @'
        private ConversationSession(
                UUID conversationId,
                Speaker speaker,
                UUID villagerUuid,
                boolean hadAiEnabledBeforeConversation,
                VillagerContext villagerContext) {
            this(
                    conversationId,
                    speaker,
                    villagerUuid,
                    hadAiEnabledBeforeConversation,
                    villagerContext,
                    new AtomicBoolean(false),
                    new AtomicReference<>(),
                    new AtomicReference<>(),
                    new AtomicBoolean(false),
                    new AtomicLong(System.currentTimeMillis()));
        }
'@

$newConstructor = @'
        private ConversationSession(
                UUID conversationId,
                Speaker speaker,
                UUID villagerUuid,
                boolean hadAiEnabledBeforeConversation,
                VillagerContext villagerContext,
                String visibility,
                Set<UUID> participants) {
            this(
                    conversationId,
                    speaker,
                    villagerUuid,
                    hadAiEnabledBeforeConversation,
                    villagerContext,
                    new AtomicBoolean(false),
                    new AtomicReference<>(),
                    new AtomicReference<>(),
                    new AtomicBoolean(false),
                    new AtomicLong(System.currentTimeMillis()),
                    visibility,
                    participants);
        }
'@

$content = $content.Replace($oldConstructor, $newConstructor)

# 2. Replace sendChiefMessage method
$oldSendChief = @'
    private void sendChiefMessage(Player player, ConversationSession session, String replyText, NamedTextColor color) {
        player.sendMessage(Component.text("[" + session.speaker().chatName() + "] ", NamedTextColor.GOLD)
                .append(Component.text(replyText, color)));
    }
'@

$newSendChief = @'
    private void sendChiefMessage(Player player, ConversationSession session, String replyText, NamedTextColor color) {
        boolean isPublic = "PUBLIC".equalsIgnoreCase(session.visibility());
        String prefix = isPublic
            ? plugin.getPluginDataLoader().getConversationPublicChiefPrefix()
            : plugin.getPluginDataLoader().getConversationWhisperChiefPrefix();

        Component message = Component.text("[" + session.speaker().chatName() + "] ", NamedTextColor.GOLD)
                .append(Component.text(prefix + " ", isPublic ? NamedTextColor.WHITE : NamedTextColor.GRAY))
                .append(Component.text(replyText, color));

        if (isPublic) {
            Villager villager = resolveVillagerFromSession(session);
            if (villager != null) {
                broadcastToNearby(session, villager, message);
            } else {
                player.sendMessage(message);
            }
        } else {
            player.sendMessage(message);
        }

        spawnConversationParticles(session);
    }
'@

$content = $content.Replace($oldSendChief, $newSendChief)

# 3. Insert new methods (broadcastToNearby, resolveVillagerFromSession, spawnConversationParticles)
# Insert before the closing brace of the outer class (before the last "}")
$newMethods = @'

    private void broadcastToNearby(ConversationSession session, Villager villager, Component message) {
        double radius = plugin.getPluginDataLoader().getConversationPublicRadiusBlocks();
        Location loc = villager.getLocation();
        if (loc.getWorld() == null) return;
        for (Player nearby : loc.getWorld().getNearbyPlayers(loc, radius)) {
            if (session.participants().contains(nearby.getUniqueId())) {
                nearby.sendMessage(message);
            }
        }
    }

    private void spawnConversationParticles(ConversationSession session) {
        if (!plugin.getPluginDataLoader().getConversationParticlesEnabled()) return;

        Villager villager = resolveVillagerFromSession(session);
        if (villager == null || !villager.isValid()) return;

        boolean isPublic = "PUBLIC".equalsIgnoreCase(session.visibility());
        String particleName = isPublic
            ? plugin.getPluginDataLoader().getConversationPublicParticle()
            : plugin.getPluginDataLoader().getConversationWhisperParticle();

        Particle particle;
        try {
            particle = Particle.valueOf(particleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unbekannter Particle-Typ in Config: " + particleName);
            return;
        }

        Location loc = villager.getEyeLocation().add(0, 0.4, 0);
        int count = plugin.getPluginDataLoader().getConversationParticleCount();
        villager.getWorld().spawnParticle(particle, loc, count, 0.2, 0.2, 0.2, 0.02);
    }

    private Villager resolveVillagerFromSession(ConversationSession session) {
        String speakerId = session.speaker().speakerId();
        if (speakerId.startsWith("villager-")) {
            try {
                UUID entityUuid = UUID.fromString(speakerId.substring("villager-".length()));
                org.bukkit.entity.Entity entity = Bukkit.getEntity(entityUuid);
                if (entity instanceof Villager v && v.isValid()) {
                    return v;
                }
            } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }

'@

# Insert new methods before the RuntimeSettings record (which is before last })
$insertBefore = @'

        public record RuntimeSettings(
'@
$content = $content.Replace($insertBefore, $newMethods + $insertBefore)

$content | Set-Content $file -Encoding UTF8
Write-Host "Edits applied successfully."