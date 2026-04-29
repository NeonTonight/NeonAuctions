package xyz.neontonight.auction.menu;

import xyz.neontonight.auction.auction.AuctionListing;
import xyz.neontonight.auction.claim.AuctionClaim;
import xyz.neontonight.auction.claim.ClaimType;
import xyz.neontonight.auction.serialization.ItemSerializer;
import xyz.neontonight.auction.text.Text;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class MenuItems {

    private MenuItems() {
    }

    public static ItemStack border() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("");
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack close() {
        return named(Material.ARROW, "&c&lBack", List.of(
                "",
                "&7Return to the game.",
                "",
                "&7LEFT CLICK ▸ &fClose"
        ));
    }

    public static ItemStack next() {
        return named(Material.ARROW, "&#ffe400&lNext Page", List.of(
                "",
                "&7Open the next auction page.",
                "",
                "&7LEFT CLICK ▸ &fOpen"
        ));
    }

    public static ItemStack previous() {
        return named(Material.ARROW, "&#ffe400&lPrevious Page", List.of(
                "",
                "&7Open the previous auction page.",
                "",
                "&7LEFT CLICK ▸ &fOpen"
        ));
    }

    public static ItemStack confirm() {
        return named(Material.LIME_DYE, "&a&lConfirm", List.of(
                "",
                "&7Confirm this action.",
                "",
                "&7LEFT CLICK ▸&a Confirm"
        ));
    }

    public static ItemStack cancel() {
        return named(Material.RED_DYE, "&c&lCancel", List.of(
                "",
                "&7Cancel this action.",
                "",
                "&7LEFT CLICK ▸&c Cancel"
        ));
    }

    public static ItemStack claimInfo() {
        return named(Material.CHEST, "&#ffe400&lClaims", List.of(
                "",
                "&7Collect bought items, expired items,",
                "&7and seller payments here.",
                "",
                "&7LEFT CLICK ▸ &fOpen"
        ));
    }

    public static ItemStack auctionItem(AuctionListing listing, ItemSerializer serializer) {
        ItemStack item = serializer.deserialize(listing.itemData()).clone();
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(Text.color("&7▪ &fPrice: &#69d7ff" + formatMoney(listing.price()) + "$"));
        lore.add(Text.color("&7▪ &fSeller: &e" + listing.sellerName()));
        lore.add(Text.color("&7▪ &fExpires in: &e" + formatDuration(Duration.between(Instant.now(), listing.expiresAt()))));
        lore.add("");
        lore.add(Text.color("&7LEFT CLICK ▸ &fBuy"));
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack buySummary(AuctionListing listing, ItemSerializer serializer) {
        ItemStack item = serializer.deserialize(listing.itemData()).clone();
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(Text.color("&7▪ &fPrice: &#69d7ff" + formatMoney(listing.price()) + "$"));
        lore.add(Text.color("&7▪ &fSeller: &e" + listing.sellerName()));
        lore.add("");
        lore.add(Text.color("&7LEFT CLICK ▸&a Confirm purchase"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack sellSummary(ItemStack snapshot, double price) {
        ItemStack item = snapshot.clone();
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(Text.color("&7▪ &fSale price: &#69d7ff" + formatMoney(price) + "$"));
        lore.add("");
        lore.add(Text.color("&7LEFT CLICK ▸&a Confirm listing"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack listingItem(AuctionListing listing, ItemSerializer serializer) {
        ItemStack item = auctionItem(listing, serializer);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        lore.add(Text.color("&7▪ &fStatus: &e" + listing.status().name()));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack claimItem(AuctionClaim claim, ItemSerializer serializer) {
        if (claim.type() == ClaimType.SELLER_PAYMENT) {
            return named(Material.GOLD_INGOT, "&#ffe400&lSeller Payment", List.of(
                    "",
                    "&7▪ &fAmount: &#69d7ff" + formatMoney(claim.amount()) + "$",
                    "",
                    "&7LEFT CLICK ▸ &fClaim"
            ));
        }
        ItemStack item = serializer.deserialize(claim.itemData()).clone();
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(Text.color("&7▪ &fType: &e" + claim.type().name().replace('_', ' ')));
        lore.add("");
        lore.add(Text.color("&7LEFT CLICK ▸ &fClaim"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack empty(Material material, String title, String line) {
        return named(material, title, List.of("", line, ""));
    }

    private static ItemStack named(Material material, String displayName, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Text.color(displayName));
        meta.setLore(Text.color(lore));
        item.setItemMeta(meta);
        return item;
    }

    private static String formatMoney(double value) {
        return String.format("%,.0f", value);
    }

    private static String formatDuration(Duration duration) {
        if (duration.isNegative() || duration.isZero()) {
            return "now";
        }
        long days = duration.toDays();
        if (days > 0) {
            return days + "d " + duration.minusDays(days).toHours() + "h";
        }
        long hours = duration.toHours();
        if (hours > 0) {
            return hours + "h " + duration.minusHours(hours).toMinutes() + "m";
        }
        long minutes = duration.toMinutes();
        if (minutes > 0) {
            return minutes + "m";
        }
        return duration.toSeconds() + "s";
    }
}
