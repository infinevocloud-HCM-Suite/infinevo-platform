package com.infinevo.payroll.payrun;

/**
 * Which contributor wrote a line (W-29.2 §6). Each tag is reserved for the ticket that owns its
 * contributor, so a statutory or tax figure cannot be computed here "because legacy did" (§9).
 */
public enum LineSource {
    /** The salary structure — {@link StructureLineContributor}, W-29.2. */
    STRUCTURE,
    /** Payable-days scaling and loss of pay — W-29.3. */
    LOP,
    /** W-19 pay inputs — W-29.3. */
    PAY_INPUT,
    /** Provident fund, state insurance, professional tax — W-31. */
    STATUTORY,
    /** Tax deducted at source — W-36. */
    TAX
}
