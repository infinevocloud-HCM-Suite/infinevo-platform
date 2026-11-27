package com.infinevo.payroll.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiActionField;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.action.AiActionDescription;
import com.infinevo.core.ai.action.AiActionExecutor;
import com.infinevo.core.report.ExportFormat;
import com.infinevo.payroll.payrun.bankfile.BankFileColumn;
import com.infinevo.payroll.payrun.bankfile.BankFileColumnSource;
import com.infinevo.payroll.payrun.bankfile.BankFileField;
import com.infinevo.payroll.payrun.bankfile.BankFileTemplateException;
import com.infinevo.payroll.payrun.bankfile.BankFileTemplateRequest;
import com.infinevo.payroll.payrun.bankfile.BankFileTemplateResponse;
import com.infinevo.payroll.payrun.bankfile.BankFileTemplateService;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Adds a bank file template the AI helper proposed (W-114.28 step 6). Confirm calls
 * {@link BankFileTemplateService#create}, what {@code POST /api/v1/payroll/bank-file-templates} calls
 * ({@code payrun/bankfile/BankFileTemplateController.java:59-65}), so every check of the screen runs again; a name
 * taken in between ends {@code FAILED}. A settings step: it touches no pay run and no money.
 */
@Component
public class BankFileTemplateCreateExecutor implements AiActionExecutor {

    public static final String ACTION_CODE = "payroll.bank_file_template.create";
    static final String NAME = "name";
    static final String FORMAT = "fileFormat";
    static final String HEADER = "includeHeader";
    static final String COLUMNS = "columns";
    static final String HEADING = "heading";
    static final String SOURCE = "source";
    static final String FIELD = "field";
    static final String VALUE = "value";
    static final String VALUE_FORMAT = "format";

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final BankFileTemplateService templates;

    public BankFileTemplateCreateExecutor(BankFileTemplateService templates) {
        this.templates = Objects.requireNonNull(templates, "templates must not be null");
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String requiredAction() {
        return "payroll.settings.manage";
    }

    @Override
    public PlatformModule requiredModule() {
        return PlatformModule.PAYROLL;
    }

    /** Payroll set-up: an officer need not be an employee. */
    @Override
    public boolean requiresEmployeeProfile() {
        return false;
    }

    @Override
    public AiActionDescription describe(AiToolContext ctx, JsonNode payload) {
        BankFileTemplateRequest request = request(payload);
        List<AiActionField> fields = new ArrayList<>();
        fields.add(new AiActionField("Name", request.name()));
        fields.add(new AiActionField("File format", request.fileFormat().name()));
        fields.add(new AiActionField("Heading row", Boolean.TRUE.equals(request.includeHeader()) ? "Yes" : "No"));
        int i = 1;
        for (BankFileColumn c : request.columns()) {
            fields.add(new AiActionField("Column " + i++, describe(c)));
        }
        return new AiActionDescription(
                "Add bank file template " + request.name() + " with "
                        + request.columns().size() + " columns",
                fields);
    }

    @Override
    public JsonNode execute(AiToolContext ctx, JsonNode payload) {
        BankFileTemplateResponse created;
        try {
            created = templates.create(request(payload));
        } catch (BankFileTemplateException e) {
            throw new IllegalStateException(e.getMessage());
        }
        ObjectNode result = NODES.objectNode();
        result.put("templateId", created.id());
        return result;
    }

    /** What a column takes, in the screen's words. */
    static String describe(BankFileColumn c) {
        String from =
                switch (c.source()) {
                    case SERIAL -> "Serial number";
                    case FIXED -> "Fixed text \"" + (c.value() == null ? "" : c.value()) + "\"";
                    case FIELD -> c.field().label() + (c.format() == null ? "" : " (" + c.format() + ")");
                };
        return c.heading() + ": " + from;
    }

    /** The stored payload as the screen's request. Enum names only; no ids. */
    static BankFileTemplateRequest request(JsonNode p) {
        List<BankFileColumn> columns = new ArrayList<>();
        for (JsonNode c : p.path(COLUMNS)) {
            BankFileColumnSource source =
                    BankFileColumnSource.valueOf(c.path(SOURCE).asText());
            columns.add(new BankFileColumn(
                    c.path(HEADING).asText(),
                    source,
                    source == BankFileColumnSource.FIELD
                            ? BankFileField.valueOf(c.path(FIELD).asText())
                            : null,
                    source == BankFileColumnSource.FIXED ? c.path(VALUE).asText("") : null,
                    c.hasNonNull(VALUE_FORMAT) ? c.path(VALUE_FORMAT).asText() : null));
        }
        return new BankFileTemplateRequest(
                p.path(NAME).asText(),
                ExportFormat.valueOf(p.path(FORMAT).asText("CSV")),
                p.path(HEADER).asBoolean(true),
                columns);
    }
}
