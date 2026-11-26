package com.infinevo.app.help;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.ai.help.HelpArticle;
import com.infinevo.core.ai.help.HelpLibrary;
import com.infinevo.core.navigation.NavigationCatalogue;
import com.infinevo.core.portal.PortalPanelProvider;
import com.infinevo.hrms.navigation.HrmsNavigation;
import com.infinevo.payroll.navigation.PayrollNavigation;
import com.infinevo.shared.authz.RequiresAction;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.web.bind.annotation.RestController;

/**
 * W-114.11: every shipped help article points at a screen the full menu has, and needs only an action something in the
 * product declares. Lives in {@code app}, the one module that sees core, hrms and payroll together; the menu is built
 * the way {@code SeededRoleHomePathTest} builds it.
 *
 * <p>An action is declared when a menu item requires it (or lists it as an alternative), a portal panel requires it,
 * or an endpoint's {@code @RequiresAction} names it. The last is needed for {@code payroll.salary.manage}: the Salary
 * tab is not a menu item or a panel, but its endpoints and buttons check that action.
 */
class HelpArticlesMatchTheMenuTest {

    private static final List<HelpArticle> ARTICLES = new HelpLibrary().all();
    private static final Set<String> PATHS = new HashSet<>();
    private static final Set<String> ACTIONS = new HashSet<>();

    @BeforeAll
    static void readTheMenu() throws Exception {
        List<NavigationCatalogue.ItemDefinition> menu =
                NavigationCatalogue.withContributed(List.of(new HrmsNavigation(), new PayrollNavigation()));
        collect(menu);

        ClassPathScanningCandidateComponentProvider panels = new ClassPathScanningCandidateComponentProvider(false);
        panels.addIncludeFilter(new AssignableTypeFilter(PortalPanelProvider.class));
        for (BeanDefinition bean : panels.findCandidateComponents("com.infinevo")) {
            PortalPanelProvider panel = (PortalPanelProvider) Class.forName(bean.getBeanClassName())
                    .getDeclaredConstructor()
                    .newInstance();
            ACTIONS.add(panel.panel(UUID.randomUUID()).requiredAction());
        }

        ClassPathScanningCandidateComponentProvider controllers =
                new ClassPathScanningCandidateComponentProvider(false);
        controllers.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (BeanDefinition bean : controllers.findCandidateComponents("com.infinevo")) {
            Class<?> type = Class.forName(bean.getBeanClassName());
            addRequired(type.getAnnotation(RequiresAction.class));
            for (Method method : type.getDeclaredMethods()) {
                addRequired(method.getAnnotation(RequiresAction.class));
            }
        }
    }

    private static void collect(List<NavigationCatalogue.ItemDefinition> items) {
        for (NavigationCatalogue.ItemDefinition item : items) {
            if (item.path() != null) {
                PATHS.add(item.path());
            }
            if (item.requiredAction() != null) {
                ACTIONS.add(item.requiredAction());
            }
            ACTIONS.addAll(item.anyOf());
            if (item.children() != null) {
                collect(item.children());
            }
        }
    }

    private static void addRequired(RequiresAction required) {
        if (required != null) {
            ACTIONS.add(required.value());
            ACTIONS.addAll(Arrays.asList(required.anyOf()));
        }
    }

    @Test
    @DisplayName("the menu was read: it has the employee list and the portal")
    void menuWasRead() {
        assertThat(PATHS).contains("/employees", "/me", "/payroll/runs", "/hrms/attendance");
        assertThat(ARTICLES).hasSize(30);
    }

    @Test
    @DisplayName("every article's screen is a path in the full menu")
    void everyScreenIsInTheMenu() {
        assertThat(ARTICLES).allSatisfy(a -> assertThat(PATHS)
                .as("screen of help article %s", a.id())
                .contains(a.screen()));
    }

    @Test
    @DisplayName("every article's action is declared by a menu item, a portal panel or an endpoint")
    void everyActionIsDeclared() {
        assertThat(ARTICLES.stream().filter(a -> a.action() != null))
                .isNotEmpty()
                .allSatisfy(a -> assertThat(ACTIONS)
                        .as("action of help article %s", a.id())
                        .contains(a.action()));
    }
}
