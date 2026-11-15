
import asyncio
import json
import logging
import re
import urllib.request

from app.core.config import settings

logger = logging.getLogger(__name__)


def _send_email_via_api(
    to_email: str,
    subject: str,
    body: str
) -> bool:
    """Send an email through the Google Apps Script HTTPS API."""

    payload = {
        "secret": settings.EMAIL_API_SECRET,
        "to": to_email,
        "subject": subject,
        "body": body
    }

    request_data = json.dumps(payload).encode("utf-8")

    request = urllib.request.Request(
        settings.EMAIL_API_URL,
        data=request_data,
        headers={
            "Content-Type": "application/json"
        },
        method="POST"
    )

    # Google Apps Script may redirect its response (HTTP 302).
    with urllib.request.urlopen(request, timeout=40) as response:
        result = json.loads(response.read().decode("utf-8"))

    if result.get("success") is True:
        return True

    logger.error("Email API reported failure for recipient %s", to_email)
    return False


async def send_email_otp(
    to_email: str,
    otp: str,
    name: str = "User"
) -> bool:
    """
    Send a 6-digit verification OTP through Google Apps Script.
    """

    if not re.fullmatch(r"\d{6}", otp):
        logger.error("Invalid OTP format.")
        return False

    if not settings.EMAIL_API_URL or not settings.EMAIL_API_SECRET:
        logger.error("Email API configuration is missing.")
        return False

    subject = "Verify your email address"

    body = (
        f"Hello {name},\n\n"
        f"Your Secure Login App verification code is: {otp}\n"
        f"This code is valid for {settings.OTP_EXPIRE_MINUTES} minutes.\n\n"
        "Do not share this code with anyone.\n\n"
        "If you did not request this code, please ignore this email."
    )

    try:
        success = await asyncio.to_thread(
            _send_email_via_api,
            to_email,
            subject,
            body
        )

        if success:
            logger.info("Email OTP successfully sent to %s", to_email)
            return True

        return False

    except Exception:
        logger.exception("Failed to send email OTP to %s", to_email)
        return False
