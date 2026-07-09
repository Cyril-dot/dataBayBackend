package com.databundleHum.OnetBundleHub.services;

import com.databundleHum.OnetBundleHub.entity.WalletTransaction.TransactionType;
import java.math.BigDecimal;
import java.util.UUID;

public interface WalletService {

    /**
     * Debit the user's wallet. Throws InsufficientBalanceException if balance is too low.
     */
    void debit(UUID userId, BigDecimal amount, TransactionType type,
               String description, String reference);

    /**
     * Credit the user's wallet.
     */
    void credit(UUID userId, BigDecimal amount, TransactionType type,
                String description, String reference);

    /**
     * Returns the current wallet balance for the user.
     */
    BigDecimal getBalance(UUID userId);
}