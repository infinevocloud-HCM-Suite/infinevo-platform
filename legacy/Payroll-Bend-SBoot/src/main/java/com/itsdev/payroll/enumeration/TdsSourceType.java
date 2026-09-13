package com.itsdev.payroll.enumeration;

public enum TdsSourceType {
    
    POI_BASED("POI Based"),
    DEFAULT_REGIME("Default Tax Regime"),
    SALARY_REVISION("Salary Revision");
    
    private final String displayName;
    
    TdsSourceType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    // Optional: Helper to get from string
    public static TdsSourceType fromString(String value) {
        if (value == null) return null;
        
        for (TdsSourceType type : TdsSourceType.values()) {
            if (type.name().equalsIgnoreCase(value) || 
                type.getDisplayName().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }
}