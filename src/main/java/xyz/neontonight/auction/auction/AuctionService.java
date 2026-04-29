package xyz.neontonight.auction.auction;

import xyz.neontonight.auction.claim.AuctionClaim;
import xyz.neontonight.auction.claim.ClaimType;
import xyz.neontonight.auction.config.AuctionConfig;
import xyz.neontonight.auction.database.MongoAuctionRepository;
import xyz.neontonight.auction.database.MongoClaimRepository;
import xyz.neontonight.auction.economy.EconomyService;
import xyz.neontonight.auction.redis.RedisSyncService;
import xyz.neontonight.auction.scheduler.AuctionScheduler;
import xyz.neontonight.auction.serialization.ItemSerializer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuctionService {

    private static final int RECOVERY_BATCH_SIZE = 250;

    private final JavaPlugin plugin;
    private final AuctionConfig config;
    private final AuctionScheduler scheduler;
    private final MongoAuctionRepository auctionRepository;
    private final MongoClaimRepository claimRepository;
    private final ItemSerializer itemSerializer;
    private final EconomyService economyService;
    private final RedisSyncService redisSyncService;

    public AuctionService(
            JavaPlugin plugin,
            AuctionConfig config,
            AuctionScheduler scheduler,
            MongoAuctionRepository auctionRepository,
            MongoClaimRepository claimRepository,
            ItemSerializer itemSerializer,
            EconomyService economyService,
            RedisSyncService redisSyncService
    ) {
        this.plugin = plugin;
        this.config = config;
        this.scheduler = scheduler;
        this.auctionRepository = auctionRepository;
        this.claimRepository = claimRepository;
        this.itemSerializer = itemSerializer;
        this.economyService = economyService;
        this.redisSyncService = redisSyncService;
    }

    public CompletableFuture<ListResult> listItem(Player player, ItemStack snapshot, double price) {
        UUID sellerId = player.getUniqueId();
        String sellerName = player.getName();

        return scheduler.supplyAsync(() -> {
            if (price < config.minPrice() || price > config.maxPrice()) {
                return ListResult.fail("invalid-price");
            }
            if (auctionRepository.countActiveBySeller(sellerId) >= config.maxListingsPerPlayer()) {
                return ListResult.fail("list-failed");
            }

            String itemData = itemSerializer.serialize(snapshot);
            String auctionId = UUID.randomUUID().toString();
            Instant now = Instant.now();
            AuctionListing listing = new AuctionListing(
                    auctionId,
                    sellerId,
                    sellerName,
                    null,
                    itemData,
                    itemSerializer.sha256(itemData),
                    price,
                    AuctionStatus.CREATING,
                    0,
                    now,
                    now.plus(config.defaultDurationMinutes(), ChronoUnit.MINUTES),
                    config.serverId(),
                    false,
                    null,
                    null
            );
            auctionRepository.createCreating(listing);
            return ListResult.pendingRemoval(auctionId);
        }).thenCompose(result -> {
            if (!result.successful()) {
                return CompletableFuture.completedFuture(result);
            }
            CompletableFuture<ListResult> completion = new CompletableFuture<>();
            scheduler.runPlayer(player, () -> {
                ItemStack inHand = player.getInventory().getItemInMainHand();
                if (inHand == null || inHand.getType() == Material.AIR || !inHand.isSimilar(snapshot) || inHand.getAmount() < snapshot.getAmount()) {
                    completion.complete(ListResult.fail("list-failed", result.auctionId(), false));
                    return;
                }
                inHand.setAmount(inHand.getAmount() - snapshot.getAmount());
                player.getInventory().setItemInMainHand(inHand.getAmount() <= 0 ? null : inHand);
                completion.complete(ListResult.ok(result.auctionId()));
            });
            return completion;
        }).thenCompose(result -> scheduler.supplyAsync(() -> {
            if (!result.successful()) {
                return result;
            }
            AuctionListing creating = auctionRepository.findById(result.auctionId());
            if (creating == null) {
                return ListResult.fail("list-failed");
            }
            if (!auctionRepository.markItemRemoved(creating.id())) {
                refundFailedListing(creating);
                return ListResult.fail("list-failed", creating.id(), true);
            }
            if (!auctionRepository.markListed(creating.id())) {
                refundFailedListing(creating);
                return ListResult.fail("list-failed", creating.id(), true);
            }
            AuctionListing listed = auctionRepository.findById(creating.id());
            if (listed != null) {
                redisSyncService.publish("AUCTION_LISTED", listed.id(), listed.version());
            }
            return ListResult.ok(result.auctionId());
        })).thenApply(result -> {
            if (!result.successful() && result.auctionId() != null && !result.itemRemoved()) {
                auctionRepository.markFailed(result.auctionId());
            }
            return result;
        }).exceptionally(exception -> {
            plugin.getLogger().warning("Could not list auction: " + exception.getMessage());
            return ListResult.fail("list-failed");
        });
    }

    public CompletableFuture<PurchaseReservation> reservePurchase(UUID buyerId, String auctionId) {
        return scheduler.supplyAsync(() -> {
            if (!economyService.available()) {
                return PurchaseReservation.fail("economy-not-ready");
            }
            Instant now = Instant.now();
            String reservationId = UUID.randomUUID().toString();
            AuctionListing reserved = auctionRepository.reserveForPurchase(
                    auctionId,
                    buyerId,
                    reservationId,
                    now,
                    now.plus(config.reservationTimeoutSeconds(), ChronoUnit.SECONDS)
            );
            if (reserved == null) {
                return PurchaseReservation.fail("purchase-unavailable");
            }
            if (reserved.sellerId().equals(buyerId)) {
                auctionRepository.releaseReservation(auctionId, buyerId, reservationId);
                return PurchaseReservation.fail("seller-cannot-buy");
            }
            redisSyncService.publish("AUCTION_RESERVED", reserved.id(), reserved.version());
            return PurchaseReservation.ok(reserved, reservationId);
        }).exceptionally(exception -> {
            plugin.getLogger().warning("Could not reserve auction " + auctionId + ": " + exception.getMessage());
            return PurchaseReservation.fail("purchase-failed");
        });
    }

    public CompletableFuture<AuctionListing> findAuction(String auctionId) {
        return scheduler.supplyAsync(() -> auctionRepository.findById(auctionId));
    }

    public CompletableFuture<PurchaseResult> finalizePurchase(UUID buyerId, String buyerName, String auctionId, String reservationId) {
        return scheduler.supplyAsync(() -> {
            AuctionListing sold = auctionRepository.markSold(auctionId, buyerId, reservationId);
            if (sold == null) {
                return PurchaseResult.fail("purchase-unavailable");
            }

            ensureSoldClaims(sold, buyerName);
            auctionRepository.markFulfilled(sold.id());
            redisSyncService.publish("AUCTION_SOLD", sold.id(), sold.version());
            return PurchaseResult.ok();
        }).exceptionally(exception -> {
            plugin.getLogger().warning("Could not purchase auction " + auctionId + ": " + exception.getMessage());
            return PurchaseResult.fail("purchase-failed");
        });
    }

    public CompletableFuture<Void> releaseReservation(String auctionId, UUID buyerId, String reservationId) {
        return scheduler.runAsync(() -> auctionRepository.releaseReservation(auctionId, buyerId, reservationId));
    }

    public boolean withdrawBuyer(UUID buyerId, String buyerName, double amount, String auctionId) {
        if (!economyService.available()) {
            return false;
        }
        return economyService.withdraw(buyerId, buyerName, amount, "auction-buy:" + auctionId + ":" + buyerId);
    }

    public boolean refundBuyer(UUID buyerId, String buyerName, double amount, String auctionId) {
        if (!economyService.available()) {
            return false;
        }
        return economyService.deposit(buyerId, buyerName, amount, "auction-buy:" + auctionId + ":" + buyerId + ":refund");
    }

    public CompletableFuture<List<AuctionListing>> findActive(int page) {
        return scheduler.supplyAsync(() -> auctionRepository.findActive(page, config.pageSize(), Instant.now()));
    }

    public CompletableFuture<List<AuctionListing>> findListingsBySeller(UUID sellerId, int page) {
        return scheduler.supplyAsync(() -> auctionRepository.findBySeller(sellerId, page, config.pageSize()));
    }

    public void recoverStartupState() {
        scheduler.runAsync(() -> {
            recoverStaleCreating();
            recoverSoldFulfillment();
            expireAndRelease();
        });
    }

    public void startExpirationScanner() {
        scheduler.runAsyncTimer(this::expireAndRelease, config.expirationScanSeconds(), config.expirationScanSeconds(), TimeUnit.SECONDS);
    }

    private void expireAndRelease() {
        try {
            for (AuctionListing listing : auctionRepository.expireListed(Instant.now(), RECOVERY_BATCH_SIZE)) {
                claimRepository.upsertClaim(new AuctionClaim(
                        "expired-item:" + listing.id(),
                        listing.sellerId(),
                        listing.sellerName(),
                        ClaimType.EXPIRED_ITEM,
                        listing.id(),
                        listing.itemData(),
                        0.0D,
                        false,
                        Instant.now(),
                        null
                ));
                redisSyncService.publish("AUCTION_EXPIRED", listing.id(), listing.version());
            }
            for (AuctionListing listing : auctionRepository.releaseExpiredReservations(Instant.now(), RECOVERY_BATCH_SIZE)) {
                redisSyncService.publish("AUCTION_RELEASED", listing.id(), listing.version());
            }
            recoverSoldFulfillment();
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Auction expiration scan failed: " + exception.getMessage());
        }
    }

    private void recoverStaleCreating() {
        Instant olderThan = Instant.now().minus(config.creatingRecoveryMinutes(), ChronoUnit.MINUTES);
        for (AuctionListing listing : auctionRepository.findStaleCreating(olderThan, RECOVERY_BATCH_SIZE)) {
            if (auctionRepository.markFailed(listing.id())) {
                if (listing.itemRemoved()) {
                    refundFailedListing(listing);
                }
            }
        }
    }

    private void recoverSoldFulfillment() {
        for (AuctionListing listing : auctionRepository.findSoldWithoutFulfillment(RECOVERY_BATCH_SIZE)) {
            ensureSoldClaims(listing, "Buyer");
            auctionRepository.markFulfilled(listing.id());
        }
    }

    private void ensureSoldClaims(AuctionListing sold, String buyerName) {
        if (sold.buyerId() == null) {
            return;
        }
        Instant now = Instant.now();
        claimRepository.upsertClaim(new AuctionClaim(
                "buyer-item:" + sold.id(),
                sold.buyerId(),
                buyerName,
                ClaimType.BUYER_ITEM,
                sold.id(),
                sold.itemData(),
                0.0D,
                false,
                now,
                null
        ));
        claimRepository.upsertClaim(new AuctionClaim(
                "seller-payment:" + sold.id(),
                sold.sellerId(),
                sold.sellerName(),
                ClaimType.SELLER_PAYMENT,
                sold.id(),
                null,
                sold.price(),
                false,
                now,
                null
        ));
    }

    private void refundFailedListing(AuctionListing listing) {
        claimRepository.upsertClaim(new AuctionClaim(
                "failed-listing-refund:" + listing.id(),
                listing.sellerId(),
                listing.sellerName(),
                ClaimType.FAILED_LISTING_REFUND,
                listing.id(),
                listing.itemData(),
                0.0D,
                false,
                Instant.now(),
                null
        ));
        auctionRepository.markFailed(listing.id());
    }

}
