
import smtplib
import ssl
import asyncio
import logging
import re
from html import escape
from email.message import EmailMessage

from app.core.config import settings

logger = logging.getLogger(__name__)


async def send_email_otp(
    to_email: str,
    otp: str,
    name: str = "User"
) -> bool:
    """
    Send a 6-digit verification OTP using SMTP.
    Returns True only when SMTP successfully sends the email.
    """

    # Validate OTP before including it in the email.
    if not re.fullmatch(r"\d{6}", otp):
        logger.error("Invalid OTP format.")
        return False

    # Do not silently report success when SMTP is not configured.
    if (
        not settings.SMTP_USER
        or not settings.SMTP_PASSWORD
        or settings.SMTP_USER == "your_email@gmail.com"
    ):
        logger.error("SMTP credentials are not configured.")
        return False

    safe_name = escape(name)
    safe_otp = escape(otp)

    subject = "Verify your email address"

    text_content = (
        f"Hello {name},\n\n"
        f"Your Secure Login App verification code is: {otp}\n"
        f"This code is valid for {settings.OTP_EXPIRE_MINUTES} minutes.\n\n"
        "Do not share this code with anyone."
    )

    html_content = f"""
    <!DOCTYPE html>
    <html>
    <body style="font-family: Arial, sans-serif; background: #f4f6f9; padding: 20px;">
        <div style="max-width: 500px; margin: auto; background: white;
                    border-radius: 10px; padding: 30px;">
            <h2>Verify Your Email Address</h2>
            <p>Hello {safe_name},</p>
            <p>Your verification code is:</p>

            <div style="font-size: 32px; font-weight: bold;
                        letter-spacing: 8px; color: #3157D5;
                        background: #EBF0FF; padding: 15px;
                        text-align: center; border-radius: 8px;">
                {safe_otp}
            </div>

            <p>This code is valid for
               <strong>{settings.OTP_EXPIRE_MINUTES} minutes</strong>.
            </p>

            <p>Do not share this code with anyone.</p>
            <p>If you did not request this code, ignore this email.</p>
        </div>
    </body>
    </html>
    """

    try:
        msg = EmailMessage()
        msg["Subject"] = subject
        msg["From"] = settings.SMTP_USER
        msg["To"] = to_email

        msg.set_content(text_content)
        msg.add_alternative(html_content, subtype="html")

        def _send():
            context = ssl.create_default_context()

            with smtplib.SMTP(
                settings.SMTP_HOST,
                settings.SMTP_PORT,
                timeout=20
            ) as server:
                server.ehlo()
                server.starttls(context=context)
                server.ehlo()

                server.login(
                    settings.SMTP_USER,
                    settings.SMTP_PASSWORD
                )

                server.send_message(msg)

        await asyncio.to_thread(_send)

        logger.info("Email OTP successfully sent to %s", to_email)
        return True

    except Exception:
        logger.exception("Failed to send email OTP to %s", to_email)
        return False
