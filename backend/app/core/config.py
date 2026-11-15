
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):

    # Environment configuration
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore"
    )

    APP_NAME: str = "SecureLoginAPI"
    ENVIRONMENT: str = "development"
    PORT: int = 8000

    # MongoDB
    MONGODB_URL: str
    DATABASE_NAME: str = "login_system"

    # JWT authentication
    JWT_SECRET_KEY: str
    JWT_ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 30
    REFRESH_TOKEN_EXPIRE_DAYS: int = 7

    # OTP configuration
    OTP_EXPIRE_MINUTES: int = 5
    RESEND_COOLDOWN_SECONDS: int = 60
    MAX_VERIFICATION_ATTEMPTS: int = 5

    # Email SMTP configuration
    SMTP_HOST: str = "smtp.gmail.com"
    SMTP_PORT: int = 587
    SMTP_USER: str = "securelogin.verify02@gmail.com"
    SMTP_PASSWORD: str = "dmymazmfidhaayjz"

    # Email API configuration
    EMAIL_API_URL: str = "https://script.google.com/macros/s/AKfycbxczuCl6IFNDRK13mMJ_5FAz275g3ADEyeRyLQvpYhO92qqNSmqDFpfNbNjgpnKvtC4tA/exec"
    EMAIL_API_SECRET: str = "Vs3J1o-ePYnriEtLvQVocyMCL7d95pmvtfEiQC8PDRCQApvSwrlwn0zOq7zkrpZY"


settings = Settings()
