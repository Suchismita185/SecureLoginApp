package com.example.secureloginapp.data.model

import com.google.gson.annotations.SerializedName

// Registration & Email OTP Models
data class RegisterRequest(
    @SerializedName("full_name") val fullName: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("confirm_password") val confirmPassword: String
)

data class RegisterResponse(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String,
    @SerializedName("registration_ref") val registrationRef: String,
    @SerializedName("email") val email: String
)

data class SendOtpRequest(
    @SerializedName("registration_ref") val registrationRef: String
)

data class VerifyOtpRequest(
    @SerializedName("registration_ref") val registrationRef: String,
    @SerializedName("otp") val otp: String
)

data class GenericResponse(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String,
    @SerializedName("resend_cooldown_seconds") val resendCooldownSeconds: Int? = null
)

data class CompleteRegistrationRequest(
    @SerializedName("registration_ref") val registrationRef: String
)

// Login & Session Models
data class LoginRequest(
    @SerializedName("identifier") val identifier: String,
    @SerializedName("password") val password: String
)

data class UserDto(
    @SerializedName("id") val id: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("email") val email: String,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("email_verified") val emailVerified: Boolean,
    @SerializedName("account_status") val accountStatus: String,
    @SerializedName("created_at") val createdAt: String? = null
)

data class TokenResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Int,
    @SerializedName("user") val user: UserDto? = null
)

data class RefreshTokenRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

// Password Recovery Models
data class ForgotPasswordRequest(
    @SerializedName("identifier") val identifier: String
)

data class VerifyResetOtpRequest(
    @SerializedName("identifier") val identifier: String,
    @SerializedName("otp") val otp: String
)

data class ResetOtpResponse(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String,
    @SerializedName("reset_token") val resetToken: String
)

data class ResetPasswordRequest(
    @SerializedName("reset_token") val resetToken: String,
    @SerializedName("new_password") val newPassword: String,
    @SerializedName("confirm_new_password") val confirmNewPassword: String
)
