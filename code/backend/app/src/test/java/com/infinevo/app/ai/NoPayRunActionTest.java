package com.infinevo.app.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.ai.AiToolProvider;
import com.infinevo.core.ai.action.AiActionExecutor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * W-114.21 spec, Tests ({@code NoPayRunActionIT}, built as a classpath scan): the founder's rule of 2026-11-23 - the AI
 * helper never creates, locks, computes, approves, pays or cancels a pay run. Here, with every module on the
 * classpath, no {@link AiActionExecutor} has an action code starting {@code payroll.} but the settings steps listed below, and the only tool names
 * starting {@code payroll.payrun_} are the two read tools. A scan, not a Spring context: the executors and tools of all
 * modules are visible only in {@code app}, and their action codes and names are their {@code static final} constants.
 */
class NoPayRunActionTest {

    /**
     * The only payroll actions allowed: settings steps that touch no pay run and no money (W-114.28, founder
     * 2026-11-27). Anything else, a pay run step above all, fails here.
     */
    private static final Set<String> SETTINGS_ACTIONS = Set.of("payroll.bank_file_template.create");

    /** The executor classes of those steps: a payroll executor not named here fails, whatever its action code. */
    private static final Set<String> SETTINGS_EXECUTORS =
            Set.of("com.infinevo.payroll.ai.BankFileTemplateCreateExecutor");

    private static List<Class<?>> concrete(Class<?> type) throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(type));
        List<Class<?>> found = new ArrayList<>();
        for (BeanDefinition d : scanner.findCandidateComponents("com.infinevo")) {
            Class<?> c = Class.forName(d.getBeanClassName());
            if (!Modifier.isAbstract(c.getModifiers()) && !c.isInterface()) {
                found.add(c);
            }
        }
        return found;
    }

    private static Set<String> stringConstants(Class<?> c) throws IllegalAccessException {
        Set<String> values = new TreeSet<>();
        for (Field f : c.getDeclaredFields()) {
            int m = f.getModifiers();
            if (Modifier.isStatic(m) && Modifier.isFinal(m) && f.getType() == String.class) {
                f.setAccessible(true);
                Object v = f.get(null);
                if (v != null) {
                    values.add((String) v);
                }
            }
        }
        return values;
    }

    @Test
    @DisplayName("no payroll action but the settings steps; every executor names its code in ACTION_CODE")
    void noPayrollExecutor() throws Exception {
        List<Class<?>> executors = concrete(AiActionExecutor.class);
        assertThat(executors).isNotEmpty();
        for (Class<?> c : executors) {
            Field code = c.getDeclaredField("ACTION_CODE");
            code.setAccessible(true);
            String value = (String) code.get(null);
            if (value.startsWith("payroll.")) {
                assertThat(value).as(c.getName()).isIn(SETTINGS_ACTIONS);
            }
        }
    }

    @Test
    @DisplayName(
            "whatever its code, no executor lives in payroll but the settings steps, and none takes a pay run service")
    void noPayRunExecutorByClass() throws Exception {
        for (Class<?> c : concrete(AiActionExecutor.class)) {
            if (c.getName().startsWith("com.infinevo.payroll.")) {
                assertThat(c.getName()).as("a payroll executor").isIn(SETTINGS_EXECUTORS);
            }
            for (Constructor<?> k : c.getDeclaredConstructors()) {
                for (Class<?> param : k.getParameterTypes()) {
                    assertThat(param.getName())
                            .as(c.getName() + " takes " + param.getName())
                            .doesNotStartWith("com.infinevo.payroll.payrun.PayRun")
                            .doesNotStartWith("com.infinevo.payroll.payrun.payment");
                }
            }
        }
    }

    @Test
    @DisplayName("the only payroll.payrun_ tools are the two read tools")
    void onlyTheTwoReadTools() throws Exception {
        Set<String> payRunTools = new TreeSet<>();
        for (Class<?> c : concrete(AiToolProvider.class)) {
            for (String v : stringConstants(c)) {
                if (v.startsWith("payroll.payrun_")) {
                    payRunTools.add(v);
                }
            }
        }
        assertThat(payRunTools).containsExactly("payroll.payrun_next_step", "payroll.payrun_summary");
    }
}
