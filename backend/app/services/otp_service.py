import secrets
import logging
from datetime import datetime, timedelta, timezone
from app.core.config import settings
from app.core.security import hash_otp, verify_otp_hash
from app.core.time_utils import get_ist_time

logger = logging.getLogger(__name__)

def generate_6digit_otp() -> str:
    """Generate a cryptographically secure 6-digit numeric OTP."""
    return f"{secrets.randbelow(900000) + 100000}"

def get_otp_expiry(minutes: int = settings.OTP_EXPIRE_MINUTES) -> datetime:
    """Calculate OTP expiration timestamp in IST."""
    return get_ist_time() + timedelta(minutes=minutes)

def check_resend_cooldown(last_sent_at: datetime, cooldown_seconds: int = settings.RESEND_COOLDOWN_SECONDS) -> tuple[bool, int]:
    """
    Check if user is within resend cooldown period.
    Returns (is_allowed, seconds_remaining).
    """
    if not last_sent_at:
        return True, 0

    now = get_ist_time()
    # Ensure timezone awareness match
    if last_sent_at.tzinfo is None:
        last_sent_at = last_sent_at.replace(tzinfo=timezone.utc)

    elapsed = (now - last_sent_at).total_seconds()
    if elapsed < cooldown_seconds:
        remaining = int(cooldown_seconds - elapsed)
        return False, remaining
    return True, 0
