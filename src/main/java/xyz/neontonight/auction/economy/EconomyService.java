package xyz.neontonight.auction.economy;

import java.util.UUID;

public interface EconomyService {

    boolean available();

    boolean withdraw(UUID playerId, String playerName, double amount, String transactionId);

    boolean deposit(UUID playerId, String playerName, double amount, String transactionId);
}
