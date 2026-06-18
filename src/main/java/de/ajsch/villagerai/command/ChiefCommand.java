package de.ajsch.villagerai.command;

import de.ajsch.villagerai.VillageChiefPlugin;
import de.ajsch.villagerai.model.Speaker;
import de.ajsch.villagerai.model.Quest;
import de.ajsch.villagerai.model.SpeakerReputation;
import de.ajsch.villagerai.model.VillagerContext;
import de.ajsch.villagerai.model.VillageReputation;
import de.ajsch.villagerai.service.ChiefService;
import de.ajsch.villagerai.service.SpeakerService;
import de.ajsch.villagerai.service.ConversationService;
import de.ajsch.villagerai.service.MourningService;
import de.ajsch.villagerai.service.QuestDifficultyService;
import de.ajsch.villagerai.service.QuestOfferService;
import de.ajsch.villagerai.service.QuestService;
import de.ajsch.villagerai.service.QuestUiService;
import de.ajsch.villagerai.service.ReputationService;
import de.ajsch.villagerai.service.VillageIdentityService;
import de.ajsch.villagerai.service.VillagerContextService;
import de.ajsch.villagerai.service.VillagePerimeterDisplayService;
import de.ajsch.villagerai.service.VillagerDebugOverlayService;
import de.ajsch.villagerai.util.EntityTargetingUtil;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.EntityType;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.potion.PotionType;

public final class ChiefCommand implements TabExecutor {

    private static final int MIN_REPUTATION_SCORE = -100;
    private static final int MAX_REPUTATION_SCORE = 100;

    private final VillageChiefPlugin plugin;
    private final ChiefService chiefService;
    private final SpeakerService speakerService;
    private final ConversationService conversationService;
    private final QuestOfferService questOfferService;
    private final QuestService questService;
    private final QuestDifficultyService questDifficultyService;
    private final QuestUiService questUiService;
    private final ReputationService reputationService;
    private final MourningService mourningService;
    private final VillageIdentityService villageIdentityService;
    private final VillagerContextService villagerContextService;
    private final VillagePerimeterDisplayService villagePerimeterDisplayService;
    private final VillagerDebugOverlayService villagerDebugOverlayService;

    public ChiefCommand(
            VillageChiefPlugin plugin,
            ChiefService chiefService,
            SpeakerService speakerService,
            ConversationService conversationService,
            QuestService questService,
            QuestOfferService questOfferService,
            QuestDifficultyService questDifficultyService,
            QuestUiService questUiService,
            ReputationService reputationService,
            MourningService mourningService,
            VillageIdentityService villageIdentityService,
            VillagerContextService villagerContextService,
            VillagePerimeterDisplayService villagePerimeterDisplayService,
            VillagerDebugOverlayService villagerDebugOverlayService) {
        this.plugin = plugin;
        this.chiefService = chiefService;
        this.speakerService = speakerService;
        this.conversationService = conversationService;
        this.questService = questService;
        this.questOfferService = questOfferService;
        this.questDifficultyService = questDifficultyService;
        this.questUiService = questUiService;
        this.reputationService = reputationService;
        this.mourningService = mourningService;
        this.villageIdentityService = villageIdentityService;
        this.villagerContextService = villagerContextService;
        this.villagePerimeterDisplayService = villagePerimeterDisplayService;
        this.villagerDebugOverlayService = villagerDebugOverlayService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Handle standalone /whisper and /w commands
        if ("whisper".equalsIgnoreCase(label) || "w".equalsIgnoreCase(label)) {
            return handleWhisper(sender, args);
        }

        if (args.length == 0) {
            sender.sendMessage(Component.text("Verwendung: /chief <set|unset|info|exit|debug|quest|perimeter|reload|forget|whisper>", NamedTextColor.YELLOW));
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set" -> handleSet(sender, args);
            case "unset" -> handleUnset(sender);
            case "info" -> handleInfo(sender);
            case "exit" -> handleExit(sender);
            case "debug" -> handleDebug(sender, args);
            case "quest" -> handleQuest(sender, args);
            case "perimeter" -> handlePerimeter(sender);
            case "reload" -> handleReload(sender);
            case "forget" -> handleForget(sender);
            case "whisper" -> handleWhisper(sender, args);
            default -> {
                sender.sendMessage(Component.text("Unbekannter Subcommand.", NamedTextColor.RED));
                yield true;
            }
        };
    }

    private boolean handleReload(CommandSender sender) {
        try {
            for (String line : plugin.reloadRuntimeConfiguration(sender)) {
                sender.sendMessage(Component.text(line, NamedTextColor.GREEN));
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Konfigurations-Reload fehlgeschlagen: " + exception.getMessage());
            sender.sendMessage(Component.text("Reload fehlgeschlagen. Details stehen im Server-Log.", NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleForget(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (!plugin.getConfig().getBoolean("memory.enabled", false)) {
            player.sendMessage(Component.text("Das Memory-System ist deaktiviert.", NamedTextColor.RED));
            return true;
        }

        String bridgeBaseUrl = plugin.getConfig().getString("ai.http.endpoint", "http://127.0.0.1:8080/v1/chief/reply");
        URI replyUri = URI.create(bridgeBaseUrl);
        String forgetUrl = replyUri.getScheme() + "://" + replyUri.getHost() + ":" + replyUri.getPort()
                + "/v1/chief/forget?player_uuid=" + player.getUniqueId().toString();

        HttpClient client = HttpClient.newHttpClient();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(forgetUrl))
                    .timeout(Duration.ofSeconds(10))
                    .DELETE()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                player.sendMessage(Component.text("Deine Gespraechshistorie wurde geloescht.", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("Die Bridge hat mit Status " + response.statusCode() + " geantwortet.", NamedTextColor.YELLOW));
            }
        } catch (Exception exception) {
            player.sendMessage(Component.text("Die Bridge ist nicht erreichbar. Historie konnte nicht geloescht werden.", NamedTextColor.RED));
            plugin.getLogger().warning("[ChiefCommand] /chief forget failed: " + exception.getMessage());
        }
        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        Villager villager = EntityTargetingUtil.findTargetedVillager(player, plugin.getTargetRangeBlocks());
        if (villager == null) {
            sender.sendMessage(Component.text("Du musst einen Villager ansehen.", NamedTextColor.RED));
            return true;
        }

        // villageId vorab ermitteln, um Trauer-Prüfung VOR markChief zu ermöglichen
        String villageId = args.length >= 2 ? args[1] : null;
        if (villageId == null || villageId.isBlank()) {
            try {
                villageId = villageIdentityService.resolve(villager).villageId();
            } catch (Exception e) {
                villageId = "unknown";
            }
        }

        // Edge Case: Trauer vorzeitig beenden, wenn Admin /chief set während Trauerphase
        if (mourningService.isVillageInMourning(villageId)) {
            mourningService.cancelMourning(villageId);
            sender.sendMessage(Component.text("Trauerphase für Dorf " + villageId + " vorzeitig beendet (Admin-Override).", NamedTextColor.YELLOW));
        }

                        Speaker speaker = (args.length >= 2 && !args[1].isBlank())
                ? chiefService.markChief(villager, args[1])
                : chiefService.markChief(villager);

        sender.sendMessage(Component.text("Chief gesetzt: ", NamedTextColor.GREEN)
                .append(Component.text(speaker.speakerId(), NamedTextColor.WHITE))
                .append(Component.text(" in Dorf ", NamedTextColor.GREEN))
                .append(Component.text(speaker.villageId(), NamedTextColor.WHITE)));
        return true;
    }

    private boolean handleUnset(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        Villager villager = EntityTargetingUtil.findTargetedVillager(player, plugin.getTargetRangeBlocks());
        if (villager == null) {
            sender.sendMessage(Component.text("Du musst einen Villager ansehen.", NamedTextColor.RED));
            return true;
        }

        if (!chiefService.unmarkChief(villager)) {
            sender.sendMessage(Component.text("Dieser Villager ist kein Chief.", NamedTextColor.RED));
            return true;
        }

        // Trauerphase starten
        Speaker speaker = speakerService.getSpeaker(villager).orElse(null);
        if (speaker != null) {
            try {
                mourningService.beginMourning(speaker.villageId(), speaker.speakerId());
            } catch (RuntimeException e) {
                plugin.getLogger().warning("[ChiefCommand] Mourning-Partikel konnten nicht gestartet werden: " + e.getMessage());
            }
        }

        sender.sendMessage(Component.text("Chief-Markierung entfernt.", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleInfo(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        // 1) Target-Pfad: Spieler schaut ein Villager an
        Villager villager = EntityTargetingUtil.findTargetedVillager(player, plugin.getTargetRangeBlocks());
        if (villager != null) {
            Optional<Speaker> optionalSpeaker = speakerService.getSpeaker(villager);
            if (optionalSpeaker.isEmpty()) {
                sender.sendMessage(Component.text("Dieser Villager ist kein Chief.", NamedTextColor.RED));
                return true;
            }

            Speaker speaker = optionalSpeaker.get();
            sender.sendMessage(Component.text("Chief-Info", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("Chief-ID: " + speaker.speakerId(), NamedTextColor.WHITE));
            sender.sendMessage(Component.text("Village-ID: " + speaker.villageId(), NamedTextColor.WHITE));
            sender.sendMessage(Component.text("Village-Name: " + speaker.villageName(), NamedTextColor.WHITE));
            sender.sendMessage(Component.text("Name: " + speaker.displayName(), NamedTextColor.WHITE));
            sender.sendMessage(Component.text("Rolle: " + speaker.role(), NamedTextColor.WHITE));
            sender.sendMessage(Component.text("Persoenlichkeit: " + speaker.personality(), NamedTextColor.WHITE));
            sender.sendMessage(Component.text("Begruessung: " + speaker.greeting(), NamedTextColor.WHITE));
            sender.sendMessage(Component.text("Entity-UUID: " + speaker.entityUuid(), NamedTextColor.WHITE));
            sender.sendMessage(Component.text(
                    "Position: " + speaker.world() + " " + (int) speaker.x() + ", " + (int) speaker.y() + ", " + (int) speaker.z(),
                    NamedTextColor.WHITE));
            return true;
        }

        // 2) Spieler-Pfad: kein Target → aktuelle villageId des Spielers ermitteln
        Optional<String> villageId = villageIdentityService.resolveVillageIdFromPlayer(player);
        if (villageId.isEmpty()) {
            sender.sendMessage(Component.text("Du befindest dich in keinem Dorf.", NamedTextColor.RED));
            return true;
        }

        Optional<Speaker> optionalSpeaker = speakerService.findActiveChiefByVillageId(villageId.get());
        if (optionalSpeaker.isEmpty()) {
            sender.sendMessage(Component.text("Dieses Dorf hat derzeit keinen Haeuptling.", NamedTextColor.YELLOW));
            return true;
        }

        Speaker speaker = optionalSpeaker.get();
        sender.sendMessage(Component.text("Haeuptling deines Dorfes", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Name: " + speaker.displayName(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Dorf: " + speaker.villageName() + " (" + speaker.villageId() + ")", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Rolle: " + speaker.role(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Persoenlichkeit: " + speaker.personality(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text(
                "Position: " + speaker.world() + " " + (int) speaker.x() + ", " + (int) speaker.y() + ", " + (int) speaker.z(),
                NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Gekroent seit: " + formatTimeAgo(0L /* TODO: get from chiefRepository */), NamedTextColor.WHITE));
        return true;
    }

    /** Formatiert einen Unix-Timestamp als menschenlesbare „vor X Tagen"-Angabe. */
    private static String formatTimeAgo(long crownedAt) {
        if (crownedAt <= 0L) {
            return "unbekannt";
        }
        long diffMs = System.currentTimeMillis() - crownedAt;
        long days = diffMs / (1000L * 60L * 60L * 24L);
        if (days <= 0) {
            return "heute";
        }
        if (days == 1) {
            return "vor 1 Tag";
        }
        return "vor " + days + " Tagen";
    }

    private boolean handleExit(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (!conversationService.endConversation(player.getUniqueId())) {
            sender.sendMessage(Component.text("Du fuehrst gerade kein Gespraech.", NamedTextColor.RED));
            return true;
        }

        return true;
    }

    private boolean handleWhisper(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        ConversationService.ConversationSnapshot snapshot = conversationService
                .getConversation(player.getUniqueId()).orElse(null);

        if (snapshot == null) {
            player.sendMessage(Component.text("Du fuehrst kein aktives Gespraech.", NamedTextColor.RED));
            player.sendActionBar(Component.text("Kein aktives Gespraech – /whisper ist nur waehrend einer Konversation moeglich", NamedTextColor.RED));
            return true;
        }

        String currentVisibility = snapshot.visibility();
        String newVisibility;

        if (args.length >= 1 && args[0].equalsIgnoreCase("on")) {
            newVisibility = "WHISPER";
        } else if (args.length >= 1 && args[0].equalsIgnoreCase("off")) {
            newVisibility = "PUBLIC";
        } else {
            newVisibility = "WHISPER".equalsIgnoreCase(currentVisibility) ? "PUBLIC" : "WHISPER";
        }

        boolean success = conversationService.setVisibility(player.getUniqueId(), newVisibility);
        if (!success) {
            player.sendMessage(Component.text("Fehler beim Umschalten des Modus.", NamedTextColor.RED));
            return true;
        }

        if ("PUBLIC".equalsIgnoreCase(newVisibility)) {
            player.sendActionBar(Component.text("Oeffentlicher Modus – andere koennen zuhoeren", NamedTextColor.GREEN));
            player.sendMessage(Component.text("Du sprichst jetzt oeffentlich. Andere im Umkreis koennen zuhoeren.", NamedTextColor.GREEN));
        } else {
            player.sendActionBar(Component.text("Fluester-Modus – nur du hoerst das Gespraech", NamedTextColor.GRAY));
            player.sendMessage(Component.text("Du fluesterst jetzt. Nur du hoerst das Gespraech.", NamedTextColor.GRAY));
        }

        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (args.length >= 2 && "chat".equalsIgnoreCase(args[1])) {
            return handleDebugChat(player, args);
        }
        if (args.length >= 2 && "watch".equalsIgnoreCase(args[1])) {
            return handleDebugWatch(player);
        }
        if (args.length >= 2 && "set".equalsIgnoreCase(args[1])) {
            return handleDebugSet(player, args);
        }

        Villager villager = EntityTargetingUtil.findTargetedVillager(player, plugin.getTargetRangeBlocks());
        if (villager == null) {
            sender.sendMessage(Component.text("Kein Villager im Fokus.", NamedTextColor.RED));
            return true;
        }

        var optionalSpeaker = speakerService.getSpeaker(villager);
        var villageIdentity = optionalSpeaker.<de.ajsch.villagerai.model.VillageIdentity>map(foundSpeaker ->
            new de.ajsch.villagerai.model.VillageIdentity(
                    foundSpeaker.villageId(),
                    foundSpeaker.villageName(),
                    villageIdentityService.resolve(villager).villageDescription(),
                    villageIdentityService.resolve(villager).villageAttributes(),
                    villageIdentityService.resolve(villager).villageBiome(),
                    villageIdentityService.resolve(villager).villagePopulationEstimate(),
                    villageIdentityService.resolve(villager).villageEventSummary()))
            .orElseGet(() -> villageIdentityService.resolve(villager));
        VillagerContext villagerContext = villagerContextService.resolve(villager, player.getUniqueId());
        Quest activeQuest = questService.findActiveQuest(player.getUniqueId()).orElse(null);
        boolean questMatchesTarget = activeQuest != null
            && (activeQuest.villageId().equals(villageIdentity.villageId())
            || optionalSpeaker.map(foundSpeaker -> foundSpeaker.speakerId().equals(activeQuest.speakerId())).orElse(false));

        sender.sendMessage(Component.text("Debug fuer Villager " + villager.getUniqueId(), NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Chief aktiv: " + speakerService.getSpeaker(villager).map(Speaker::isChief).orElse(false), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Beruf: " + villager.getProfession().name(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Village-ID: " + villageIdentity.villageId(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Village-Name: " + villageIdentity.villageName(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Dorfbeschreibung: " + villageIdentity.villageDescription(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Dorfmerkmale: " + villageIdentity.villageAttributes(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Dorfbiom: " + villageIdentity.villageBiome(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Geschaetzte Bewohnerzahl: " + villageIdentity.villagePopulationEstimate(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Wichtiges Dorfereignis: " + villageIdentity.villageEventSummary(), NamedTextColor.WHITE));
        int villageReputationScore = reputationService.getVillageScore(player.getUniqueId(), villageIdentity.villageId());
        int speakerReputationScore = reputationService.getSpeakerScore(
                player.getUniqueId(),
                optionalSpeaker.map(Speaker::speakerId).orElseGet(() -> speakerService.createOrRefreshProfile(villager).speakerId()));
        int combinedReputationScore = reputationService.getCombinedScore(
                player.getUniqueId(),
                villageIdentity.villageId(),
                optionalSpeaker.map(Speaker::speakerId).orElseGet(() -> speakerService.createOrRefreshProfile(villager).speakerId()));
        sender.sendMessage(Component.text(
            "Dorfruf: " + villageReputationScore
                + " (" + reputationService.getVillageSummary(player.getUniqueId(), villageIdentity.villageId()) + ")",
            NamedTextColor.WHITE));
        sender.sendMessage(Component.text(
            "Villager-Ruf: " + speakerReputationScore
                + " (" + reputationService.getSpeakerSummary(
                        player.getUniqueId(),
                        optionalSpeaker.map(Speaker::speakerId).orElseGet(() -> speakerService.createOrRefreshProfile(villager).speakerId())) + ")",
            NamedTextColor.WHITE));
        sender.sendMessage(Component.text(
            "Kombiniert: " + combinedReputationScore
                + " (" + reputationService.getCombinedSummary(
                        player.getUniqueId(),
                        villageIdentity.villageId(),
                        optionalSpeaker.map(Speaker::speakerId).orElseGet(() -> speakerService.createOrRefreshProfile(villager).speakerId())) + ")",
            NamedTextColor.WHITE));
        int unlockedDifficultyTier = questDifficultyService.resolveUnlockedTier(villageReputationScore);
        int preferredDifficultyTier = questDifficultyService.getPreference(
                player.getUniqueId(),
                optionalSpeaker.map(Speaker::speakerId).orElseGet(() -> speakerService.createOrRefreshProfile(villager).speakerId()))
            .preferredDifficultyTier();
        sender.sendMessage(Component.text(
            "Quest-Schwierigkeit: bevorzugt " + questDifficultyService.describeTier(preferredDifficultyTier)
                + " | freigeschaltet " + questDifficultyService.describeTier(unlockedDifficultyTier),
            NamedTextColor.WHITE));
        sender.sendMessage(Component.text(
            "Legendary-Status: " + conversationService.describeLegendaryBlocker(
                player,
                optionalSpeaker.map(Speaker::speakerId).orElseGet(() -> speakerService.createOrRefreshProfile(villager).speakerId()),
                villageReputationScore,
                speakerReputationScore),
            NamedTextColor.WHITE));
        sender.sendMessage(Component.text(
            "Gesundheit: " + Math.round(villagerContext.currentHealth()) + "/" + Math.round(villagerContext.maxHealth())
                + " | gegessen: " + villagerContext.ateRecently(),
            NamedTextColor.WHITE));
        sender.sendMessage(Component.text(
            "Welt/Biom: " + villagerContext.worldName() + " | " + villagerContext.currentBiome()
                + " | Tag: " + villagerContext.isDay() + " | Regen: " + villagerContext.isRaining()
                + " | Gewitter: " + villagerContext.isThundering(),
            NamedTextColor.WHITE));
        sender.sendMessage(Component.text(
            "Trade-Summary: " + nullToPlaceholder(villagerContext.tradeSummary()),
            NamedTextColor.WHITE));
        sender.sendMessage(Component.text(
            "Confinement: " + nullToPlaceholder(villagerContext.confinementSummary()),
            NamedTextColor.WHITE));
        sender.sendMessage(Component.text(
            "POIs: Home=" + nullToPlaceholder(villagerContext.homePoi())
                + " | Job=" + nullToPlaceholder(villagerContext.jobSitePoi())
                + " | Job?=" + nullToPlaceholder(villagerContext.potentialJobSitePoi())
                + " | Meeting=" + nullToPlaceholder(villagerContext.meetingPointPoi()),
            NamedTextColor.WHITE));
        if (activeQuest == null) {
            sender.sendMessage(Component.text("Aktive Quest: keine", NamedTextColor.WHITE));
        } else {
            sender.sendMessage(Component.text(
                "Aktive Quest: " + activeQuest.type() + " | " + activeQuest.status() + " | "
                    + activeQuest.progress() + "/" + activeQuest.goal(),
                NamedTextColor.WHITE));
            sender.sendMessage(Component.text(
                "Quest passt zu diesem Villager/Dorf: " + questMatchesTarget,
                NamedTextColor.WHITE));
        }
        conversationService.getConversation(player.getUniqueId()).ifPresentOrElse(snapshot -> {
            sender.sendMessage(Component.text("Aktives Gespraech: ja", NamedTextColor.WHITE));
            sender.sendMessage(Component.text("Chief-ID: " + snapshot.chiefId(), NamedTextColor.WHITE));
            sender.sendMessage(Component.text("Village-ID: " + snapshot.villageId(), NamedTextColor.WHITE));
            sender.sendMessage(Component.text("Idle-Sekunden: " + snapshot.idleSeconds(), NamedTextColor.WHITE));
        }, () -> sender.sendMessage(Component.text("Aktives Gespraech: nein", NamedTextColor.WHITE)));
        return true;
    }

    private String nullToPlaceholder(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private boolean handleDebugSet(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Component.text(
                    "Verwendung: /chief debug set <village|villager> <wert von -100 bis 100>",
                    NamedTextColor.YELLOW));
            return true;
        }

        Villager villager = EntityTargetingUtil.findTargetedVillager(player, plugin.getTargetRangeBlocks());
        if (villager == null) {
            player.sendMessage(Component.text("Kein Villager im Fokus.", NamedTextColor.RED));
            return true;
        }

        int score;
        try {
            score = Integer.parseInt(args[3]);
        } catch (NumberFormatException exception) {
            player.sendMessage(Component.text("Der Rufwert muss eine ganze Zahl sein.", NamedTextColor.RED));
            return true;
        }

        if (score < MIN_REPUTATION_SCORE || score > MAX_REPUTATION_SCORE) {
            player.sendMessage(Component.text("Der Rufwert muss zwischen -100 und 100 liegen.", NamedTextColor.RED));
            return true;
        }

        Speaker speaker = speakerService.getSpeaker(villager).orElse(null);
        String scope = args[2].toLowerCase(Locale.ROOT);
        return switch (scope) {
            case "village" -> handleDebugSetVillage(player, villager, speaker, score);
            case "villager" -> handleDebugSetVillager(player, speaker, score);
            default -> {
                player.sendMessage(Component.text("Unbekannter Debug-Set-Typ. Nutze village oder villager.", NamedTextColor.RED));
                yield true;
            }
        };
    }

    private boolean handleDebugSetVillage(Player player, Villager villager, Speaker speaker, int score) {
        var villageIdentity = villageIdentityService.resolve(villager);
        VillageReputation updated = reputationService.setVillageReputation(
                player.getUniqueId(),
                villageIdentity.villageId(),
                score,
                "debug:set:village");
        int combinedScore = reputationService.getCombinedScore(
                player.getUniqueId(),
                villageIdentity.villageId(),
                speaker.speakerId());
        player.sendMessage(Component.text(
                "Dorfruf fuer " + villageIdentity.villageName() + " auf " + updated.score()
                        + " gesetzt (" + reputationService.getVillageSummary(player.getUniqueId(), villageIdentity.villageId()) + ").",
                NamedTextColor.GREEN));
        player.sendMessage(Component.text(
                "Kombinierter Ruf mit diesem Villager: " + combinedScore
                        + " (" + reputationService.getCombinedSummary(
                                player.getUniqueId(),
                                villageIdentity.villageId(),
                                speaker.speakerId()) + ")",
                NamedTextColor.WHITE));
        return true;
    }

    private boolean handleDebugSetVillager(Player player, Speaker speaker, int score) {
        SpeakerReputation updated = reputationService.setSpeakerReputation(
                player.getUniqueId(),
                speaker.villageId(),
                speaker.speakerId(),
                score,
                "debug:set:villager");
        player.sendMessage(Component.text(
                "Villager-Ruf fuer " + speaker.displayName() + " auf " + updated.score()
                        + " gesetzt (" + reputationService.getSpeakerSummary(player.getUniqueId(), speaker.speakerId()) + ").",
                NamedTextColor.GREEN));
        return true;
    }

    private boolean handleDebugChat(Player player, String[] args) {
        if (!player.hasPermission("villagerai.debugchat")) {
            player.sendMessage(Component.text("Du hast keine Berechtigung fuer diesen Befehl.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 3) {
            player.sendMessage(Component.text(
                    "Verwendung: /chief debug chat <off|normal|verbose>", NamedTextColor.YELLOW));
            return true;
        }

        String levelArg = args[2].toLowerCase(Locale.ROOT);
        VillageChiefPlugin.ChatDebugLevel newLevel = VillageChiefPlugin.ChatDebugLevel.fromConfigKey(levelArg);

        // Pruefen, dass der Wert nicht OFF ist, wenn "off" eingegeben wurde – fromConfigKey liefert OFF als default
        if (!levelArg.equals("off") && !levelArg.equals("normal") && !levelArg.equals("verbose")) {
            player.sendMessage(Component.text(
                    "Unbekannter Chat-Debug-Level. Nutze off, normal oder verbose.", NamedTextColor.RED));
            return true;
        }

        plugin.setChatDebugLevel(newLevel);
        player.sendMessage(Component.text(
                "Chat-Debug-Level auf " + levelArg + " gesetzt (fluechtig, bis zum naechsten Reload/Neustart).", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleDebugWatch(Player player) {
        boolean enabled = villagerDebugOverlayService.toggle(player);
        if (enabled) {
            player.sendMessage(Component.text(
                    "Debug-Overlay aktiv. Schau einen Villager an, um die Mehrzeilen-Debuginfos in der Sidebar zu sehen.",
                    NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Debug-Overlay deaktiviert.", NamedTextColor.YELLOW));
        }
        return true;
    }

    private boolean handleQuest(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Verwendung: /chief quest <talk|fetch|deliver|repair|build|breed|brew|kill|visit|explore|secure|difficulty|cancel|list>", NamedTextColor.YELLOW));
            return true;
        }

        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "talk" -> handleQuestTalk(player);
            case "fetch" -> handleQuestFetch(player, args);
            case "deliver" -> handleQuestDeliver(player, args);
            case "repair" -> handleQuestRepair(player, args);
            case "build" -> handleQuestBuild(player, args);
            case "breed" -> handleQuestBreed(player, args);
            case "brew" -> handleQuestBrew(player, args);
            case "kill" -> handleQuestKill(player, args);
            case "visit" -> handleQuestVisit(player, args);
            case "explore" -> handleQuestExplore(player, args);
            case "secure" -> handleQuestSecure(player, args);
            case "difficulty" -> handleQuestDifficulty(player, args);
            case "cancel" -> handleQuestCancel(player);
            case "list" -> handleQuestList(player);
            default -> {
                sender.sendMessage(Component.text("Unbekannter Quest-Subcommand.", NamedTextColor.RED));
                yield true;
            }
        };
    }

    private boolean handleQuestTalk(Player player) {
        Villager villager = EntityTargetingUtil.findTargetedVillager(player, plugin.getTargetRangeBlocks());
        if (villager == null) {
            player.sendMessage(Component.text("Du musst einen Villager ansehen.", NamedTextColor.RED));
            return true;
        }

        Speaker speaker = speakerService.getSpeaker(villager).orElse(null);

        QuestService.TalkQuestAvailability availability = questService.validateQuestActivation(
                player.getUniqueId(),
                speaker.speakerId());
        if (!availability.allowed()) {
            player.sendMessage(Component.text(availability.failureMessage(), NamedTextColor.RED));
            return true;
        }

        Quest quest = questService.activateTalkQuest(
                player.getUniqueId(),
                                speaker,
                "Sprich mit " + speaker.displayName(),
                "Melde dich bei " + speaker.displayName() + " aus " + speaker.villageName() + ".");
        questUiService.refresh(player);
        player.sendMessage(Component.text("Talk-Quest aktiviert: " + quest.title(), NamedTextColor.GREEN));
        return true;
    }

    private boolean handleQuestList(Player player) {
        List<Quest> quests = questService.findPlayerQuests(player.getUniqueId()).stream().toList();
        if (quests.isEmpty()) {
            player.sendMessage(Component.text("Du hast derzeit keine Quests.", NamedTextColor.GRAY));
            return true;
        }

        player.sendMessage(Component.text("Deine Quests:", NamedTextColor.GOLD));
        for (Quest quest : quests) {
            player.sendMessage(Component.text(
                    quest.questId() + " | " + quest.status() + " | " + quest.title(),
                    NamedTextColor.WHITE));
        }
        return true;
    }

    private boolean handleQuestDeliver(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Component.text("Verwendung: /chief quest deliver <material> <anzahl>", NamedTextColor.YELLOW));
            return true;
        }

        Villager villager = EntityTargetingUtil.findTargetedVillager(player, plugin.getTargetRangeBlocks());
        if (villager == null) {
            player.sendMessage(Component.text("Du musst einen Villager ansehen.", NamedTextColor.RED));
            return true;
        }

        Speaker speaker = speakerService.getSpeaker(villager).orElse(null);

        QuestService.TalkQuestAvailability availability = questService.validateQuestActivation(
                player.getUniqueId(),
                speaker.speakerId());
        if (!availability.allowed()) {
            player.sendMessage(Component.text(availability.failureMessage(), NamedTextColor.RED));
            return true;
        }

        Material material = Material.matchMaterial(args[2]);
        if (material == null || material.isAir()) {
            player.sendMessage(Component.text("Unbekanntes Material.", NamedTextColor.RED));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException exception) {
            player.sendMessage(Component.text("Die Anzahl muss eine Zahl sein.", NamedTextColor.RED));
            return true;
        }

        if (amount <= 0) {
            player.sendMessage(Component.text("Die Anzahl muss groesser als 0 sein.", NamedTextColor.RED));
            return true;
        }

        Quest quest = questService.activateDeliverQuest(player.getUniqueId(), speaker, material, amount);
        questUiService.refresh(player);
        player.sendMessage(Component.text("Liefer-Quest aktiviert: " + quest.title(), NamedTextColor.GREEN));
        return true;
    }

    private boolean handleQuestFetch(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Component.text("Verwendung: /chief quest fetch <material> <anzahl>", NamedTextColor.YELLOW));
            return true;
        }

        Villager villager = EntityTargetingUtil.findTargetedVillager(player, plugin.getTargetRangeBlocks());
        if (villager == null) {
            player.sendMessage(Component.text("Du musst einen Villager ansehen.", NamedTextColor.RED));
            return true;
        }

        Speaker speaker = speakerService.getSpeaker(villager).orElse(null);

        QuestService.TalkQuestAvailability availability = questService.validateQuestActivation(
                player.getUniqueId(),
                speaker.speakerId());
        if (!availability.allowed()) {
            player.sendMessage(Component.text(availability.failureMessage(), NamedTextColor.RED));
            return true;
        }

        Material material = Material.matchMaterial(args[2]);
        if (material == null || material.isAir()) {
            player.sendMessage(Component.text("Unbekanntes Material.", NamedTextColor.RED));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException exception) {
            player.sendMessage(Component.text("Die Anzahl muss eine Zahl sein.", NamedTextColor.RED));
            return true;
        }

        if (amount <= 0) {
            player.sendMessage(Component.text("Die Anzahl muss groesser als 0 sein.", NamedTextColor.RED));
            return true;
        }

        Quest quest = questService.activateFetchQuest(player, speaker, material, amount);
        questUiService.refresh(player);
        player.sendMessage(Component.text("Sammel-Quest aktiviert: " + quest.title(), NamedTextColor.GREEN));
        return true;
    }

    private boolean handleQuestBrew(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Component.text("Verwendung: /chief quest brew <potion-type> <anzahl>", NamedTextColor.YELLOW));
            return true;
        }

        Villager villager = EntityTargetingUtil.findTargetedVillager(player, plugin.getTargetRangeBlocks());
        if (villager == null) {
            player.sendMessage(Component.text("Du musst einen Villager ansehen.", NamedTextColor.RED));
            return true;
        }

        Speaker speaker = speakerService.getSpeaker(villager).orElse(null);

        QuestService.TalkQuestAvailability availability = questService.validateQuestActivation(
                player.getUniqueId(),
                speaker.speakerId());
        if (!availability.allowed()) {
            player.sendMessage(Component.text(availability.failureMessage(), NamedTextColor.RED));
            return true;
        }

        PotionType potionType;
        try {
            potionType = PotionType.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Component.text("Unbekannter Potion-Typ.", NamedTextColor.RED));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException exception) {
            player.sendMessage(Component.text("Die Anzahl muss eine Zahl sein.", NamedTextColor.RED));
            return true;
        }

        if (amount <= 0) {
            player.sendMessage(Component.text("Die Anzahl muss groesser als 0 sein.", NamedTextColor.RED));
            return true;
        }

        Quest quest = questService.activateBrewQuest(player.getUniqueId(), speaker, potionType, amount);
        questUiService.refresh(player);
        player.sendMessage(Component.text("Brau-Quest aktiviert: " + quest.title(), NamedTextColor.GREEN));
        return true;
    }

    private boolean handleQuestRepair(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Component.text("Verwendung: /chief quest repair <material> <anzahl>", NamedTextColor.YELLOW));
            return true;
        }

        Villager villager = EntityTargetingUtil.findTargetedVillager(player, plugin.getTargetRangeBlocks());
        if (villager == null) {
            player.sendMessage(Component.text("Du musst einen Villager ansehen.", NamedTextColor.RED));
            return true;
        }

        Speaker speaker = speakerService.getSpeaker(villager).orElse(null);
        QuestService.TalkQuestAvailability availability = questService.validateQuestActivation(player.getUniqueId(), speaker.speakerId());
        if (!availability.allowed()) {
            player.sendMessage(Component.text(availability.failureMessage(), NamedTextColor.RED));
            return true;
        }

        Material material = Material.matchMaterial(args[2]);
        if (material == null || material.isAir()) {
            player.sendMessage(Component.text("Unbekanntes Material.", NamedTextColor.RED));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException exception) {
            player.sendMessage(Component.text("Die Anzahl muss eine Zahl sein.", NamedTextColor.RED));
            return true;
        }
        if (amount <= 0) {
            player.sendMessage(Component.text("Die Anzahl muss groesser als 0 sein.", NamedTextColor.RED));
            return true;
        }

        Quest quest = questService.activateRepairQuest(player.getUniqueId(), speaker, material, amount);
        questUiService.refresh(player);
        player.sendMessage(Component.text("Reparatur-Quest aktiviert: " + quest.title(), NamedTextColor.GREEN));
        return true;
    }

    private boolean handleQuestBuild(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Component.text("Verwendung: /chief quest build <material> <anzahl>", NamedTextColor.YELLOW));
            return true;
        }

        Villager villager = EntityTargetingUtil.findTargetedVillager(player, plugin.getTargetRangeBlocks());
        if (villager == null) {
            player.sendMessage(Component.text("Du musst einen Villager ansehen.", NamedTextColor.RED));
            return true;
        }

        Speaker speaker = speakerService.getSpeaker(villager).orElse(null);
        QuestService.TalkQuestAvailability availability = questService.validateQuestActivation(player.getUniqueId(), speaker.speakerId());
        if (!availability.allowed()) {
            player.sendMessage(Component.text(availability.failureMessage(), NamedTextColor.RED));
            return true;
        }

        Material material = Material.matchMaterial(args[2]);
        if (material == null || material.isAir() || !material.isBlock()) {
            player.sendMessage(Component.text("Das Ziel muss ein platzierbarer Block sein.", NamedTextColor.RED));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException exception) {
            player.sendMessage(Component.text("Die Anzahl muss eine Zahl sein.", NamedTextColor.RED));
            return true;
        }
        if (amount <= 0) {
            player.sendMessage(Component.text("Die Anzahl muss groesser als 0 sein.", NamedTextColor.RED));
            return true;
        }

        Quest quest = questService.activateBuildQuest(player.getUniqueId(), speaker, material, amount);
        questUiService.refresh(player);
        player.sendMessage(Component.text("Bau-Quest aktiviert: " + quest.title(), NamedTextColor.GREEN));
        return true;
    }

    private boolean handleQuestBreed(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Component.text("Verwendung: /chief quest breed <tierart> <anzahl>", NamedTextColor.YELLOW));
            return true;
        }

        Villager villager = EntityTargetingUtil.findTargetedVillager(player, plugin.getTargetRangeBlocks());
        if (villager == null) {
            player.sendMessage(Component.text("Du musst einen Villager ansehen.", NamedTextColor.RED));
            return true;
        }

        Speaker speaker = speakerService.getSpeaker(villager).orElse(null);
        QuestService.TalkQuestAvailability availability = questService.validateQuestActivation(player.getUniqueId(), speaker.speakerId());
        if (!availability.allowed()) {
            player.sendMessage(Component.text(availability.failureMessage(), NamedTextColor.RED));
            return true;
        }

        EntityType entityType;
        try {
            entityType = EntityType.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Component.text("Unbekannte Tierart.", NamedTextColor.RED));
            return true;
        }
        if (!entityType.isAlive()) {
            player.sendMessage(Component.text("Das Ziel muss ein lebendes Tier sein.", NamedTextColor.RED));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException exception) {
            player.sendMessage(Component.text("Die Anzahl muss eine Zahl sein.", NamedTextColor.RED));
            return true;
        }
        if (amount <= 0) {
            player.sendMessage(Component.text("Die Anzahl muss groesser als 0 sein.", NamedTextColor.RED));
            return true;
        }

        Quest quest = questService.activateBreedQuest(player.getUniqueId(), speaker, entityType, amount);
        questUiService.refresh(player);
        player.sendMessage(Component.text("Zucht-Quest aktiviert: " + quest.title(), NamedTextColor.GREEN));
        return true;
    }

    private boolean handleQuestDifficulty(Player player, String[] args) {
        Villager villager = EntityTargetingUtil.findTargetedVillager(player, plugin.getTargetRangeBlocks());
        if (villager == null) {
            player.sendMessage(Component.text("Du musst einen Villager ansehen.", NamedTextColor.RED));
            return true;
        }

        Speaker speaker = speakerService.getSpeaker(villager).orElse(null);
        int villageReputationScore = reputationService.getVillageScore(player.getUniqueId(), speaker.villageId());
        int unlockedTier = questDifficultyService.resolveUnlockedTier(villageReputationScore);

        if (args.length < 3) {
            int currentTier = questDifficultyService.getPreference(player.getUniqueId(), speaker.speakerId()).preferredDifficultyTier();
            player.sendMessage(Component.text(
                    "Quest-Schwierigkeit fuer " + speaker.displayName() + ": bevorzugt "
                            + questDifficultyService.describeTier(currentTier)
                            + ", freigeschaltet " + questDifficultyService.describeTier(unlockedTier) + ".",
                    NamedTextColor.WHITE));
            player.sendMessage(Component.text(
                    "Verwendung: /chief quest difficulty <normal|0|1|2|3|4>",
                    NamedTextColor.YELLOW));
            return true;
        }

        int preferredTier = parseDifficultyTier(args[2]);
        if (preferredTier < 0) {
            player.sendMessage(Component.text("Unbekannte Schwierigkeit. Nutze normal oder 0 bis 4.", NamedTextColor.RED));
            return true;
        }

        int clampedTier = questDifficultyService.setPreferredDifficultyTier(player.getUniqueId(), speaker.speakerId(), preferredTier)
                .preferredDifficultyTier();
        player.sendMessage(Component.text(
                "Quest-Schwierigkeit fuer " + speaker.displayName() + " auf "
                        + questDifficultyService.describeTier(clampedTier)
                        + " gesetzt. Aktuell freigeschaltet: " + questDifficultyService.describeTier(unlockedTier) + ".",
                NamedTextColor.GREEN));
        if (clampedTier > unlockedTier) {
            player.sendMessage(Component.text(
                    "Hoehere Stufen bleiben vorgemerkt und greifen erst, wenn dein Dorfruf sie freischaltet.",
                    NamedTextColor.GRAY));
        }
        return true;
    }

    private int parseDifficultyTier(String rawValue) {
        String value = rawValue.toLowerCase(Locale.ROOT).trim();
        return switch (value) {
            case "normal" -> 0;
            case "0", "1", "2", "3", "4" -> Integer.parseInt(value);
            default -> -1;
        };
    }

    private boolean handleQuestCancel(Player player) {
        Quest cancelledQuest = questService.cancelActiveQuest(player.getUniqueId()).orElse(null);
        if (cancelledQuest == null) {
            player.sendMessage(Component.text("Du hast aktuell keine aktive Quest zum Abbrechen.", NamedTextColor.RED));
            return true;
        }

        questUiService.refresh(player);
        player.sendMessage(Component.text(
                "Quest abgebrochen: " + cancelledQuest.title() + ". Du kannst sofort eine neue Quest annehmen.",
                NamedTextColor.YELLOW));
        return true;
    }

    private boolean handleQuestKill(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Component.text("Verwendung: /chief quest kill <mob> <anzahl>", NamedTextColor.YELLOW));
            return true;
        }

        Villager villager = EntityTargetingUtil.findTargetedVillager(player, plugin.getTargetRangeBlocks());
        if (villager == null) {
            player.sendMessage(Component.text("Du musst einen Villager ansehen.", NamedTextColor.RED));
            return true;
        }

        Speaker speaker = speakerService.getSpeaker(villager).orElse(null);

        QuestService.TalkQuestAvailability availability = questService.validateQuestActivation(
                player.getUniqueId(),
                speaker.speakerId());
        if (!availability.allowed()) {
            player.sendMessage(Component.text(availability.failureMessage(), NamedTextColor.RED));
            return true;
        }

        EntityType entityType;
        try {
            entityType = EntityType.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Component.text("Unbekannter Mob-Typ.", NamedTextColor.RED));
            return true;
        }

        if (!entityType.isAlive()) {
            player.sendMessage(Component.text("Der Zieltyp muss ein lebendes Wesen sein.", NamedTextColor.RED));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException exception) {
            player.sendMessage(Component.text("Die Anzahl muss eine Zahl sein.", NamedTextColor.RED));
            return true;
        }

        if (amount <= 0) {
            player.sendMessage(Component.text("Die Anzahl muss groesser als 0 sein.", NamedTextColor.RED));
            return true;
        }

        Quest quest = questService.activateKillQuest(player.getUniqueId(), speaker, entityType, amount);
        questUiService.refresh(player);
        player.sendMessage(Component.text("Jagd-Quest aktiviert: " + quest.title(), NamedTextColor.GREEN));
        return true;
    }

    private boolean handleQuestSecure(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Verwendung: /chief quest secure <material> <anzahl> [radius]   oder   /chief quest secure village-light [material] [difficulty-tier]", NamedTextColor.YELLOW));
            return true;
        }

        Villager villager = EntityTargetingUtil.findTargetedVillager(player, plugin.getTargetRangeBlocks());
        if (villager == null) {
            player.sendMessage(Component.text("Du musst einen Villager ansehen.", NamedTextColor.RED));
            return true;
        }

        Speaker speaker = speakerService.getSpeaker(villager).orElse(null);
        QuestService.TalkQuestAvailability availability = questService.validateQuestActivation(player.getUniqueId(), speaker.speakerId());
        if (!availability.allowed()) {
            player.sendMessage(Component.text(availability.failureMessage(), NamedTextColor.RED));
            return true;
        }

        // ---- village-light shortcut ----
        if (args.length >= 2 && "village-light".equalsIgnoreCase(args[2])) {
            Material themeMaterial = Material.TORCH;
            if (args.length >= 4) {
                themeMaterial = Material.matchMaterial(args[3]);
                if (themeMaterial == null || themeMaterial.isAir()) {
                    themeMaterial = Material.TORCH;
                }
            }
            int difficultyTier = 0;
            if (args.length >= 5) {
                try {
                    difficultyTier = Integer.parseInt(args[4]);
                } catch (NumberFormatException ignored) {
                }
            }
            QuestOfferService.QuestOffer offer = questOfferService.villageLightSecureOffer(
                                speaker, themeMaterial, "Du hast nach einer Aufgabe gefragt.", difficultyTier);
            if (offer == null) {
                player.sendMessage(Component.text(
                        "Kein gueltiger Sub-Bereich gefunden – das Dorf ist vermutlich bereits hell.",
                        NamedTextColor.RED));
                return true;
            }
            Quest quest = questOfferService.acceptOffer(player, speaker, offer);
            questUiService.refresh(player);
            player.sendMessage(Component.text("Village-light-Quest aktiviert: " + quest.title(), NamedTextColor.GREEN));
            if (questService.isVillageLightSecureQuest(quest)) {
                String[] parts = quest.targetKey().split("\\|");
                if (parts.length >= 9) {
                    try {
                        int initialDark = Integer.parseInt(parts[5]);
                        int cx = Integer.parseInt(parts[6]);
                        int cz = Integer.parseInt(parts[8]);
                        int cy = Integer.parseInt(parts[7]);
                        int distance = (int) Math.round(player.getLocation().distance(
                                new org.bukkit.Location(player.getWorld(), cx + 0.5D, cy + 0.5D, cz + 0.5D)));
                        player.sendMessage(Component.text(
                                initialDark + " dunkle Stellen im Zielbereich (~" + distance + "m entfernt). "
                                        + "Setze beliebige Lichtquellen – die Bossbar zeigt deinen Fortschritt.",
                                NamedTextColor.GRAY));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return true;
        }

        // ---- block-count (original) ----
        if (args.length < 4) {
            player.sendMessage(Component.text("Verwendung: /chief quest secure <material> <anzahl> [radius]", NamedTextColor.YELLOW));
            return true;
        }

        Material material = Material.matchMaterial(args[2]);
        if (material == null || material.isAir() || !material.isBlock()) {
            player.sendMessage(Component.text("Das Ziel muss ein platzierbarer Block sein (z. B. TORCH).", NamedTextColor.RED));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException exception) {
            player.sendMessage(Component.text("Die Anzahl muss eine Zahl sein.", NamedTextColor.RED));
            return true;
        }
        if (amount <= 0) {
            player.sendMessage(Component.text("Die Anzahl muss groesser als 0 sein.", NamedTextColor.RED));
            return true;
        }

        int radius = 8;
        if (args.length >= 5) {
            try {
                radius = Integer.parseInt(args[4]);
            } catch (NumberFormatException exception) {
                player.sendMessage(Component.text("Der Radius muss eine Zahl sein.", NamedTextColor.RED));
                return true;
            }
        }
        if (radius <= 0) {
            player.sendMessage(Component.text("Der Radius muss groesser als 0 sein.", NamedTextColor.RED));
            return true;
        }

        int baseX = (int) Math.round(speaker.x());
        int baseZ = (int) Math.round(speaker.z());
        int targetX = baseX + 30;
        int targetZ = baseZ - 30;
        Location location = player.getLocation();

        Quest quest = questService.activateSecureQuest(
                        player.getUniqueId(),
                        speaker,
                material,
                amount,
                location.getWorld().getName(),
                targetX,
                targetZ,
                radius);
        questUiService.refresh(player);
        player.sendMessage(Component.text("Sicherungs-Quest aktiviert: " + quest.title() + " (Radius " + radius + ")", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleQuestExplore(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Component.text("Verwendung: /chief quest explore <x> <z> [radius]", NamedTextColor.YELLOW));
            return true;
        }

        Villager villager = EntityTargetingUtil.findTargetedVillager(player, plugin.getTargetRangeBlocks());
        if (villager == null) {
            player.sendMessage(Component.text("Du musst einen Villager ansehen.", NamedTextColor.RED));
            return true;
        }

        Speaker speaker = speakerService.getSpeaker(villager).orElse(null);
        QuestService.TalkQuestAvailability availability = questService.validateQuestActivation(player.getUniqueId(), speaker.speakerId());
        if (!availability.allowed()) {
            player.sendMessage(Component.text(availability.failureMessage(), NamedTextColor.RED));
            return true;
        }

        int targetX;
        int targetZ;
        int radius = 8;
        try {
            targetX = Integer.parseInt(args[2]);
            targetZ = Integer.parseInt(args[3]);
            if (args.length >= 5) {
                radius = Integer.parseInt(args[4]);
            }
        } catch (NumberFormatException exception) {
            player.sendMessage(Component.text("Koordinaten und Radius muessen Zahlen sein.", NamedTextColor.RED));
            return true;
        }
        if (radius <= 0) {
            player.sendMessage(Component.text("Der Radius muss groesser als 0 sein.", NamedTextColor.RED));
            return true;
        }

        Location location = player.getLocation();
        Quest quest = questService.activateExploreQuest(
                        player.getUniqueId(),
                        speaker,
                location.getWorld().getName(),
                targetX,
                targetZ,
                radius);
        questUiService.refresh(player);
        player.sendMessage(Component.text("Erkundungs-Quest aktiviert: " + quest.title() + " (Radius " + radius + ")", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleQuestVisit(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Component.text(
                    "Verwendung: /chief quest visit <x> <z> [radius] oder /chief quest visit <x> <y> <z> [radius]",
                    NamedTextColor.YELLOW));
            return true;
        }

        Villager villager = EntityTargetingUtil.findTargetedVillager(player, plugin.getTargetRangeBlocks());
        if (villager == null) {
            player.sendMessage(Component.text("Du musst einen Villager ansehen.", NamedTextColor.RED));
            return true;
        }

        Speaker speaker = speakerService.getSpeaker(villager).orElse(null);

        QuestService.TalkQuestAvailability availability = questService.validateQuestActivation(
                player.getUniqueId(),
                speaker.speakerId());
        if (!availability.allowed()) {
            player.sendMessage(Component.text(availability.failureMessage(), NamedTextColor.RED));
            return true;
        }

        int targetX;
        int targetZ;
        int radius = 8;
        try {
            targetX = Integer.parseInt(args[2]);
            if (args.length == 4) {
                targetZ = Integer.parseInt(args[3]);
            } else if (args.length == 5) {
                int thirdNumber = Integer.parseInt(args[4]);
                if (Math.abs(thirdNumber) > 64) {
                    targetZ = thirdNumber;
                } else {
                    targetZ = Integer.parseInt(args[3]);
                    radius = thirdNumber;
                }
            } else {
                targetZ = Integer.parseInt(args[4]);
                radius = Integer.parseInt(args[5]);
            }
        } catch (NumberFormatException exception) {
            player.sendMessage(Component.text("Koordinaten und Radius muessen Zahlen sein.", NamedTextColor.RED));
            return true;
        }

        if (radius <= 0) {
            player.sendMessage(Component.text("Der Radius muss groesser als 0 sein.", NamedTextColor.RED));
            return true;
        }

        Location location = player.getLocation();
        Quest quest = questService.activateVisitQuest(
                        player.getUniqueId(),
                        speaker,
                location.getWorld().getName(),
                targetX,
                targetZ,
                radius);
        questUiService.refresh(player);
        player.sendMessage(Component.text(
                "Reise-Quest aktiviert: " + quest.title() + " (Radius " + radius + ")",
                NamedTextColor.GREEN));
        return true;
    }

    private boolean handlePerimeter(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        Villager villager = EntityTargetingUtil.findTargetedVillager(player, plugin.getTargetRangeBlocks());
        if (villager == null) {
            player.sendMessage(Component.text("Du musst einen Villager ansehen.", NamedTextColor.RED));
            return true;
        }

        boolean active = villagePerimeterDisplayService.toggle(player, villager);
        player.sendMessage(Component.text(
                "Perimeter-Anzeige " + (active ? "aktiviert" : "deaktiviert") + ".",
                active ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        return true;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }

        sender.sendMessage(Component.text("Dieser Command kann nur im Spiel verwendet werden.", NamedTextColor.RED));
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Handle standalone /whisper and /w tab completions
        if ("whisper".equalsIgnoreCase(alias) || "w".equalsIgnoreCase(alias)) {
            if (args.length == 0 || args.length == 1) {
                return List.of("on", "off");
            }
            return List.of();
        }

        if (args.length == 1) {
            return List.of("set", "unset", "info", "exit", "debug", "quest", "perimeter", "reload", "forget", "whisper");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            return List.of("chat", "watch", "set");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("chat")) {
            return List.of("off", "normal", "verbose");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("set")) {
            return List.of("village", "villager");
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("set")) {
            return List.of("-100", "-50", "0", "50", "100");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("quest")) {
            return List.of("talk", "fetch", "deliver", "repair", "build", "breed", "brew", "kill", "visit", "explore", "secure", "difficulty", "cancel", "list");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("quest") && args[1].equalsIgnoreCase("difficulty")) {
            return List.of("normal", "0", "1", "2", "3", "4");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("whisper")) {
            return List.of("on", "off");
        }
        return List.of();
    }
}