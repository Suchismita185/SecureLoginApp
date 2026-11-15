package com.example.secureloginapp.data.api

import com.example.secureloginapp.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("auth/register")
    suspend fun registerUser(
        @Body request: RegisterRequest
    ): Response<RegisterResponse>

    @POST("auth/send-email-otp")
    suspend fun sendEmailOtp(
        @Body request: SendOtpRequest
    ): Response<GenericResponse>

    @POST("auth/verify-email-otp")
    suspend fun verifyEmailOtp(
        @Body request: VerifyOtpRequest
    ): Response<GenericResponse>

    @POST("auth/send-phone-otp")
    suspend fun sendPhoneOtp(
        @Body request: SendOtpRequest
    ): Response<GenericResponse>

    @POST("auth/verify-phone-otp")
    suspend fun verifyPhoneOtp(
        @Body request: VerifyOtpRequest
    ): Response<GenericResponse>

    @POST("auth/complete-registration")
    suspend fun completeRegistration(
        @Body request: CompleteRegistrationRequest
    ): Response<TokenResponse>

    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<TokenResponse>

    @POST("auth/refresh")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest
    ): Response<TokenResponse>

    @POST("auth/logout")
    suspend fun logout(
        @Body request: RefreshTokenRequest
    ): Response<GenericResponse>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(
        @Body request: ForgotPasswordRequest
    ): Response<GenericResponse>

    @POST("auth/verify-reset-otp")
    suspend fun verifyResetOtp(
        @Body request: VerifyResetOtpRequest
    ): Response<ResetOtpResponse>

    @POST("auth/reset-password")
    suspend fun resetPassword(
        @Body request: ResetPasswordRequest
    ): Response<GenericResponse>

    @GET("auth/me")
    suspend fun getCurrentUser(): Response<UserDto>
}
