package com.phegondev.usersmanagementsystem.enumuration;

public enum DocumentType {
    // Identification Documents
    AADHAR_CARD,
    PHOTO,
    OTHER_IDENTIFICATION,

    // Government IDs
    PAN_CARD,
    EPIC_CARD,
    OTHER_GOVERNMENT_ID,

    // Address Proof Documents
    RENT_AGREEMENT,
    LIGHT_BILL,
    BANK_STATEMENT,
    OTHER_ADDRESS_PROOF,

    // Qualification Documents
    TENTH_CERTIFICATE,
    TWELFTH_CERTIFICATE_DIPLOMA,
    DEGREE_CERTIFICATE,
    POST_GRADUATION_CERTIFICATE,
    OTHER_QUALIFICATION,

    // Previous Company Documents
    APPOINTMENT_LETTER,
    RELIEVING_LETTER,
    EXPERIENCE_LETTER,
    SALARY_SLIP_LAST_3_MONTHS,
    OTHER_PREVIOUS_COMPANY_DOC,

    // Passport Documents
    PASSPORT;


    // Helper method to convert from frontend string to enum
    public static DocumentType fromFrontendValue(String frontendValue) {
        if (frontendValue == null) {
            return null;
        }
        return DocumentType.valueOf(
                frontendValue.toUpperCase()
                        .replace(" ", "_")
                        .replace("/", "_")
                        .replace("(", "")
                        .replace(")", "")
                        .replace("-", "_")
        );
    }
}