package com.databundleHum.OnetBundleHub.services;

import com.databundleHum.OnetBundleHub.dtos.response.WalletResponse;
import com.databundleHum.OnetBundleHub.dtos.response.WalletTransactionResponse;
import com.databundleHum.OnetBundleHub.entity.User;
import com.databundleHum.OnetBundleHub.entity.WalletTransaction;
import com.databundleHum.OnetBundleHub.repos.UserRepository;
import com.databundleHum.OnetBundleHub.repos.WalletTransactionRepository;
import com.databundleHum.OnetBundleHub.security.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Read-only wallet queries — balance and paginated transaction ledger.
 * Kept separate from WalletServiceImpl (which handles mutations) for clarity.
 * Used by WalletController for GET /api/wallet/balance and GET /api/wallet/transactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletQueryService {

    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Transactional(readOnly = true)
    public WalletResponse getBalance(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        log.debug("Wallet balance fetched: userId={} balance={}", userId, user.getWalletBalance());
        return WalletResponse.builder()
                .userId(userId)
                .balance(user.getWalletBalance())
                .build();
    }

    @Transactional(readOnly = true)
    public Page<WalletTransactionResponse> getTransactions(UUID userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        return walletTransactionRepository
                .findByUserOrderByCreatedAtDesc(user, pageable)
                .map(this::toResponse);
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private WalletTransactionResponse toResponse(WalletTransaction t) {
        return WalletTransactionResponse.builder()
                .id(t.getId())
                .type(t.getType().name())        // enum → String, matches String field in DTO
                .amount(t.getAmount())
                .balanceAfter(t.getBalanceAfter())
                .reference(t.getReference())
                .description(t.getDescription())
                .createdAt(t.getCreatedAt())
                .build();
    }
}