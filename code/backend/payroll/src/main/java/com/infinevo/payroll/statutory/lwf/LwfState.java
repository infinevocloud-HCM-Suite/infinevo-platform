package com.infinevo.payroll.statutory.lwf;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * State LWF levy status in national reference schema (W-92).
 */
@Entity
@Table(name = "lwf_state", schema = "reference")
public class LwfState {

    @Id
    @Column(name = "state_code", length = 10, nullable = false)
    private String stateCode;

    @Column(name = "state_name", length = 64, nullable = false)
    private String stateName;

    @Column(name = "levies_lwf", nullable = false)
    private boolean leviesLwf = true;

    protected LwfState() {}

    public LwfState(String stateCode, String stateName, boolean leviesLwf) {
        this.stateCode = stateCode;
        this.stateName = stateName;
        this.leviesLwf = leviesLwf;
    }

    public String getStateCode() {
        return stateCode;
    }

    public String getStateName() {
        return stateName;
    }

    public boolean isLeviesLwf() {
        return leviesLwf;
    }
}
