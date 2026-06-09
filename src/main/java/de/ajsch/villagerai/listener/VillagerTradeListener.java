package de.ajsch.villagerai.listener;

import de.ajsch.villagerai.service.VillagerTradeService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public final class VillagerTradeListener implements Listener {

    private final JavaPlugin plugin;
    private final VillagerTradeService villagerTradeService;

    public VillagerTradeListener(JavaPlugin plugin, VillagerTradeService villagerTradeService) {
        this.plugin = plugin;
        this.villagerTradeService = villagerTradeService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(event.getInventory() instanceof MerchantInventory merchantInventory)) {
            return;
        }

        if (event.getSlotType() != InventoryType.SlotType.RESULT || event.getAction() == InventoryAction.NOTHING) {
            return;
        }

        ItemStack currentItem = event.getCurrentItem();
        if (currentItem == null || currentItem.getType().isAir()) {
            return;
        }

        Merchant merchant = merchantInventory.getMerchant();
        if (!(merchant instanceof Villager villager)) {
            return;
        }

        MerchantRecipe selectedRecipe = merchantInventory.getSelectedRecipe();
        if (selectedRecipe == null) {
            return;
        }

        int selectedRecipeIndex = merchantInventory.getSelectedRecipeIndex();
        int previousUses = selectedRecipe.getUses();
        MerchantRecipe recipeSnapshot = new MerchantRecipe(selectedRecipe);

        Bukkit.getScheduler().runTask(plugin, () -> villagerTradeService.captureCompletedTrade(
                player.getUniqueId(),
                villager,
                selectedRecipeIndex,
                previousUses,
                recipeSnapshot));
    }
}