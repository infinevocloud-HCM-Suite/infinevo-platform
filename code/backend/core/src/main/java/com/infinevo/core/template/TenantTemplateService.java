package com.infinevo.core.template;

import com.infinevo.core.tenant.TenantClock;
import com.infinevo.shared.entitlement.EntitlementSource;
import com.infinevo.shared.entitlement.PlatformModule;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies a country's template to a tenant (W-73.9): reads its sections from {@code reference.country_template}
 * and hands each one to the {@link TenantTemplateContributor} bean that owns it, in a fixed order. Each run is
 * recorded in {@code core.tenant_template_applied}, one row per section.
 *
 * <p>Runs with the tenant already bound, inside the caller's transaction: provisioning calls it from
 * {@code TenantServiceImpl}, after the tenant and its roles exist, and the same method serves
 * {@code POST /api/v1/tenants/{id}/apply-template} for a tenant created before templates. A section whose
 * contributor is not deployed, or whose module the tenant does not hold, is skipped and recorded so.
 */
@Service
public class TenantTemplateService {

    private static final Logger log = LoggerFactory.getLogger(TenantTemplateService.class);

    /** The order sections are applied in; one not listed here runs last, by name. */
    static final List<String> SECTION_ORDER =
            List.of("holidays", "leave_types", "pay_schedule", "salary_components", "statutory");

    static final String APPLIED = "APPLIED";
    static final String SKIPPED = "SKIPPED";

    private final CountryTemplateReader reader;
    private final JdbcTemplate jdbcTemplate;
    private final Map<String, TenantTemplateContributor> contributors;
    private final EntitlementSource entitlementSource;
    private final Supplier<LocalDate> today;

    @Autowired
    public TenantTemplateService(
            CountryTemplateReader reader,
            JdbcTemplate jdbcTemplate,
            List<TenantTemplateContributor> contributors,
            ObjectProvider<EntitlementSource> entitlementSourceProvider,
            ObjectProvider<TenantClock> tenantClockProvider) {
        this(
                reader,
                jdbcTemplate,
                contributors,
                entitlementSourceProvider != null ? entitlementSourceProvider.getIfAvailable() : null,
                todayOf(tenantClockProvider != null ? tenantClockProvider.getIfAvailable() : null));
    }

    TenantTemplateService(
            CountryTemplateReader reader,
            JdbcTemplate jdbcTemplate,
            List<TenantTemplateContributor> contributors,
            EntitlementSource entitlementSource,
            Supplier<LocalDate> today) {
        this.reader = Objects.requireNonNull(reader, "reader must not be null");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.entitlementSource = entitlementSource;
        this.today = Objects.requireNonNull(today, "today must not be null");
        Map<String, TenantTemplateContributor> bySection = new HashMap<>();
        for (TenantTemplateContributor c : contributors != null ? contributors : List.<TenantTemplateContributor>of()) {
            TenantTemplateContributor previous = bySection.putIfAbsent(c.section(), c);
            if (previous != null) {
                throw new IllegalStateException("Two TenantTemplateContributors for section " + c.section() + ": "
                        + previous.getClass().getName() + " and " + c.getClass().getName());
            }
        }
        this.contributors = Map.copyOf(bySection);
    }

    private static Supplier<LocalDate> todayOf(TenantClock tenantClock) {
        if (tenantClock != null) {
            return tenantClock::today;
        }
        Clock utc = Clock.systemUTC();
        return () -> LocalDate.now(utc.withZone(ZoneOffset.UTC));
    }

    /** Every country that has a template. */
    @Transactional(readOnly = true)
    public List<CountryTemplateSummary> countries() {
        return reader.summaries();
    }

    /**
     * Applies the country's template to the bound tenant. Idempotent: a section the tenant already has rows for
     * is skipped.
     *
     * @return the sections applied and skipped; both empty when the country has no template
     */
    @Transactional
    public TemplateApplyResponse apply(UUID tenantId, String countryCode) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        List<CountryTemplateSection> sections = new ArrayList<>(reader.sections(countryCode));
        if (sections.isEmpty()) {
            return new TemplateApplyResponse(countryCode, List.of(), List.of());
        }
        sections.sort(Comparator.comparingInt((CountryTemplateSection s) -> rank(s.section()))
                .thenComparing(CountryTemplateSection::section));

        Set<PlatformModule> modules = entitlementSource != null ? entitlementSource.modulesOf(tenantId) : Set.of();
        LocalDate date = today.get();
        List<String> applied = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (CountryTemplateSection section : sections) {
            TenantTemplateContributor contributor = contributors.get(section.section());
            boolean wrote = false;
            if (contributor != null && (contributor.module() == null || modules.contains(contributor.module()))) {
                wrote = contributor.apply(tenantId, section.payload(), date);
            }
            (wrote ? applied : skipped).add(section.section());
            jdbcTemplate.update(
                    "INSERT INTO core.tenant_template_applied (tenant_id, country_code, section, version, outcome,"
                            + " applied_by) VALUES (?, ?, ?, ?, ?, ?)",
                    tenantId,
                    section.countryCode(),
                    section.section(),
                    section.version(),
                    wrote ? APPLIED : SKIPPED,
                    TenantTemplateContributor.ACTOR);
        }
        String country = sections.get(0).countryCode();
        log.info("Country template {} on tenant {}: applied {}, skipped {}", country, tenantId, applied, skipped);
        return new TemplateApplyResponse(country, applied, skipped);
    }

    private static int rank(String section) {
        int i = SECTION_ORDER.indexOf(section);
        return i < 0 ? SECTION_ORDER.size() : i;
    }
}
