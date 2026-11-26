package com.infinevo.app.help;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.ai.help.HelpArticle;
import com.infinevo.core.ai.help.HelpLibrary;
import com.infinevo.core.ai.help.SetupStepScreens;
import com.infinevo.core.navigation.NavigationCatalogue;
import com.infinevo.core.portal.PanelDescriptor;
import com.infinevo.core.portal.PortalPanelProvider;
import com.infinevo.hrms.navigation.HrmsNavigation;
import com.infinevo.payroll.navigation.PayrollNavigation;
import com.infinevo.shared.authz.RequiresAction;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.web.bind.annotation.RestController;

/**
 * W-114.11: every shipped help article points at a screen the full menu has, and needs only an action something in the
 * product declares. W-114.12: every role guide line names a key of the full menu, and every leaf of the full menu has a
 * plain name in {@code help/screens.json}. Lives in {@code app}, the one module that sees core, hrms and payroll together; the menu is built
 * the way {@code SeededRoleHomePathTest} builds it.
 *
 * <p>An action is declared when a menu item requires it (or lists it as an alternative), a portal panel requires it,
 * or an endpoint's {@code @RequiresAction} names it. The last is needed for {@code payroll.salary.manage}: the Salary
 * tab is not a menu item or a panel, but its endpoints and buttons check that action.
 */
class HelpArticlesMatchTheMenuTest {

    private static final HelpLibrary LIBRARY = new HelpLibrary();
    private static final List<HelpArticle> ARTICLES = LIBRARY.all();
    private static final Set<String> PATHS = new HashSet<>();
    private static final Set<String> KEYS = new HashSet<>();
    private static final Set<String> LEAF_KEYS = new HashSet<>();
    private static final Map<String, String> ITEM_PATHS = new HashMap<>();
    private static final Set<String> ACTIONS = new HashSet<>();

    @BeforeAll
    static void readTheMenu() throws Exception {
        List<NavigationCatalogue.ItemDefinition> menu =
                NavigationCatalogue.withContributed(List.of(new HrmsNavigation(), new PayrollNavigation()));
        collect(menu);

        ClassPathScanningCandidateComponentProvider panels = new ClassPathScanningCandidateComponentProvider(false);
        panels.addIncludeFilter(new AssignableTypeFilter(PortalPanelProvider.class));
        for (BeanDefinition bean : panels.findCandidateComponents("com.infinevo")) {
            PanelDescriptor descriptor =
                    newPanel(Class.forName(bean.getBeanClassName())).panel(UUID.randomUUID());
            // A panel that reads data (W-102 onboarding) answers nothing for an unknown employee; it declares no
            // action an article could name.
            if (descriptor != null && descriptor.requiredAction() != null) {
                ACTIONS.add(descriptor.requiredAction());
            }
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

    /** A panel built with its first constructor, every argument a Mockito mock. */
    private static PortalPanelProvider newPanel(Class<?> type) throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        Object[] args = Arrays.stream(constructor.getParameterTypes())
                .map(Mockito::mock)
                .toArray();
        return (PortalPanelProvider) constructor.newInstance(args);
    }

    private static void collect(List<NavigationCatalogue.ItemDefinition> items) {
        for (NavigationCatalogue.ItemDefinition item : items) {
            if (item.path() != null) {
                PATHS.add(item.path());
            }
            KEYS.add(item.key());
            if (item.path() != null) {
                ITEM_PATHS.put(item.key(), item.path());
            }
            if (item.children() == null || item.children().isEmpty()) {
                LEAF_KEYS.add(item.key());
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

    @Test
    @DisplayName("W-114.12: every screen a role guide line names is a leaf of the full menu; every action is declared")
    void everyGuideScreenIsInTheMenu() {
        assertThat(LIBRARY.guides()).hasSize(6);
        LIBRARY.guides().values().forEach(g -> g.sections()
                .forEach(s -> s.lines().forEach(l -> {
                    // A group key would pass startup but never match the caller's leaves, so the line would never show.
                    assertThat(LEAF_KEYS)
                            .as("screen of a line in the %s guide", g.role())
                            .contains(l.screen());
                    if (l.action() != null) {
                        assertThat(ACTIONS)
                                .as("action of a line in the %s guide", g.role())
                                .contains(l.action());
                    }
                })));
    }

    @Test
    @DisplayName("W-114.16: every setup step's menu key is a menu leaf, and its path is that item's or below it")
    void everySetupScreenIsInTheMenu() {
        assertThat(SetupStepScreens.all()).isNotEmpty();
        SetupStepScreens.all().forEach((code, screen) -> {
            assertThat(LEAF_KEYS).as("menu key of step %s", code).contains(screen.menuKey());
            String itemPath = ITEM_PATHS.get(screen.menuKey());
            assertThat(screen.path().equals(itemPath) || screen.path().startsWith(itemPath + "/"))
                    .as("step %s path %s under %s", code, screen.path(), itemPath)
                    .isTrue();
        });
    }

    @Test
    @DisplayName("W-114.13: every parent in sub-routes.json is a path in the full menu")
    void everySubRouteParentIsInTheMenu() {
        assertThat(LIBRARY.subRoutes()).isNotEmpty();
        LIBRARY.subRoutes()
                .forEach((path, parent) ->
                        assertThat(PATHS).as("parent of sub-route %s", path).contains(parent));
    }

    @Test
    @DisplayName("W-114.12: every leaf of the full menu has a name in screens.json, and every name is a menu key")
    void everyLeafHasAName() {
        assertThat(LEAF_KEYS).contains("core.leave.types", "payroll.runs", "hrms.attendance");
        assertThat(LIBRARY.screenNames().keySet())
                .as("menu leaves without a name in help/screens.json")
                .containsAll(LEAF_KEYS);
        assertThat(KEYS)
                .as("names in help/screens.json for keys the menu does not have")
                .containsAll(LIBRARY.screenNames().keySet());
    }
}
