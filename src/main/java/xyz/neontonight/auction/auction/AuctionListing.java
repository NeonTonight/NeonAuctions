package xyz.neontonight.auction.auction;

import java.time.Instant;
import java.util.UUID;

public record AuctionListing(
        String id,
        UUID sellerId,
        String sellerName,
        UUID buyerId,
        String itemData,
        String itemHash,
        double price,
        AuctionStatus status,
        int version,
        Instant createdAt,
        Instant expiresAt,
        String serverId,
        boolean itemRemoved,
        String reservationId,
        Instant reservationExpiresAt
) {
}
