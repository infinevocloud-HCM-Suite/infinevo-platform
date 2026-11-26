package com.infinevo.payroll.compliance.form24q;

import java.util.List;

/**
 * Everything the Form 138 batch header says about the employer and the person responsible (W-89). The address
 * lines are already cut to the file's 25 characters each; the responsible person's address is the employer's.
 */
record Form138Deductor(
        String tan,
        String pan,
        String name,
        List<String> addressLines,
        Integer stateCode,
        String pin,
        String email,
        String phone,
        String deductorType,
        String gstin,
        String responsibleName,
        String responsibleDesignation,
        String responsiblePan,
        String responsibleEmail,
        String responsiblePhone) {}
