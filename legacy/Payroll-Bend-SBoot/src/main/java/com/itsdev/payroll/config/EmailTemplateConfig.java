package com.itsdev.payroll.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

import java.util.HashMap;
import java.util.Map;

@Configuration
@Getter
@Slf4j
public class EmailTemplateConfig {

    @Value("${brevo.template.poi.reminder}")
    private Long poiReminderTemplateId;

    @Value("${brevo.template.itdeclaration.reminder}")
    private Long itDeclarationReminderTemplateId;

    @Value("${brevo.template.itdeclaration.lock}")
    private Long itDeclarationLockTemplateId;

    @Value("${brevo.template.poi.submission}")
    private Long poiSubmissionTemplateId;

    @Value("${brevo.template.itdeclaration.release}")
    private Long itDeclarationReleaseTemplateId;

    @Value("${brevo.template.salary.slip}")
    private Long salarySlipTemplateId;

    @Value("${brevo.template.employee.invitation}")
    private Long employeeInvitationTemplateId;

    @Value("${brevo.template.employee.credentials}")
    private Long employeeCredentialsTemplateId;

    @Value("${brevo.template.user.invitation}")
    private Long userInvitationTemplateId;

    @Value("${brevo.template.user.credentials}")
    private Long userCredentialsTemplateId;

    // Future templates - add @Value here as needed:
    // @Value("${brevo.template.forgot.password}")
    // private Long forgotPasswordTemplateId;

    // Template registry
    private final Map<String, Long> templateMap = new HashMap<>();

    @PostConstruct
    public void init() {
        // Register all available templates
        registerTemplate("POI_REMINDER", poiReminderTemplateId);
        registerTemplate("IT_DECLARATION_REMINDER", itDeclarationReminderTemplateId);
        registerTemplate("IT_DECLARATION_LOCK", itDeclarationLockTemplateId);
        registerTemplate("POI_SUBMISSION", poiSubmissionTemplateId);
        registerTemplate("IT_DECLARATION_RELEASE", itDeclarationReleaseTemplateId);
        registerTemplate("SALARY_SLIP", salarySlipTemplateId);
        registerTemplate("EMPLOYEE_INVITATION", employeeInvitationTemplateId);
        registerTemplate("EMPLOYEE_CREDENTIALS", employeeCredentialsTemplateId);
        registerTemplate("USER_INVITATION", userInvitationTemplateId);
        registerTemplate("USER_CREDENTIALS", userCredentialsTemplateId);

        // Future: Register more templates here
        // registerTemplate("FORGOT_PASSWORD", forgotPasswordTemplateId);
        // registerTemplate("WELCOME_EMPLOYEE", welcomeEmployeeTemplateId);

        log.info("Email templates registered: {}", templateMap.keySet());
    }

    private void registerTemplate(String key, Long templateId) {
        if (templateId != null && templateId > 0) {
            templateMap.put(key, templateId);
            log.debug("Registered template: {} -> {}", key, templateId);
        } else {
            log.warn("Skipping template registration for {}: invalid ID {}", key, templateId);
        }
    }

    /**
     * Get template ID by key
     * 
     * @param templateKey Template key (e.g., "POI_REMINDER")
     * @return Template ID
     * @throws IllegalArgumentException if template not found
     */
    public Long getTemplateId(String templateKey) {
        Long templateId = templateMap.get(templateKey);
        if (templateId == null) {
            String available = String.join(", ", templateMap.keySet());
            throw new IllegalArgumentException(
                    String.format("Template '%s' not found. Available: %s",
                            templateKey, available));
        }
        return templateId;
    }

    /**
     * Check if template exists
     */
    public boolean hasTemplate(String templateKey) {
        return templateMap.containsKey(templateKey);
    }
}