# Certification tracker — Infinevo Test Co

Status values: `Blocked` `Ready` `Pass` `Fail` `Exception`. Update your own rows only. One line per step. A Fail also gets one row in Defects at the bottom.

## Gates

| Gate | Owner | Needs | Status | Date |
|---|---|---|---|---|
| P0 Environment: worker fixed, seeded Oct pay run deleted, four logins work | Founder | – | Ready | |
| P1 Setup: K-00…K-07x | Karmaveer | P0 | Blocked | |
| P2 People and payroll config: K-05, K-06, M-01…M-05 | Krushna, Mohit | P1 | Blocked | |
| J1 October pay run may start: L-06, L-09, T-03, T-04, T-06, E-07, E-08a, E-09, S-02, P-01…P-03 | Mohit | P2 | Blocked | |
| J2 Reconciliation: M-14 paid | all | J1 | Blocked | |

## Karmaveer — tenant admin

Steps are in `karmaveer-tenant-admin.md`. Section A is P1.

| Id | Status | Date | Note |
|---|---|---|---|
| K-00 | Blocked | | |
| K-01 | Blocked | | |
| K-01a | Blocked | | |
| K-01b | Blocked | | needs M-01, M-03 |
| K-02 | Blocked | | |
| K-02a | Blocked | | |
| K-02b | Blocked | | |
| K-02c | Blocked | | |
| K-02d | Blocked | | |
| K-03 | Blocked | | |
| K-04 | Blocked | | |
| K-05x | Blocked | | |
| K-06x | Blocked | | |
| K-07x | Blocked | | founder re-seeds if short |
| K-08 | Blocked | | |
| K-08a | Blocked | | |
| K-09 | Blocked | | |
| K-10 | Blocked | | needs K-05 |
| K-10a | Blocked | | |
| K-10b | Blocked | | |
| K-20 | Blocked | | staff.infinevo |
| K-21 | Blocked | | staff.infinevo |
| K-22 | Blocked | | |
| K-23 | Blocked | | |
| K-24 | Blocked | | staff.infinevo |
| K-25 | Blocked | | staff.infinevo |
| K-26 | Blocked | | staff.infinevo |
| K-11 | Blocked | | J2 |
| K-12 | Blocked | | J2 |
| K-13 | Blocked | | J2 |
| K-14 | Blocked | | |
| N-01 | Blocked | | |
| N-02 | Blocked | | |

## Krushna — HR + manager

| Id | Status | Date | Note |
|---|---|---|---|
| K-05 | Blocked | | |
| K-05a | Blocked | | |
| K-05b | Blocked | | |
| K-06 | Blocked | | |
| K-07 | Blocked | | |
| H-01 | Blocked | | |
| H-02 | Blocked | | |
| H-03 | Blocked | | |
| H-04 | Blocked | | |
| H-05 | Blocked | | |
| H-06 | Blocked | | |
| L-01 | Blocked | | |
| L-02 | Blocked | | |
| L-03 | Blocked | | |
| L-04 | Blocked | | |
| L-05 | Blocked | | needs E-03 |
| L-06 | Blocked | | |
| L-07 | Blocked | | needs E-03a |
| L-08 | Blocked | | |
| L-09 | Blocked | | |
| L-10 | Blocked | | |
| L-11 | Exception | | D-43 |
| Y-01 | Blocked | | |
| Y-02 | Blocked | | |
| T-01 | Blocked | | |
| T-02 | Blocked | | needs E-05 |
| T-03 | Blocked | | needs E-05a |
| T-04 | Blocked | | needs E-06 |
| T-05 | Blocked | | |
| T-06 | Blocked | | needs E-07 |
| T-07 | Blocked | | |
| T-08 | Blocked | | |
| N-01 | Blocked | | |
| N-02 | Blocked | | |

## Mohit — payroll officer + finance

| Id | Status | Date | Note |
|---|---|---|---|
| M-01 | Pass | 2026-10-13 | Monthly pay schedule set to last working day, Oct 2026 |
| M-02 | Pass | 2026-10-13 | FBP enabled, Meal ₹2,200/mo, Fuel ₹1,000/mo, Oct window open |
| M-03 | Pass | 2026-10-13 | Karnataka and Maharashtra PT slabs active |
| M-04 | Pass | 2026-10-13 | FY 2026-27 tax declaration window open through 2026-11-30 |
| M-05 | Pass | 2026-10-13 | EPF 12/12, ESI 0.75/3.25 confirmed as seeded |
| S-01 | Pass | 2026-10-13 | Shift Allowance custom component created |
| S-02 | Pass | 2026-10-13 | CTC structures assigned to 5 new hires (NH-1..NH-5) |
| S-03 | Pass | 2026-10-13 | NH-2 (EMP0046) ESI eligible confirmed |
| S-04 | Pass | 2026-10-13 | Sahmad ITC-0044 salary revision created (+10% hike, effective 2026-10-01) |
| S-05 | Fail | 2026-10-13 | Upload fails: requires action core.document.upload |
| P-01 | Blocked | | |
| P-02 | Blocked | | |
| P-03 | Blocked | | needs E-09 |
| P-04 | Blocked | | needs E-08a |
| M-10 | Blocked | | J1 |
| M-11 | Blocked | | |
| M-12 | Blocked | | |
| M-13 | Blocked | | |
| M-13a | Blocked | | |
| M-14 | Blocked | | |
| M-15 | Blocked | | |
| M-16 | Blocked | | |
| R-01 | Blocked | | J2 |
| R-02 | Blocked | | |
| R-03 | Blocked | | |
| R-04 | Blocked | | |
| N-01 | Blocked | | |
| N-02 | Blocked | | |

## Sahmad — employee

| Id | Status | Date | Note |
|---|---|---|---|
| E-01 | Blocked | | |
| E-02 | Blocked | | |
| E-03 | Blocked | | |
| E-03a | Blocked | | |
| E-03b | Blocked | | |
| E-03c | Blocked | | needs Y-01 |
| E-04 | Blocked | | needs L-06 |
| E-05 | Blocked | | |
| E-05a | Blocked | | |
| E-06 | Blocked | | |
| E-06a | Blocked | | needs T-05 |
| E-07 | Blocked | | |
| E-08 | Blocked | | needs M-02 |
| E-08a | Blocked | | needs M-04 |
| E-08b | Exception | | D-25 |
| E-09 | Blocked | | |
| E-10 | Blocked | | J2 |
| E-10a | Blocked | | |
| E-11 | Blocked | | needs R-02 |
| N-01 | Blocked | | |
| N-02 | Blocked | | |
| N-03 | Blocked | | |

## Defects

| # | Date | Tester | Step | Role | URL | What happened | Expected | Founder no. |
|---|---|---|---|---|---|---|---|---|
| 1 | 2026-10-13 | Mohit | S-05 | payroll-officer | /payroll/prior-payroll | Upload prior payroll CSV fails with "Not permitted: requires action 'core.document.upload'" | Role payroll-officer should hold core.document.upload or prior payroll import should allow payroll-officer | |
