package de.ajsch.villagerai.service;

import de.ajsch.villagerai.VillageChiefPlugin;
import de.ajsch.villagerai.model.Speaker;
import de.ajsch.villagerai.model.Quest;
import de.ajsch.villagerai.model.VillagerContext;
import de.ajsch.villagerai.model.VillageIdentity;
import de.ajsch.villagerai.util.EntityTargetingUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

public final class VillagerDebugOverlayService {

    private static final String OBJECTIVE_NAME = "vai_debug";
    private static final String OBJECTIVE_TITLE = ChatColor.GOLD + "Villager Debug";
    private static final int MAX_LINE_WIDTH = 28;
    private static final int MAX_LINES = 15;
    private static final ChatColor[] ENTRY_COLORS = {
        ChatColor.BLACK,
        ChatColor.DARK_BLUE,
        ChatColor.DARK_GREEN,
        ChatColor.DARK_AQUA,
        ChatColor.DARK_RED,
        ChatColor.DARK_PURPLE,
        ChatColor.GOLD,
        ChatColor.GRAY,
        ChatColor.DARK_GRAY,
        ChatColor.BLUE,
        ChatColor.GREEN,
        ChatColor.AQUA,
        ChatColor.RED,
        ChatColor.LIGHT_PURPLE,
        ChatColor.YELLOW
    };

    private final VillageChiefPlugin plugin;
    private final SpeakerService speakerService;
    private final ConversationService conversationService;
    private final QuestService questService;
    private final ReputationService reputationService;
    private final VillagerContextService villagerContextService;
    private final VillageIdentityService villageIdentityService;
    private final Set<UUID> enabledPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Scoreboard> previousScoreboards = new ConcurrentHashMap<>();
    private final BukkitTask updateTask;

    public VillagerDebugOverlayService(
            VillageChiefPlugin plugin,
            SpeakerService speakerService,
            ConversationService conversationService,
            QuestService questService,
            ReputationService reputationService,
            VillagerContextService villagerContextService,
            VillageIdentityService villageIdentityService) {
        this.plugin = plugin;
        this.speakerService = speakerService;
        this.conversationService = conversationService;
        this.questService = questService;
        this.reputationService = reputationService;
        this.villagerContextService = villagerContextService;
        this.villageIdentityService = villageIdentityService;
        this.updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public boolean toggle(Player player) {
        UUID playerUuid = player.getUniqueId();
        if (!enabledPlayers.add(playerUuid)) {
            enabledPlayers.remove(playerUuid);
            restoreScoreboard(player);
            return false;
        }

        previousScoreboards.putIfAbsent(playerUuid, player.getScoreboard());
        renderSidebar(player);
        return true;
    }

    public boolean isEnabled(Player player) {
        return enabledPlayers.contains(player.getUniqueId());
    }

    public void shutdown() {
        updateTask.cancel();
        for (UUID playerUuid : enabledPlayers) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                restoreScoreboard(player);
            }
        }
        enabledPlayers.clear();
        previousScoreboards.clear();
    }

    private void tick() {
        enabledPlayers.removeIf(playerUuid -> {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player == null || !player.isOnline()) {
                previousScoreboards.remove(playerUuid);
                return true;
            }

            renderSidebar(player);
            return false;
        });
    }

    private void renderSidebar(Player player) {
        ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
        if (scoreboardManager == null) {
            return;
        }

        Scoreboard scoreboard = scoreboardManager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, OBJECTIVE_TITLE);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> lines = buildOverlayLines(player);
        int score = lines.size();
        for (int index = 0; index < lines.size() && index < ENTRY_COLORS.length; index++) {
            String entry = ENTRY_COLORS[index].toString() + ChatColor.RESET;
            Team team = scoreboard.registerNewTeam("line_" + index);
            team.addEntry(entry);
            team.prefix(Component.text(truncate(lines.get(index), 64)));
            objective.getScore(entry).setScore(score--);
        }

        player.setScoreboard(scoreboard);
    }

    private List<String> buildOverlayLines(Player player) {
        List<String> lines = new ArrayList<>();
        Villager villager = EntityTargetingUtil.findTargetedVillager(player, plugin.getTargetRangeBlocks());
        if (villager == null) {
            lines.add("Kein Villager im Fokus");
            lines.add("Sieh einen Villager an");
            lines.add("/chief debug = Langtext");
            return lines;
        }

        Speaker speaker = speakerService.getSpeaker(villager).orElse(null);
        boolean isChief = speaker != null && speaker.isChief();
        VillageIdentity villageIdentity = villageIdentityService.resolve(villager);
        VillagerContext villagerContext = villagerContextService.resolve(villager, player.getUniqueId());
        Quest activeQuest = questService.findActiveQuest(player.getUniqueId()).orElse(null);
        boolean questMatchesTarget = activeQuest != null
                && (activeQuest.villageId().equals(villageIdentity.villageId())
                || speaker.speakerId().equals(activeQuest.speakerId()));
        String questSummary = activeQuest == null
                ? "keine"
                : activeQuest.type() + " " + activeQuest.progress() + "/" + activeQuest.goal()
                        + (questMatchesTarget ? " *" : "");
        boolean inConversation = conversationService.getConversation(player.getUniqueId())
            .map(snapshot -> snapshot.speakerId().equals(speaker.speakerId()))
            .orElse(false);
        String healthSummary = Math.round(villagerContext.currentHealth()) + "/" + Math.round(villagerContext.maxHealth());
        int villageReputationScore = reputationService.getVillageScore(player.getUniqueId(), villageIdentity.villageId());
        int speakerReputationScore = reputationService.getSpeakerScore(player.getUniqueId(), speaker.speakerId());
        int combinedReputationScore = reputationService.getCombinedScore(
            player.getUniqueId(),
            villageIdentity.villageId(),
            speaker.speakerId());

        lines.add("Ziel: " + (isChief ? "Chief" : "Villager") + " | " + speaker.chatName());
        lines.add("Dorf: " + villageIdentity.villageName());
        lines.add("Biom: " + villageIdentity.villageBiome());
        lines.add("Bewohner: ~" + villageIdentity.villagePopulationEstimate());
        lines.add("Ereignis: " + villageIdentity.villageEventSummary());
        lines.add("Merkmale: " + villageIdentity.villageAttributes());
        lines.add("Beruf: " + villagerContext.profession());
        lines.add("Dorf-Ruf: " + villageReputationScore);
        lines.add("Villager: " + speakerReputationScore + " | Gesamt: " + combinedReputationScore);
        lines.add("Ruftext: " + reputationService.getCombinedSummary(
            player.getUniqueId(),
            villageIdentity.villageId(),
            speaker.speakerId()));
        lines.add("HP: " + healthSummary + " | Essen: " + yesNo(villagerContext.ateRecently()));
        lines.add("Talk: " + yesNo(inConversation) + " | Regen: " + yesNo(villagerContext.isRaining()));
        lines.add("Quest: " + questSummary);
        appendWrappedLines(lines, "Trades: ", villagerContext.tradeSummary(), 2);
        appendWrappedLines(lines, "Enge: ", villagerContext.confinementSummary(), 3);

        if (lines.size() > MAX_LINES) {
            return new ArrayList<>(lines.subList(0, MAX_LINES));
        }
        return lines;
    }

    private void appendWrappedLines(List<String> lines, String label, String value, int maxLinesForField) {
        String safeValue = value == null || value.isBlank() ? "-" : value;
        List<String> wrapped = wrapText(safeValue, MAX_LINE_WIDTH - label.length());
        if (wrapped.isEmpty()) {
            lines.add(label + "-");
            return;
        }

        int allowedLines = Math.max(1, maxLinesForField);
        for (int index = 0; index < wrapped.size() && index < allowedLines; index++) {
            String prefix = index == 0 ? label : "  ";
            String line = prefix + wrapped.get(index);
            if (index == allowedLines - 1 && wrapped.size() > allowedLines) {
                line = truncate(line, Math.max(4, MAX_LINE_WIDTH - 3)) + "...";
            }
            lines.add(line);
        }
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }

        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();
        int effectiveWidth = Math.max(8, maxWidth);
        for (String word : words) {
            if (currentLine.length() == 0) {
                currentLine.append(word);
                continue;
            }

            if (currentLine.length() + 1 + word.length() <= effectiveWidth) {
                currentLine.append(' ').append(word);
                continue;
            }

            lines.add(currentLine.toString());
            currentLine = new StringBuilder(word);
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    private String yesNo(boolean value) {
        return value ? "ja" : "nein";
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength));
    }

    private void restoreScoreboard(Player player) {
        Scoreboard previous = previousScoreboards.remove(player.getUniqueId());
        if (previous != null) {
            player.setScoreboard(previous);
            return;
        }

        ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
        if (scoreboardManager != null) {
            player.setScoreboard(scoreboardManager.getMainScoreboard());
        }
    }
}