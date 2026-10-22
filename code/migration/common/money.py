"""Money and day counts are Decimal end to end (W-68 Task 3, CONVENTIONS). Never float."""
from __future__ import annotations

from decimal import Decimal, InvalidOperation, ROUND_HALF_UP

TWO_PLACES = Decimal("0.01")
ONE_PLACE = Decimal("0.1")


def to_decimal(value, places: Decimal | None = TWO_PLACES) -> Decimal | None:
    """None, '' and unparseable text become None. Floats are read through str() so a
    binary float never decides a paisa."""
    if value is None or value == "":
        return None
    if isinstance(value, bool):
        return None
    try:
        d = value if isinstance(value, Decimal) else Decimal(str(value).strip().replace(",", ""))
    except (InvalidOperation, ValueError):
        return None
    if not d.is_finite():
        return None
    return d.quantize(places, rounding=ROUND_HALF_UP) if places is not None else d


def to_money(value) -> Decimal | None:
    return to_decimal(value, TWO_PLACES)


def to_days(value) -> Decimal | None:
    return to_decimal(value, ONE_PLACE)


def money_or_zero(value) -> Decimal:
    d = to_money(value)
    return d if d is not None else Decimal("0.00")
