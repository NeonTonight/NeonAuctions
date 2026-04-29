package xyz.neontonight.auction.economy;

import java.util.UUID;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class VaultEconomyService implements EconomyService {

    private final JavaPlugin plugin;

    public VaultEconomyService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean available() {
        return economy() != null;
    }

    @Override
    public boolean withdraw(UUID playerId, String playerName, double amount, String transactionId) {
        Economy economy = economy();
        if (economy == null) {
            return false;
        }
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerId);
        if (!economy.has(player, amount)) {
            return false;
        }
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    @Override
    public boolean deposit(UUID playerId, String playerName, double amount, String transactionId) {
        Economy economy = economy();
        if (economy == null) {
            return false;
        }
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerId);
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    private Economy economy() {
        Server server = plugin.getServer();
        RegisteredServiceProvider<Economy> provider = server.getServicesManager().getRegistration(Economy.class);
        return provider == null ? null : provider.getProvider();
    }
}
