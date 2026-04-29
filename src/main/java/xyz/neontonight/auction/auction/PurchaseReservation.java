package xyz.neontonight.auction.auction;

public record PurchaseReservation(AuctionListing listing, String reservationId, String messageKey) {

    public static PurchaseReservation ok(AuctionListing listing, String reservationId) {
        return new PurchaseReservation(listing, reservationId, null);
    }

    public static PurchaseReservation fail(String messageKey) {
        return new PurchaseReservation(null, null, messageKey);
    }

    public boolean successful() {
        return listing != null;
    }
}
