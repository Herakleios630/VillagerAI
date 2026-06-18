package de.ajsch.villagerai;

import de.ajsch.villagerai.ai.AIService;
import de.ajsch.villagerai.ai.DummyAIService;
import de.ajsch.villagerai.ai.HttpAIService;
import de.ajsch.villagerai.command.ChiefCommand;
import de.ajsch.villagerai.config.PluginDataLoader;
import de.ajsch.villagerai.listener.ChiefDeathHandler;
import de.ajsch.villagerai.listener.PlayerChatListener;
import de.ajsch.villagerai.listener.QuestLifecycleListener;
import de.ajsch.villagerai.listener.QuestUiListener;
import de.ajsch.villagerai.listener.ReputationListener;
import de.ajsch.villagerai.listener.SpeakerLifecycleListener;
import de.ajsch.villagerai.listener.VillagerTradeListener;
import de.ajsch.villagerai.listener.VillagerInteractListener;
import de.ajsch.villagerai.service.ChiefAutoAssignmentService;
import de.ajsch.villagerai.service.ChiefMeetingObserver;
import de.ajsch.villagerai.service.LegendaryUnlockService;
import de.ajsch.villagerai.service.MourningService;
import de.ajsch.villagerai.service.ChiefService;
import de.ajsch.villagerai.service.SpeakerService;
import de.ajsch.villagerai.service.ChiefVisualService;
import de.ajsch.villagerai.service.ConversationService;
import de.ajsch.villagerai.service.DarkBlockCache;
import de.ajsch.villagerai.service.LightLevelScanner;
import de.ajsch.villagerai.service.QuestDifficultyService;
import de.ajsch.villagerai.service.QuestService;
import de.ajsch.villagerai.service.QuestRewardService;
import de.ajsch.villagerai.service.QuestUiService;
import de.ajsch.villagerai.service.QuestGiverLocatorService;
import de.ajsch.villagerai.service.QuestMarkerService;
import de.ajsch.villagerai.service.QuestOfferService;
import de.ajsch.villagerai.service.ReputationService;
import de.ajsch.villagerai.service.VillageIdentityService;
import de.ajsch.villagerai.service.VillageLightParticleMarkerService;
import de.ajsch.villagerai.service.VillagePerimeterDisplayService;
import de.ajsch.villagerai.service.VillagePerimeterService;
import de.ajsch.villagerai.service.VillagerDebugOverlayService;
import de.ajsch.villagerai.service.VillagerConfinementService;
import de.ajsch.villagerai.service.VillagerContextService;
import de.ajsch.villagerai.service.VillagerTradeService;
import de.ajsch.villagerai.storage.ChiefRepository;
import de.ajsch.villagerai.storage.SpeakerRepository;
import de.ajsch.villagerai.storage.YamlSpeakerRepository;
import de.ajsch.villagerai.storage.ConversationHistoryRepository;
import de.ajsch.villagerai.storage.QuestDifficultyPreferenceRepository;
import de.ajsch.villagerai.storage.QuestRepository;
import de.ajsch.villagerai.storage.ReputationRepository;

import de.ajsch.villagerai.storage.VillagerTradeRepository;
import de.ajsch.villagerai.storage.YamlChiefRepository;
import de.ajsch.villagerai.storage.YamlConversationHistoryRepository;
import de.ajsch.villagerai.storage.YamlQuestDifficultyPreferenceRepository;
import de.ajsch.villagerai.storage.YamlQuestRepository;
import de.ajsch.villagerai.storage.YamlReputationRepository;

import de.ajsch.villagerai.storage.YamlVillagerTradeRepository;
import de.ajsch.villagerai.storage.YamlVillageRepository;
import de.ajsch.villagerai.util.Keys;
import de.ajsch.villagerai.model.Quest;
import de.ajsch.villagerai.model.QuestStatus;
import de.ajsch.villagerai.model.QuestType;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class VillageChiefPlugin extends JavaPlugin {

    public enum ChatDebugLevel {
        OFF, NORMAL, VERBOSE;

        public static ChatDebugLevel fromConfigKey(String key) {
            if (key == null) return OFF;
            return switch (key.toLowerCase(Locale.ROOT)) {
                case "normal" -> NORMAL;
                case "verbose" -> VERBOSE;
                default -> OFF;
            };
        }
    }

    private volatile ChatDebugLevel chatDebugLevel = ChatDebugLevel.OFF;
    private volatile PrintWriter chatDebugWriter;
    private static final DateTimeFormatter CHAT_DEBUG_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private PluginDataLoader dataLoader;
    private ExecutorService aiExecutor;
    private ChiefRepository chiefRepository;
    private QuestRepository questRepository;
    private QuestDifficultyPreferenceRepository questDifficultyPreferenceRepository;
    private ConversationHistoryRepository conversationHistoryRepository;
    private ReputationRepository reputationRepository;
    private VillagerTradeRepository villagerTradeRepository;
    private SpeakerRepository speakerRepository;
    private SpeakerService speakerService;
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
    private VillagePerimeterService villagePerimeterService;
    private VillagePerimeterDisplayService villagePerimeterDisplayService;
    private DarkBlockCache darkBlockCache;
    private LightLevelScanner lightLevelScanner;
    private VillageLightParticleMarkerService villageLightParticleMarkerService;
    private ChiefVisualService chiefVisualService;
    private ChiefAutoAssignmentService chiefAutoAssignmentService;
    private MourningService mourningService;

    @Override
    public void onEnable() {
        this.dataLoader = new PluginDataLoader(this);
        dataLoader.saveBundledResources();

        // Chat-Debug-Log initialisieren
        initChatDebugLog();
        this.chatDebugLevel = ChatDebugLevel.fromConfigKey(
                getConfig().getString("debug.chat-debug-level", "off"));

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
        this.villageIdentityService = new VillageIdentityService(keys);
        this.villageIdentityService.setVillageRepository(new YamlVillageRepository(this));
        this.villageIdentityService.setLogger(getLogger());
        this.speakerRepository = new YamlSpeakerRepository(this);
        this.speakerService = new SpeakerService(this, speakerRepository, villageIdentityService, getLogger());
        this.villagePerimeterService = new VillagePerimeterService(
                dataLoader.questsSecurePerimeterMargin(),
                dataLoader.questsSecureMinPerimeterSize(),
                Duration.ofSeconds(dataLoader.questsSecureDarkListCacheTtlSeconds()));
        this.darkBlockCache = new DarkBlockCache(Duration.ofSeconds(dataLoader.questsSecureDarkListCacheTtlSeconds()));
        this.lightLevelScanner = new LightLevelScanner();
        // Enable debug logging to diagnose dark-block detection issues
        this.darkBlockCache.setDebugLogging(dataLoader.debugDarkBlockScanLogging());
        this.lightLevelScanner.setDebugLogging(dataLoader.debugDarkBlockScanLogging());
        this.chiefVisualService = new ChiefVisualService(this, chiefRepository, speakerRepository, getLogger());
                this.reputationService = new ReputationService(reputationRepository);
        this.reputationService.setLogger(getLogger());
        // MourningService ohne ChiefService erzeugen (entkoppelt via Setter)
        this.mourningService = new MourningService(
                this,
                reputationService,
                villageIdentityService,
                villagePerimeterService,
                chiefRepository,
                getLogger());
        this.villageIdentityService.setChiefRepository(chiefRepository);
        this.villageIdentityService.setMourningService(mourningService);
                this.chiefService = new ChiefService(
            keys,
            chiefRepository,
            villageIdentityService,
            chiefVisualService,
            speakerService,
            mourningService,
            reputationService,
            getLogger());
        // Nachträgliche Verdrahtung: MourningService bekommt ChiefService per Setter
        this.mourningService.setChiefService(chiefService);
        this.questService = new QuestService(
            questRepository,
            getLogger(),
            lightLevelScanner,
            villagePerimeterService,
            villageIdentityService,
            darkBlockCache,
            dataLoader.questTalkCooldownSeconds());
        this.villageLightParticleMarkerService = new VillageLightParticleMarkerService(
                this, questService, lightLevelScanner,
                dataLoader.debugVillageLightParticleMarker());
        this.questDifficultyService = new QuestDifficultyService(
            questDifficultyPreferenceRepository,
            dataLoader.loadQuestDifficultySettings());
                this.questOfferService = new QuestOfferService(
                            getLogger(),
                            questService,
                            questDifficultyService,
                            dataLoader.loadQuestOfferTemplatesSection(),
                            villageIdentityService,
                            villagePerimeterService,
                            darkBlockCache,
                            chiefRepository,
                            reputationService,
                            dataLoader.questsSecureSubAreaSize(),
                            dataLoader.questsSecureMinDarkBlocks());
                this.questGiverLocatorService = new QuestGiverLocatorService(speakerService);
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
            dataLoader.questMarkerHeightAboveHead(),
            dataLoader.questWorldMarkersEnabled(),
            dataLoader.questWorldMarkerSecureMaterial(),
            dataLoader.questWorldMarkerExploreMaterial(),
            dataLoader.questWorldMarkerHeightAboveGround(),
            dataLoader.questWorldMarkerParticle(),
            dataLoader.questWorldMarkerLabelDistance(),
            dataLoader.questWorldMarkerShowBlock());
        this.questMarkerService.start();
        this.questUiService = new QuestUiService(
            this,
            questService,
            questGiverLocatorService,
            questMarkerService,
            dataLoader.questUiEnabled());
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
                mourningService,
                                reputationService,
                                speakerService,
                                chiefRepository,
                                villageIdentityService,
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
            conversationSettings.recentConversationTurnsLimit(),
            conversationSettings.recentConversationTurnsLimit(),
            conversationSettings.friendlySpontaneousOfferReputationThreshold(),
            conversationSettings.questCooldownMinutesThresholdSeconds(),
            conversationSettings.repeatFallbackLowHealthThreshold());
                this.villagerDebugOverlayService = new VillagerDebugOverlayService(
            this,
            speakerService,
            conversationService,
            questService,
            reputationService,
            villagerContextService,
            villageIdentityService);
                this.villagePerimeterDisplayService = new VillagePerimeterDisplayService(
                this, speakerService, villagePerimeterService, villageIdentityService);

        ChiefMeetingObserver chiefMeetingObserver = new ChiefMeetingObserver(villageIdentityService, getLogger());
        this.chiefAutoAssignmentService = new ChiefAutoAssignmentService(
                chiefService, chiefRepository, villageIdentityService, mourningService, chiefMeetingObserver, getLogger());
        getServer().getPluginManager().registerEvents(chiefAutoAssignmentService, this);

                LegendaryUnlockService legendaryUnlockService = new LegendaryUnlockService(
                chiefVisualService, chiefService, chiefRepository, reputationService, getLogger());
        getServer().getPluginManager().registerEvents(legendaryUnlockService, this);

        // Alte PDC-Werte bereinigen, bevor Chiefs zugewiesen werden
        villageIdentityService.purgeAllStalePdc();
        chiefAutoAssignmentService.initialScan();
        mourningService.loadAndReschedule();
        chiefVisualService.restoreAllBanners(chiefService);
        chiefVisualService.start();

        registerListeners();
                registerCommands();

                // Villager-AI-Recovery: alle 30 Sekunden eingefrorene Villager wiederbeleben
        this.villagerRecoveryTask = Bukkit.getScheduler().runTaskTimer(this,
            () -> conversationService.recoverAllVillagers(), 20L, 600L);

        // RETINUE_GUARD-Timer: alle 60 Sekunden prüfen, ob Spieler in der Nähe des Chiefs sind
        Bukkit.getScheduler().runTaskTimer(this, () -> advanceRetinueGuardQuests(), 20L, 1200L);

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

        if (villageLightParticleMarkerService != null) {
            villageLightParticleMarkerService.shutdown();
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

        if (villagePerimeterDisplayService != null) {
            villagePerimeterDisplayService.shutdown();
        }

        if (chiefVisualService != null) {
            chiefVisualService.shutdown();
        }

        if (aiService != null) {
            aiService.close();
        }

        if (aiExecutor != null) {
            aiExecutor.shutdownNow();
        }
        closeChatDebugLog();
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
                speakerService,
                conversationService,
                chiefService,
                reputationService,
                villageIdentityService,
                getConfig().getBoolean("interaction.allow-regular-villager-conversations", true),
                getConfig().getBoolean("interaction.regular-villager-conversations-require-sneak", true)), this);
        pluginManager.registerEvents(new PlayerChatListener(conversationService), this);
        pluginManager.registerEvents(new QuestUiListener(questUiService), this);
                pluginManager.registerEvents(new QuestLifecycleListener(
            this,
            speakerService,
            questService,
            questUiService), this);
        pluginManager.registerEvents(new ReputationListener(speakerService, villageIdentityService, reputationService), this);
        pluginManager.registerEvents(new VillagerTradeListener(this, villagerTradeService), this);
        pluginManager.registerEvents(new SpeakerLifecycleListener(speakerService), this);
        pluginManager.registerEvents(new ChiefDeathHandler(chiefService, getLogger()), this);

        // ChunkLoad-Listener: Partikel-Tasks für trauernde Dörfer bei Rejoin wiederherstellen
        {
            var mourning = mourningService;
            pluginManager.registerEvents(new org.bukkit.event.Listener() {
                @org.bukkit.event.EventHandler
                public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
                    mourning.onChunkLoad(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
                }
            }, this);
        }
        pluginManager.registerEvents(chiefVisualService, this);
    }

    private void registerCommands() {
        PluginCommand chiefCommand = getCommand("chief");
        if (chiefCommand == null) {
            throw new IllegalStateException("Command 'chief' is missing from plugin.yml");
        }

        ChiefCommand executor = new ChiefCommand(
            this,
            chiefService,
            speakerService,
            conversationService,
            questService,
            questOfferService,
            questDifficultyService,
            questUiService,
            reputationService,
            mourningService,
            villageIdentityService,
                villagerContextService,
                villagePerimeterDisplayService,
            villagerDebugOverlayService);
        chiefCommand.setExecutor(executor);
        chiefCommand.setTabCompleter(executor);
    }

    public VillagePerimeterService villagePerimeterService() {
        return villagePerimeterService;
    }

    public DarkBlockCache darkBlockCache() {
        return darkBlockCache;
    }

    public LightLevelScanner lightLevelScanner() {
        return lightLevelScanner;
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

            // Speaker-Profile werden on-demand erstellt; kein Batch-Load nötig
        int refreshedVillagerProfiles = refreshLoadedVillagerProfiles();
        questService.reloadTalkQuestCooldown(dataLoader.questTalkCooldownSeconds());
        questDifficultyService.reloadSettings(dataLoader.loadQuestDifficultySettings());
        questOfferService.reloadTemplates(dataLoader.loadQuestOfferTemplatesSection());
        questRewardService.reloadRewards(dataLoader.loadQuestRewardDefinitions(), dataLoader.loadRewardScalingSettings());
        questUiService.reloadSettings(dataLoader.questUiEnabled());
        conversationService.reloadSettings(dataLoader.loadConversationRuntimeSettings());
        villageLightParticleMarkerService.reloadEnabled(
                dataLoader.debugVillageLightParticleMarker());
        chiefVisualService.reloadConfig();
        mourningService.reloadConfig();

        // Chat-Debug-Level aus Config uebernehmen
        this.chatDebugLevel = ChatDebugLevel.fromConfigKey(
                getConfig().getString("debug.chat-debug-level", "off"));

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
        speakerService.refreshLoadedVillagerProfiles(villagers);
        return villagers.size();
    }

    public ChatDebugLevel getChatDebugLevel() {
        return chatDebugLevel;
    }

    private void advanceRetinueGuardQuests() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Collection<Quest> retinueGuardQuests = new ArrayList<>();
            for (Quest quest : questService.findPlayerQuests(player.getUniqueId())) {
                if (quest.type() == QuestType.RETINUE_GUARD && quest.status() == QuestStatus.ACTIVE) {
                    retinueGuardQuests.add(quest);
                }
            }
            for (Quest quest : retinueGuardQuests) {
                // targetKey: RETINUE_GUARD:chiefEntityUuid:durationMinutes
                String[] parts = quest.targetKey().split(":");
                if (parts.length < 3) continue;
                UUID chiefEntityUuid;
                try {
                    chiefEntityUuid = UUID.fromString(parts[1]);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                org.bukkit.entity.Entity entity = Bukkit.getEntity(chiefEntityUuid);
                if (entity == null) continue;
                boolean nearby = entity.getLocation().getWorld().equals(player.getWorld())
                        && entity.getLocation().distanceSquared(player.getLocation()) <= 32.0 * 32.0;
                if (nearby) {
                    questService.advanceRetinueGuardQuests(player, chiefEntityUuid);
                    questUiService.refresh(player);
                }
            }
        }
    }

    public void setChatDebugLevel(ChatDebugLevel level) {
        this.chatDebugLevel = level;
    }

    public void logChatDebug(ChatDebugLevel requiredLevel, String line) {
        if (chatDebugLevel.ordinal() < requiredLevel.ordinal()) {
            return;
        }
        // Konsole
        getLogger().info("[ChatDebug] " + line);
        // Datei
        PrintWriter writer = this.chatDebugWriter;
        if (writer != null) {
            writer.println("[" + CHAT_DEBUG_TIMESTAMP.format(Instant.now()) + "] " + line);
            writer.flush();
        }
    }

    private void initChatDebugLog() {
        try {
            Path logFile = getDataFolder().toPath().resolve("chat-debug.log");
            this.chatDebugWriter = new PrintWriter(new FileWriter(logFile.toFile(), true), true);
            getLogger().info("Chat-Debug-Log geoeffnet: " + logFile.toAbsolutePath());
        } catch (IOException e) {
            getLogger().warning("Konnte chat-debug.log nicht oeffnen: " + e.getMessage());
            this.chatDebugWriter = null;
        }
    }

    private void closeChatDebugLog() {
        PrintWriter writer = this.chatDebugWriter;
        if (writer != null) {
            writer.close();
            this.chatDebugWriter = null;
        }
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
