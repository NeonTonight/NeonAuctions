package xyz.neontonight.auction.claim;

import xyz.neontonight.auction.database.MongoClaimRepository;
import xyz.neontonight.auction.economy.EconomyService;
import xyz.neontonight.auction.scheduler.AuctionScheduler;
import xyz.neontonight.auction.serialization.ItemSerializer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClaimService {

    private final JavaPlugin plugin;
    private final AuctionScheduler scheduler;
    private final MongoClaimRepository claimRepository;
    private final ItemSerializer itemSerializer;
    private final EconomyService economyService;

    public ClaimService(JavaPlugin plugin, AuctionScheduler scheduler, MongoClaimRepository claimRepository, ItemSerializer itemSerializer, EconomyService economyService) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.claimRepository = claimRepository;
        this.itemSerializer = itemSerializer;
        this.economyService = economyService;
    }

    public CompletableFuture<List<AuctionClaim>> findClaims(UUID playerId) {
        return scheduler.supplyAsync(() -> claimRepository.findUnclaimed(playerId, 45));
    }

    public CompletableFuture<AuctionClaim> reserveClaim(UUID playerId, String claimId) {
        return scheduler.supplyAsync(() -> claimRepository.claimIfItem(claimId, playerId))
                .exceptionally(exception -> {
                    plugin.getLogger().warning("Could not claim reward " + claimId + ": " + exception.getMessage());
                    return null;
                });
    }

    public CompletableFuture<AuctionClaim> markPaymentClaimed(UUID playerId, String claimId) {
        return scheduler.supplyAsync(() -> claimRepository.markClaimed(claimId, playerId))
                .exceptionally(exception -> {
                    plugin.getLogger().warning("Could not mark payment claim " + claimId + ": " + exception.getMessage());
                    return null;
                });
    }

    public CompletableFuture<AuctionClaim> previewClaim(UUID playerId, String claimId) {
        return scheduler.supplyAsync(() -> claimRepository.findUnclaimed(playerId, claimId))
                .exceptionally(exception -> {
                    plugin.getLogger().warning("Could not load claim " + claimId + ": " + exception.getMessage());
                    return null;
                });
    }

    public ItemStack item(AuctionClaim claim) {
        if (claim.itemData() == null || claim.itemData().isEmpty()) {
            return null;
        }
        return itemSerializer.deserialize(claim.itemData());
    }

    public boolean depositPayment(AuctionClaim claim) {
        if (!economyService.available()) {
            return false;
        }
        return economyService.deposit(claim.playerId(), claim.playerName(), claim.amount(), "auction-claim:" + claim.id());
    }

    public boolean canFit(Player player, ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return true;
        }
        Inventory inventory = player.getInventory();
        int remaining = itemStack.getAmount();
        int maxStackSize = itemStack.getMaxStackSize();
        for (ItemStack content : inventory.getStorageContents()) {
            if (content == null || content.getType() == Material.AIR) {
                remaining -= maxStackSize;
            } else if (content.isSimilar(itemStack)) {
                remaining -= Math.max(0, maxStackSize - content.getAmount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }
}
