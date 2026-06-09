package de.ajsch.villagerai.service;

import de.ajsch.villagerai.model.VillagerTradeHistory;
import de.ajsch.villagerai.model.VillagerTradeRecord;
import de.ajsch.villagerai.storage.VillagerTradeRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.entity.Villager;

public final class VillagerTradeService {

    private final VillagerTradeRepository villagerTradeRepository;
    private final int summaryRecentTrades;

    public VillagerTradeService(VillagerTradeRepository villagerTradeRepository, int summaryRecentTrades) {
        this.villagerTradeRepository = villagerTradeRepository;
        this.summaryRecentTrades = Math.max(1, summaryRecentTrades);
    }

    public void captureCompletedTrade(
            UUID playerUuid,
            Villager villager,
            int selectedRecipeIndex,
            int previousUses,
            MerchantRecipe recipeSnapshot) {
        MerchantRecipe currentRecipe = resolveCurrentRecipe(villager, selectedRecipeIndex, recipeSnapshot);
        int completedTrades = currentRecipe.getUses() - previousUses;
        if (completedTrades <= 0) {
            return;
        }

        List<ItemStack> ingredients = new ArrayList<>(recipeSnapshot.getIngredients());
        ItemStack firstIngredient = resolveFirstIngredient(recipeSnapshot, ingredients);
        ItemStack secondIngredient = ingredients.size() > 1 ? ingredients.get(1) : null;

        villagerTradeRepository.appendTrade(
                playerUuid,
                villager.getUniqueId(),
                new VillagerTradeRecord(
                        materialName(recipeSnapshot.getResult()),
                        itemAmount(recipeSnapshot.getResult()),
                        materialName(firstIngredient),
                        itemAmount(firstIngredient),
                        materialNameOrNull(secondIngredient),
                        itemAmount(secondIngredient),
                        completedTrades,
                        System.currentTimeMillis()));
    }

    public String buildSummary(UUID playerUuid, UUID villagerUuid) {
        return villagerTradeRepository.findHistory(playerUuid, villagerUuid)
                .map(this::summarizeHistory)
                .orElse(null);
    }

    private String summarizeHistory(VillagerTradeHistory history) {
        if (history.trades().isEmpty()) {
            return null;
        }

        int totalTrades = history.trades().stream()
                .mapToInt(VillagerTradeRecord::tradeCount)
                .sum();

        List<VillagerTradeRecord> recentTrades = history.trades().subList(
                Math.max(0, history.trades().size() - summaryRecentTrades),
                history.trades().size());

        String recentSummary = recentTrades.stream()
                .map(this::formatTrade)
                .collect(Collectors.joining("; "));

        return "insgesamt " + totalTrades + " erfolgreiche Trades; zuletzt: " + recentSummary;
    }

    private String formatTrade(VillagerTradeRecord trade) {
        StringBuilder builder = new StringBuilder();
        builder.append(trade.tradeCount()).append("x ");
        builder.append(trade.firstIngredientAmount()).append(' ').append(formatMaterial(trade.firstIngredientItem()));
        if (trade.secondIngredientItem() != null) {
            builder.append(" + ")
                    .append(trade.secondIngredientAmount())
                    .append(' ')
                    .append(formatMaterial(trade.secondIngredientItem()));
        }
        builder.append(" -> ")
                .append(trade.resultAmount())
                .append(' ')
                .append(formatMaterial(trade.resultItem()));
        return builder.toString();
    }

    private MerchantRecipe resolveCurrentRecipe(Villager villager, int selectedRecipeIndex, MerchantRecipe fallback) {
        if (selectedRecipeIndex >= 0 && selectedRecipeIndex < villager.getRecipeCount()) {
            return villager.getRecipe(selectedRecipeIndex);
        }

        return fallback;
    }

    private ItemStack resolveFirstIngredient(MerchantRecipe recipeSnapshot, List<ItemStack> ingredients) {
        ItemStack adjustedIngredient = recipeSnapshot.getAdjustedIngredient1();
        if (adjustedIngredient != null && !adjustedIngredient.getType().isAir()) {
            return adjustedIngredient;
        }

        return ingredients.size() > 0 ? ingredients.get(0) : null;
    }

    private String materialName(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return "NONE";
        }

        return itemStack.getType().name();
    }

    private String materialNameOrNull(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }

        return itemStack.getType().name();
    }

    private int itemAmount(ItemStack itemStack) {
        return itemStack == null || itemStack.getType().isAir() ? 0 : itemStack.getAmount();
    }

    private String formatMaterial(String materialName) {
        return materialName.toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}