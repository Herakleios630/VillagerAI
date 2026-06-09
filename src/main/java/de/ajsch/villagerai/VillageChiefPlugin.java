package de.ajsch.villagerai;

import de.ajsch.villagerai.ai.AIService;
import de.ajsch.villagerai.ai.DummyAIService;
import de.ajsch.villagerai.ai.HttpAIService;
import de.ajsch.villagerai.command.ChiefCommand;
import de.ajsch.villagerai.config.PluginDataLoader;
import de.ajsch.villagerai.listener.PlayerChatListener;
import de.ajsch.villagerai.listener.QuestLifecycleListener;
import de.ajsch.villagerai.listener.QuestUiListener;
import de.ajsch.villagerai.listener.ReputationListener;
import de.ajsch.villagerai.listener.VillagerProfileListener;
import de.ajsch.villagerai.listener.VillagerTradeListener;
import de.ajsch.villagerai.listener.VillagerInteractListener;
import de.ajsch.villagerai.service.ChiefService;
import de.ajsch.villagerai.service.ConversationService;
import de.ajsch.villagerai.service.QuestDifficultyService;
import de.ajsch.villagerai.service.QuestService;
import de.ajsch.villagerai.service.QuestRewardService;
import de.ajsch.villagerai.service.QuestUiService;
import de.ajsch.villagerai.service.QuestGiverLocatorService;
import de.ajsch.villagerai.service.QuestMarkerService;
import de.ajsch.villagerai.service.QuestOfferService;
import de.ajsch.villagerai.service.ReputationService;
import de.ajsch.villagerai.service.VillageIdentityService;
import de.ajsch.villagerai.service.VillagerDebugOverlayService;
import de.ajsch.villagerai.service.VillagerConfinementService;
import de.ajsch.villagerai.service.VillagerContextService;
import de.ajsch.villagerai.service.VillagerTradeService;
import de.ajsch.villagerai.storage.ChiefRepository;
import de.ajsch.villagerai.storage.ConversationHistoryRepository;
import de.ajsch.villagerai.storage.QuestDifficultyPreferenceRepository;
import de.ajsch.villagerai.storage.QuestRepository;
import de.ajsch.villagerai.storage.ReputationRepository;
import de.ajsch.villagerai.storage.VillagerProfileRepository;
import de.ajsch.villagerai.storage.VillagerTradeRepository;
import de.ajsch.villagerai.storage.YamlChiefRepository;
import de.ajsch.villagerai.storage.YamlConversationHistoryRepository;
import de.ajsch.villagerai.storage.YamlQuestDifficultyPreferenceRepository;
import de.ajsch.villagerai.storage.YamlQuestRepository;
import de.ajsch.villagerai.storage.YamlReputationRepository;
import de.ajsch.villagerai.storage.YamlVillagerProfileRepository;
import de.ajsch.villagerai.storage.YamlVillagerTradeRepository;
import de.ajsch.villagerai.util.Keys;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class VillageChiefPlugin extends JavaPlugin {

    private PluginDataLoader dataLoader;
    private ExecutorService aiExecutor;
    private ChiefRepository chiefRepository;
    private QuestRepository questRepository;
    private QuestDifficultyPreferenceRepository questDifficultyPreferenceRepository;
    private ConversationHistoryRepository conversationHistoryRepository;
    private ReputationRepository reputationRepository;
    private VillagerTradeRepository villagerTradeRepository;
    private VillagerProfileRepository villagerProfileRepository;
    private ChiefService chiefService;
    private QuestService questService;
    private QuestDifficultyService questDifficultyService;
    private QuestOfferService questOfferService;
    private QuestRewardService questRewardService;
        private QuestUiService questUiService;
    private QuestMarkerService questMarkerService;
    private QuestGiverLocatorService questGiverLocatorService;
    private ReputationService reputationService;
    private VillagerDebugOverlayService villagerDebugOverlayService;
    private VillagerTradeService villagerTradeService;
        private AIService aiService;
    private ConversationService conversationService;
    private BukkitTask villagerRecoveryTask;
    private VillagerConfinementService villagerConfinementService;
    private VillagerContextService villagerContextService;
    private VillageIdentityService villageIdentityService;

    @Override
    public void onEnable() {
        this.dataLoader = new PluginDataLoader(this);
        dataLoader.saveBundledResources();

        this.aiExecutor = Executors.newFixedThreadPool(2, new AiThreadFactory());
        PluginDataLoader.ConfinementSettings confinementSettings = dataLoader.confinementSettings();
        ConversationService.RuntimeSettings conversationSettings = dataLoader.loadConversationRuntimeSettings();

        Keys keys = new Keys(this);
        this.chiefRepository = new YamlChiefRepository(this);
        this.questRepository = new YamlQuestRepository(this);
        this.questDifficultyPreferenceRepository = new YamlQuestDifficultyPreferenceRepository(this);
        this.conversationHistoryRepository = new YamlConversationHistoryRepository(
            this,
            dataLoader.conversationHistoryMaxTurnsPerChief());
        this.reputationRepository = new YamlReputationRepository(this);
        this.villagerTradeRepository = new YamlVillagerTradeRepository(
            this,
            dataLoader.villagerTradeHistoryLimit());
        this.villagerProfileRepository = new YamlVillagerProfileRepository(this);
        this.villageIdentityService = new VillageIdentityService();
        this.chiefService = new ChiefService(
            keys,
            chiefRepository,
            villagerProfileRepository,
            villageIdentityService,
            getLogger(),
            dataLoader.loadChiefProfilesSection());
        this.questService = new QuestService(
            questRepository,
            dataLoader.questTalkCooldownSeconds());
        this.questDifficultyService = new QuestDifficultyService(
            questDifficultyPreferenceRepository,
            dataLoader.loadQuestDifficultySettings());
        this.questOfferService = new QuestOfferService(
            getLogger(),
            questService,
            questDifficultyService,
            dataLoader.loadQuestOfferTemplatesSection());
                this.questGiverLocatorService = new QuestGiverLocatorService(chiefService, villagerProfileRepository);
        this.questRewardService = new QuestRewardService(
            dataLoader.loadQuestRewardDefinitions(),
            dataLoader.loadRewardScalingSettings());
                this.questMarkerService = new QuestMarkerService(
            this,
            questService,
            questGiverLocatorService,
            dataLoader.questMarkersEnabled(),
            dataLoader.questMarkerActiveSymbol(),
            dataLoader.questMarkerReadySymbol(),
            dataLoader.questMarkerHeightAboveHead());
        this.questMarkerService.start();
        this.questUiService = new QuestUiService(
            this,
            questService,
            questGiverLocatorService,
            questMarkerService,
            dataLoader.questUiEnabled());
        this.reputationService = new ReputationService(reputationRepository);
        this.villagerTradeService = new VillagerTradeService(
            villagerTradeRepository,
            dataLoader.villagerTradeSummaryRecentTrades());
        this.villagerConfinementService = new VillagerConfinementService(
            this,
            confinementSettings.scanIntervalSeconds(),
            confinementSettings.stationaryRadiusBlocks(),
            confinementSettings.stationaryMinutes(),
            confinementSettings.cantReachWalkTargetMinutes(),
            confinementSettings.staleSleepMinutes(),
            confinementSettings.staleWorkMinutes());
        this.villagerContextService = new VillagerContextService(villagerTradeService, villagerConfinementService);
        this.aiService = createAiService();
        this.conversationService = new ConversationService(
                this,
                aiService,
                questService,
            questDifficultyService,
                questOfferService,
                questRewardService,
                questUiService,
                reputationService,
                conversationHistoryRepository,
                villagerContextService,
                conversationSettings.timeout(),
            getConfig().getLong("conversation.check-interval-seconds", 15L),
            conversationSettings.maxConcurrentRequests(),
            conversationSettings.waitingMessage(),
            conversationSettings.chiefBusyMessage(),
            conversationSettings.queueFullMessage(),
            conversationSettings.spontaneousQuestOfferChance(),
            conversationSettings.spontaneousQuestOfferCooldownSeconds(),
            conversationSettings.spontaneousQuestOfferDeclineCooldownSeconds(),
            conversationSettings.spontaneousQuestOfferMinCombinedReputation(),
            conversationSettings.recentChiefRepliesLimit(),
            conversationSettings.recentConversationTurnsLimit(),
            conversationSettings.friendlySpontaneousOfferReputationThreshold(),
            conversationSettings.questCooldownMinutesThresholdSeconds(),
            conversationSettings.repeatFallbackLowHealthThreshold());
        this.villagerDebugOverlayService = new VillagerDebugOverlayService(
            this,
            chiefService,
            conversationService,
            questService,
            reputationService,
            villagerContextService,
            villageIdentityService);

        registerListeners();
                registerCommands();

        // Villager-AI-Recovery: alle 30 Sekunden eingefrorene Villager wiederbeleben
        this.villagerRecoveryTask = Bukkit.getScheduler().runTaskTimer(this,
            () -> conversationService.recoverAllVillagers(), 20L, 600L);

        getLogger().info("VillagerAI enabled using AI provider: " + aiService.getName());
        getLogger().info("Quest service ready with repository: " + questRepository.getClass().getSimpleName());
    }

    @Override
    public void onDisable() {
        if (conversationService != null) {
            conversationService.shutdown();
        }

                if (questUiService != null) {
            questUiService.shutdown();
        }

        if (questMarkerService != null) {
            questMarkerService.shutdown();
        }

        if (villagerRecoveryTask != null) {
            villagerRecoveryTask.cancel();
        }

        if (villagerDebugOverlayService != null) {
            villagerDebugOverlayService.shutdown();
        }

        if (villagerConfinementService != null) {
            villagerConfinementService.shutdown();
        }

        if (aiService != null) {
            aiService.close();
        }

        if (aiExecutor != null) {
            aiExecutor.shutdownNow();
        }
    }

    private AIService createAiService() {
        String provider = getConfig().getString("ai.provider", "dummy").toLowerCase(Locale.ROOT);
        return switch (provider) {
            case "http" -> new HttpAIService(
                    aiExecutor,
                    getConfig().getString("ai.http.endpoint", "http://127.0.0.1:8080/v1/chief/reply"),
                    Duration.ofMillis(getConfig().getLong("ai.http.connect-timeout-millis", 2000L)),
                    Duration.ofMillis(getConfig().getLong("ai.http.request-timeout-millis", 5000L)),
                    getConfig().getString(
                            "ai.http.system-prompt",
                            "Du bist ein glaubwuerdiger Sprecher in einem Minecraft-Dorf. Antworte passend zur Rolle, kurz und natuerlich auf Deutsch."));
            default -> new DummyAIService(
                    aiExecutor,
                    getConfig().getString("ai.dummy-prefix", "Der Haeuptling sagt"));
        };
    }

    private void registerListeners() {
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new VillagerInteractListener(
                chiefService,
                conversationService,
                getConfig().getBoolean("interaction.allow-regular-villager-conversations", true),
                getConfig().getBoolean("interaction.regular-villager-conversations-require-sneak", true)), this);
        pluginManager.registerEvents(new PlayerChatListener(conversationService), this);
        pluginManager.registerEvents(new QuestUiListener(questUiService), this);
        pluginManager.registerEvents(new QuestLifecycleListener(
            this,
            chiefService,
            questService,
            questUiService,
            villagerProfileRepository), this);
        pluginManager.registerEvents(new ReputationListener(chiefService, villageIdentityService, reputationService), this);
        pluginManager.registerEvents(new VillagerTradeListener(this, villagerTradeService), this);
        pluginManager.registerEvents(new VillagerProfileListener(chiefService), this);
    }

    private void registerCommands() {
        PluginCommand chiefCommand = getCommand("chief");
        if (chiefCommand == null) {
            throw new IllegalStateException("Command 'chief' is missing from plugin.yml");
        }

        ChiefCommand executor = new ChiefCommand(
            this,
            chiefService,
            conversationService,
            questService,
            questDifficultyService,
            questUiService,
            reputationService,
            villageIdentityService,
                villagerContextService,
            villagerDebugOverlayService);
        chiefCommand.setExecutor(executor);
        chiefCommand.setTabCompleter(executor);
    }

    public double getTargetRangeBlocks() {
        return getConfig().getDouble("interaction.target-range-blocks", 6.0D);
    }

    public List<String> reloadRuntimeConfiguration(CommandSender sender) {
            reloadConfig();

            if (!getConfig().getBoolean("interaction.allow-regular-villager-conversations", true)) {
                getLogger().warning("interaction.allow-regular-villager-conversations ist FALSE - Spieler koennen nur mit per /chief set markierten Villagern sprechen!");
                if (sender != null) {
                    sender.sendMessage("§e[VillageChiefAI] WARNUNG: allow-regular-villager-conversations ist deaktiviert. Normale Villager koennen nicht angesprochen werden.");
                }
            }

            chiefService.reloadProfiles(dataLoader.loadChiefProfilesSection());
        int refreshedVillagerProfiles = refreshLoadedVillagerProfiles();
        questService.reloadTalkQuestCooldown(dataLoader.questTalkCooldownSeconds());
        questDifficultyService.reloadSettings(dataLoader.loadQuestDifficultySettings());
        questOfferService.reloadTemplates(dataLoader.loadQuestOfferTemplatesSection());
        questRewardService.reloadRewards(dataLoader.loadQuestRewardDefinitions(), dataLoader.loadRewardScalingSettings());
        questUiService.reloadSettings(dataLoader.questUiEnabled());
        conversationService.reloadSettings(dataLoader.loadConversationRuntimeSettings());

        return List.of(
                "config.yml neu geladen",
                "chief-profiles.yml neu geladen",
                "Geladene Villager-Profile aktualisiert: " + refreshedVillagerProfiles,
                "quest-offers.yml neu geladen",
                "quest-rewards.yml neu geladen",
                "Quest- und Gespraechs-Tuning neu angewendet",
                "Hinweis: AI-Provider-Wechsel und Interaction-Listener-Flags brauchen weiterhin einen Neustart");
    }

    private int refreshLoadedVillagerProfiles() {
        List<org.bukkit.entity.Villager> villagers = new ArrayList<>();
        getServer().getWorlds().forEach(world -> villagers.addAll(world.getEntitiesByClass(org.bukkit.entity.Villager.class)));
        chiefService.refreshLoadedVillagerProfiles(villagers);
        return villagers.size();
    }

    private static final class AiThreadFactory implements ThreadFactory {

        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "villagerai-ai-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}