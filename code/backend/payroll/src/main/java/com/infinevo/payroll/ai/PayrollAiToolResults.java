package com.infinevo.payroll.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiToolResults;
import com.infinevo.payroll.payslip.PayslipLineResponse;
import com.infinevo.payroll.payslip.PayslipResponse;
import com.infinevo.payroll.payslip.PayslipSummaryResponse;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationResponse;
import com.infinevo.payroll.taxdeclaration.summary.dto.ComputedTaxSummaryResponse;
import com.infinevo.payroll.taxdeclaration.summary.dto.DeclaredTotals;
import com.infinevo.payroll.taxdeclaration.summary.dto.TaxSummaryResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The Payroll responses as the AI helper reads them (W-114.7 rules 2 and 5), and the one place an amount becomes text.
 *
 * <p>Each response is copied field by field, so only the fields named here leave the tool. A payslip carries the
 * period, run type, pay and paid dates, paid days, each earning, deduction and reimbursement line (code, name, amount,
 * taxable) and the ready-made totals; not the run or employee id, not the employee block (number, PF and UAN numbers),
 * not the employer, not the link. A null field is left out. Every answer passes {@link AiToolResults#withoutIds} as a
 * second guard.
 *
 * <p>Every amount is a {@link BigDecimal} from the service and is written by {@link #amount} as
 * {@code setScale(2, HALF_UP).toPlainString()} - the payslip's own rounding ({@code PayslipServiceImpl.scale2}) - so
 * {@code 45000} is {@code "45000.00"} and never {@code 4.5E4}. {@code "currency": "INR"} is written once per answer.
 */
final class PayrollAiToolResults {

    static final String CURRENCY = "INR";

    /** {@code PayslipServiceImpl.scale2}'s rounding ({@code payslip/PayslipServiceImpl.java:268-272}). */
    static final RoundingMode PAYSLIP_ROUNDING = RoundingMode.HALF_UP;

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private PayrollAiToolResults() {}

    /** {@code PayslipService.listOwn}: newest month first, as the service orders them. */
    static JsonNode payslips(List<PayslipSummaryResponse> slips) {
        ObjectNode out = NODES.objectNode();
        out.put("currency", CURRENCY);
        ArrayNode list = out.putArray("payslips");
        if (slips != null) {
            for (PayslipSummaryResponse s : slips) {
                ObjectNode row = list.addObject();
                put(row, "period", s.period());
                put(row, "paidOn", s.paidOn());
                putAmount(row, "netPay", s.netPay());
            }
        }
        out.put("count", list.size());
        if (list.isEmpty()) {
            out.put("message", PayrollAiToolProvider.NO_PAYSLIPS);
        }
        return AiToolResults.withoutIds(out);
    }

    /**
     * {@code PayslipService.own}: the month's main payslip, and under {@code otherPayslipsThisMonth} the month's other
     * paid payslips (off-cycle runs), each in the same shape. The list is left out when there are none.
     */
    static JsonNode payslip(PayslipResponse main, List<PayslipResponse> others) {
        ObjectNode out = (ObjectNode) payslip(main);
        if (others != null && !others.isEmpty()) {
            ArrayNode list = out.putArray("otherPayslipsThisMonth");
            for (PayslipResponse other : others) {
                list.add(payslip(other));
            }
        }
        return AiToolResults.withoutIds(out);
    }

    /** {@code PayslipService.own}: one paid payslip. */
    static JsonNode payslip(PayslipResponse p) {
        ObjectNode out = NODES.objectNode();
        out.put("currency", CURRENCY);
        PayslipResponse.RunSummary run = p.run();
        if (run != null) {
            put(out, "period", run.period());
            put(out, "type", run.type());
            put(out, "payDate", run.payDate());
            put(out, "paidOn", run.paidOn());
        }
        if (p.days() != null) {
            putAmount(out, "paidDays", p.days().paidDays());
        }
        out.set("earnings", lines(p.earnings()));
        out.set("deductions", lines(p.deductions()));
        out.set("reimbursements", lines(p.reimbursements()));
        PayslipResponse.TotalsSummary totals = p.totals();
        if (totals != null) {
            putAmount(out, "grossEarnings", totals.grossEarnings());
            putAmount(out, "totalDeductions", totals.totalDeductions());
            putAmount(out, "totalReimbursements", totals.totalReimbursements());
            putAmount(out, "netPay", totals.netPay());
        }
        return AiToolResults.withoutIds(out);
    }

    /** No payslip or declaration to show: the period asked about, if any, and one plain sentence. */
    static JsonNode none(String period, String message) {
        ObjectNode out = NODES.objectNode();
        if (period != null) {
            out.put("period", period);
        }
        out.put("found", false);
        out.put("message", message);
        return out;
    }

    /** {@code TaxDeclarationService.readOwn}: the declaration header, without its id or the employee's. */
    static JsonNode taxDeclaration(TaxDeclarationResponse d) {
        ObjectNode out = NODES.objectNode();
        out.put("currency", CURRENCY);
        put(out, "financialYear", d.financialYear());
        put(out, "taxRegime", d.taxRegime());
        put(out, "status", d.status());
        out.put("locked", d.isLocked());
        put(out, "submittedAt", d.submittedAt());
        put(out, "lockedAt", d.lockedAt());
        put(out, "updatedAt", d.updatedAt());
        out.put("windowOpen", d.windowOpen());
        out.put("editable", d.editable());
        out.put("stayingInRentedHouse", d.isStayingInRentedHouse());
        out.put("repayingSelfOccupiedHomeLoan", d.isRepayingSelfOccupiedLoan());
        out.put("hasLetOutProperty", d.hasLetOutProperty());
        out.put("landlordPanRequiredOverRentThreshold", d.panRequiredForRentOverThreshold());
        putAmount(out, "rentThresholdForLandlordPan", d.rentPanThreshold());
        return AiToolResults.withoutIds(out);
    }

    /** {@code TaxSummaryService.summaryOwn}: what was declared and what was computed. */
    static JsonNode taxSummary(TaxSummaryResponse s) {
        ObjectNode out = NODES.objectNode();
        out.put("currency", CURRENCY);
        put(out, "financialYear", s.financialYear());
        put(out, "taxRegime", s.taxRegime());
        put(out, "status", s.status());
        DeclaredTotals d = s.declared();
        if (d != null) {
            ObjectNode declared = out.putObject("declared");
            putAmount(declared, "houseRentAnnual", d.houseRentAnnual());
            putAmount(declared, "homeLoanPrincipal", d.homeLoanPrincipal());
            putAmount(declared, "homeLoanInterest", d.homeLoanInterest());
            putAmount(declared, "letOutNet", d.letOutNet());
            if (d.section6aByGroup() != null && !d.section6aByGroup().isEmpty()) {
                ObjectNode groups = declared.putObject("section6aByGroup");
                for (Map.Entry<String, BigDecimal> e : new TreeMap<>(d.section6aByGroup()).entrySet()) {
                    putAmount(groups, e.getKey(), e.getValue());
                }
            }
            putAmount(declared, "section6aTotal", d.section6aTotal());
            putAmount(declared, "preTaxTotal", d.preTaxTotal());
            putAmount(declared, "previousEmploymentIncome", d.prevEmploymentIncome());
            putAmount(declared, "previousEmploymentTax", d.prevEmploymentTax());
            putAmount(declared, "otherIncomeTotal", d.otherIncomeTotal());
        }
        ComputedTaxSummaryResponse c = s.computed();
        if (c != null) {
            ObjectNode computed = out.putObject("computed");
            putAmount(computed, "taxableIncome", c.taxableIncome());
            putAmount(computed, "netTaxableIncome", c.netTaxableIncome());
            putAmount(computed, "taxOnTaxableIncome", c.taxOnTaxableIncome());
            putAmount(computed, "taxPaidSoFar", c.taxYtdAmount());
            putAmount(computed, "taxToBePaid", c.taxToBePaid());
            putAmount(computed, "tdsThroughPayroll", c.tdsThroughPayroll());
            putAmount(computed, "tdsPreviousEmployer", c.tdsPreviousEmployer());
            putAmount(computed, "tdsOtherIncome", c.tdsOtherIncome());
            putAmount(computed, "otherSourcesIncome", c.otherSourcesIncome());
            putAmount(computed, "exemptionUnderSection10", c.exemptionUnderSection10());
            putAmount(computed, "exemptionUnderSection6a", c.exemptionUnderSection6a());
            if (c.remainingMonths() != null) {
                computed.put("remainingMonths", c.remainingMonths());
            }
            put(computed, "computedAt", c.computedAt());
        }
        return AiToolResults.withoutIds(out);
    }

    // ---- W-114.14: what changed, and why ----

    /** The loss of pay of this month, as {@code PayFigureExplanationService.explain} prices it. */
    record LossOfPay(BigDecimal days, BigDecimal amount, BigDecimal divisor, String dayBasis) {}

    /** {@code payroll.explain_payslip}: the comparison, every amount as two-decimal text, reasons as codes. */
    static JsonNode explainPayslip(PayslipComparison.Result r, LossOfPay lossOfPay) {
        ObjectNode out = NODES.objectNode();
        out.put("currency", CURRENCY);
        put(out, "period", r.period());
        if (r.comparedWith() == null) {
            out.putNull("comparedWith");
        } else {
            out.put("comparedWith", r.comparedWith());
        }
        if (r.firstPayslip()) {
            out.put("firstPayslip", true);
        }
        if (r.offCycle()) {
            out.put("offCycle", true);
            out.put("note", PayslipComparison.OFF_CYCLE_NOTE);
        }
        ObjectNode days = out.putObject("days");
        days.set("thisMonth", days(r.days()));
        if (r.previousDays() != null) {
            days.set("lastMonth", days(r.previousDays()));
        }
        ArrayNode lines = out.putArray("lines");
        r.lines().forEach(l -> lines.add(comparedLine(l)));
        ArrayNode gone = out.putArray("gone");
        r.gone().forEach(l -> gone.add(comparedLine(l)));
        ObjectNode totals = out.putObject("totals");
        totals.set("thisMonth", totals(r.totals()));
        if (r.previousTotals() != null) {
            totals.set("lastMonth", totals(r.previousTotals()));
            ObjectNode diff = totals.putObject("difference");
            putAmount(
                    diff,
                    "gross",
                    PayslipComparison.difference(
                            r.totals().gross(), r.previousTotals().gross()));
            putAmount(
                    diff,
                    "deductions",
                    PayslipComparison.difference(
                            r.totals().deductions(), r.previousTotals().deductions()));
            putAmount(
                    diff,
                    "net",
                    PayslipComparison.difference(
                            r.totals().net(), r.previousTotals().net()));
        }
        if (lossOfPay != null) {
            ObjectNode lop = out.putObject("lossOfPay");
            putAmount(lop, "days", lossOfPay.days());
            putAmount(lop, "amount", lossOfPay.amount());
            putAmount(lop, "divisor", lossOfPay.divisor());
            put(lop, "dayBasis", lossOfPay.dayBasis());
        }
        return AiToolResults.withoutIds(out);
    }

    /** {@code payroll.explain_tds}: the two months' TDS, the year's record and the recalculations between. */
    static JsonNode explainTds(TdsExplanation.Result r) {
        ObjectNode out = NODES.objectNode();
        out.put("currency", CURRENCY);
        out.put("period", r.period());
        out.put("financialYear", r.financialYear());
        putAmount(out, "tdsThisMonth", r.tdsThisMonth());
        if (r.tdsLastMonth() != null) {
            putAmount(out, "tdsLastMonth", r.tdsLastMonth());
            putAmount(out, "difference", r.difference());
        }
        TdsExplanation.YearRecord y = r.yearRecord();
        if (y == null) {
            out.put("noRecord", true);
            out.put("message", TdsExplanation.NO_RECORD);
        } else {
            ObjectNode year = out.putObject("yearRecord");
            year.put("asItStands", "today");
            year.put("note", TdsExplanation.RECORD_NOTE);
            put(year, "financialYear", y.financialYear());
            put(year, "regime", y.regime());
            put(year, "source", y.source());
            putAmount(year, "annualGross", y.annualGross());
            putAmount(year, "annualTaxableIncome", y.annualTaxableIncome());
            putAmount(year, "annualTax", y.annualTax());
            put(year, "appliesFrom", y.effectiveFrom());
            putAmount(year, "deductedSoFar", y.deductedSoFar());
            putAmount(year, "remaining", y.remaining());
        }
        ArrayNode recalcs = out.putArray("recalculations");
        for (TdsExplanation.Listed l : r.recalculations()) {
            ObjectNode row = recalcs.addObject();
            put(row, "date", l.on());
            put(row, "cause", l.cause());
            putAmount(row, "annualTaxBefore", l.annualTaxBefore());
            putAmount(row, "annualTaxAfter", l.annualTaxAfter());
        }
        out.put("rule", TdsExplanation.RULE);
        return AiToolResults.withoutIds(out);
    }

    private static ObjectNode comparedLine(PayslipComparison.Line l) {
        ObjectNode row = NODES.objectNode();
        put(row, "kind", l.kind());
        put(row, "code", l.code());
        put(row, "name", l.name());
        putAmount(row, "amount", l.amount());
        if (l.previousAmount() == null) {
            row.putNull("previousAmount");
        } else {
            putAmount(row, "previousAmount", l.previousAmount());
        }
        if (l.difference() != null) {
            putAmount(row, "difference", l.difference());
        }
        ArrayNode reasons = row.putArray("reasons");
        l.reasons().forEach(reason -> reasons.add(reason.name()));
        return row;
    }

    private static ObjectNode days(PayslipComparison.Days d) {
        ObjectNode out = NODES.objectNode();
        putAmount(out, "payable", d.payable());
        putAmount(out, "paid", d.paid());
        putAmount(out, "lossOfPay", d.lossOfPay());
        putAmount(out, "unpaid", d.unpaid());
        return out;
    }

    private static ObjectNode totals(PayslipComparison.Totals t) {
        ObjectNode out = NODES.objectNode();
        putAmount(out, "gross", t.gross());
        putAmount(out, "deductions", t.deductions());
        putAmount(out, "net", t.net());
        return out;
    }

    // ---- writing ----

    /** An amount as text at two decimals, the payslip's rounding: {@code 45000} is {@code "45000.00"}. */
    static String amount(BigDecimal value) {
        return value == null ? null : value.setScale(2, PAYSLIP_ROUNDING).toPlainString();
    }

    private static ArrayNode lines(List<PayslipLineResponse> lines) {
        ArrayNode list = NODES.arrayNode();
        if (lines != null) {
            for (PayslipLineResponse l : lines) {
                ObjectNode row = list.addObject();
                put(row, "code", l.code());
                put(row, "name", l.name());
                putAmount(row, "amount", l.amount());
                row.put("taxable", l.isTaxable());
            }
        }
        return list;
    }

    private static void putAmount(ObjectNode out, String name, BigDecimal value) {
        String text = amount(value);
        if (text != null) {
            out.put(name, text);
        }
    }

    /** Writes one value: enums by name, dates and times by their ISO {@code toString}; nothing for null. */
    private static void put(ObjectNode out, String name, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Enum<?> constant) {
            out.put(name, constant.name());
        } else {
            out.put(name, value.toString());
        }
    }
}
