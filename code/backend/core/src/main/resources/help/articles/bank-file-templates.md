---
id: bank-file-templates
title: Make a bank file template
summary: Lay out the salary file your bank's bulk upload asks for: name, CSV or Excel, heading row and columns.
roles: payroll-officer, tenant-admin
screen: /payroll/settings
route: /payroll/settings/bank-files
keywords: bank file template, bank template, bulk upload, salary upload, neft file, bank format, bank columns, hdfc, icici, sbi
---
Who can do this: the payroll officer or the tenant admin, or anyone whose role lets them manage payroll settings. Others see the list but no buttons.

1. Open Payroll, then Payroll settings, and choose Bank files.
2. The list shows each template with its File (CSV or Excel) and number of Columns. The Built-in template cannot be changed; click Copy to start from it.
3. Click New template, or Edit on one of your own.
4. Enter the Template name, for example the bank's name.
5. Under File type choose CSV or Excel (.xlsx). Turn on Heading row if your bank wants column headings on the first line.
6. Click Add column for each column of the bank's file, in the bank's order. For each one:
   - Heading: the column heading the bank expects.
   - Fills with: A field (pick it under Field or text, such as the account number), Fixed text (type the text, such as NEFT) or Serial no. (1, 2, 3 …).
   - Format: for some fields, such as the pay month, pick how the value is written.
   The Sample column shows what each column will look like. Use the arrows to move a column up or down, or Remove to drop it.
7. Click Save.

To remove a template, click Delete and confirm. Files already made from it stay in each pay run's history.

To use a template, open an approved pay run and click Bank file… (see "Make and download the bank file").
