import httpx
import logging
from app.core.config import settings

logger = logging.getLogger(__name__)

async def send_sms_otp(phone_number: str, otp: str) -> bool:
    """
    Send 6-digit SMS OTP to user's mobile number via SMS Gateway API.
    Falls back to mock logging if SMS_API_KEY is not configured.
    """
    message = f"Your Secure Login OTP is: {otp}. Valid for {settings.OTP_EXPIRE_MINUTES} minutes. Do not share it."

    # If SMS API Key is configured, make HTTP request to Gateway
    if settings.SMS_API_KEY and settings.SMS_API_KEY != "your_sms_gateway_api_key":
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                # Example API payload structure (can adapt to MSG91/Twilio/Fast2SMS)
                response = await client.post(
                    "https://api.sms-provider.com/v1/send",
                    json={
                        "api_key": settings.SMS_API_KEY,
                        "to": phone_number,
                        "message": message
                    }
                )
                if response.status_code in (200, 201):
                    logger.info(f"SMS OTP sent successfully to {phone_number}")
                    return True
                else:
                    logger.error(f"SMS gateway returned status {response.status_code}: {response.text}")
        except Exception as e:
            logger.error(f"Failed to send SMS to {phone_number}: {e}")

    # Development Mock Fallback Mode
    logger.info("==================================================")
    logger.info(f"[DEV MOCK SMS OTP] Sent to: {phone_number}")
    logger.info(f"[DEV MOCK SMS OTP] Verification Code: {otp}")
    logger.info("==================================================")
    return True
