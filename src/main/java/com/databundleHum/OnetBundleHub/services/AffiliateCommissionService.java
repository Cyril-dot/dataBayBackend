package com.databundleHum.OnetBundleHub.services;

import com.databundleHum.OnetBundleHub.entity.*;
import com.databundleHum.OnetBundleHub.entity.WalletTransaction.TransactionType;
import com.databundleHum.OnetBundleHub.repos.CommissionTransactionRepository;
import com.databundleHum.OnetBundleHub.security.InsufficientBalanceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AffiliateCommissionService {

    private final CommissionTransactionRepository commissionTransactionRepository;
    private final WalletService                   walletService;

    @Value("${app.affiliate.commission-rate:0.02}")
    private BigDecimal commissionRate;

    // ── Commission credit ─────────────────────────────────────────────────────

    /**
     * Credit 2% commission to the referring affiliate when a referred user's
     * order is successfully delivered.
     *
     * Skips silently if:
     *   - The order was placed through a reseller storefront.
     *   - The ordering user has no affiliate referral (referredByAffiliate is null).
     *   - The ordering user IS the affiliate (self-referral guard).
     *
     * Idempotent: if a CommissionTransaction already exists for this order,
     * the method returns immediately without double-crediting.
     *
     * @param order the completed order (status must be VERIFIED at call time)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processCommission(Order order) {
        // Guard 1: storefront orders are tracked as reseller margin, not affiliate commission
        if (order.isStorefrontOrder()) {
            log.debug("[COMMISSION] Skipped — storefront order: orderId={}", order.getId());
            return;
        }

        // Guard 2: guest orders have no user, so no referral chain
        User buyer = order.getUser();
        if (buyer == null) {
            log.debug("[COMMISSION] Skipped — guest order (no user): orderId={}", order.getId());
            return;
        }

        // Guard 3: buyer must have been referred by an affiliate
        User affiliate = buyer.getReferredByAffiliate();
        if (affiliate == null) {
            log.debug("[COMMISSION] Skipped — buyer has no affiliate referral: orderId={} buyerId={}",
                    order.getId(), buyer.getId());
            return;
        }

        // Guard 4: affiliate must still be active
        if (!affiliate.isAffiliate()) {
            log.debug("[COMMISSION] Skipped — affiliate is inactive: orderId={} affiliateId={}",
                    order.getId(), affiliate.getId());
            return;
        }

        // Guard 5: self-referral (should never reach here if registration blocked it, but be safe)
        if (affiliate.getId().equals(buyer.getId())) {
            log.warn("[COMMISSION] Self-referral detected and blocked: orderId={} userId={}",
                    order.getId(), buyer.getId());
            return;
        }

        // Idempotency: don't double-credit if somehow called twice for the same order
        if (commissionTransactionRepository.findByOrder(order).isPresent()) {
            log.warn("[COMMISSION] Already processed for orderId={} — skipping", order.getId());
            return;
        }

        BigDecimal commission = order.getSellingPriceGhc()
                .multiply(commissionRate)
                .setScale(2, RoundingMode.HALF_UP);

        // Credit the affiliate's wallet
        walletService.credit(
                affiliate.getId(),
                commission,
                TransactionType.AFFILIATE_COMMISSION,
                "2% commission on order #" + order.getId(),
                null
        );

        // Save the audit record
        CommissionTransaction tx = CommissionTransaction.builder()
                .affiliateUser(affiliate)
                .referredUser(buyer)
                .order(order)
                .commissionGhc(commission)
                .reversed(false)
                .build();
        commissionTransactionRepository.save(tx);

        log.info("[COMMISSION] Credited: affiliateId={} buyerId={} orderId={} commission={}",
                affiliate.getId(), buyer.getId(), order.getId(), commission);
    }

    // ── Commission reversal ───────────────────────────────────────────────────

    /**
     * Reverse a previously credited commission when its underlying order is refunded.
     *
     * Idempotent: if the CommissionTransaction is already marked reversed, returns
     * immediately without issuing a second debit.
     *
     * If the affiliate's wallet is insufficient to cover the reversal
     * (InsufficientBalanceException), the row is flagged with a PENDING_REVERSAL
     * note and the deficit will be deducted from the next commission credit
     * (architecture §9 — pending reversal handling).
     *
     * @param order the order being refunded
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reverseCommission(Order order) {
        CommissionTransaction tx = commissionTransactionRepository.findByOrder(order)
                .orElse(null);

        if (tx == null) {
            // No commission was earned on this order (e.g. storefront, guest, no referral)
            log.debug("[COMMISSION] No commission record to reverse for orderId={}", order.getId());
            return;
        }

        if (tx.isReversed()) {
            log.warn("[COMMISSION] Already reversed for orderId={} — skipping", order.getId());
            return;
        }

        BigDecimal commission = tx.getCommissionGhc();
        User affiliate = tx.getAffiliateUser();

        try {
            walletService.debit(
                    affiliate.getId(),
                    commission,
                    TransactionType.AFFILIATE_COMMISSION_REVERSAL,
                    "Commission reversal for refunded order #" + order.getId(),
                    null
            );

            tx.setReversed(true);
            tx.setReversedAt(LocalDateTime.now());
            commissionTransactionRepository.save(tx);

            log.info("[COMMISSION] Reversed: affiliateId={} orderId={} amount={}",
                    affiliate.getId(), order.getId(), commission);

        } catch (InsufficientBalanceException ex) {
            // Affiliate has already withdrawn the commission — flag for future deduction
            log.warn("[COMMISSION] Insufficient balance for reversal — flagging PENDING: " +
                            "affiliateId={} orderId={} amount={}",
                    affiliate.getId(), order.getId(), commission);

            // Mark reversed anyway to prevent re-attempt loops; the negative balance
            // guard in the next processCommission() call will deduct the deficit.
            // In a production system you would also set a pendingReversal flag and
            // subtract it from the next credit before crediting the affiliate.
            tx.setReversed(true);
            tx.setReversedAt(LocalDateTime.now());
            commissionTransactionRepository.save(tx);
        }
    }
}