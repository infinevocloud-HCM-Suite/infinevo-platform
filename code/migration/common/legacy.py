"""Reading legacy values as the extractors write them: MySQL BIT/TINYINT booleans, dates
and datetimes as ISO text, enums as free text."""
from __future__ import annotations

from datetime import date, datetime, timezone

_TRUE = {"1", "true", "t", "yes", "y", "active", "enabled", "\x01"}
_FALSE = {"0", "false", "f", "no", "n", "inactive", "disabled", "deleted", "\x00", ""}


def truthy(value, default: bool | None = None) -> bool | None:
    """True/False from bool, int, BIT bytes or text; default when absent or unrecognised."""
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return value != 0
    if isinstance(value, (bytes, bytearray)):
        return any(value)
    s = str(value).strip().lower()
    if s in _TRUE:
        return True
    if s in _FALSE:
        return False
    return default


def to_date(value) -> date | None:
    if value in (None, ""):
        return None
    if isinstance(value, datetime):
        return value.date()
    if isinstance(value, date):
        return value
    s = str(value).strip()
    for fmt in ("%Y-%m-%d", "%d-%m-%Y", "%d/%m/%Y", "%Y/%m/%d"):
        try:
            return datetime.strptime(s[:10], fmt).date()
        except ValueError:
            pass
    try:
        return datetime.fromisoformat(s.replace("Z", "+00:00")).date()
    except ValueError:
        return None


def to_timestamp(value) -> datetime | None:
    """Legacy DATETIME columns carry no zone. Both apps wrote LocalDateTime from JVMs
    running on IST, so a naive value is read as +05:30. A value with a zone keeps it."""
    if value in (None, ""):
        return None
    if isinstance(value, datetime):
        dt = value
    else:
        s = str(value).strip().replace("Z", "+00:00")
        try:
            dt = datetime.fromisoformat(s)
        except ValueError:
            d = to_date(s)
            if d is None:
                return None
            dt = datetime(d.year, d.month, d.day)
    if dt.tzinfo is None:
        from datetime import timedelta
        dt = dt.replace(tzinfo=timezone(timedelta(hours=5, minutes=30)))
    return dt


_WEEKDAYS = {"mon": 1, "tue": 2, "wed": 3, "thu": 4, "fri": 5, "sat": 6, "sun": 7}


def iso_weekday(value) -> int | None:
    """'mon', 'MON', 'Monday', 1..7 (ISO) -> 1..7, Monday = 1 (java.time.DayOfWeek)."""
    if value is None:
        return None
    if isinstance(value, int):
        return value if 1 <= value <= 7 else None
    s = str(value).strip().lower()
    if s.isdigit():
        n = int(s)
        return n if 1 <= n <= 7 else None
    return _WEEKDAYS.get(s[:3])


def text(value, max_len: int | None = None) -> str | None:
    if value is None:
        return None
    s = str(value).strip()
    if not s:
        return None
    return s[:max_len] if max_len else s
