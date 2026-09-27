package com.infinevo.payroll.salary;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link EmployeeStatutoryProfileService} (W-26.2).
 */
@Service
@Transactional
public class EmployeeStatutoryProfileServiceImpl implements EmployeeStatutoryProfileService {

    private static final int MAX_ACTOR_LEN = 100;

    private final EmployeeStatutoryProfileRepository statutoryProfileRepository;
    private final EmployeeService employeeService;

    public EmployeeStatutoryProfileServiceImpl(
            EmployeeStatutoryProfileRepository statutoryProfileRepository, EmployeeService employeeService) {
        this.statutoryProfileRepository =
                Objects.requireNonNull(statutoryProfileRepository, "statutoryProfileRepository must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public StatutoryProfileResponse get(UUID employeeId) {
        UUID tenantId = TenantContext.require();
        employeeService.get(employeeId);

        EmployeeStatutoryProfile profile = statutoryProfileRepository
                .findByTenantIdAndEmployeeId(tenantId, employeeId)
                .orElseGet(() ->
                        new EmployeeStatutoryProfile(tenantId, employeeId, EmployeeStatutoryProfile.ACTOR_SYSTEM));

        return StatutoryProfileResponse.from(profile);
    }

    @Override
    public StatutoryProfileResponse upsert(UUID employeeId, StatutoryProfileRequest request) {
        if (request == null) {
            throw new SalaryValidationException("request", "Request body must not be null");
        }
        UUID tenantId = TenantContext.require();
        employeeService.get(employeeId);
        String actor = currentActor();

        EmployeeStatutoryProfile profile = statutoryProfileRepository
                .findByTenantIdAndEmployeeId(tenantId, employeeId)
                .orElseGet(() -> new EmployeeStatutoryProfile(tenantId, employeeId, actor));

        profile.setEligibleForPf(Boolean.TRUE.equals(request.eligibleForPf()));
        profile.setEligibleForPt(Boolean.TRUE.equals(request.eligibleForPt()));
        profile.setEligibleForLwf(Boolean.TRUE.equals(request.eligibleForLwf()));
        profile.setEligibleForEsi(Boolean.TRUE.equals(request.eligibleForEsi()));
        profile.setEligibleForEps(Boolean.TRUE.equals(request.eligibleForEps()));
        profile.setContributesEpsOnHigherWages(Boolean.TRUE.equals(request.contributesEpsOnHigherWages()));
        profile.setDirector(Boolean.TRUE.equals(request.director()));
        profile.setPfAccountNumber(request.pfAccountNumber());
        profile.setUan(request.uan());
        profile.setEsiNumber(request.esiNumber());
        profile.setUpdatedBy(actor);

        EmployeeStatutoryProfile saved = statutoryProfileRepository.save(profile);
        return StatutoryProfileResponse.from(saved);
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return EmployeeStatutoryProfile.ACTOR_SYSTEM;
        }
        String name = auth.getName();
        return name.length() > MAX_ACTOR_LEN ? name.substring(0, MAX_ACTOR_LEN) : name;
    }
}
