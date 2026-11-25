package com.infinevo.payroll.payrun.bankfile;

import java.util.List;

/** Bank file templates: the built-in plus the company's own (W-88.2 spec sections 4.3, 4.4). */
public interface BankFileTemplateService {

    /** The id of the built-in template, which is never stored. */
    String BUILT_IN_ID = "generic-neft";

    /** The built-in first, then the company's templates by name. */
    List<BankFileTemplateResponse> list();

    List<BankFileFieldResponse> fields();

    /** @param id a template UUID or {@link #BUILT_IN_ID} */
    BankFileTemplateResponse get(String id);

    BankFileTemplateResponse create(BankFileTemplateRequest request);

    BankFileTemplateResponse update(String id, BankFileTemplateRequest request);

    void delete(String id);

    /** A company template made from {@code id}, named "Copy of …", numbered when the name is taken. */
    BankFileTemplateResponse copy(String id);
}
