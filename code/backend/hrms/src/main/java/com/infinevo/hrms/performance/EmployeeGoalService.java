package com.infinevo.hrms.performance;

import java.util.List;
import java.util.UUID;

/**
 * Service contract for managing employee goals and OKR key results (W-106.2).
 */
public interface EmployeeGoalService {

    EmployeeGoalResponse createGoal(CreateGoalRequest request);

    EmployeeGoalResponse editGoal(UUID goalId, UpdateGoalRequest request);

    EmployeeGoalResponse startGoal(UUID goalId);

    EmployeeGoalResponse cancelGoal(UUID goalId);

    EmployeeGoalResponse checkIn(UUID goalId, GoalCheckInRequest request);

    List<GoalCheckInResponse> getCheckIns(UUID goalId);

    List<EmployeeGoalResponse> getMyGoals();

    List<EmployeeGoalResponse> getTeamGoals();

    List<EmployeeGoalResponse> getGoalsForEmployee(UUID employeeId, UUID cycleId);

    default List<EmployeeGoalResponse> getGoalsForEmployee(UUID employeeId) {
        return getGoalsForEmployee(employeeId, null);
    }

    List<EmployeeGoalResponse> getGoalsForCycle(UUID cycleId);
}
