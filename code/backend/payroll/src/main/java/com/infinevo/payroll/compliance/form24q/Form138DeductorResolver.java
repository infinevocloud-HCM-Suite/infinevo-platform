package com.infinevo.payroll.compliance.form24q;

import com.infinevo.core.org.WorkLocation;
import com.infinevo.core.org.WorkLocationRepository;
import com.infinevo.core.tenant.TenantBranding;
import com.infinevo.core.tenant.TenantProfileService;
import com.infinevo.payroll.taxdeductor.TaxDeductorResponse;
import com.infinevo.payroll.taxdeductor.TaxDeductorService;
import com.infinevo.shared.tenant.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Gathers the Form 138 employer block (W-89) from Form 16's deductor details, the company name and the filing
 * work location, and names every item that is missing so the screen can say what to fill in.
 */
@Component
class Form138DeductorResolver {

    static final int ADDRESS_LINE = 25;

    private final TaxDeductorService taxDeductorService;
    private final TenantProfileService tenantProfileService;
    private final WorkLocationRepository workLocationRepository;

    Form138DeductorResolver(
            TaxDeductorService taxDeductorService,
            TenantProfileService tenantProfileService,
            WorkLocationRepository workLocationRepository) {
        this.taxDeductorService = taxDeductorService;
        this.tenantProfileService = tenantProfileService;
        this.workLocationRepository = workLocationRepository;
    }

    record Resolution(Form138Deductor deductor, List<String> missing) {}

    Resolution resolve() {
        List<String> missing = new ArrayList<>();
        Optional<TaxDeductorResponse> found = taxDeductorService.current();
        if (found.isEmpty()) {
            missing.add("Deductor details (Payroll › Settings › TDS deductor)");
            return new Resolution(null, missing);
        }
        TaxDeductorResponse d = found.get();
        String name = tenantProfileService
                .branding()
                .map(TenantBranding::name)
                .map(String::trim)
                .orElse("");
        if (name.isBlank()) {
            missing.add("Company name (Settings › Company profile)");
        }
        Optional<WorkLocation> filing =
                workLocationRepository.findByTenantIdAndFilingAddressTrue(TenantContext.require());
        List<String> lines = List.of();
        Integer state = null;
        String pin = null;
        if (filing.isEmpty()) {
            missing.add("A work location marked as the filing address (Settings › Work locations)");
        } else {
            WorkLocation w = filing.get();
            lines = addressLines(w.getAddressLine1(), w.getAddressLine2(), w.getCity());
            if (lines.isEmpty()) {
                missing.add("The filing address's first line");
            }
            state = Form138States.code(w.getStateCode(), w.getState()).orElse(null);
            if (state == null) {
                missing.add("The filing address's state (an Indian state or union territory)");
            }
            pin = w.getZipCode() == null ? null : w.getZipCode().replaceAll("\\s", "");
            if (pin == null || !pin.matches("^[1-9][0-9]{5}$")) {
                missing.add("The filing address's 6-digit PIN code");
                pin = null;
            }
        }
        require(d.deductorType(), "Deductor type", missing);
        require(d.email(), "Deductor email", missing);
        require(d.phone(), "Deductor phone", missing);
        require(d.responsiblePan(), "PAN of the person responsible (the signatory)", missing);
        require(d.responsibleEmail(), "Email of the person responsible", missing);
        require(d.responsiblePhone(), "Phone of the person responsible", missing);
        return new Resolution(
                new Form138Deductor(
                        d.tan(),
                        d.pan(),
                        name,
                        lines,
                        state,
                        pin,
                        d.email(),
                        d.phone(),
                        d.deductorType(),
                        d.gstin(),
                        d.signatoryName(),
                        d.signatoryDesignation(),
                        d.responsiblePan(),
                        d.responsibleEmail(),
                        d.responsiblePhone()),
                missing);
    }

    private static void require(String value, String label, List<String> missing) {
        if (value == null || value.isBlank()) {
            missing.add(label + " (Payroll › Settings › TDS deductor)");
        }
    }

    /** The address words flowed into at most five 25-character lines, for the file's five address fields. */
    static List<String> addressLines(String... parts) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            for (String word : Form138File.ascii(part).split("\\s+")) {
                if (word.isEmpty()) {
                    continue;
                }
                while (word.length() > ADDRESS_LINE) {
                    if (!line.isEmpty()) {
                        out.add(line.toString());
                        line.setLength(0);
                    }
                    out.add(word.substring(0, ADDRESS_LINE));
                    word = word.substring(ADDRESS_LINE);
                }
                if (line.length() + (line.isEmpty() ? 0 : 1) + word.length() > ADDRESS_LINE) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                if (!line.isEmpty()) {
                    line.append(' ');
                }
                line.append(word);
            }
        }
        if (!line.isEmpty()) {
            out.add(line.toString());
        }
        return out.size() > 5 ? List.copyOf(out.subList(0, 5)) : List.copyOf(out);
    }
}
