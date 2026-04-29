package xyz.neontonight.auction.database;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.ne;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;

import xyz.neontonight.auction.claim.AuctionClaim;
import xyz.neontonight.auction.claim.ClaimType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.ReturnDocument;
import org.bson.Document;

public final class MongoClaimRepository {

    private final MongoCollection<Document> collection;

    public MongoClaimRepository(MongoDatabase database) {
        this.collection = database.getCollection("claims");
        collection.createIndex(Indexes.ascending("playerId", "claimed"));
        collection.createIndex(Indexes.ascending("auctionId", "type"));
    }

    public void upsertClaim(AuctionClaim claim) {
        collection.replaceOne(eq("_id", claim.id()), toDocument(claim), new ReplaceOptions().upsert(true));
    }

    public List<AuctionClaim> findUnclaimed(UUID playerId, int limit) {
        List<AuctionClaim> claims = new ArrayList<>();
        collection.find(and(eq("playerId", playerId.toString()), eq("claimed", false)))
                .sort(Indexes.ascending("createdAt"))
                .limit(limit)
                .forEach(document -> claims.add(fromDocument(document)));
        return claims;
    }

    public AuctionClaim findUnclaimed(UUID playerId, String claimId) {
        Document document = collection.find(and(eq("_id", claimId), eq("playerId", playerId.toString()), eq("claimed", false))).first();
        return document == null ? null : fromDocument(document);
    }

    public AuctionClaim claim(String claimId, UUID playerId) {
        Document document = collection.findOneAndUpdate(
                and(eq("_id", claimId), eq("playerId", playerId.toString()), eq("claimed", false)),
                combine(set("claimed", true), set("claimedAt", Instant.now().toEpochMilli())),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        );
        return document == null ? null : fromDocument(document);
    }

    public AuctionClaim claimIfItem(String claimId, UUID playerId) {
        Document document = collection.findOneAndUpdate(
                and(
                        eq("_id", claimId),
                        eq("playerId", playerId.toString()),
                        eq("claimed", false),
                        ne("type", ClaimType.SELLER_PAYMENT.name())
                ),
                combine(set("claimed", true), set("claimedAt", Instant.now().toEpochMilli())),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        );
        return document == null ? null : fromDocument(document);
    }

    public AuctionClaim markClaimed(String claimId, UUID playerId) {
        return claim(claimId, playerId);
    }

    public static Document toDocument(AuctionClaim claim) {
        return new Document("_id", claim.id())
                .append("playerId", claim.playerId().toString())
                .append("playerName", claim.playerName())
                .append("type", claim.type().name())
                .append("auctionId", claim.auctionId())
                .append("itemData", claim.itemData())
                .append("amount", claim.amount())
                .append("claimed", claim.claimed())
                .append("createdAt", claim.createdAt().toEpochMilli())
                .append("claimedAt", claim.claimedAt() == null ? null : claim.claimedAt().toEpochMilli());
    }

    public static AuctionClaim fromDocument(Document document) {
        Long claimedAt = document.getLong("claimedAt");
        return new AuctionClaim(
                document.getString("_id"),
                UUID.fromString(document.getString("playerId")),
                document.getString("playerName"),
                ClaimType.valueOf(document.getString("type")),
                document.getString("auctionId"),
                document.getString("itemData"),
                document.getDouble("amount"),
                document.getBoolean("claimed", false),
                Instant.ofEpochMilli(document.getLong("createdAt")),
                claimedAt == null ? null : Instant.ofEpochMilli(claimedAt)
        );
    }
}
