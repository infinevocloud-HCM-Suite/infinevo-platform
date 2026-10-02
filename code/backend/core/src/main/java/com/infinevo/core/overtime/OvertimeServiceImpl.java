package com.infinevo.core.overtime;

import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.payinput.PayInputCommand;
import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputResponse;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.shared.money.Money;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementation of {@link OvertimeService} (W-39.2). */
@Service
public class OvertimeServiceImpl implements OvertimeService {

    private static final String ACTOR_SYSTEM = "system";
    private static final BigDecimal MAX_HOURS = new BigDecimal("24");

    private final OvertimeRequestRepository overtimeRequests;
    private final EmployeeRepository employees;
    private final PayInputService payInputService;
    private final Clock clock;

    @Autowired
    public OvertimeServiceImpl(
            OvertimeRequestRepository overtimeRequests, EmployeeRepository employees, PayInputService payInputService) {
        this(overtimeRequests, employees, payInputService, Clock.systemUTC());
    }

    OvertimeServiceImpl(
            OvertimeRequestRepository overtimeRequests,
            EmployeeRepository employees,
            PayInputService payInputService,
            Clock clock) {
        this.overtimeRequests = Objects.requireNonNull(overtimeRequests, "overtimeRequests must not be null");
        this.employees = Objects.requireNonNull(employees, "employees must not be null");
        this.payInputService = Objects.requireNonNull(payInputService, "payInputService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    @Transactional
    public OvertimeResponse record(OvertimeEntry entry) {
        Objects.requireNonNull(entry, "entry must not be null");
        UUID tenantId = TenantContext.require();
        validate(entry, tenantId);
        String actor = currentActor();

        // Inserted first, pay_input_id still null: the ledger is not called until this row exists
        // (spec §6), so a retried POST that fails after the ledger call re-enters record() rather
        // than update a half-posted row — there is no edit, only cancel and re-enter (spec §2).
        OvertimeRequest overtime = overtimeRequests.saveAndFlush(new OvertimeRequest(
                tenantId,
                entry.employeeId(),
                entry.overtimeDate(),
                entry.hours(),
                entry.amount(),
                OvertimeSource.ADMIN,
                entry.remarks(),
                actor));

        PayInputResponse posted = payInputService.record(new PayInputCommand(
                entry.employeeId(),
                YearMonth.from(entry.overtimeDate()),
                PayInputKind.OVERTIME,
                entry.hours(),
                entry.amount() != null ? Money.of(entry.amount()) : null,
                "core",
                "overtime_request:" + overtime.getId()));

        // Same transaction as the insert above: if this fails, the whole request rolls back and the
        // overtime row above is never committed either (spec §4 rules table, last row).
        overtime.markPosted(posted.id(), posted.postedPeriod(), actor);
        overtimeRequests.save(overtime);

        return OvertimeResponse.from(overtime);
    }

    @Override
    @Transactional
    public OvertimeResponse submit(OvertimeEntry entry) {
        Objects.requireNonNull(entry, "entry must not be null");
        UUID tenantId = TenantContext.require();

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        if (entry.amount() != null) {
            fieldErrors.put("amount", "amount is not allowed on overtime requests");
        }
        if (entry.remarks() != null && entry.remarks().length() > 255) {
            fieldErrors.put("remarks", "remarks must not exceed 255 characters");
        }
        validateBaseFields(entry, tenantId, fieldErrors);
        if (!fieldErrors.isEmpty()) {
            throw new ValidationException(fieldErrors);
        }

        String actor = currentActor();
        OvertimeRequest overtime = overtimeRequests.save(OvertimeRequest.pending(
                tenantId, entry.employeeId(), entry.overtimeDate(), entry.hours(), entry.remarks(), actor));

        return OvertimeResponse.from(overtime);
    }

    @Override
    @Transactional
    public OvertimeResponse approve(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        UUID tenantId = TenantContext.require();
        OvertimeRequest overtime =
                overtimeRequests.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new NotFoundException(id));

        if (overtime.getStatus() == OvertimeStatus.APPROVED) {
            return OvertimeResponse.from(overtime);
        }
        if (overtime.getStatus() != OvertimeStatus.PENDING) {
            throw new IllegalStateException("Cannot approve overtime request in status: " + overtime.getStatus());
        }

        String actor = currentActor();
        PayInputResponse posted = payInputService.record(new PayInputCommand(
                overtime.getEmployeeId(),
                YearMonth.from(overtime.getOvertimeDate()),
                PayInputKind.OVERTIME,
                overtime.getHours(),
                overtime.getAmount() != null ? Money.of(overtime.getAmount()) : null,
                "core",
                "overtime_request:" + overtime.getId()));

        overtime.approve(actor);
        overtime.markPosted(posted.id(), posted.postedPeriod(), actor);
        overtimeRequests.save(overtime);

        return OvertimeResponse.from(overtime);
    }

    @Override
    @Transactional
    public OvertimeResponse reject(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        UUID tenantId = TenantContext.require();
        OvertimeRequest overtime =
                overtimeRequests.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new NotFoundException(id));

        if (overtime.getStatus() == OvertimeStatus.REJECTED) {
            return OvertimeResponse.from(overtime);
        }
        if (overtime.getStatus() != OvertimeStatus.PENDING) {
            throw new IllegalStateException("Cannot reject overtime request in status: " + overtime.getStatus());
        }

        overtime.reject(currentActor());
        overtimeRequests.save(overtime);

        return OvertimeResponse.from(overtime);
    }

    @Override
    @Transactional
    public OvertimeResponse cancel(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        UUID tenantId = TenantContext.require();
        OvertimeRequest overtime =
                overtimeRequests.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new NotFoundException(id));
        if (overtime.getStatus() == OvertimeStatus.CANCELLED) {
            throw new AlreadyCancelledException(id);
        }
        if (overtime.getStatus() == OvertimeStatus.REJECTED) {
            throw new NotCancellableException(id);
        }

        if (overtime.getStatus() == OvertimeStatus.APPROVED) {
            payInputService.reverse(overtime.getPayInputId(), "overtime cancelled");
        }

        overtime.cancel(currentActor());
        overtimeRequests.save(overtime);

        return OvertimeResponse.from(overtime);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OvertimeResponse> list(LocalDate from, LocalDate to, UUID employeeId) {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        UUID tenantId = TenantContext.require();
        if (from.isAfter(to)) {
            throw new ValidationException(Map.of("from", "from must not be after to"));
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new ValidationException(Map.of("to", "the range must not exceed " + MAX_RANGE_DAYS + " days"));
        }

        List<OvertimeRequest> rows = employeeId != null
                ? overtimeRequests
                        .findByTenantIdAndEmployeeIdAndOvertimeDateBetweenOrderByOvertimeDateDescEmployeeIdAsc(
                                tenantId, employeeId, from, to)
                : overtimeRequests.findByTenantIdAndOvertimeDateBetweenOrderByOvertimeDateDescEmployeeIdAsc(
                        tenantId, from, to);
        return rows.stream().map(OvertimeResponse::from).toList();
    }

    private void validate(OvertimeEntry entry, UUID tenantId) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        validateBaseFields(entry, tenantId, fieldErrors);
        if (entry.amount() != null && entry.amount().compareTo(BigDecimal.ZERO) <= 0) {
            fieldErrors.put("amount", "amount must be positive");
        }
        if (!fieldErrors.isEmpty()) {
            throw new ValidationException(fieldErrors);
        }
    }

    private void validateBaseFields(OvertimeEntry entry, UUID tenantId, Map<String, String> fieldErrors) {
        if (entry.employeeId() == null) {
            fieldErrors.put("employeeId", "employeeId is required");
        } else if (employees
                .findByIdAndTenantIdAndDeletedFalse(entry.employeeId(), tenantId)
                .isEmpty()) {
            fieldErrors.put("employeeId", "No employee " + entry.employeeId() + " in this tenant");
        }

        if (entry.overtimeDate() == null) {
            fieldErrors.put("overtimeDate", "overtimeDate is required");
        } else if (entry.overtimeDate().isAfter(LocalDate.now(clock))) {
            fieldErrors.put("overtimeDate", "overtimeDate must not be in the future");
        }

        if (entry.hours() == null) {
            fieldErrors.put("hours", "hours is required");
        } else if (entry.hours().compareTo(BigDecimal.ZERO) <= 0
                || entry.hours().compareTo(MAX_HOURS) > 0) {
            fieldErrors.put("hours", "hours must be more than 0 and at most 24");
        }
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return ACTOR_SYSTEM;
        }
        String name = auth.getName();
        return name.length() > 100 ? name.substring(0, 100) : name;
    }
}
