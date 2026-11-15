
from datetime import datetime, timezone
from zoneinfo import ZoneInfo

# Indian Standard Time
IST = ZoneInfo("Asia/Kolkata")

# UTC timezone
UTC = timezone.utc


def get_ist_time() -> datetime:
    """Return the current timezone-aware IST datetime."""
    return datetime.now(IST)


def convert_to_ist(dt: datetime) -> datetime:
    """Convert an existing datetime to IST."""

    if dt.tzinfo is None:
        # Treat naive database timestamps as UTC
        dt = dt.replace(tzinfo=UTC)

    return dt.astimezone(IST)


def get_ist_isoformat(dt: datetime = None) -> str:
    """Return an ISO 8601 timestamp with IST offset."""

    if dt is None:
        dt = get_ist_time()
    else:
        dt = convert_to_ist(dt)

    return dt.isoformat()
