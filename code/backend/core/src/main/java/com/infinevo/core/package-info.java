/**
 * Always-on capabilities, available to every tenant whatever they bought:
 * employee master, leave, holidays, identity, approvals, audit,
 * and the basic capture of attendance and overtime (D-35).

 core must not depend on hrms or payroll. The build enforces it.
 *
 * <p>Package convention inside a feature:
 * {@code com.infinevo.core.<feature>.controller | service | serviceimpl | repository |
 * entity | dto | mapper}.
 *
 * <p>See {@code docs/target-state/03-code-structure.md} section 3.
 */
package com.infinevo.core;
