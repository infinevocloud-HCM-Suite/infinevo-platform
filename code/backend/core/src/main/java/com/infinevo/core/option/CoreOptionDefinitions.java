package com.infinevo.core.option;

import com.infinevo.core.portal.PanelDescriptor;
import com.infinevo.core.portal.PortalPanelProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Registers core tenant options including employee self-edit and portal panel visibility (W-104 §4.1).
 */
@Component
public class CoreOptionDefinitions implements OptionDefinitionSource {

    private final List<PortalPanelProvider> panelProviders;

    public CoreOptionDefinitions(List<PortalPanelProvider> panelProviders) {
        this.panelProviders = panelProviders != null ? panelProviders : List.of();
    }

    @Override
    public List<OptionDefinition> definitions() {
        List<OptionDefinition> list = new ArrayList<>();
        list.add(OptionDefinition.of(
                "core.employee.self_edit",
                OptionValueType.BOOLEAN,
                OptionCategory.PORTAL,
                "true",
                "Allow employee self-edit",
                "When off, employees can view their profile but cannot change it."));

        for (PortalPanelProvider provider : panelProviders) {
            if (provider == null || "profile".equalsIgnoreCase(provider.code())) {
                continue;
            }
            String title = provider.code();
            try {
                PanelDescriptor descriptor = provider.panel(UUID.randomUUID());
                if (descriptor != null && descriptor.title() != null) {
                    title = descriptor.title();
                }
            } catch (Exception ignored) {
            }
            list.add(OptionDefinition.of(
                    "core.portal.panel." + provider.code() + ".visible",
                    OptionValueType.BOOLEAN,
                    OptionCategory.PORTAL,
                    "true",
                    "Show the " + title + " tab",
                    "Hides the tab only; access stays governed by roles."));
        }
        return list;
    }
}
