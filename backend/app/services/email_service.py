
import asyncio
import logging
import re
import requests

from app.core.config import settings

logger = logging.getLogger(__name__)


def _send_email_via_api(
    to_email: str,
    subject: str,
    body: str
) -> bool:
    """Send an email through Google Apps Script HTTPS API."""

    payload = {
        "secret": settings.EMAIL_API_SECRET,
        "to": to_email,
        "subject": subject,
        "body": body
    }

    response = requests.post(
        settings.EMAIL_API_URL,
        json=payload,
        timeout=40,
        allow_redirects=False
    )

    # Google Apps Script commonly responds with HTTP 302.
    if response.status_code in (301, 302, 303, 307, 308):
        redirect_url = response.headers.get("Location")

        if not redirect_url:
            logger.error("Email API redirect did not include a Location header.")
            return False

        # Fetch the redirected response using GET.
        response = requests.get(
            redirect_url,
            timeout=40
        )

    response.raise_for_status()

    try:
        result = response.json()
    except ValueError:
        logger.error("Email API returned an invalid JSON response.")
        return False

    if result.get("success") is True:
        return True

    logger.error(
        "Email API reported failure for recipient %s: %s",
        to_email,
        result.get("message", "Unknown error")
    )
    return False


async def send_email_otp(
    to_email: str,
    otp: str,
    name: str = "User"
) -> bool:
    """Send a six-digit verification OTP."""

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

    except requests.RequestException:
        logger.exception("Email API request failed for %s", to_email)
        return False

    except Exception:
        logger.exception("Unexpected email sending error for %s", to_email)
        return False
