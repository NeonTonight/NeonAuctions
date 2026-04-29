package xyz.neontonight.auction.database;

import xyz.neontonight.auction.config.AuctionConfig;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

public final class MongoConnection implements AutoCloseable {

    private final MongoClient client;
    private final MongoDatabase database;

    public MongoConnection(AuctionConfig config) {
        this.client = MongoClients.create(config.mongoUri());
        this.database = client.getDatabase(config.mongoDatabase());
    }

    public MongoDatabase database() {
        return database;
    }

    @Override
    public void close() {
        client.close();
    }
}
