package xyz.neontonight.auction.scheduler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class AuctionScheduler implements AutoCloseable {

    private final Plugin plugin;
    private final ExecutorService executor;

    public AuctionScheduler(Plugin plugin, int asyncThreads) {
        this.plugin = plugin;
        this.executor = Executors.newFixedThreadPool(asyncThreads, new ThreadFactory() {
            private int count;

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "NeonAuction-IO-" + ++count);
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, executor);
    }

    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    public void runPlayer(Player player, Runnable runnable) {
        player.getScheduler().run(plugin, task -> runnable.run(), null);
    }

    public void runAsyncTimer(Runnable runnable, long initialDelay, long period, TimeUnit unit) {
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> runnable.run(), initialDelay, period, unit);
    }

    @Override
    public void close() {
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        executor.shutdownNow();
    }
}
