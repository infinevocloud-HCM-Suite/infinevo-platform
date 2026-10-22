"""HRMS in-app notifications (3m). Column names are the legacy MySQL ones (Spring default
naming of Notification.java and TimesheetsNotification.java). All founder tenant.

notifications: 1 and 3 load; 2 is for the admin inbox, 4 an unknown employee code, 5 an
unknown type. timesheet_notifications: 1, 2 and 3 load (legacy ids overlap the other table).
"""


def _n(nid, recipient, ntype, read=False, **kw):
    row = {"id": nid, "message": f"Timesheet TS-{nid} needs attention", "recipient_id": recipient,
           "sender_id": "system", "type": ntype, "is_read": read, "created_at": "2024-06-03T09:00:00",
           "timesheet_id": f"TS-{nid}"}
    row.update(kw)
    return row


def rows():
    return {
        "stg_hrms_notifications.json": [
            _n(1, "E001", "SUBMISSION", read=True),
            _n(2, "admin", "SUBMISSION"),
            _n(3, "E002", "REMINDER"),
            _n(4, "E999", "REMINDER"),
            _n(5, "E003", "BIRTHDAY"),
        ],
        "stg_hrms_timesheet_notifications.json": [
            _n(1, "E004", "APPROVAL", read=True, project_id=1, rejection_reason=None),
            _n(2, "E005", "REJECTION", project_id=1, rejection_reason="Hours missing"),
            _n(3, "E006", "ALERT", project_id=2, rejection_reason=None, created_at="2024-06-04T18:45:00"),
        ],
    }
