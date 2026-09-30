package com.infinevo.payroll.payrun;

import java.util.List;

/**
 * The port every source of pay lines implements (W-29.2 §4). The computation runs every bean in
 * {@code @Order} and then sums; a later ticket adds a bean and never edits the loop. W-29.2 ships
 * {@link StructureLineContributor} only.
 *
 * <p>A contributor throws when it cannot compute the employee — a missing catalogue component, an
 * unreadable frequency. The computation records the message on that employee's row and carries on
 * with the others.
 */
public interface PayLineContributor {

    List<PayLine> contribute(PayRunEmployeeContext ctx);
}
