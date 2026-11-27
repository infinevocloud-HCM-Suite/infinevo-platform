package com.infinevo.core.ai.action;

import static com.infinevo.core.ai.AiToolInputs.date;
import static com.infinevo.core.ai.AiToolInputs.dateProperty;
import static com.infinevo.core.ai.AiToolInputs.rejectUnknown;
import static com.infinevo.core.ai.AiToolInputs.requireOrder;
import static com.infinevo.core.ai.AiToolInputs.schema;
import static com.infinevo.core.ai.action.OrgMasterActionToolProvider.answer;
import static com.infinevo.core.ai.action.OrgMasterActionToolProvider.given;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiProposedAction;
import com.infinevo.core.ai.AiTool;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.AiToolInputs.BadInput;
import com.infinevo.core.ai.AiToolProvider;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.holiday.HolidayCalendarResponse;
import com.infinevo.core.holiday.HolidayCalendarService;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-114.28 step 4: {@code core.propose_holiday}. Finds the calendar by name (the default one when none is named),
 * checks the holiday is not already in it, then proposes; nothing is added until Confirm. Needs
 * {@code core.holiday.manage}, the Holidays screen's add action.
 */
@Component
public class HolidayActionToolProvider implements AiToolProvider {

    static final String TOOL = "core.propose_holiday";
    static final int MAX_LISTED = 20;
    static final String NO_CALENDARS = "There is no holiday calendar yet. Make one on the Holidays screen first.";

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final Supplier<List<HolidayCalendarResponse>> calendars;
    private final AiActionProposals proposals;
    private final TransactionOperations tx;
    private final ObjectNode inputSchema;

    @Autowired
    public HolidayActionToolProvider(
            HolidayCalendarService calendars,
            AiActionProposals proposals,
            PlatformTransactionManager transactionManager) {
        this(calendars::listCalendars, proposals, new TransactionTemplate(transactionManager));
    }

    HolidayActionToolProvider(
            Supplier<List<HolidayCalendarResponse>> calendars, AiActionProposals proposals, TransactionOperations tx) {
        this.calendars = Objects.requireNonNull(calendars, "calendars must not be null");
        this.proposals = Objects.requireNonNull(proposals, "proposals must not be null");
        this.tx = Objects.requireNonNull(tx, "tx must not be null");
        Map<String, ObjectNode> props = new LinkedHashMap<>();
        props.put(
                "calendar",
                NODES.objectNode()
                        .put("type", "string")
                        .put("maxLength", 128)
                        .put("description", "The holiday calendar's name. Leave out for the default calendar."));
        props.put(
                "name",
                NODES.objectNode()
                        .put("type", "string")
                        .put("maxLength", 128)
                        .put("description", "The holiday's name, for example Diwali."));
        props.put("from", dateProperty("The first day, YYYY-MM-DD."));
        props.put("to", dateProperty("The last day, YYYY-MM-DD. Leave out for a one-day holiday."));
        props.put(
                "restricted",
                NODES.objectNode()
                        .put("type", "boolean")
                        .put("description", "True for a restricted (optional) holiday. Leave out for a normal one."));
        this.inputSchema = schema(props, "name", "from");
    }

    @Override
    public List<AiTool> tools() {
        return List.of(new ProposeHoliday());
    }

    private final class ProposeHoliday implements AiTool {

        @Override
        public String name() {
            return TOOL;
        }

        @Override
        public String description() {
            return "Proposes adding a holiday to a holiday calendar. Nothing is added: the user sees a card and must"
                    + " press Confirm. Ask for the name and the date if they are not given.";
        }

        @Override
        public JsonNode inputSchema() {
            return inputSchema.deepCopy();
        }

        @Override
        public String requiredAction() {
            return "core.holiday.manage";
        }

        @Override
        public AiToolResult execute(AiToolContext ctx, JsonNode input) {
            JsonNode args = input == null || input.isNull() ? NODES.objectNode() : input;
            try {
                if (!args.isObject()) {
                    throw new BadInput("input must be a JSON object");
                }
                rejectUnknown(args, inputSchema.path("properties"));
                String name = given(args, "name");
                LocalDate from = date(args, "from");
                if (name == null || from == null) {
                    throw new BadInput("name and from are required. Ask the user for them.");
                }
                LocalDate to = date(args, "to");
                to = to == null ? from : to;
                requireOrder(from, to);
                JsonNode restrictedNode = args.get("restricted");
                if (restrictedNode != null && !restrictedNode.isNull() && !restrictedNode.isBoolean()) {
                    throw new BadInput("restricted must be true or false");
                }
                boolean restricted = restrictedNode != null && restrictedNode.asBoolean(false);
                String wanted = given(args, "calendar");
                LocalDate last = to;
                AiProposedAction card = tx.execute(s -> {
                    HolidayCalendarResponse calendar = pick(calendars.get(), wanted);
                    if (HolidayAddExecutor.alreadyThere(calendar, name, from)) {
                        throw new BadInput(HolidayAddExecutor.ALREADY);
                    }
                    ObjectNode payload = NODES.objectNode();
                    payload.put(HolidayAddExecutor.CALENDAR, calendar.name().trim());
                    payload.put(HolidayAddExecutor.NAME, name);
                    payload.put(HolidayAddExecutor.FROM, from.toString());
                    payload.put(HolidayAddExecutor.TO, last.toString());
                    payload.put(HolidayAddExecutor.RESTRICTED, restricted);
                    return proposals.propose(ctx, HolidayAddExecutor.ACTION_CODE, payload);
                });
                return AiToolResult.success(answer(Objects.requireNonNull(card, "card")));
            } catch (BadInput e) {
                return AiToolResult.failure(e.getMessage());
            } catch (IllegalArgumentException | IllegalStateException e) {
                return AiToolResult.failure(
                        e.getMessage() != null && e.getMessage().contains("already proposed")
                                ? "One step at a time. Ask the user about the next one."
                                : e.getMessage());
            }
        }
    }

    /** The named calendar; with no name, the default one (or the only one). An unknown name lists the real ones. */
    static HolidayCalendarResponse pick(List<HolidayCalendarResponse> all, String wanted) {
        if (all.isEmpty()) {
            throw new BadInput(NO_CALENDARS);
        }
        if (wanted != null) {
            return HolidayAddExecutor.find(all, wanted)
                    .orElseThrow(() -> new BadInput("No holiday calendar named " + wanted + ". The calendars are: "
                            + String.join(
                                    ", ",
                                    all.stream()
                                            .limit(MAX_LISTED)
                                            .map(HolidayCalendarResponse::name)
                                            .toList())
                            + "."));
        }
        if (all.size() == 1) {
            return all.get(0);
        }
        return all.stream()
                .filter(HolidayCalendarResponse::isDefault)
                .findFirst()
                .orElseThrow(() -> new BadInput("Which calendar? The calendars are: "
                        + String.join(
                                ", ",
                                all.stream()
                                        .limit(MAX_LISTED)
                                        .map(HolidayCalendarResponse::name)
                                        .toList())
                        + "."));
    }
}
