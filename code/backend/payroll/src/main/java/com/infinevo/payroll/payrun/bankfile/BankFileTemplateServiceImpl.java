package com.infinevo.payroll.payrun.bankfile;

import com.infinevo.core.report.ExportFormat;
import com.infinevo.payroll.payrun.bankfile.BankFileTemplateException.Kind;
import com.infinevo.shared.tenant.TenantContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BankFileTemplateServiceImpl implements BankFileTemplateService {

    static final int MAX_NAME = 80;
    static final int MAX_HEADING = 60;
    static final int MAX_FIXED = 100;
    static final int MAX_COLUMNS = 40;
    private static final int MAX_ACTOR_LEN = 100;

    /** W-88's layout, so the one-click file and the built-in template are the same file. */
    static final BankFileTemplateResponse BUILT_IN = new BankFileTemplateResponse(
            BUILT_IN_ID,
            "Generic bulk NEFT",
            ExportFormat.CSV,
            true,
            List.of(
                    new BankFileColumn("Sr No", BankFileColumnSource.SERIAL, null, null, null),
                    field("Employee Number", BankFileField.EMPLOYEE_NUMBER),
                    field("Employee Name", BankFileField.EMPLOYEE_NAME),
                    field("Account Holder Name", BankFileField.ACCOUNT_HOLDER_NAME),
                    field("Bank Account Number", BankFileField.ACCOUNT_NUMBER),
                    field("IFSC Code", BankFileField.IFSC),
                    field("Bank Name", BankFileField.BANK_NAME),
                    field("Account Type", BankFileField.ACCOUNT_TYPE),
                    new BankFileColumn(
                            "Net Pay (INR)", BankFileColumnSource.FIELD, BankFileField.NET_PAY, null, "DECIMAL_2"),
                    field("Narration", BankFileField.NARRATION)),
            true,
            null,
            null);

    private final BankFileTemplateRepository repository;

    public BankFileTemplateServiceImpl(BankFileTemplateRepository repository) {
        this.repository = repository;
    }

    private static BankFileColumn field(String heading, BankFileField field) {
        return new BankFileColumn(heading, BankFileColumnSource.FIELD, field, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BankFileTemplateResponse> list() {
        List<BankFileTemplateResponse> all = new ArrayList<>();
        all.add(BUILT_IN);
        repository.findByTenantIdOrderByNameAsc(TenantContext.require()).stream()
                .map(BankFileTemplateResponse::from)
                .forEach(all::add);
        return all;
    }

    @Override
    public List<BankFileFieldResponse> fields() {
        return Arrays.stream(BankFileField.values())
                .map(BankFileFieldResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BankFileTemplateResponse get(String id) {
        if (BUILT_IN_ID.equals(id)) {
            return BUILT_IN;
        }
        return BankFileTemplateResponse.from(require(id));
    }

    @Override
    public BankFileTemplateResponse create(BankFileTemplateRequest request) {
        UUID tenantId = TenantContext.require();
        Validated valid = validate(request);
        if (repository.existsByTenantIdAndNameIgnoreCase(tenantId, valid.name())) {
            throw new BankFileTemplateException(
                    Kind.CONFLICT, "A bank file template named " + valid.name() + " exists");
        }
        BankFileTemplate template = new BankFileTemplate(tenantId, currentActor());
        apply(template, valid);
        return BankFileTemplateResponse.from(repository.saveAndFlush(template));
    }

    @Override
    public BankFileTemplateResponse update(String id, BankFileTemplateRequest request) {
        refuseBuiltIn(id, "changed");
        BankFileTemplate template = require(id);
        Validated valid = validate(request);
        if (repository.existsByTenantIdAndNameIgnoreCaseAndIdNot(
                template.getTenantId(), valid.name(), template.getId())) {
            throw new BankFileTemplateException(
                    Kind.CONFLICT, "A bank file template named " + valid.name() + " exists");
        }
        apply(template, valid);
        template.setUpdatedBy(currentActor());
        return BankFileTemplateResponse.from(repository.saveAndFlush(template));
    }

    @Override
    public void delete(String id) {
        refuseBuiltIn(id, "deleted");
        repository.delete(require(id));
    }

    @Override
    public BankFileTemplateResponse copy(String id) {
        UUID tenantId = TenantContext.require();
        BankFileTemplateResponse source = get(id);
        String name = copyName(tenantId, source.name());
        BankFileTemplate template = new BankFileTemplate(tenantId, currentActor());
        apply(
                template,
                new Validated(name, source.fileFormat(), source.includeHeader(), List.copyOf(source.columns())));
        return BankFileTemplateResponse.from(repository.saveAndFlush(template));
    }

    private String copyName(UUID tenantId, String sourceName) {
        String base = truncate("Copy of " + sourceName, MAX_NAME);
        if (!repository.existsByTenantIdAndNameIgnoreCase(tenantId, base)) {
            return base;
        }
        for (int n = 2; n < 1000; n++) {
            String suffix = " (" + n + ")";
            String candidate = truncate(base, MAX_NAME - suffix.length()) + suffix;
            if (!repository.existsByTenantIdAndNameIgnoreCase(tenantId, candidate)) {
                return candidate;
            }
        }
        throw new BankFileTemplateException(Kind.CONFLICT, "Too many copies of " + sourceName);
    }

    private record Validated(
            String name, ExportFormat fileFormat, boolean includeHeader, List<BankFileColumn> columns) {}

    /** Spec section 4.3; an IllegalArgumentException is a 400 naming the first problem. */
    static Validated validate(BankFileTemplateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("A template is required");
        }
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name is required");
        }
        if (name.length() > MAX_NAME) {
            throw new IllegalArgumentException("name must be at most " + MAX_NAME + " characters");
        }
        List<BankFileColumn> columns = request.columns();
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("A template needs at least one column");
        }
        if (columns.size() > MAX_COLUMNS) {
            throw new IllegalArgumentException("A template has at most " + MAX_COLUMNS + " columns");
        }
        List<BankFileColumn> clean = new ArrayList<>();
        Set<String> headings = new HashSet<>();
        for (int i = 0; i < columns.size(); i++) {
            BankFileColumn column = columns.get(i);
            String where = "column " + (i + 1);
            if (column == null) {
                throw new IllegalArgumentException(where + " is empty");
            }
            String heading = column.heading() == null ? "" : column.heading().trim();
            if (heading.isEmpty()) {
                throw new IllegalArgumentException(where + ": heading is required");
            }
            if (heading.length() > MAX_HEADING) {
                throw new IllegalArgumentException(where + ": heading must be at most " + MAX_HEADING + " characters");
            }
            if (heading.indexOf('\n') >= 0 || heading.indexOf('\r') >= 0) {
                throw new IllegalArgumentException(where + ": heading must be one line");
            }
            if (!headings.add(heading.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(where + ": heading " + heading + " is used twice");
            }
            if (column.source() == null) {
                throw new IllegalArgumentException(where + ": source is required");
            }
            clean.add(
                    switch (column.source()) {
                        case SERIAL -> new BankFileColumn(heading, BankFileColumnSource.SERIAL, null, null, null);
                        case FIXED -> {
                            String value = column.value() == null ? "" : column.value();
                            if (value.length() > MAX_FIXED) {
                                throw new IllegalArgumentException(
                                        where + ": fixed text must be at most " + MAX_FIXED + " characters");
                            }
                            if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                                throw new IllegalArgumentException(where + ": fixed text must be one line");
                            }
                            yield new BankFileColumn(heading, BankFileColumnSource.FIXED, null, value, null);
                        }
                        case FIELD -> {
                            if (column.field() == null) {
                                throw new IllegalArgumentException(where + ": field is required");
                            }
                            String format =
                                    column.format() == null || column.format().isBlank()
                                            ? null
                                            : column.format().trim();
                            List<String> allowed = column.field().kind().formats();
                            if (format != null && !allowed.contains(format)) {
                                throw new IllegalArgumentException(
                                        allowed.isEmpty()
                                                ? where + ": " + column.field().label() + " takes no format"
                                                : where + ": format must be one of " + String.join(", ", allowed));
                            }
                            yield new BankFileColumn(heading, BankFileColumnSource.FIELD, column.field(), null, format);
                        }
                    });
        }
        ExportFormat format = request.fileFormat() == null ? ExportFormat.CSV : request.fileFormat();
        boolean header = request.includeHeader() == null || request.includeHeader();
        return new Validated(name, format, header, List.copyOf(clean));
    }

    private static void apply(BankFileTemplate template, Validated valid) {
        template.setName(valid.name());
        template.setFileFormat(valid.fileFormat());
        template.setIncludeHeader(valid.includeHeader());
        template.setColumns(valid.columns());
    }

    private BankFileTemplate require(String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BankFileTemplateException(Kind.NOT_FOUND, "No bank file template " + id);
        }
        return repository
                .findByIdAndTenantId(uuid, TenantContext.require())
                .orElseThrow(() -> new BankFileTemplateException(Kind.NOT_FOUND, "No bank file template " + id));
    }

    private static void refuseBuiltIn(String id, String verb) {
        if (BUILT_IN_ID.equals(id)) {
            throw new BankFileTemplateException(
                    Kind.CONFLICT, "The built-in template cannot be " + verb + "; copy it and change the copy");
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return BankFileTemplate.ACTOR_SYSTEM;
        }
        String name = auth.getName();
        return name.length() > MAX_ACTOR_LEN ? name.substring(0, MAX_ACTOR_LEN) : name;
    }
}
