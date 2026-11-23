package com.infinevo.core.ai;

import com.infinevo.core.subscription.Subscription;
import com.infinevo.core.subscription.SubscriptionRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link AiAccessGate} (W-114.1).
 *
 * <p>The switch and the cap are read from {@code core.subscription}, the count from {@code core.ai_usage}, both
 * through row-level security for the tenant bound in {@link TenantContext}. The month is the calendar month in UTC,
 * taken from {@link Clock} so a test can move it; a new month is a new row and nothing resets.
 */
@Service
public class AiAccessGateImpl implements AiAccessGate {

    private final SubscriptionRepository subscriptionRepository;
    private final AiUsageRepository aiUsageRepository;
    private final Clock clock;

    @Autowired
    public AiAccessGateImpl(SubscriptionRepository subscriptionRepository, AiUsageRepository aiUsageRepository) {
        this(subscriptionRepository, aiUsageRepository, Clock.systemUTC());
    }

    AiAccessGateImpl(SubscriptionRepository subscriptionRepository, AiUsageRepository aiUsageRepository, Clock clock) {
        this.subscriptionRepository =
                Objects.requireNonNull(subscriptionRepository, "subscriptionRepository must not be null");
        this.aiUsageRepository = Objects.requireNonNull(aiUsageRepository, "aiUsageRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public AiStatus status() {
        UUID tenantId = TenantContext.require();
        YearMonth month = currentMonth();
        Optional<Subscription> subscription = subscriptionRepository.findByTenantId(tenantId);
        boolean enabled = subscription.map(Subscription::isAiEnabled).orElse(false);
        Integer cap = subscription.map(Subscription::getAiMonthlyRequestCap).orElse(null);
        int used = aiUsageRepository
                .findByTenantIdAndUsageMonth(tenantId, month.atDay(1))
                .map(AiUsage::getRequestCount)
                .orElse(0);
        return new AiStatus(enabled, cap, used, month.toString());
    }

    @Override
    @Transactional
    public int consume() {
        UUID tenantId = TenantContext.require();
        Subscription subscription =
                subscriptionRepository.findByTenantId(tenantId).orElseThrow(AiDisabledException::new);
        Integer cap = subscription.getAiMonthlyRequestCap();
        // The database refuses "on with no cap" (ck_subscription_ai_enabled_needs_cap); if it were ever seen, off.
        if (!subscription.isAiEnabled() || cap == null) {
            throw new AiDisabledException();
        }
        LocalDate monthStart = currentMonth().atDay(1);
        return aiUsageRepository.countOne(tenantId, monthStart, cap).orElseThrow(() -> new AiCapReachedException(cap));
    }

    private YearMonth currentMonth() {
        return YearMonth.from(clock.instant().atZone(ZoneOffset.UTC));
    }
}
