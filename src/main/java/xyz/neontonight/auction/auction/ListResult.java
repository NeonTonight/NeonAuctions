package xyz.neontonight.auction.auction;

public record ListResult(boolean successful, String messageKey, String auctionId, boolean itemRemoved) {
    public static ListResult ok(String auctionId) {
        return new ListResult(true, "listed", auctionId, true);
    }

    public static ListResult pendingRemoval(String auctionId) {
        return new ListResult(true, "listed", auctionId, false);
    }

    public static ListResult fail(String messageKey) {
        return new ListResult(false, messageKey, null, false);
    }

    public static ListResult fail(String messageKey, String auctionId, boolean itemRemoved) {
        return new ListResult(false, messageKey, auctionId, itemRemoved);
    }
}
