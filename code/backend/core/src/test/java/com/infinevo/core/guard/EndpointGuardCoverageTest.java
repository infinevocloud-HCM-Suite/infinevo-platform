package com.infinevo.core.guard;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.authz.RequiresAction;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every endpoint in {@code core} and {@code shared} carries {@link RequiresAction} (W-11.2).
 *
 * <p>Enforcement is opt-in: an endpoint without the annotation is open to every member of the
 * tenant, and nothing else would notice. This test is the notice. A new controller either names
 * its action or is added to {@link #EXEMPT} with the reason it needs none.
 *
 * <p>Scans the production classes only — test apps declare throwaway controllers of their own.
 * Controllers in {@code app}, {@code hrms} and {@code payroll} are not on this classpath and are not
 * covered here.
 */
class EndpointGuardCoverageTest {

    /** Controllers that deliberately need no action, and why. */
    private static final Map<String, String> EXEMPT = Map.of(
            "com.infinevo.shared.identity.MeController",
            "returns only the caller's own identity; every authenticated member may see themselves",
            "com.infinevo.core.navigation.NavigationController",
            "every signed-in user has a menu; items inside are filtered by module and action (W-12.3)");

    @Test
    @DisplayName("every mapped method in core and shared names the action it requires")
    void everyEndpointIsGuarded() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> controllers = new ArrayList<>();
        List<String> unguarded = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.infinevo")) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            if (!isProductionClass(type) || EXEMPT.containsKey(type.getName())) {
                continue;
            }
            controllers.add(type.getSimpleName());
            boolean classGuarded = AnnotatedElementUtils.hasAnnotation(type, RequiresAction.class);
            for (Method method : type.getMethods()) {
                if (AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)
                        && !classGuarded
                        && !AnnotatedElementUtils.hasAnnotation(method, RequiresAction.class)) {
                    unguarded.add(type.getSimpleName() + "." + method.getName());
                }
            }
        }

        // Guards against a scan that silently finds nothing and so passes vacuously.
        assertThat(controllers).contains("RoleController", "EmployeeController", "AuditController");
        assertThat(unguarded)
                .as("endpoints with no @RequiresAction — add one, or exempt the controller with a reason")
                .isEmpty();
    }

    private static boolean isProductionClass(Class<?> type) {
        String location =
                type.getProtectionDomain().getCodeSource().getLocation().toString();
        return !location.contains("test-classes") && !location.endsWith("-tests.jar");
    }
}
