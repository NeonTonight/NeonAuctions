package xyz.neontonight.auction.menu;

import xyz.neontonight.auction.auction.AuctionListing;
import xyz.neontonight.auction.auction.AuctionStatus;
import xyz.neontonight.auction.auction.AuctionService;
import xyz.neontonight.auction.auction.PurchaseReservation;
import xyz.neontonight.auction.auction.PurchaseResult;
import xyz.neontonight.auction.claim.AuctionClaim;
import xyz.neontonight.auction.claim.ClaimService;
import xyz.neontonight.auction.claim.ClaimType;
import xyz.neontonight.auction.config.AuctionConfig;
import xyz.neontonight.auction.redis.AuctionSyncEvent;
import xyz.neontonight.auction.scheduler.AuctionScheduler;
import xyz.neontonight.auction.serialization.ItemSerializer;
import xyz.neontonight.auction.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuctionMenuService {

    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final JavaPlugin plugin;
    private final AuctionConfig config;
    private final AuctionScheduler scheduler;
    private final AuctionService auctionService;
    private final ClaimService claimService;
    private final ItemSerializer itemSerializer = new ItemSerializer();
    private final Map<UUID, MenuSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, SellDraft> sellDrafts = new ConcurrentHashMap<>();

    public AuctionMenuService(JavaPlugin plugin, AuctionConfig config, AuctionScheduler scheduler, AuctionService auctionService, ClaimService claimService) {
        this.plugin = plugin;
        this.config = config;
        this.scheduler = scheduler;
        this.auctionService = auctionService;
        this.claimService = claimService;
    }

    public void openAuctions(Player player, int page) {
        auctionService.findActive(page).thenAccept(listings -> scheduler.runPlayer(player, () -> {
            Inventory inventory = Bukkit.createInventory(new AuctionMenuHolder(MenuType.AUCTIONS, page), 54, "Auction House" + pageTitle(page));
            decorate(inventory);
            List<String> ids = new ArrayList<>();
            for (int index = 0; index < listings.size() && index < CONTENT_SLOTS.length; index++) {
                AuctionListing listing = listings.get(index);
                ids.add(listing.id());
                inventory.setItem(CONTENT_SLOTS[index], MenuItems.auctionItem(listing, itemSerializer));
            }
            if (listings.isEmpty()) {
                inventory.setItem(22, MenuItems.empty(Material.BARRIER, "&#ffe400&lNo Auctions", "&7There are no active auctions."));
            }
            inventory.setItem(49, MenuItems.close());
            inventory.setItem(48, MenuItems.claimInfo());
            if (page > 0) {
                inventory.setItem(45, MenuItems.previous());
            }
            if (listings.size() == CONTENT_SLOTS.length) {
                inventory.setItem(53, MenuItems.next());
            }
            sessions.put(player.getUniqueId(), new MenuSession(MenuType.AUCTIONS, page, ids));
            player.openInventory(inventory);
        }));
    }

    public void openClaims(Player player) {
        claimService.findClaims(player.getUniqueId()).thenAccept(claims -> scheduler.runPlayer(player, () -> {
            Inventory inventory = Bukkit.createInventory(new AuctionMenuHolder(MenuType.CLAIMS, 0), 54, "My Claims");
            decorate(inventory);
            List<String> ids = new ArrayList<>();
            for (int index = 0; index < claims.size() && index < CONTENT_SLOTS.length; index++) {
                AuctionClaim claim = claims.get(index);
                ids.add(claim.id());
                inventory.setItem(CONTENT_SLOTS[index], MenuItems.claimItem(claim, itemSerializer));
            }
            if (claims.isEmpty()) {
                inventory.setItem(22, MenuItems.empty(Material.CHEST, "&#ffe400&lNo Claims", "&7You do not have anything to collect."));
            }
            inventory.setItem(49, MenuItems.close());
            sessions.put(player.getUniqueId(), new MenuSession(MenuType.CLAIMS, 0, ids));
            player.openInventory(inventory);
        }));
    }

    public void openListings(Player player, int page) {
        auctionService.findListingsBySeller(player.getUniqueId(), page).thenAccept(listings -> scheduler.runPlayer(player, () -> {
            Inventory inventory = Bukkit.createInventory(new AuctionMenuHolder(MenuType.LISTINGS, page), 54, "My Listings" + pageTitle(page));
            decorate(inventory);
            List<String> ids = new ArrayList<>();
            for (int index = 0; index < listings.size() && index < CONTENT_SLOTS.length; index++) {
                AuctionListing listing = listings.get(index);
                ids.add(listing.id());
                inventory.setItem(CONTENT_SLOTS[index], MenuItems.listingItem(listing, itemSerializer));
            }
            if (listings.isEmpty()) {
                inventory.setItem(22, MenuItems.empty(Material.PAPER, "&#ffe400&lNo Listings", "&7You do not have any listings."));
            }
            inventory.setItem(49, MenuItems.close());
            if (page > 0) {
                inventory.setItem(45, MenuItems.previous());
            }
            if (listings.size() == CONTENT_SLOTS.length) {
                inventory.setItem(53, MenuItems.next());
            }
            sessions.put(player.getUniqueId(), new MenuSession(MenuType.LISTINGS, page, ids));
            player.openInventory(inventory);
        }));
    }

    public void openSellConfirm(Player player, ItemStack snapshot, double price) {
        sellDrafts.put(player.getUniqueId(), new SellDraft(snapshot.clone(), price));
        Inventory inventory = Bukkit.createInventory(new AuctionMenuHolder(MenuType.SELL_CONFIRM, 0), 27, "Confirm Listing");
        decorateConfirm(inventory);
        inventory.setItem(11, MenuItems.cancel());
        inventory.setItem(13, MenuItems.sellSummary(snapshot, price));
        inventory.setItem(15, MenuItems.confirm());
        sessions.put(player.getUniqueId(), new MenuSession(MenuType.SELL_CONFIRM, 0, List.of()));
        player.openInventory(inventory);
    }

    public void openBuyConfirm(Player player, String auctionId) {
        auctionService.findAuction(auctionId).thenAccept(listing -> scheduler.runPlayer(player, () -> {
            if (listing == null || listing.status() != AuctionStatus.LISTED || listing.expiresAt().isBefore(java.time.Instant.now())) {
                player.sendMessage(Text.color(config.message("purchase-unavailable")));
                openAuctions(player, 0);
                return;
            }
            if (listing.sellerId().equals(player.getUniqueId())) {
                player.sendMessage(Text.color(config.message("seller-cannot-buy")));
                openAuctions(player, 0);
                return;
            }
            Inventory inventory = Bukkit.createInventory(new AuctionMenuHolder(MenuType.BUY_CONFIRM, 0), 27, "Confirm Purchase");
            decorateConfirm(inventory);
            inventory.setItem(11, MenuItems.cancel());
            inventory.setItem(13, MenuItems.buySummary(listing, itemSerializer));
            inventory.setItem(15, MenuItems.confirm());
            sessions.put(player.getUniqueId(), new MenuSession(MenuType.BUY_CONFIRM, 0, List.of(auctionId)));
            player.openInventory(inventory);
        }));
    }

    public void handleClick(Player player, AuctionMenuHolder holder, int rawSlot) {
        if (holder.type() == MenuType.BUY_CONFIRM || holder.type() == MenuType.SELL_CONFIRM) {
            handleConfirmClick(player, holder, rawSlot);
            return;
        }
        if (rawSlot == 49) {
            player.closeInventory();
            return;
        }
        if (holder.type() == MenuType.AUCTIONS && rawSlot == 48) {
            openClaims(player);
            return;
        }
        if (rawSlot == 45 && holder.page() > 0) {
            openPage(player, holder.type(), holder.page() - 1);
            return;
        }
        if (rawSlot == 53) {
            openPage(player, holder.type(), holder.page() + 1);
            return;
        }

        int contentIndex = contentIndex(rawSlot);
        if (contentIndex < 0) {
            return;
        }
        MenuSession session = sessions.get(player.getUniqueId());
        if (session == null || session.type() != holder.type() || contentIndex >= session.ids().size()) {
            return;
        }
        String id = session.ids().get(contentIndex);
        if (holder.type() == MenuType.AUCTIONS) {
            openBuyConfirm(player, id);
        } else if (holder.type() == MenuType.CLAIMS) {
            claim(player, id);
        }
    }

    private void handleConfirmClick(Player player, AuctionMenuHolder holder, int rawSlot) {
        if (rawSlot == 11) {
            if (holder.type() == MenuType.SELL_CONFIRM) {
                sellDrafts.remove(player.getUniqueId());
            }
            openAuctions(player, 0);
            return;
        }
        if (rawSlot != 15) {
            return;
        }
        if (holder.type() == MenuType.SELL_CONFIRM) {
            confirmSell(player);
            return;
        }
        MenuSession session = sessions.get(player.getUniqueId());
        if (session == null || session.ids().isEmpty()) {
            openAuctions(player, 0);
            return;
        }
        buy(player, session.ids().get(0));
    }

    private void confirmSell(Player player) {
        SellDraft draft = sellDrafts.remove(player.getUniqueId());
        if (draft == null) {
            player.sendMessage(Text.color(config.message("list-failed")));
            openAuctions(player, 0);
            return;
        }
        auctionService.listItem(player, draft.itemStack(), draft.price()).thenAccept(result -> scheduler.runPlayer(player, () -> {
            String message = config.message(result.messageKey())
                    .replace("%price%", String.format("%,.0f", draft.price()))
                    .replace("%min%", String.format("%,.0f", config.minPrice()))
                    .replace("%max%", String.format("%,.0f", config.maxPrice()));
            player.sendMessage(Text.color(message));
            openAuctions(player, 0);
        }));
    }

    public void invalidateAndRefresh(AuctionSyncEvent event) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            MenuSession session = sessions.get(player.getUniqueId());
            if (session == null || session.type() != MenuType.AUCTIONS) {
                continue;
            }
            scheduler.runPlayer(player, () -> {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof AuctionMenuHolder) {
                    openAuctions(player, session.page());
                }
            });
        }
    }

    private void buy(Player player, String auctionId) {
        player.sendMessage(Text.color(config.message("purchase-started")));
        UUID buyerId = player.getUniqueId();
        String buyerName = player.getName();
        auctionService.findAuction(auctionId).thenAccept(preview -> scheduler.runPlayer(player, () -> {
            if (preview == null || preview.status() != AuctionStatus.LISTED || preview.expiresAt().isBefore(java.time.Instant.now())) {
                finishPurchase(player, PurchaseResult.fail("purchase-unavailable"));
                return;
            }
            if (preview.sellerId().equals(buyerId)) {
                finishPurchase(player, PurchaseResult.fail("seller-cannot-buy"));
                return;
            }
            boolean withdrawn = auctionService.withdrawBuyer(buyerId, buyerName, preview.price(), preview.id());
            if (!withdrawn) {
                finishPurchase(player, PurchaseResult.fail("purchase-money"));
                return;
            }
            auctionService.reservePurchase(buyerId, auctionId).thenAccept(reservation -> scheduler.runPlayer(player, () -> {
                if (!reservation.successful()) {
                    auctionService.refundBuyer(buyerId, buyerName, preview.price(), preview.id());
                    finishPurchase(player, PurchaseResult.fail(reservation.messageKey()));
                    return;
                }

                AuctionListing listing = reservation.listing();
                auctionService.finalizePurchase(buyerId, buyerName, listing.id(), reservation.reservationId()).thenAccept(result -> scheduler.runPlayer(player, () -> {
                    if (!result.successful()) {
                        auctionService.refundBuyer(buyerId, buyerName, preview.price(), preview.id());
                    }
                    finishPurchase(player, result);
                }));
            }));
        }));
    }

    private void finishPurchase(Player player, PurchaseResult result) {
        player.sendMessage(Text.color(config.message(result.messageKey())));
        openAuctions(player, 0);
    }

    private void claim(Player player, String claimId) {
        claimService.previewClaim(player.getUniqueId(), claimId).thenAccept(preview -> scheduler.runPlayer(player, () -> {
            if (preview == null) {
                player.sendMessage(Text.color(config.message("claim-failed")));
                openClaims(player);
                return;
            }
            if (preview.type() == ClaimType.SELLER_PAYMENT) {
                claimPayment(player, preview);
                return;
            }
            ItemStack previewItem = preview.type() == ClaimType.SELLER_PAYMENT ? null : claimService.item(preview);
            if (!claimService.canFit(player, previewItem)) {
                player.sendMessage(Text.color(config.message("claim-full")));
                return;
            }

            claimService.reserveClaim(player.getUniqueId(), claimId).thenAccept(claim -> scheduler.runPlayer(player, () -> {
                if (claim == null) {
                    player.sendMessage(Text.color(config.message("claim-failed")));
                    openClaims(player);
                    return;
                }
                if (claim.type() == ClaimType.SELLER_PAYMENT) {
                    player.sendMessage(Text.color(config.message("claim-failed")));
                    openClaims(player);
                    return;
                }
                ItemStack item = claimService.item(claim);
                HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
                if (!leftovers.isEmpty()) {
                    player.sendMessage(Text.color(config.message("claim-full")));
                    return;
                }
                player.sendMessage(Text.color(config.message("claim-success")));
                openClaims(player);
            }));
        }));
    }

    private void claimPayment(Player player, AuctionClaim preview) {
        if (!claimService.depositPayment(preview)) {
            player.sendMessage(Text.color(config.message("claim-failed")));
            openClaims(player);
            return;
        }
        claimService.markPaymentClaimed(player.getUniqueId(), preview.id()).thenAccept(claim -> scheduler.runPlayer(player, () -> {
            if (claim == null) {
                player.sendMessage(Text.color(config.message("claim-failed")));
                openClaims(player);
                return;
            }
            player.sendMessage(Text.color(config.message("claim-success")));
            openClaims(player);
        }));
    }

    private void openPage(Player player, MenuType type, int page) {
        if (type == MenuType.AUCTIONS) {
            openAuctions(player, page);
        } else if (type == MenuType.LISTINGS) {
            openListings(player, page);
        } else {
            openClaims(player);
        }
    }

    private void decorate(Inventory inventory) {
        ItemStack border = MenuItems.border();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int row = slot / 9;
            int column = slot % 9;
            if (row == 0 || row == 5 || column == 0 || column == 8) {
                inventory.setItem(slot, border);
            }
        }
    }

    private void decorateConfirm(Inventory inventory) {
        ItemStack border = MenuItems.border();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, border);
        }
    }

    private int contentIndex(int slot) {
        for (int index = 0; index < CONTENT_SLOTS.length; index++) {
            if (CONTENT_SLOTS[index] == slot) {
                return index;
            }
        }
        return -1;
    }

    private String pageTitle(int page) {
        return page <= 0 ? "" : " (Page " + (page + 1) + ")";
    }
}
