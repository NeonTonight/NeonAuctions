package xyz.neontonight.auction.redis;

public record AuctionSyncEvent(String type, String auctionId, int version, String serverId) {
}
