from pydantic import (
    BaseModel,
    EmailStr,
    Field,
    field_validator,
    model_validator
)
import re
from typing import Optional
from datetime import datetime
from app.core.time_utils import convert_to_ist

# -------------------------------
# Common User Fields
# -------------------------------

class UserBase(BaseModel):
    full_name: str = Field(..., min_length=2, max_length=100)
    email: EmailStr


# -------------------------------
# Registration
# -------------------------------

class RegisterRequest(BaseModel):
    full_name: str = Field(..., min_length=2, max_length=100)
    email: EmailStr
    password: str = Field(..., min_length=8, max_length=64)
    confirm_password: str = Field(..., min_length=8, max_length=64)

    @field_validator("password")
    @classmethod
    def validate_password_strength(cls, v: str) -> str:
        if not re.search(r"[A-Z]", v):
            raise ValueError("Password must contain an uppercase letter.")

        if not re.search(r"[a-z]", v):
            raise ValueError("Password must contain a lowercase letter.")

        if not re.search(r"\d", v):
            raise ValueError("Password must contain a digit.")

        if not re.search(r'[!@#$%^&*(),.?":{}|<>]', v):
            raise ValueError("Password must contain a special character.")

        return v

    @model_validator(mode="after")
    def validate_passwords_match(self):
        if self.password != self.confirm_password:
            raise ValueError("Passwords do not match.")
        return self


# -------------------------------
# Email OTP
# -------------------------------

class SendEmailOtpRequest(BaseModel):
    registration_ref: str = Field(..., min_length=1)


class VerifyEmailOtpRequest(BaseModel):
    registration_ref: str = Field(..., min_length=1)
    otp: str = Field(..., min_length=6, max_length=6)

    @field_validator("otp")
    @classmethod
    def validate_otp(cls, v: str) -> str:
        if not v.isdigit():
            raise ValueError("OTP must contain exactly 6 digits.")
        return v


# -------------------------------
# Complete Registration
# -------------------------------

class CompleteRegistrationRequest(BaseModel):
    registration_ref: str = Field(..., min_length=1)


# -------------------------------
# Login
# -------------------------------

class LoginRequest(BaseModel):
    identifier: str = Field(..., min_length=1)
    password: str = Field(..., min_length=1)


# -------------------------------
# JWT Tokens
# -------------------------------

class RefreshTokenRequest(BaseModel):
    refresh_token: str = Field(..., min_length=1)


class TokenResponse(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    expires_in: int
    user: dict


# -------------------------------
# Forgot Password (Email OTP only)
# -------------------------------

class ForgotPasswordRequest(BaseModel):
    email: EmailStr


class VerifyResetOtpRequest(BaseModel):
    email: EmailStr
    otp: str = Field(..., min_length=6, max_length=6)

    @field_validator("otp")
    @classmethod
    def validate_otp(cls, v: str) -> str:
        if not v.isdigit():
            raise ValueError("OTP must contain exactly 6 digits.")
        return v


class ResetPasswordRequest(BaseModel):
    reset_token: str = Field(..., min_length=1)
    new_password: str = Field(..., min_length=8, max_length=64)
    confirm_new_password: str = Field(..., min_length=8, max_length=64)

    @field_validator("new_password")
    @classmethod
    def validate_password_strength(cls, v: str) -> str:
        if not re.search(r"[A-Z]", v):
            raise ValueError("Password must contain an uppercase letter.")

        if not re.search(r"[a-z]", v):
            raise ValueError("Password must contain a lowercase letter.")

        if not re.search(r"\d", v):
            raise ValueError("Password must contain a digit.")

        if not re.search(r'[!@#$%^&*(),.?":{}|<>]', v):
            raise ValueError("Password must contain a special character.")

        return v

    @model_validator(mode="after")
    def validate_passwords_match(self):
        if self.new_password != self.confirm_new_password:
            raise ValueError("Passwords do not match.")
        return self


# -------------------------------
# User Profile Response
# -------------------------------

class UserResponse(BaseModel):
    id: str
    full_name: str
    email: str
    email_verified: bool
    account_status: str
    created_at: Optional[datetime] = None

    @field_validator("created_at")
    @classmethod
    def convert_created_at_to_ist(cls, value):
        if value is None:
            return value
        return convert_to_ist(value)
