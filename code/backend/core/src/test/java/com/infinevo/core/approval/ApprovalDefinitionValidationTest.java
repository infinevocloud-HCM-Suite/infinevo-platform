package com.infinevo.core.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating approval definition constraints (W-15.1, spec section 7).
 *
 * <ul>
 *   <li>a definition with no steps refused
 *   <li>an unknown approver kind refused
 *   <li>a named-employee step without an employee refused
 *   <li>a step saved without escalate_after_days reads back as 3
 *   <li>a PROJECT_MANAGER step with no ApproverResolver bean registered is refused at validation
 * </ul>
 */
class ApprovalDefinitionValidationTest {

    @Test
    @DisplayName("a definition with no steps refused")
    void definitionWithNoStepsRefused() {
        ApprovalDefinitionRepository repository = mock(ApprovalDefinitionRepository.class);
        ApprovalDefinitionService service = new ApprovalDefinitionService(repository, Collections.emptyList());

        ApprovalDefinitionRequest requestEmpty = new ApprovalDefinitionRequest(
                StepOrdering.SEQUENTIAL, CommentScope.PER_STEP, LocalDate.now(), Collections.emptyList());

        assertThatThrownBy(() -> service.validate(requestEmpty))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one step");

        ApprovalDefinitionRequest requestNull =
                new ApprovalDefinitionRequest(StepOrdering.SEQUENTIAL, CommentScope.PER_STEP, LocalDate.now(), null);

        assertThatThrownBy(() -> service.validate(requestNull))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one step");
    }

    @Test
    @DisplayName("an unknown approver kind refused")
    void unknownApproverKindRefused() {
        ApprovalDefinitionRepository repository = mock(ApprovalDefinitionRepository.class);
        ApprovalDefinitionService service = new ApprovalDefinitionService(repository, Collections.emptyList());

        ApprovalStepDefinition badStep = new ApprovalStepDefinition(null, null, 3, false);
        ApprovalDefinitionRequest request = new ApprovalDefinitionRequest(
                StepOrdering.SEQUENTIAL, CommentScope.PER_STEP, LocalDate.now(), List.of(badStep));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid approver kind");
    }

    @Test
    @DisplayName("a named-employee step without an employee refused")
    void namedEmployeeStepWithoutEmployeeRefused() {
        ApprovalDefinitionRepository repository = mock(ApprovalDefinitionRepository.class);
        ApprovalDefinitionService service = new ApprovalDefinitionService(repository, Collections.emptyList());

        ApprovalStepDefinition stepNull = new ApprovalStepDefinition(ApproverKind.NAMED_EMPLOYEE, null, 3, false);
        ApprovalDefinitionRequest requestNull = new ApprovalDefinitionRequest(
                StepOrdering.SEQUENTIAL, CommentScope.PER_STEP, LocalDate.now(), List.of(stepNull));

        assertThatThrownBy(() -> service.validate(requestNull))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NAMED_EMPLOYEE requires a valid employee assignee");

        ApprovalStepDefinition stepBlank = new ApprovalStepDefinition(ApproverKind.NAMED_EMPLOYEE, "   ", 3, false);
        ApprovalDefinitionRequest requestBlank = new ApprovalDefinitionRequest(
                StepOrdering.SEQUENTIAL, CommentScope.PER_STEP, LocalDate.now(), List.of(stepBlank));

        assertThatThrownBy(() -> service.validate(requestBlank))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NAMED_EMPLOYEE requires a valid employee assignee");
    }

    @Test
    @DisplayName("a step saved without escalate_after_days reads back as 3")
    void stepWithoutEscalateAfterDaysDefaultsToThree() {
        ApprovalStepDefinition step = new ApprovalStepDefinition(ApproverKind.REPORTING_MANAGER);
        step.setEscalateAfterDays(null);

        // Getter returns 3 when set to null
        assertThat(step.getEscalateAfterDays()).isEqualTo(3);

        // When processed through service save, normalizeSteps also ensures 3 is written
        ApprovalDefinitionRepository repository = mock(ApprovalDefinitionRepository.class);
        when(repository.findByTenantIdAndFlowTypeAndEffectiveFrom(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalDefinitionService service = new ApprovalDefinitionService(repository, Collections.emptyList());
        ApprovalDefinitionRequest request = new ApprovalDefinitionRequest(
                StepOrdering.SEQUENTIAL, CommentScope.PER_STEP, LocalDate.of(2026, 1, 1), List.of(step));

        ApprovalDefinitionResponse response =
                service.saveDefinition(UUID.randomUUID(), ApprovalFlowType.LEAVE, request);
        assertThat(response.steps()).hasSize(1);
        assertThat(response.steps().get(0).getEscalateAfterDays()).isEqualTo(3);
    }

    @Test
    @DisplayName("a PROJECT_MANAGER step with no ApproverResolver bean registered is refused at validation")
    void projectManagerWithoutResolverRefused() {
        ApprovalDefinitionRepository repository = mock(ApprovalDefinitionRepository.class);
        ApprovalDefinitionService service = new ApprovalDefinitionService(repository, Collections.emptyList());

        ApprovalStepDefinition pmStep = new ApprovalStepDefinition(ApproverKind.PROJECT_MANAGER);
        ApprovalDefinitionRequest request = new ApprovalDefinitionRequest(
                StepOrdering.SEQUENTIAL, CommentScope.PER_STEP, LocalDate.now(), List.of(pmStep));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PROJECT_MANAGER requires an ApproverResolver bean registered");
    }

    @Test
    @DisplayName("a PROJECT_MANAGER step with an ApproverResolver bean registered passes validation")
    void projectManagerWithResolverPasses() {
        ApprovalDefinitionRepository repository = mock(ApprovalDefinitionRepository.class);
        ApproverResolver pmResolver = mock(ApproverResolver.class);
        when(pmResolver.kind()).thenReturn(ApproverKind.PROJECT_MANAGER);

        ApprovalDefinitionService service = new ApprovalDefinitionService(repository, List.of(pmResolver));

        ApprovalStepDefinition pmStep = new ApprovalStepDefinition(ApproverKind.PROJECT_MANAGER, null, 3, true);
        ApprovalDefinitionRequest request = new ApprovalDefinitionRequest(
                StepOrdering.SEQUENTIAL, CommentScope.PER_STEP, LocalDate.now(), List.of(pmStep));

        service.validate(request); // Passes without exception
    }

    @Test
    @DisplayName("a PROJECT_MANAGER step that is not per_item is refused, even with a resolver registered (W-42.2)")
    void projectManagerMustBePerItem() {
        ApprovalDefinitionRepository repository = mock(ApprovalDefinitionRepository.class);
        ApproverResolver pmResolver = mock(ApproverResolver.class);
        when(pmResolver.kind()).thenReturn(ApproverKind.PROJECT_MANAGER);
        ApprovalDefinitionService service = new ApprovalDefinitionService(repository, List.of(pmResolver));

        ApprovalStepDefinition approver = new ApprovalStepDefinition(ApproverKind.REPORTING_MANAGER);
        ApprovalStepDefinition pmStep = new ApprovalStepDefinition(ApproverKind.PROJECT_MANAGER, null, 3, false);
        ApprovalDefinitionRequest request = new ApprovalDefinitionRequest(
                StepOrdering.SEQUENTIAL, CommentScope.PER_STEP, LocalDate.now(), List.of(approver, pmStep));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Step at index 1: PROJECT_MANAGER must be per_item");
    }
}
