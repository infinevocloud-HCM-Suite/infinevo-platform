# Certification of Infinevo Test Co — read first

| Item | Value |
|---|---|
| Company | Infinevo Test Co, Azure dev, `https://ep-infinevo-dev-huc0atg2hvd2fgbr.z02.azurefd.net` |
| Pay period under test | October 2026 |
| Leave year | 1 Jan – 31 Dec 2026 (seeded policies CL 12, EL 18, SL 10) |
| Seeded people | 53 employees ITC-0001…ITC-0053, CTC and bank on all, Sept attendance, 2026-07 projects |
| Logins | Karmaveer `kmohapatra@` tenant-admin · Krushna `ksonalkar@` hr+manager · Mohit `mbirla@` payroll-officer+finance · Sahmad `sahmad@` employee (all `@infinevocloud.com`) |
| Plans | `karmaveer-tenant-admin.md` · `krushna-hr-manager.md` · `mohit-payroll-finance.md` · `sahmad-employee.md` |
| Status | `TRACKER.md` — the only file testers edit |

## Shared test data (everyone uses exactly this)

| Key | Value |
|---|---|
| New hires (Krushna, K-05) | NH-1 Ritu Anand, SE, ENG, BLR, CTC 9,60,000, manager Krushna · NH-2 Farhan Ali, EXEC, OPS, HYD, CTC 2,40,000 (ESI) · NH-3 Lakshmi Raman, VP, FIN, PUN, CTC 36,00,000 · NH-4 Dev Kulkarni, SE, ENG, BLR, CTC 7,20,000, manager Krushna · NH-5 Asha Pillai, ACCT, FIN, PUN, CTC 6,00,000. Joining 2026-10-01; PAN and Aadhaar any valid-format unique values |
| Sahmad's leave | CL 2026-10-15 to 2026-10-16 (approve) · SL 2026-10-20 (reject) · EL 2026-10-27 (apply then cancel) |
| Sahmad's claim | Internet, 1,500, receipt PDF, October |
| Sahmad's tax | New regime first, then switch to old: 80C 1,50,000, HRA rent 15,000 per month, 80D 25,000 |
| Overtime | Sahmad 2026-10-18, 3 hours |
| Timesheet | Week of 2026-10-12, 8 h per day on Cert Project |
| Known exceptions, do not raise again | D-25 proof-of-investment screens · D-43–D-58 (leave import, approvals history, timesheet recall, attendance history, /me layout, holiday view) · D-78–D-87 screen audit phases 3–4 |

## Order

| Phase | Who | Gate |
|---|---|---|
| P0 | Founder: worker fixed, seeded Oct pay run deleted, all four can sign in | tracker P0 Pass |
| P1 | Karmaveer K-00…K-07x: verify the seeded company details, create whatever is missing (company settings, masters, holiday calendar, approval rules, leave types, LOP policy) | tracker P1 Pass |
| P2 | Krushna K-05, K-06 (hires and invitations); Mohit M-01…M-05 (schedule, FBP, PT, tax window) in parallel | tracker P2 Pass |
| Parallel | all three plans | — |
| J1 | Mohit M-10 pay run: waits for Krushna L-06, L-09, T-03, T-04, T-06 and Sahmad E-07, E-08, E-09 | tracker J1 Pass |
| J2 | Mohit M-14 onward, Sahmad E-10, Karmaveer K-11 audit sample | sign-off |

## Defect reporting

One row per defect in `TRACKER.md` section Defects: date, tester, plan step id, role, URL, one line on what happened, one line on what was expected. The founder numbers it `D-nn`. No separate report files.
