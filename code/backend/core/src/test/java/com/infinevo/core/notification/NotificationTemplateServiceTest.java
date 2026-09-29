package com.infinevo.core.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-20.1 spec section 13 decision 1 — a tenant edits its own wording: a new version from today, only
 * the event's placeholders, and an email always has a subject.
 */
class NotificationTemplateServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);

    private NotificationTemplateRepository templates;
    private NotificationTemplateService service;

    @BeforeEach
    void setUp() {
        templates = mock(NotificationTemplateRepository.class);
        when(templates.saveAndFlush(any(NotificationTemplate.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new NotificationTemplateServiceImpl(
                templates, Clock.fixed(Instant.parse("2026-09-25T10:00:00Z"), ZoneOffset.UTC));
        TenantContext.set(TENANT);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("An edit is a new version from today")
    void editIsANewVersion() {
        NotificationTemplateResponse saved = service.put(
                NotificationEvent.LEAVE_APPROVED,
                new NotificationTemplateRequest(Channel.EMAIL, "Approved", "<p>${employee_name}</p>", null));

        assertThat(saved.effectiveFrom()).isEqualTo(TODAY);
        assertThat(saved.active()).isTrue();
        assertThat(saved.placeholders()).contains("employee_name", "leave_type");
    }

    @Test
    @DisplayName("A second edit on the same day replaces that day's version rather than adding another")
    void sameDayEditReplaces() {
        NotificationTemplate todays =
                new NotificationTemplate(TENANT, NotificationEvent.LEAVE_APPROVED, Channel.IN_APP, TODAY, "t");
        todays.apply(null, "old", true, "t");
        when(templates.findByTenantIdAndEventAndChannelAndLocaleAndEffectiveFrom(
                        TENANT, NotificationEvent.LEAVE_APPROVED, Channel.IN_APP, "en", TODAY))
                .thenReturn(Optional.of(todays));

        service.put(
                NotificationEvent.LEAVE_APPROVED,
                new NotificationTemplateRequest(Channel.IN_APP, "ignored", "Approved: ${leave_type}", true));

        assertThat(todays.getBody()).isEqualTo("Approved: ${leave_type}");
        assertThat(todays.getSubject()).as("an in-app template has no subject").isNull();
    }

    @Test
    @DisplayName("A placeholder the event does not supply is refused, naming it")
    void unknownPlaceholderIsRefused() {
        assertThatThrownBy(() -> service.put(
                        NotificationEvent.LEAVE_APPROVED,
                        new NotificationTemplateRequest(Channel.IN_APP, null, "Salary ${salary}", true)))
                .isInstanceOfSatisfying(
                        NotificationService.ValidationException.class,
                        e -> assertThat(e.fieldErrors().get("body")).contains("salary"));
        verify(templates, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("A malformed ${, an email with no subject, and a missing channel are refused")
    void shapeErrors() {
        assertThatThrownBy(() -> service.put(
                        NotificationEvent.LEAVE_APPROVED,
                        new NotificationTemplateRequest(Channel.IN_APP, null, "Hi ${Employee_Name}", true)))
                .isInstanceOf(NotificationService.ValidationException.class);
        assertThatThrownBy(() -> service.put(
                        NotificationEvent.LEAVE_APPROVED,
                        new NotificationTemplateRequest(Channel.EMAIL, " ", "<p>ok</p>", true)))
                .isInstanceOfSatisfying(NotificationService.ValidationException.class, e -> assertThat(e.fieldErrors())
                        .containsKey("subject"));
        assertThatThrownBy(() -> service.put(
                        NotificationEvent.LEAVE_APPROVED, new NotificationTemplateRequest(null, null, "ok", true)))
                .isInstanceOfSatisfying(NotificationService.ValidationException.class, e -> assertThat(e.fieldErrors())
                        .containsKey("channel"));
    }
}
