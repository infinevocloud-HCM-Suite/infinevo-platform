package com.itsdev.payroll.serviceimpl.employeeitdeclaration.taxCalculator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator.TaxSlabCreateRequest;
import com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator.TaxSlabResponseDTO;
import com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator.TaxSlabRowDTO;
import com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator.TaxSlabSaveRequest;
import com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator.TaxSlabMaster;
import com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator.TaxSlabMasterHistory;
import com.itsdev.payroll.repository.employeeitdeclaration.taxcalculator.TaxSlabMasterHistoryRepository;
import com.itsdev.payroll.repository.employeeitdeclaration.taxcalculator.TaxSlabMasterRepository;
import com.itsdev.payroll.service.employeeitdeclaration.taxCalculator.TaxSlabService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Business logic for reading and editing income-tax regime slabs.
 *
 * CONTROL FLOW (save):
 *   TaxSlabMasterController.updateSlabs(...)
 *      -> this.updateSlabs(id, request, changedBy)
 *           1. load the existing master row (by id)
 *           2. validateSlabs(...)            <-- hard-blocks bad data
 *           3. snapshot old JSON for audit
 *           4. re-serialize rows -> slabJson, save IN PLACE (same row)
 *           5. write a TaxSlabMasterHistory audit row
 *           6. return the saved regime as a DTO
 *
 * Why "in place": the tax engine looks up exactly ONE active row per
 * (financialYear, taxRegime) and throws if it finds more than one. We therefore
 * never insert a new row on save and never touch financialYear / taxRegime /
 * isActive — we only overwrite slabJson on the existing record.
 */
@Service
public class TaxSlabServiceImpl implements TaxSlabService {

    private static final Logger log = LoggerFactory.getLogger(TaxSlabServiceImpl.class);

    private final TaxSlabMasterRepository slabRepo;
    private final TaxSlabMasterHistoryRepository historyRepo;

    // Same ObjectMapper contract the tax engine uses to read slabJson.
    private final ObjectMapper mapper = new ObjectMapper();

    public TaxSlabServiceImpl(TaxSlabMasterRepository slabRepo,
                              TaxSlabMasterHistoryRepository historyRepo) {
        this.slabRepo = slabRepo;
        this.historyRepo = historyRepo;
    }

    /* =====================================================================
     * READ
     * ===================================================================== */
    @Override
    public List<TaxSlabResponseDTO> getActiveSlabs() {
        List<TaxSlabMaster> masters = slabRepo.findByIsActiveTrueOrderByTaxRegimeAsc();

        List<TaxSlabResponseDTO> result = new ArrayList<>();
        for (TaxSlabMaster m : masters) {
            result.add(toResponse(m));
        }
        return result;
    }

    /* =====================================================================
     * CREATE (validate uniqueness + slabs -> insert new row -> audit)
     * ===================================================================== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaxSlabResponseDTO createRegime(TaxSlabCreateRequest request, String createdBy) {

        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }

        String fy = request.getFinancialYear();
        String regime = request.getTaxRegime();

        if (fy == null || fy.isBlank()) {
            throw new IllegalArgumentException("Financial year is required (e.g. \"2025-2026\").");
        }
        if (!fy.matches("\\d{4}-\\d{4}")) {
            throw new IllegalArgumentException("Financial year must be in YYYY-YYYY format (e.g. \"2025-2026\").");
        }
        if (regime == null || (!regime.equals("OLD") && !regime.equals("NEW"))) {
            throw new IllegalArgumentException("Tax regime must be \"OLD\" or \"NEW\".");
        }

        // One active row per (FY, regime) — reject duplicates so the tax engine stays safe.
        List<TaxSlabMaster> existing = slabRepo.findByFinancialYearAndTaxRegimeAndIsActiveTrue(fy, regime);
        if (!existing.isEmpty()) {
            throw new IllegalArgumentException(
                    "An active " + regime + " regime already exists for FY " + fy +
                    ". Edit the existing one instead of creating a duplicate.");
        }

        List<TaxSlabRowDTO> rows = request.getSlabs();
        validateSlabs(rows);

        TaxSlabMaster master = new TaxSlabMaster();
        master.setFinancialYear(fy);
        master.setTaxRegime(regime);
        master.setSlabJson(writeJson(rows));
        master.setActive(true);
        TaxSlabMaster saved = slabRepo.save(master);

        log.info("✅ New tax regime created | id={}, FY={}, regime={}, by={}",
                saved.getId(), fy, regime, createdBy);

        writeAudit(saved, null, saved.getSlabJson(), createdBy);
        return toResponse(saved);
    }

    /* =====================================================================
     * UPDATE (validate -> snapshot -> save in place -> audit)
     * ===================================================================== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaxSlabResponseDTO updateSlabs(Long id, TaxSlabSaveRequest request, String changedBy) {

        // 1. Load the existing regime row. Editing only — never create here.
        TaxSlabMaster master = slabRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tax slab configuration not found for id=" + id));

        List<TaxSlabRowDTO> rows = request != null ? request.getSlabs() : null;

        // 2. HARD validation. Throws IllegalArgumentException on any bad data.
        validateSlabs(rows);

        // 3. Snapshot the current JSON before we overwrite it (for the audit row).
        String oldJson = master.getSlabJson();

        // 4. Serialize the validated rows back into the engine's JSON shape and
        //    save IN PLACE. financialYear / taxRegime / isActive are untouched.
        String newJson = writeJson(rows);
        master.setSlabJson(newJson);
        TaxSlabMaster saved = slabRepo.save(master);

        log.info("✅ Tax slab updated in place | id={}, FY={}, regime={}, by={}",
                saved.getId(), saved.getFinancialYear(), saved.getTaxRegime(), changedBy);

        // 5. Write the audit trail row.
        writeAudit(saved, oldJson, newJson, changedBy);

        // 6. Return the saved regime to the UI.
        return toResponse(saved);
    }

    /* =====================================================================
     * DELETE (soft-delete: isActive = false -> audit)
     * ===================================================================== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRegime(Long id, String deletedBy) {

        TaxSlabMaster master = slabRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tax slab configuration not found for id=" + id));

        String oldJson = master.getSlabJson();
        master.setActive(false);
        TaxSlabMaster saved = slabRepo.save(master);

        log.info("🗑️ Tax regime deactivated | id={}, FY={}, regime={}, by={}",
                saved.getId(), saved.getFinancialYear(), saved.getTaxRegime(), deletedBy);

        TaxSlabMasterHistory h = new TaxSlabMasterHistory();
        h.setTaxSlabMasterId(saved.getId());
        h.setFinancialYear(saved.getFinancialYear());
        h.setTaxRegime(saved.getTaxRegime());
        h.setOldSlabJson(oldJson);
        h.setNewSlabJson(null);
        h.setActionType("DELETE");
        h.setChangedBy(deletedBy);
        h.setChangedAt(LocalDateTime.now());
        historyRepo.save(h);
    }

    /* =====================================================================
     * VALIDATION  (this is the "hard-block invalid slabs" guarantee)
     *
     * Rules enforced (all must pass or the whole save is rejected):
     *   - at least one slab
     *   - every row: from != null, from >= 0, rate in [0..100]
     *   - rows sorted ascending by `from`
     *   - first slab starts at 0
     *   - ONLY the last slab is open-ended (to == null); every other slab has
     *     a finite `to` greater than its `from`
     *   - no gaps / no overlaps between consecutive slabs: each slab's `from`
     *     must equal the previous slab's `to` OR previous `to` + 1
     *     (the "+1" tolerates the standard Indian boundary style
     *      e.g. ...to:250000 then from:250001...)
     * ===================================================================== */
    private void validateSlabs(List<TaxSlabRowDTO> rows) {

        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("At least one tax slab is required.");
        }

        // Sort ascending by `from` so we can validate boundaries linearly.
        rows.sort((a, b) -> {
            if (a.getFrom() == null || b.getFrom() == null) {
                throw new IllegalArgumentException("Every slab must have a 'from' amount.");
            }
            return a.getFrom().compareTo(b.getFrom());
        });

        for (int i = 0; i < rows.size(); i++) {
            TaxSlabRowDTO row = rows.get(i);
            boolean isLast = (i == rows.size() - 1);

            // from
            if (row.getFrom() == null || row.getFrom().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Slab #" + (i + 1) + ": 'from' must be zero or greater.");
            }

            // rate 0..100
            if (row.getRate() == null
                    || row.getRate().compareTo(BigDecimal.ZERO) < 0
                    || row.getRate().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("Slab #" + (i + 1) + ": 'rate' must be between 0 and 100.");
            }

            // first slab must start at 0
            if (i == 0 && row.getFrom().compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalArgumentException("The first slab must start at 0.");
            }

            if (isLast) {
                // The top slab MUST be open-ended, otherwise income above it goes
                // untaxed by the engine (which uses remaining income for to == null).
                if (row.getTo() != null) {
                    throw new IllegalArgumentException(
                            "The last (highest) slab must be open-ended — leave its 'To' empty.");
                }
            } else {
                // Non-last slab: needs a finite, sensible upper bound.
                if (row.getTo() == null) {
                    throw new IllegalArgumentException(
                            "Slab #" + (i + 1) + ": only the last slab may have an empty 'To'.");
                }
                if (row.getTo().compareTo(row.getFrom()) <= 0) {
                    throw new IllegalArgumentException(
                            "Slab #" + (i + 1) + ": 'To' must be greater than 'From'.");
                }

                // No gap / no overlap with the next slab.
                TaxSlabRowDTO next = rows.get(i + 1);
                BigDecimal expected = row.getTo();              // contiguous
                BigDecimal expectedPlusOne = row.getTo().add(BigDecimal.ONE); // +1 boundary style
                if (next.getFrom().compareTo(expected) != 0
                        && next.getFrom().compareTo(expectedPlusOne) != 0) {
                    throw new IllegalArgumentException(
                            "Slabs must be continuous with no gaps or overlaps. "
                            + "Slab #" + (i + 2) + " should start at " + expected
                            + " (or " + expectedPlusOne + ").");
                }
            }
        }
    }

    /* =====================================================================
     * Helpers
     * ===================================================================== */

    /** Parse stored slabJson -> structured DTO for the UI. */
    private TaxSlabResponseDTO toResponse(TaxSlabMaster m) {
        TaxSlabResponseDTO dto = new TaxSlabResponseDTO();
        dto.setId(m.getId());
        dto.setFinancialYear(m.getFinancialYear());
        dto.setTaxRegime(m.getTaxRegime());
        dto.setIsActive(m.getActive());

        List<TaxSlabRowDTO> rows = new ArrayList<>();
        if (m.getSlabJson() != null && !m.getSlabJson().isBlank()) {
            try {
                rows = mapper.readValue(
                        m.getSlabJson(),
                        mapper.getTypeFactory().constructCollectionType(List.class, TaxSlabRowDTO.class));
            } catch (Exception e) {
                // Don't blow up the whole list if one regime has malformed JSON;
                // surface an empty slab list so the admin can re-enter it.
                log.error("⚠️ Failed to parse slabJson for slabId={}", m.getId(), e);
            }
        }
        dto.setSlabs(rows);
        return dto;
    }

    /** Serialize validated rows -> JSON string in the engine's expected shape. */
    private String writeJson(List<TaxSlabRowDTO> rows) {
        try {
            return mapper.writeValueAsString(rows);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize tax slabs", e);
        }
    }

    /** Persist one audit row capturing before/after JSON. */
    private void writeAudit(TaxSlabMaster saved, String oldJson, String newJson, String changedBy) {
        TaxSlabMasterHistory h = new TaxSlabMasterHistory();
        h.setTaxSlabMasterId(saved.getId());
        h.setFinancialYear(saved.getFinancialYear());
        h.setTaxRegime(saved.getTaxRegime());
        h.setOldSlabJson(oldJson);
        h.setNewSlabJson(newJson);
        // First time the row ever got slabs => INSERT, otherwise UPDATE.
        h.setActionType((oldJson == null || oldJson.isBlank()) ? "INSERT" : "UPDATE");
        h.setChangedBy(changedBy);
        h.setChangedAt(LocalDateTime.now());
        historyRepo.save(h);

        log.info("📝 Tax slab audit saved | slabId={}, action={}, by={}",
                saved.getId(), h.getActionType(), changedBy);
    }
}
