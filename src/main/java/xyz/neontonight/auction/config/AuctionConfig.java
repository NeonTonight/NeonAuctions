package xyz.neontonight.auction.config;

import java.io.File;
import java.util.Locale;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public record AuctionConfig(
        String serverId,
        String mongoUri,
        String mongoDatabase,
        String redisUri,
        String redisChannelPrefix,
        long defaultDurationMinutes,
        int maxListingsPerPlayer,
        double minPrice,
        double maxPrice,
        long reservationTimeoutSeconds,
        int pageSize,
        long cacheRefreshSeconds,
        long expirationScanSeconds,
        long creatingRecoveryMinutes,
        int asyncThreads,
        FileConfiguration messages
) {

    public static AuctionConfig load(JavaPlugin plugin) {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        File credentialsFile = new File(plugin.getDataFolder(), "credentials.yml");
        FileConfiguration credentials = YamlConfiguration.loadConfiguration(credentialsFile);

        String serverId = config.getString("server-id", "server-1").toLowerCase(Locale.ROOT);
        return new AuctionConfig(
                serverId,
                credentials.getString("mongodb.uri", "mongodb://localhost:27017/admin"),
                credentials.getString("mongodb.database", "neon_auction"),
                credentials.getString("redis.uri", "redis://localhost:6379"),
                credentials.getString("redis.channel-prefix", "neon:auction"),
                config.getLong("auction.default-duration-minutes", 1440L),
                config.getInt("auction.max-listings-per-player", 20),
                config.getDouble("auction.min-price", 1.0D),
                config.getDouble("auction.max-price", 100000000.0D),
                config.getLong("auction.reservation-timeout-seconds", 30L),
                Math.max(1, Math.min(28, config.getInt("auction.page-size", 28))),
                config.getLong("auction.cache-refresh-seconds", 45L),
                config.getLong("auction.expiration-scan-seconds", 60L),
                config.getLong("auction.creating-recovery-minutes", 10L),
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                config
        );
    }

    public String message(String key) {
        return messages.getString("messages." + key, "&4&l✘ &cMissing message: " + key);
    }
}
