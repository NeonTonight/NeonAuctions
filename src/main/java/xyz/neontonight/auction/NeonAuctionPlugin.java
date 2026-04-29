package xyz.neontonight.auction;

import xyz.neontonight.auction.auction.AuctionService;
import xyz.neontonight.auction.claim.ClaimService;
import xyz.neontonight.auction.command.AuctionCommand;
import xyz.neontonight.auction.config.AuctionConfig;
import xyz.neontonight.auction.database.MongoConnection;
import xyz.neontonight.auction.database.MongoAuctionRepository;
import xyz.neontonight.auction.database.MongoClaimRepository;
import xyz.neontonight.auction.economy.EconomyService;
import xyz.neontonight.auction.economy.VaultEconomyService;
import xyz.neontonight.auction.menu.AuctionMenuListener;
import xyz.neontonight.auction.menu.AuctionMenuService;
import xyz.neontonight.auction.redis.RedisSyncService;
import xyz.neontonight.auction.scheduler.AuctionScheduler;
import xyz.neontonight.auction.serialization.ItemSerializer;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class NeonAuctionPlugin extends JavaPlugin {

    private AuctionConfig auctionConfig;
    private AuctionScheduler scheduler;
    private MongoConnection mongoConnection;
    private RedisSyncService redisSyncService;
    private AuctionService auctionService;
    private ClaimService claimService;
    private AuctionMenuService menuService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("credentials.yml", false);

        this.auctionConfig = AuctionConfig.load(this);
        this.scheduler = new AuctionScheduler(this, auctionConfig.asyncThreads());

        try {
            this.mongoConnection = new MongoConnection(auctionConfig);
            MongoAuctionRepository auctionRepository = new MongoAuctionRepository(mongoConnection.database());
            MongoClaimRepository claimRepository = new MongoClaimRepository(mongoConnection.database());
            ItemSerializer itemSerializer = new ItemSerializer();
            EconomyService economyService = new VaultEconomyService(this);

            this.redisSyncService = new RedisSyncService(this, auctionConfig, scheduler);
            this.auctionService = new AuctionService(
                    this,
                    auctionConfig,
                    scheduler,
                    auctionRepository,
                    claimRepository,
                    itemSerializer,
                    economyService,
                    redisSyncService
            );
            this.claimService = new ClaimService(this, scheduler, claimRepository, itemSerializer, economyService);
            this.menuService = new AuctionMenuService(this, auctionConfig, scheduler, auctionService, claimService);

            redisSyncService.start(menuService::invalidateAndRefresh);
            auctionService.recoverStartupState();
            auctionService.startExpirationScanner();

            getServer().getPluginManager().registerEvents(new AuctionMenuListener(menuService), this);
            AuctionCommand command = new AuctionCommand(this, auctionConfig, scheduler, auctionService, claimService, menuService);
            PluginCommand pluginCommand = getCommand("ah");
            if (pluginCommand != null) {
                pluginCommand.setExecutor(command);
                pluginCommand.setTabCompleter(command);
            }
        } catch (RuntimeException exception) {
            getLogger().severe("NeonAuction failed to start: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (redisSyncService != null) {
            redisSyncService.close();
        }
        if (mongoConnection != null) {
            mongoConnection.close();
        }
        if (scheduler != null) {
            scheduler.close();
        }
    }

    public AuctionConfig auctionConfig() {
        return auctionConfig;
    }
}
