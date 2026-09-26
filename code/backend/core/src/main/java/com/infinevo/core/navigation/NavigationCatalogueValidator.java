package com.infinevo.core.navigation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * The "every menu item's target endpoint exists" check (W-12.3, spec §2).
 *
 * <p>Runs once all singletons are built, in every profile, and <strong>refuses to start the
 * application</strong> if a leaf of {@link NavigationCatalogue#DEFAULT_ITEMS} names a
 * {@code targetEndpoint} that no controller maps for {@code GET}. A menu item that leads to a
 * 404 is the drift this ticket exists to prevent, and a warning in a log nobody reads would not
 * prevent it.
 *
 * <p>Any Spring context that scans {@code com.infinevo.core.navigation} must therefore also
 * hold every controller the catalogue targets — which is exactly what
 * {@code NavigationMatchesEnforcementIT} needs to be a real test.
 */
@Component
class NavigationCatalogueValidator implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(NavigationCatalogueValidator.class);

    /**
     * Every handler mapping in the context. A web application has one; with Actuator on the
     * classpath (the worker) there is a second one for {@code @ControllerEndpoint}s, so a single
     * bean cannot be injected. The catalogue's targets may live in any of them.
     */
    private final List<RequestMappingHandlerMapping> handlerMappings;

    NavigationCatalogueValidator(List<RequestMappingHandlerMapping> handlerMappings) {
        this.handlerMappings = handlerMappings;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<String> missing = missingEndpoints(NavigationCatalogue.DEFAULT_ITEMS, registeredGetPaths());
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Navigation catalogue names endpoints that do not exist: "
                    + String.join("; ", missing)
                    + ". Remove the item, or ship the controller in the same ticket (W-12.3 §2).");
        }
        log.info(
                "Navigation catalogue: all {} leaf items have a GET mapping",
                countLeaves(NavigationCatalogue.DEFAULT_ITEMS));
    }

    /**
     * The leaves of {@code items} whose {@code targetEndpoint} is not in {@code registeredGetPaths},
     * each described for the error message. Empty when the catalogue is consistent.
     */
    static List<String> missingEndpoints(
            List<NavigationCatalogue.ItemDefinition> items, Set<String> registeredGetPaths) {
        List<String> missing = new ArrayList<>();
        for (NavigationCatalogue.ItemDefinition def : items) {
            if (def.hasChildren()) {
                missing.addAll(missingEndpoints(def.children(), registeredGetPaths));
            } else if (!registeredGetPaths.contains(def.targetEndpoint())) {
                missing.add("item '" + def.key() + "' targets '" + def.targetEndpoint() + "'");
            }
        }
        return missing;
    }

    /** Every path pattern that some handler method serves for {@code GET} (or for any method). */
    private Set<String> registeredGetPaths() {
        Set<String> paths = new HashSet<>();
        for (RequestMappingHandlerMapping mapping : handlerMappings) {
            for (RequestMappingInfo info : mapping.getHandlerMethods().keySet()) {
                Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
                if (methods.isEmpty() || methods.contains(RequestMethod.GET)) {
                    paths.addAll(info.getPatternValues());
                }
            }
        }
        return paths;
    }

    private static int countLeaves(List<NavigationCatalogue.ItemDefinition> items) {
        int count = 0;
        for (NavigationCatalogue.ItemDefinition def : items) {
            count += def.hasChildren() ? countLeaves(def.children()) : 1;
        }
        return count;
    }
}
