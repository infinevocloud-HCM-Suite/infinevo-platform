package com.itsdev.payroll.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BenefitUtility {

    public static Map<String, Object> getUtilityData() {
        Map<String, Object> data = new HashMap<>();
        data.put("benefitPlans", getBenefitPlans());
        data.put("section6aDetails", getSection6aDetails());
        return data;
    }

    public static List<Map<String, Object>> getBenefitPlans() {
        List<Map<String, Object>> plans = new ArrayList<>();

        Map<String, Object> p1 = new HashMap<>();
        p1.put("plan", "nps");
        p1.put("planNameFormatted", "National Pension Scheme");
        p1.put("category", "nps");
        p1.put("categoryDisplayName", "National Pension Scheme");
        p1.put("isPreTax", true);
        plans.add(p1);

        Map<String, Object> p2 = new HashMap<>();
        p2.put("plan", "other non taxable benefit");
        p2.put("planNameFormatted", "Other Non-Taxable Deduction");
        p2.put("category", "other non taxable benefit");
        p2.put("categoryDisplayName", "Other Non-Taxable Deduction");
        p2.put("isPreTax", true);
        plans.add(p2);

        return plans;
    }

    public static List<Map<String, Object>> getBenefitPlans(String benefitCategory) {
        return getBenefitPlans().stream()
                .filter(p -> p.get("category").equals(benefitCategory))
                .collect(Collectors.toList());
    }

    public static List<Map<String, Object>> getSection6aDetails() {
        List<Map<String, Object>> list = new ArrayList<>();

        // 80C - Life Insurance Premium
        Map<String, Object> d1 = new HashMap<>();
        d1.put("categoryFormatted", "80C");
        d1.put("category", "80c");
        d1.put("typeFormatted", "Life Insurance Premium");
        d1.put("type", "lic");
        list.add(d1);

        // 80C - Public Provident Fund (PPF)
        Map<String, Object> d2 = new HashMap<>();
        d2.put("categoryFormatted", "80C");
        d2.put("category", "80c");
        d2.put("typeFormatted", "Public Provident Fund (PPF)");
        d2.put("type", "ppf");
        list.add(d2);

        // 80C - Employee Provident Fund (EPF)
        Map<String, Object> d3 = new HashMap<>();
        d3.put("categoryFormatted", "80C");
        d3.put("category", "80c");
        d3.put("typeFormatted", "Employee Provident Fund (EPF)");
        d3.put("type", "epf");
        list.add(d3);

        // 80C - National Savings Certificate (NSC)
        Map<String, Object> d4 = new HashMap<>();
        d4.put("categoryFormatted", "80C");
        d4.put("category", "80c");
        d4.put("typeFormatted", "National Savings Certificate (NSC)");
        d4.put("type", "nsc");
        list.add(d4);

        // 80C - Tax Saving Fixed Deposit
        Map<String, Object> d5 = new HashMap<>();
        d5.put("categoryFormatted", "80C");
        d5.put("category", "80c");
        d5.put("typeFormatted", "Tax Saving Fixed Deposit");
        d5.put("type", "taxSavingFd");
        list.add(d5);

        // 80C - Sukanya Samriddhi Yojana
        Map<String, Object> d6 = new HashMap<>();
        d6.put("categoryFormatted", "80C");
        d6.put("category", "80c");
        d6.put("typeFormatted", "Sukanya Samriddhi Yojana");
        d6.put("type", "ssy");
        list.add(d6);

        // 80C - ELSS
        Map<String, Object> d7 = new HashMap<>();
        d7.put("categoryFormatted", "80C");
        d7.put("category", "80c");
        d7.put("typeFormatted", "ELSS (Equity Linked Savings Scheme)");
        d7.put("type", "elss");
        list.add(d7);

        // 80C - Children’s Tuition Fees
        Map<String, Object> d8 = new HashMap<>();
        d8.put("categoryFormatted", "80C");
        d8.put("category", "80c");
        d8.put("typeFormatted", "Children’s Tuition Fees");
        d8.put("type", "tuitionFee");
        list.add(d8);

        // 80D - Medical Insurance Premium (Self & Family)
        Map<String, Object> d9 = new HashMap<>();
        d9.put("categoryFormatted", "80D");
        d9.put("category", "80d");
        d9.put("typeFormatted", "Medical Insurance Premium (Self & Family)");
        d9.put("type", "medInsuranceSelfFamily");
        list.add(d9);

        // 80D - Medical Insurance Premium (Parents)
        Map<String, Object> d10 = new HashMap<>();
        d10.put("categoryFormatted", "80D");
        d10.put("category", "80d");
        d10.put("typeFormatted", "Medical Insurance Premium (Parents)");
        d10.put("type", "medInsuranceParents");
        list.add(d10);

        // 80CCD(1B) - NPS (Employee Contribution)
        Map<String, Object> d11 = new HashMap<>();
        d11.put("categoryFormatted", "80CCD(1B)");
        d11.put("category", "80ccd1b");
        d11.put("typeFormatted", "NPS (Employee Contribution)");
        d11.put("type", "npsEmployee");
        list.add(d11);

        // 80CCD(2) - NPS (Employer Contribution)
        Map<String, Object> d12 = new HashMap<>();
        d12.put("categoryFormatted", "80CCD(2)");
        d12.put("category", "80ccd2");
        d12.put("typeFormatted", "NPS (Employer Contribution)");
        d12.put("type", "npsEmployer");
        list.add(d12);

        // 80E - Interest on Education Loan
        Map<String, Object> d13 = new HashMap<>();
        d13.put("categoryFormatted", "80E");
        d13.put("category", "80e");
        d13.put("typeFormatted", "Interest on Education Loan");
        d13.put("type", "educationLoanInterest");
        list.add(d13);

        // 80G - Donations
        Map<String, Object> d14 = new HashMap<>();
        d14.put("categoryFormatted", "80G");
        d14.put("category", "80g");
        d14.put("typeFormatted", "Donations to Specified Funds");
        d14.put("type", "donations");
        list.add(d14);

        // 80TTA - Interest on Savings Account
        Map<String, Object> d15 = new HashMap<>();
        d15.put("categoryFormatted", "80TTA");
        d15.put("category", "80tta");
        d15.put("typeFormatted", "Interest on Savings Account");
        d15.put("type", "savingsInterest");
        list.add(d15);

        return list;
    }


}
