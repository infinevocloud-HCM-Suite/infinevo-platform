package com.infinevo.core.navigation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Development-mode catalogue consistency check (W-12.3, spec §2).
 *
 * <p>At application startup (after all beans are ready), walks every leaf entry in
 * {@link NavigationCatalogue#DEFAULT_ITEMS} and verifies that
 * {@link RequestMappingHandlerMapping} has a {@code GET} mapping whose path pattern
 * matches the {@code targetEndpoint}.  If any item's endpoint is missing, the check
 * logs a clear {@code WARN} so a developer sees it immediately — no silent drift.
 *
 * <p>Activated only under {@code dev} and {@code test} profiles so it never runs in
 * production.  In the test suite {@link NavigationMatchesEnforcementIT} provides the
 * authoritative two-way assertion; this check is an earlier, lower-cost signal.
 */
@Component
@Profile({"dev", "test"})
class NavigationCatalogueValidator implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(NavigationCatalogueValidator.class);

    private final RequestMappingHandlerMapping handlerMapping;

    NavigationCatalogueValidator(RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        List<String> missing = new ArrayList<>();
        for (NavigationCatalogue.ItemDefinition def : NavigationCatalogue.DEFAULT_ITEMS) {
            checkItem(def, missing);
        }
        if (missing.isEmpty()) {
            log.info(
                    "W-12.3 catalogue check: all {} leaf items have a registered GET endpoint",
                    countLeaves(NavigationCatalogue.DEFAULT_ITEMS));
        } else {
            for (String warn : missing) {
                log.warn("W-12.3 catalogue check: {}", warn);
            }
            log.warn(
                    "W-12.3 catalogue check: {} endpoint(s) missing — "
                            + "fix the catalogue or add the controller before merging",
                    missing.size());
        }
    }

    private void checkItem(NavigationCatalogue.ItemDefinition def, List<String> missing) {
        if (def.hasChildren()) {
            for (NavigationCatalogue.ItemDefinition child : def.children()) {
                checkItem(child, missing);
            }
            return;
        }
        if (!isRegistered(def.targetEndpoint())) {
            missing.add("item '" + def.key() + "' targets '" + def.targetEndpoint() + "' but no GET mapping found");
        }
    }

    private boolean isRegistered(String targetEndpoint) {
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            if (!methods.isEmpty() && !methods.contains(RequestMethod.GET)) {
                continue;
            }
            Set<String> patterns = info.getPatternValues();
            if (patterns.contains(targetEndpoint)) {
                return true;
            }
        }
        return false;
    }

    private int countLeaves(List<NavigationCatalogue.ItemDefinition> items) {
        int count = 0;
        for (NavigationCatalogue.ItemDefinition def : items) {
            count += def.hasChildren() ? countLeaves(def.children()) : 1;
        }
        return count;
    }
}
