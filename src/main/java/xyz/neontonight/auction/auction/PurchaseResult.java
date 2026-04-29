package xyz.neontonight.auction.auction;

public record PurchaseResult(boolean successful, String messageKey) {
    public static PurchaseResult ok() {
        return new PurchaseResult(true, "purchase-success");
    }

    public static PurchaseResult fail(String messageKey) {
        return new PurchaseResult(false, messageKey);
    }
}
