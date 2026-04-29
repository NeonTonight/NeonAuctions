package xyz.neontonight.auction.redis;

import xyz.neontonight.auction.config.AuctionConfig;
import xyz.neontonight.auction.scheduler.AuctionScheduler;

import java.util.function.Consumer;

import com.google.gson.Gson;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.bukkit.plugin.java.JavaPlugin;

public final class RedisSyncService implements AutoCloseable {

    private final JavaPlugin plugin;
    private final AuctionConfig config;
    private final AuctionScheduler scheduler;
    private final Gson gson = new Gson();
    private final String channel;
    private RedisClient client;
    private StatefulRedisConnection<String, String> publishConnection;
    private StatefulRedisPubSubConnection<String, String> subscribeConnection;

    public RedisSyncService(JavaPlugin plugin, AuctionConfig config, AuctionScheduler scheduler) {
        this.plugin = plugin;
        this.config = config;
        this.scheduler = scheduler;
        this.channel = config.redisChannelPrefix() + ":updates";
    }

    public void start(Consumer<AuctionSyncEvent> eventConsumer) {
        try {
            client = RedisClient.create(RedisURI.create(config.redisUri()));
            publishConnection = client.connect();
            subscribeConnection = client.connectPubSub();
            subscribeConnection.addListener(new RedisPubSubListener<>() {
                @Override
                public void message(String channel, String message) {
                    scheduler.runAsync(() -> handleMessage(message, eventConsumer));
                }

                @Override
                public void message(String pattern, String channel, String message) {
                    message(channel, message);
                }

                @Override
                public void subscribed(String channel, long count) {
                }

                @Override
                public void psubscribed(String pattern, long count) {
                }

                @Override
                public void unsubscribed(String channel, long count) {
                }

                @Override
                public void punsubscribed(String pattern, long count) {
                }
            });
            subscribeConnection.async().subscribe(channel);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Redis sync is offline: " + exception.getMessage());
            close();
        }
    }

    public void publish(String type, String auctionId, int version) {
        if (publishConnection == null || !publishConnection.isOpen()) {
            return;
        }
        AuctionSyncEvent event = new AuctionSyncEvent(type, auctionId, version, config.serverId());
        publishConnection.async().publish(channel, gson.toJson(event));
    }

    private void handleMessage(String message, Consumer<AuctionSyncEvent> eventConsumer) {
        try {
            AuctionSyncEvent event = gson.fromJson(message, AuctionSyncEvent.class);
            if (event == null || config.serverId().equals(event.serverId())) {
                return;
            }
            eventConsumer.accept(event);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Ignored invalid Redis auction event: " + exception.getMessage());
        }
    }

    @Override
    public void close() {
        if (subscribeConnection != null) {
            subscribeConnection.close();
        }
        if (publishConnection != null) {
            publishConnection.close();
        }
        if (client != null) {
            client.shutdown();
        }
    }
}
