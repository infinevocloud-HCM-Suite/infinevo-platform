package com.itsdev.payroll.service.leaveAndAttendance.attendance;


import com.itsdev.payroll.dto.leaveAndAttendance.attendance.AttendancePreferenceDTO;

import java.util.List;
public interface AttendancePreferenceService {

    AttendancePreferenceDTO createAttendancePreference(String orgId, AttendancePreferenceDTO dto);

    AttendancePreferenceDTO updateAttendancePreference(String orgId, Long attendancePreferenceId, AttendancePreferenceDTO dto);

    void deleteAttendancePreference(String orgId, Long attendancePreferenceId);

    AttendancePreferenceDTO getAttendancePreference(String orgId, Long attendancePreferenceId);

    List<AttendancePreferenceDTO> getAllAttendancePreferences(String orgId);
}
