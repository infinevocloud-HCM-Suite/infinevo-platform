package com.infinevo.hrms.performance;

import java.util.List;
import java.util.UUID;

/**
 * Service contract for managing employee goals and OKR key results (W-106.2).
 */
public interface EmployeeGoalService {

    EmployeeGoalResponse createGoal(CreateGoalRequest request);

    EmployeeGoalResponse updateProgress(UUID goalId, UpdateGoalProgressRequest request);

    List<EmployeeGoalResponse> getMyGoals();

    List<EmployeeGoalResponse> getTeamGoals();

    List<EmployeeGoalResponse> getGoalsForEmployee(UUID employeeId);

    List<EmployeeGoalResponse> getGoalsForCycle(UUID cycleId);
}
