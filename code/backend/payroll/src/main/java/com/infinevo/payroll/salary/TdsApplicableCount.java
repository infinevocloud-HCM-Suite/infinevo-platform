package com.infinevo.payroll.salary;

/**
 * D-145: how many of the tenant's employees employed today TDS applies to. An employee with no saved
 * statutory profile counts as applicable, as the pay run treats them (D-141).
 *
 * @param applicable employees whose profile is TDS applicable, or who have no profile
 * @param employees employees employed today
 */
public record TdsApplicableCount(int applicable, int employees) {}
