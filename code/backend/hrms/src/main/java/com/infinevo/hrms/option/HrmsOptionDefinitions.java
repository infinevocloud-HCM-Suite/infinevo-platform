package com.infinevo.hrms.option;

import com.infinevo.core.option.OptionCategory;
import com.infinevo.core.option.OptionDefinition;
import com.infinevo.core.option.OptionDefinitionSource;
import com.infinevo.core.option.OptionValueType;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Registers HRMS tenant options (W-104 §4.1).
 */
@Component
public class HrmsOptionDefinitions implements OptionDefinitionSource {

    @Override
    public List<OptionDefinition> definitions() {
        return List.of(OptionDefinition.of(
                "hrms.attendance.clock_location_required",
                OptionValueType.BOOLEAN,
                OptionCategory.ATTENDANCE,
                "false",
                "Require location for clock in/out",
                "When on, a clock-in or clock-out without a location is refused."));
    }
}
