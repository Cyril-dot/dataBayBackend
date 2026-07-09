package com.databundleHum.OnetBundleHub.controllers;

import com.databundleHum.OnetBundleHub.dtos.response.WalletResponse;
import com.databundleHum.OnetBundleHub.dtos.response.WalletTransactionResponse;
import com.databundleHum.OnetBundleHub.security.UserPrincipal;
import com.databundleHum.OnetBundleHub.services.WalletQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Wallet read-only controller.
 *
 * <p>Mutations (debit/credit) are internal — triggered via OrderService/ResellerService.
 * This controller exposes only balance and transaction ledger to the authenticated user.
 *
 * <p>Base path: {@code /api/v1/wallet}
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Wallet", description = "Wallet balance and transaction history")
public class WalletController {

    private final WalletQueryService walletQueryService;

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        return principal.userId();
    }

    @GetMapping("/balance")
    @Operation(summary = "Get current wallet balance for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Balance returned")
    public ResponseEntity<WalletResponse> getBalance() {
        UUID userId = currentUserId();
        log.debug("[WALLET] Balance requested: userId={}", userId);
        WalletResponse response = walletQueryService.getBalance(userId);
        log.debug("[WALLET] Balance fetched: userId={} balance={}", userId, response.getBalance());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions")
    @Operation(summary = "Get paginated wallet transaction ledger for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Transaction history returned")
    public ResponseEntity<Page<WalletTransactionResponse>> getTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID userId = currentUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        log.debug("[WALLET] Transactions requested: userId={} page={} size={}", userId, page, size);
        return ResponseEntity.ok(walletQueryService.getTransactions(userId, pageable));
    }
}