package xyz.neontonight.auction.claim;

import java.time.Instant;
import java.util.UUID;

public record AuctionClaim(
        String id,
        UUID playerId,
        String playerName,
        ClaimType type,
        String auctionId,
        String itemData,
        double amount,
        boolean claimed,
        Instant createdAt,
        Instant claimedAt
) {
}
