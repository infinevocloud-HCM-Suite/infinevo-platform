package com.infinevo.payroll.proof;

import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHouseRent;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHouseRentRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link ProofAmountReader} (W-34.2).
 *
 * <p>Returns approved amounts keyed by {@code (sourceKind, sourceLineId)} when the proof is
 * {@link ProofStatus#APPROVED}. If proof does not exist or status is not {@code APPROVED},
 * an empty map is returned.
 *
 * <p>For {@link ProofSourceKind#HOUSE_RENT}, the total approved amount is spread across
 * the rental period: {@code approvedAmount / months}, rounded to scale 4 with {@link RoundingMode#HALF_UP}.
 */
@Component
@Transactional(readOnly = true)
public class ProofAmountReaderImpl implements ProofAmountReader {

    private static final Logger log = LoggerFactory.getLogger(ProofAmountReaderImpl.class);

    private final EmployeeProofOfInvestmentRepository proofRepository;
    private final EmployeeProofItemRepository itemRepository;
    private final EmployeeInvHouseRentRepository houseRentRepository;

    public ProofAmountReaderImpl(
            EmployeeProofOfInvestmentRepository proofRepository,
            EmployeeProofItemRepository itemRepository,
            EmployeeInvHouseRentRepository houseRentRepository) {
        this.proofRepository = Objects.requireNonNull(proofRepository, "proofRepository must not be null");
        this.itemRepository = Objects.requireNonNull(itemRepository, "itemRepository must not be null");
        this.houseRentRepository = Objects.requireNonNull(houseRentRepository, "houseRentRepository must not be null");
    }

    @Override
    public Map<Pair<ProofSourceKind, UUID>, BigDecimal> approvedAmounts(UUID declarationId) {
        Objects.requireNonNull(declarationId, "declarationId must not be null");
        UUID tenantId = TenantContext.require();
        return approvedAmounts(tenantId, declarationId);
    }

    @Override
    public Map<Pair<ProofSourceKind, UUID>, BigDecimal> approvedAmounts(UUID tenantId, UUID declarationId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(declarationId, "declarationId must not be null");

        Optional<EmployeeProofOfInvestment> proofOpt =
                proofRepository.findByTenantIdAndDeclarationId(tenantId, declarationId);
        if (proofOpt.isEmpty()) {
            return Collections.emptyMap();
        }

        EmployeeProofOfInvestment proof = proofOpt.get();
        if (proof.getStatus() != ProofStatus.APPROVED) {
            return Collections.emptyMap();
        }

        List<EmployeeProofItem> items =
                itemRepository.findByTenantIdAndProofIdOrderByCreatedAtAscIdAsc(tenantId, proof.getId());
        if (items.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<UUID, EmployeeInvHouseRent> rentLinesById = null;
        Map<Pair<ProofSourceKind, UUID>, BigDecimal> result = new HashMap<>();

        for (EmployeeProofItem item : items) {
            BigDecimal approved = item.getApprovedAmount() != null ? item.getApprovedAmount() : BigDecimal.ZERO;

            if (item.getSourceKind() == ProofSourceKind.HOUSE_RENT) {
                if (rentLinesById == null) {
                    rentLinesById =
                            houseRentRepository
                                    .findByTenantIdAndDeclarationIdOrderByFromMonthAsc(tenantId, declarationId)
                                    .stream()
                                    .collect(Collectors.toMap(
                                            EmployeeInvHouseRent::getId, Function.identity(), (a, b) -> a));
                }
                EmployeeInvHouseRent rent = rentLinesById.get(item.getSourceLineId());
                if (rent != null) {
                    long months = ProofSourceReader.months(rent);
                    if (months > 0) {
                        BigDecimal monthly = approved.divide(BigDecimal.valueOf(months), 4, RoundingMode.HALF_UP);
                        result.put(Pair.of(item.getSourceKind(), item.getSourceLineId()), monthly);
                        continue;
                    }
                } else {
                    log.warn(
                            "House rent line {} not found for declaration {} in proof item {}",
                            item.getSourceLineId(),
                            declarationId,
                            item.getId());
                }
                result.put(
                        Pair.of(item.getSourceKind(), item.getSourceLineId()),
                        approved.setScale(4, RoundingMode.HALF_UP));
            } else {
                result.put(
                        Pair.of(item.getSourceKind(), item.getSourceLineId()),
                        approved.setScale(4, RoundingMode.HALF_UP));
            }
        }

        return Collections.unmodifiableMap(result);
    }
}
