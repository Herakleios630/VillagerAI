package de.ajsch.villagerai.model;

public record VillagerTradeRecord(
        String resultItem,
        int resultAmount,
        String firstIngredientItem,
        int firstIngredientAmount,
        String secondIngredientItem,
        int secondIngredientAmount,
        int tradeCount,
        long timestampEpochMillis) {
}