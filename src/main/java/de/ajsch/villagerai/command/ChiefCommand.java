package de.ajsch.villagerai.command;

import de.ajsch.villagerai.VillageChiefPlugin;
import de.ajsch.villagerai.model.Chief;
import de.ajsch.villagerai.model.Quest;
import de.ajsch.villagerai.model.SpeakerReputation;
import de.ajsch.villagerai.model.VillagerContext;
import de.ajsch.villagerai.model.VillageReputation;
import de.ajsch.villagerai.service.ChiefService;
import de.ajsch.villagerai.service.ConversationService;
import de.ajsch.villagerai.service.QuestDifficultyService;
import de.ajsch.villagerai.service.QuestService;
import de.ajsch.villagerai.service.QuestUiService;
import de.ajsch.villagerai.service.ReputationService;
import de.ajsch.villagerai.service.VillageIdentityService;
import de.ajsch.villagerai.service.VillagerContextService;
import de.ajsch.villagerai.service.VillagerDebugOverlayService;
import de.ajsch.villagerai.util.EntityTargetingUtil;
import java.util.List;
import java.util.Locale;
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
    private final ConversationService conversationService;
    private final QuestService questService;
    private final QuestDifficultyService questDifficultyService;
    private final QuestUiService questUiService;
    private final ReputationService reputationService;
    private final VillageIdentityService villageIdentityService;
    private final VillagerContextService villagerContextService;
    private final VillagerDebugOverlayService villagerDebugOverlayService;

    public ChiefCommand(
            VillageChiefPlugin plugin,
            ChiefService chiefService,
            ConversationService conversationService,
            QuestService questService,
            QuestDifficultyService questDifficultyService,
            QuestUiService questUiService,
            ReputationService reputationService,
            VillageIdentityService villageIdentityService,
            VillagerContextService villagerContextService,
            VillagerDebugOverlayService villagerDebugOverlayService) {
        this.plugin = plugin;
        this.chiefService = chiefService;
        this.conversationService = conversationService;
        this.questService = questService;
        this.questDifficultyService = questDifficultyService;
        this.questUiService = questUiService;
        this.reputationService = reputationService;
        this.villageIdentityService = villageIdentityService;
        this.villagerContextService = villagerContextService;
        this.villagerDebugOverlayService = villagerDebugOverlayService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Verwendung: /chief <set|unset|info|exit|debug|quest|reload>", NamedTextColor.YELLOW));
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set" -> handleSet(sender, args);
            case "unset" -> handleUnset(sender);
            case "info" -> handleInfo(sender);
            case "exit" -> handleExit(sender);
            case "debug" -> handleDebug(sender, args);
            case "quest" -> handleQuest(sender, args);
            case "reload" -> handleReload(sender);
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

        String villageId = args.length >= 2 ? args[1] : null;
        Chief chief = villageId == null || villageId.isBlank()
                ? chiefService.markChief(villager)
                : chiefService.markChief(villager, villageId);

        sender.sendMessage(Component.text("Chief gesetzt: ", NamedTextColor.GREEN)
                .append(Component.text(chief.chiefId(), NamedTextColor.WHITE))
                .append(Component.text(" in Dorf ", NamedTextColor.GREEN))
                .append(Component.text(chief.villageId(), NamedTextColor.WHITE)));
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

        sender.sendMessage(Component.text("Chief-Markierung entfernt.", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleInfo(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        Villager villager = EntityTargetingUtil.findTargetedVillager(player, plugin.getTargetRangeBlocks());
        if (villager == null) {
            sender.sendMessage(Component.text("Du musst einen Villager ansehen.", NamedTextColor.RED));
            return true;
        }

        if (chiefService.getChief(villager).isEmpty()) {
            sender.sendMessage(Component.text("Dieser Villager ist kein Chief.", NamedTextColor.RED));
            return true;
        }

        Chief chief = chiefService.getChief(villager).orElseThrow();
        sender.sendMessage(Component.text("Chief-Info", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Chief-ID: " + chief.chiefId(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Village-ID: " + chief.villageId(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Village-Name: " + chief.villageName(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Name: " + chief.displayName(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Rolle: " + chief.role(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Persoenlichkeit: " + chief.personality(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Begruessung: " + chief.greeting(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Entity-UUID: " + chief.entityUuid(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text(
                "Position: " + chief.world() + " " + (int) chief.x() + ", " + (int) chief.y() + ", " + (int) chief.z(),
                NamedTextColor.WHITE));
        return true;
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

    private boolean handleDebug(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
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

        var chief = chiefService.getChief(villager);
        var villageIdentity = chief.<de.ajsch.villagerai.model.VillageIdentity>map(foundChief ->
            new de.ajsch.villagerai.model.VillageIdentity(
                    foundChief.villageId(),
                    foundChief.villageName(),
                    foundChief.villageDescription(),
                    foundChief.villageAttributes(),
                    foundChief.villageBiome(),
                    foundChief.villagePopulationEstimate(),
                    foundChief.villageEventSummary()))
            .orElseGet(() -> villageIdentityService.resolve(villager));
        VillagerContext villagerContext = villagerContextService.resolve(villager, player.getUniqueId());
        Quest activeQuest = questService.findActiveQuest(player.getUniqueId()).orElse(null);
        boolean questMatchesTarget = activeQuest != null
            && (activeQuest.villageId().equals(villageIdentity.villageId())
            || chief.map(foundChief -> foundChief.chiefId().equals(activeQuest.chiefId())).orElse(false));

        sender.sendMessage(Component.text("Debug fuer Villager " + villager.getUniqueId(), NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Chief aktiv: " + chiefService.isChief(villager), NamedTextColor.WHITE));
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
                chief.map(Chief::chiefId).orElseGet(() -> chiefService.createConversationProfile(villager).chiefId()));
        int combinedReputationScore = reputationService.getCombinedScore(
                player.getUniqueId(),
                villageIdentity.villageId(),
                chief.map(Chief::chiefId).orElseGet(() -> chiefService.createConversationProfile(villager).chiefId()));
        sender.sendMessage(Component.text(
            "Dorfruf: " + villageReputationScore
                + " (" + reputationService.getVillageSummary(player.getUniqueId(), villageIdentity.villageId()) + ")",
            NamedTextColor.WHITE));
        sender.sendMessage(Component.text(
            "Villager-Ruf: " + speakerReputationScore
                + " (" + reputationService.getSpeakerSummary(
                        player.getUniqueId(),
                        chief.map(Chief::chiefId).orElseGet(() -> chiefService.createConversationProfile(villager).chiefId())) + ")",
            NamedTextColor.WHITE));
        sender.sendMessage(Component.text(
            "Kombiniert: " + combinedReputationScore
                + " (" + reputationService.getCombinedSummary(
                        player.getUniqueId(),
                        villageIdentity.villageId(),
                        chief.map(Chief::chiefId).orElseGet(() -> chiefService.createConversationProfile(villager).chiefId())) + ")",
            NamedTextColor.WHITE));
        int unlockedDifficultyTier = questDifficultyService.resolveUnlockedTier(villageReputationScore);
        int preferredDifficultyTier = questDifficultyService.getPreference(
                player.getUniqueId(),
                chief.map(Chief::chiefId).orElseGet(() -> chiefService.createConversationProfile(villager).chiefId()))
            .preferredDifficultyTier();
        sender.sendMessage(Component.text(
            "Quest-Schwierigkeit: bevorzugt " + questDifficultyService.describeTier(preferredDifficultyTier)
                + " | freigeschaltet " + questDifficultyService.describeTier(unlockedDifficultyTier),
            NamedTextColor.WHITE));
        sender.sendMessage(Component.text(
            "Legendary-Status: " + conversationService.describeLegendaryBlocker(
                player,
                chief.map(Chief::chiefId).orElseGet(() -> chiefService.createConversationProfile(villager).chiefId()),
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

        Chief speaker = chiefService.getConversationSpeaker(villager);
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

    private boolean handleDebugSetVillage(Player player, Villager villager, Chief speaker, int score) {
        var villageIdentity = villageIdentityService.resolve(villager);
        VillageReputation updated = reputationService.setVillageReputation(
                player.getUniqueId(),
                villageIdentity.villageId(),
                score,
                "debug:set:village");
        int combinedScore = reputationService.getCombinedScore(
                player.getUniqueId(),
                villageIdentity.villageId(),
                speaker.chiefId());
        player.sendMessage(Component.text(
                "Dorfruf fuer " + villageIdentity.villageName() + " auf " + updated.score()
                        + " gesetzt (" + reputationService.getVillageSummary(player.getUniqueId(), villageIdentity.villageId()) + ").",
                NamedTextColor.GREEN));
        player.sendMessage(Component.text(
                "Kombinierter Ruf mit diesem Villager: " + combinedScore
                        + " (" + reputationService.getCombinedSummary(
                                player.getUniqueId(),
                                villageIdentity.villageId(),
                                speaker.chiefId()) + ")",
                NamedTextColor.WHITE));
        return true;
    }

    private boolean handleDebugSetVillager(Player player, Chief speaker, int score) {
        SpeakerReputation updated = reputationService.setSpeakerReputation(
                player.getUniqueId(),
                speaker.chiefId(),
                score,
                "debug:set:villager");
        player.sendMessage(Component.text(
                "Villager-Ruf fuer " + speaker.chatName() + " auf " + updated.score()
                        + " gesetzt (" + reputationService.getSpeakerSummary(player.getUniqueId(), speaker.chiefId()) + ").",
                NamedTextColor.GREEN));
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

        Chief chief = chiefService.getConversationSpeaker(villager);

        QuestService.TalkQuestAvailability availability = questService.validateQuestActivation(
                player.getUniqueId(),
                chief.chiefId());
        if (!availability.allowed()) {
            player.sendMessage(Component.text(availability.failureMessage(), NamedTextColor.RED));
            return true;
        }

        Quest quest = questService.activateTalkQuest(
                player.getUniqueId(),
                chief,
                "Sprich mit " + chief.chatName(),
                "Melde dich bei " + chief.chatName() + " aus " + chief.villageName() + ".");
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

        Chief chief = chiefService.getConversationSpeaker(villager);

        QuestService.TalkQuestAvailability availability = questService.validateQuestActivation(
                player.getUniqueId(),
                chief.chiefId());
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

        Quest quest = questService.activateDeliverQuest(player.getUniqueId(), chief, material, amount);
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

        Chief chief = chiefService.getConversationSpeaker(villager);

        QuestService.TalkQuestAvailability availability = questService.validateQuestActivation(
                player.getUniqueId(),
                chief.chiefId());
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

        Quest quest = questService.activateFetchQuest(player, chief, material, amount);
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

        Chief chief = chiefService.getConversationSpeaker(villager);

        QuestService.TalkQuestAvailability availability = questService.validateQuestActivation(
                player.getUniqueId(),
                chief.chiefId());
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

        Quest quest = questService.activateBrewQuest(player.getUniqueId(), chief, potionType, amount);
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

        Chief chief = chiefService.getConversationSpeaker(villager);
        QuestService.TalkQuestAvailability availability = questService.validateQuestActivation(player.getUniqueId(), chief.chiefId());
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

        Quest quest = questService.activateRepairQuest(player.getUniqueId(), chief, material, amount);
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

        Chief chief = chiefService.getConversationSpeaker(villager);
        QuestService.TalkQuestAvailability availability = questService.validateQuestActivation(player.getUniqueId(), chief.chiefId());
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

        Quest quest = questService.activateBuildQuest(player.getUniqueId(), chief, material, amount);
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

        Chief chief = chiefService.getConversationSpeaker(villager);
        QuestService.TalkQuestAvailability availability = questService.validateQuestActivation(player.getUniqueId(), chief.chiefId());
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

        Quest quest = questService.activateBreedQuest(player.getUniqueId(), chief, entityType, amount);
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

        Chief chief = chiefService.getConversationSpeaker(villager);
        int villageReputationScore = reputationService.getVillageScore(player.getUniqueId(), chief.villageId());
        int unlockedTier = questDifficultyService.resolveUnlockedTier(villageReputationScore);

        if (args.length < 3) {
            int currentTier = questDifficultyService.getPreference(player.getUniqueId(), chief.chiefId()).preferredDifficultyTier();
            player.sendMessage(Component.text(
                    "Quest-Schwierigkeit fuer " + chief.chatName() + ": bevorzugt "
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

        int clampedTier = questDifficultyService.setPreferredDifficultyTier(player.getUniqueId(), chief.chiefId(), preferredTier)
                .preferredDifficultyTier();
        player.sendMessage(Component.text(
                "Quest-Schwierigkeit fuer " + chief.chatName() + " auf "
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

        Chief chief = chiefService.getConversationSpeaker(villager);

        QuestService.TalkQuestAvailability availability = questService.validateQuestActivation(
                player.getUniqueId(),
                chief.chiefId());
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

        Quest quest = questService.activateKillQuest(player.getUniqueId(), chief, entityType, amount);
        questUiService.refresh(player);
        player.sendMessage(Component.text("Jagd-Quest aktiviert: " + quest.title(), NamedTextColor.GREEN));
        return true;
    }

    private boolean handleQuestSecure(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Component.text("Verwendung: /chief quest secure <material> <anzahl> [radius]", NamedTextColor.YELLOW));
            return true;
        }

        Villager villager = EntityTargetingUtil.findTargetedVillager(player, plugin.getTargetRangeBlocks());
        if (villager == null) {
            player.sendMessage(Component.text("Du musst einen Villager ansehen.", NamedTextColor.RED));
            return true;
        }

        Chief chief = chiefService.getConversationSpeaker(villager);
        QuestService.TalkQuestAvailability availability = questService.validateQuestActivation(player.getUniqueId(), chief.chiefId());
        if (!availability.allowed()) {
            player.sendMessage(Component.text(availability.failureMessage(), NamedTextColor.RED));
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

        int baseX = (int) Math.round(chief.x());
        int baseZ = (int) Math.round(chief.z());
        int targetX = baseX + 30;
        int targetZ = baseZ - 30;
        Location location = player.getLocation();

        Quest quest = questService.activateSecureQuest(
                player.getUniqueId(),
                chief,
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

        Chief chief = chiefService.getConversationSpeaker(villager);
        QuestService.TalkQuestAvailability availability = questService.validateQuestActivation(player.getUniqueId(), chief.chiefId());
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
                chief,
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

        Chief chief = chiefService.getConversationSpeaker(villager);

        QuestService.TalkQuestAvailability availability = questService.validateQuestActivation(
                player.getUniqueId(),
                chief.chiefId());
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
                chief,
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

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }

        sender.sendMessage(Component.text("Dieser Command kann nur im Spiel verwendet werden.", NamedTextColor.RED));
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("set", "unset", "info", "exit", "debug", "quest", "reload");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            return List.of("watch", "set");
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
        return List.of();
    }
}