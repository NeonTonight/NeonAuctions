package xyz.neontonight.auction.database;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gt;
import static com.mongodb.client.model.Filters.lt;
import static com.mongodb.client.model.Filters.ne;
import static com.mongodb.client.model.Filters.or;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.currentDate;
import static com.mongodb.client.model.Updates.inc;
import static com.mongodb.client.model.Updates.set;

import xyz.neontonight.auction.auction.AuctionListing;
import xyz.neontonight.auction.auction.AuctionStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReturnDocument;
import org.bson.Document;
import org.bson.conversions.Bson;

public final class MongoAuctionRepository {

    private final MongoCollection<Document> collection;

    public MongoAuctionRepository(MongoDatabase database) {
        this.collection = database.getCollection("auctions");
        collection.createIndex(Indexes.ascending("status", "expiresAt"));
        collection.createIndex(Indexes.ascending("sellerId", "status"));
        collection.createIndex(Indexes.ascending("status", "createdAt"));
        collection.createIndex(Indexes.ascending("reservationExpiresAt"));
    }

    public AuctionListing createCreating(AuctionListing listing) {
        collection.insertOne(toDocument(listing));
        return listing;
    }

    public boolean markListed(String auctionId) {
        return collection.updateOne(
                and(eq("_id", auctionId), eq("status", AuctionStatus.CREATING.name())),
                combine(set("status", AuctionStatus.LISTED.name()), inc("version", 1), currentDate("updatedAt"))
        ).getModifiedCount() == 1;
    }

    public boolean markFailed(String auctionId) {
        return collection.updateOne(
                and(eq("_id", auctionId), eq("status", AuctionStatus.CREATING.name())),
                combine(set("status", AuctionStatus.FAILED.name()), inc("version", 1), currentDate("updatedAt"))
        ).getModifiedCount() == 1;
    }

    public boolean markItemRemoved(String auctionId) {
        return collection.updateOne(
                and(eq("_id", auctionId), eq("status", AuctionStatus.CREATING.name())),
                combine(set("itemRemoved", true), currentDate("updatedAt"))
        ).getModifiedCount() == 1;
    }

    public List<AuctionListing> findActive(int page, int pageSize, Instant now) {
        List<AuctionListing> listings = new ArrayList<>();
        collection.find(and(eq("status", AuctionStatus.LISTED.name()), gt("expiresAt", now.toEpochMilli())))
                .sort(Indexes.descending("createdAt"))
                .skip(Math.max(0, page) * pageSize)
                .limit(pageSize)
                .forEach(document -> listings.add(fromDocument(document)));
        return listings;
    }

    public List<AuctionListing> findBySeller(UUID sellerId, int page, int pageSize) {
        List<AuctionListing> listings = new ArrayList<>();
        collection.find(and(eq("sellerId", sellerId.toString()), ne("status", AuctionStatus.CLAIMED.name())))
                .sort(Indexes.descending("createdAt"))
                .skip(Math.max(0, page) * pageSize)
                .limit(pageSize)
                .forEach(document -> listings.add(fromDocument(document)));
        return listings;
    }

    public long countActiveBySeller(UUID sellerId) {
        return collection.countDocuments(and(eq("sellerId", sellerId.toString()), eq("status", AuctionStatus.LISTED.name())));
    }

    public AuctionListing findById(String auctionId) {
        Document document = collection.find(eq("_id", auctionId)).first();
        return document == null ? null : fromDocument(document);
    }

    public AuctionListing reserveForPurchase(String auctionId, UUID buyerId, String reservationId, Instant now, Instant reservedUntil) {
        Document document = collection.findOneAndUpdate(
                and(eq("_id", auctionId), eq("status", AuctionStatus.LISTED.name()), gt("expiresAt", now.toEpochMilli())),
                combine(
                        set("status", AuctionStatus.RESERVED.name()),
                        set("buyerId", buyerId.toString()),
                        set("reservationId", reservationId),
                        set("reservationExpiresAt", reservedUntil.toEpochMilli()),
                        inc("version", 1),
                        currentDate("updatedAt")
                ),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        );
        return document == null ? null : fromDocument(document);
    }

    public AuctionListing markSold(String auctionId, UUID buyerId, String reservationId) {
        Document document = collection.findOneAndUpdate(
                and(
                        eq("_id", auctionId),
                        eq("status", AuctionStatus.RESERVED.name()),
                        eq("buyerId", buyerId.toString()),
                        eq("reservationId", reservationId)
                ),
                combine(set("status", AuctionStatus.SOLD.name()), inc("version", 1), currentDate("updatedAt")),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        );
        return document == null ? null : fromDocument(document);
    }

    public void releaseReservation(String auctionId, UUID buyerId, String reservationId) {
        collection.updateOne(
                and(
                        eq("_id", auctionId),
                        eq("status", AuctionStatus.RESERVED.name()),
                        eq("buyerId", buyerId.toString()),
                        eq("reservationId", reservationId)
                ),
                combine(
                        set("status", AuctionStatus.LISTED.name()),
                        set("buyerId", null),
                        set("reservationId", null),
                        set("reservationExpiresAt", null),
                        inc("version", 1),
                        currentDate("updatedAt")
                )
        );
    }

    public List<AuctionListing> expireListed(Instant now, int limit) {
        List<AuctionListing> expired = new ArrayList<>();
        collection.find(and(eq("status", AuctionStatus.LISTED.name()), lt("expiresAt", now.toEpochMilli())))
                .limit(limit)
                .forEach(document -> {
                    String id = document.getString("_id");
                    Document updated = collection.findOneAndUpdate(
                            and(eq("_id", id), eq("status", AuctionStatus.LISTED.name())),
                            combine(set("status", AuctionStatus.EXPIRED.name()), inc("version", 1), currentDate("updatedAt")),
                            new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
                    );
                    if (updated != null) {
                        expired.add(fromDocument(updated));
                    }
                });
        return expired;
    }

    public List<AuctionListing> releaseExpiredReservations(Instant now, int limit) {
        List<AuctionListing> released = new ArrayList<>();
        collection.find(and(eq("status", AuctionStatus.RESERVED.name()), lt("reservationExpiresAt", now.toEpochMilli())))
                .limit(limit)
                .forEach(document -> {
                    String id = document.getString("_id");
                    Document updated = collection.findOneAndUpdate(
                            and(eq("_id", id), eq("status", AuctionStatus.RESERVED.name()), lt("reservationExpiresAt", now.toEpochMilli())),
                            combine(
                                    set("status", AuctionStatus.LISTED.name()),
                                    set("buyerId", null),
                                    set("reservationId", null),
                                    set("reservationExpiresAt", null),
                                    inc("version", 1),
                                    currentDate("updatedAt")
                            ),
                            new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
                    );
                    if (updated != null) {
                        released.add(fromDocument(updated));
                    }
                });
        return released;
    }

    public List<AuctionListing> findStaleCreating(Instant olderThan, int limit) {
        List<AuctionListing> stale = new ArrayList<>();
        collection.find(and(eq("status", AuctionStatus.CREATING.name()), lt("createdAt", olderThan.toEpochMilli())))
                .limit(limit)
                .forEach(document -> stale.add(fromDocument(document)));
        return stale;
    }

    public List<AuctionListing> findSoldWithoutFulfillment(int limit) {
        List<AuctionListing> sold = new ArrayList<>();
        Bson filter = and(eq("status", AuctionStatus.SOLD.name()), or(eq("fulfillmentComplete", false), eq("fulfillmentComplete", null)));
        collection.find(filter).limit(limit).forEach(document -> sold.add(fromDocument(document)));
        return sold;
    }

    public void markFulfilled(String auctionId) {
        collection.updateOne(eq("_id", auctionId), combine(set("fulfillmentComplete", true), currentDate("updatedAt")));
    }

    public static Document toDocument(AuctionListing listing) {
        Document document = new Document("_id", listing.id())
                .append("sellerId", listing.sellerId().toString())
                .append("sellerName", listing.sellerName())
                .append("buyerId", listing.buyerId() == null ? null : listing.buyerId().toString())
                .append("itemData", listing.itemData())
                .append("itemHash", listing.itemHash())
                .append("price", listing.price())
                .append("status", listing.status().name())
                .append("version", listing.version())
                .append("createdAt", listing.createdAt().toEpochMilli())
                .append("expiresAt", listing.expiresAt().toEpochMilli())
                .append("serverId", listing.serverId())
                .append("itemRemoved", listing.itemRemoved())
                .append("reservationId", listing.reservationId())
                .append("reservationExpiresAt", listing.reservationExpiresAt() == null ? null : listing.reservationExpiresAt().toEpochMilli())
                .append("fulfillmentComplete", false);
        return document;
    }

    public static AuctionListing fromDocument(Document document) {
        String buyerId = document.getString("buyerId");
        Long reservationExpiresAt = document.getLong("reservationExpiresAt");
        return new AuctionListing(
                document.getString("_id"),
                UUID.fromString(document.getString("sellerId")),
                document.getString("sellerName"),
                buyerId == null ? null : UUID.fromString(buyerId),
                document.getString("itemData"),
                document.getString("itemHash"),
                document.getDouble("price"),
                AuctionStatus.valueOf(document.getString("status")),
                document.getInteger("version", 0),
                Instant.ofEpochMilli(document.getLong("createdAt")),
                Instant.ofEpochMilli(document.getLong("expiresAt")),
                document.getString("serverId"),
                document.getBoolean("itemRemoved", false),
                document.getString("reservationId"),
                reservationExpiresAt == null ? null : Instant.ofEpochMilli(reservationExpiresAt)
        );
    }
}
