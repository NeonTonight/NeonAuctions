package xyz.neontonight.auction.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public final class AuctionMenuListener implements Listener {

    private final AuctionMenuService menuService;

    public AuctionMenuListener(AuctionMenuService menuService) {
        this.menuService = menuService;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof AuctionMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        menuService.handleClick(player, holder, event.getRawSlot());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Sessions are intentionally retained so Redis refresh can reopen the same page while the menu is open.
    }
}
