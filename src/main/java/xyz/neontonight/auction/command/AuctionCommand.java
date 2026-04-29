package xyz.neontonight.auction.command;

import xyz.neontonight.auction.auction.AuctionService;
import xyz.neontonight.auction.claim.ClaimService;
import xyz.neontonight.auction.config.AuctionConfig;
import xyz.neontonight.auction.menu.AuctionMenuService;
import xyz.neontonight.auction.scheduler.AuctionScheduler;
import xyz.neontonight.auction.text.Text;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuctionCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private AuctionConfig config;
    private final AuctionScheduler scheduler;
    private final AuctionService auctionService;
    private final ClaimService claimService;
    private final AuctionMenuService menuService;

    public AuctionCommand(
            JavaPlugin plugin,
            AuctionConfig config,
            AuctionScheduler scheduler,
            AuctionService auctionService,
            ClaimService claimService,
            AuctionMenuService menuService
    ) {
        this.plugin = plugin;
        this.config = config;
        this.scheduler = scheduler;
        this.auctionService = auctionService;
        this.claimService = claimService;
        this.menuService = menuService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return player(sender, player -> {
                if (!player.hasPermission("neonauction.use")) {
                    message(player, "no-permission");
                    return;
                }
                menuService.openAuctions(player, 0);
            });
        }

        String subCommand = args[0].toLowerCase();
        if (subCommand.equals("sell")) {
            return sell(sender, args);
        }
        if (subCommand.equals("claims")) {
            return player(sender, player -> {
                if (!player.hasPermission("neonauction.use")) {
                    message(player, "no-permission");
                    return;
                }
                menuService.openClaims(player);
            });
        }
        if (subCommand.equals("listings")) {
            return player(sender, player -> {
                if (!player.hasPermission("neonauction.use")) {
                    message(player, "no-permission");
                    return;
                }
                menuService.openListings(player, 0);
            });
        }
        if (subCommand.equals("reload")) {
            if (!sender.hasPermission("neonauction.reload")) {
                sender.sendMessage(Text.color(config.message("no-permission")));
                return true;
            }
            this.config = AuctionConfig.load(plugin);
            sender.sendMessage(Text.color(config.message("reloaded")));
            return true;
        }

        sender.sendMessage(Text.color(config.message("usage-sell")));
        return true;
    }

    private boolean sell(CommandSender sender, String[] args) {
        return player(sender, player -> {
            if (!player.hasPermission("neonauction.sell")) {
                message(player, "no-permission");
                return;
            }
            if (args.length < 2) {
                message(player, "usage-sell");
                return;
            }
            double price;
            try {
                price = Double.parseDouble(args[1]);
            } catch (NumberFormatException exception) {
                invalidPrice(player);
                return;
            }
            if (price < config.minPrice() || price > config.maxPrice()) {
                invalidPrice(player);
                return;
            }
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item == null || item.getType() == Material.AIR) {
                message(player, "hold-item");
                return;
            }
            menuService.openSellConfirm(player, item.clone(), price);
        });
    }

    private boolean player(CommandSender sender, PlayerAction action) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.color(config.message("player-only")));
            return true;
        }
        action.run(player);
        return true;
    }

    private void invalidPrice(Player player) {
        String message = config.message("invalid-price")
                .replace("%min%", String.format("%,.0f", config.minPrice()))
                .replace("%max%", String.format("%,.0f", config.maxPrice()));
        player.sendMessage(Text.color(message));
    }

    private void message(Player player, String key) {
        player.sendMessage(Text.color(config.message(key)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("sell", "claims", "listings"));
            if (sender.hasPermission("neonauction.reload")) {
                options.add("reload");
            }
            String prefix = args[0].toLowerCase();
            return options.stream().filter(option -> option.startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            return List.of("100", "1000", "10000");
        }
        return List.of();
    }

    @FunctionalInterface
    private interface PlayerAction {
        void run(Player player);
    }
}
