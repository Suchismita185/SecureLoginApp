
from fastapi import APIRouter, HTTPException, status, Depends
import uuid
from datetime import datetime, timedelta, timezone
from app.core.time_utils import get_ist_time
from bson import ObjectId

from app.db.mongodb import db
from app.core.config import settings
from app.core.security import (
    hash_password,
    verify_password,
    hash_otp,
    verify_otp_hash,
    hash_token,
    create_access_token,
    create_refresh_token,
    decode_token
)
from app.schemas.auth import (
    RegisterRequest,
    SendEmailOtpRequest,
    VerifyEmailOtpRequest,
    CompleteRegistrationRequest,
    LoginRequest,
    RefreshTokenRequest,
    TokenResponse,
    ForgotPasswordRequest,
    VerifyResetOtpRequest,
    ResetPasswordRequest,
    UserResponse
)
from app.services.otp_service import (
    generate_6digit_otp,
    get_otp_expiry,
    check_resend_cooldown
)
from app.services.email_service import send_email_otp
from app.api.deps import get_current_user

router = APIRouter(
    prefix="/auth",
    tags=["Authentication & Registration"]
)


# =====================================================
# 1. REGISTER USER
# =====================================================

@router.post("/register", status_code=status.HTTP_201_CREATED)
async def register_user(req: RegisterRequest):

    existing_user = await db.db.users.find_one({"email": str(req.email)})
    if existing_user:
        raise HTTPException(
            status_code=400,
            detail="Email address is already registered."
        )

    registration_ref = f"reg_{uuid.uuid4().hex}"
    hashed_pwd = hash_password(req.password)
    now = get_ist_time()

    pending_doc = {
        "registration_ref": registration_ref,
        "full_name": req.full_name,
        "email": str(req.email),
        "password_hash": hashed_pwd,
        "created_at": now
    }

    # Remove previous unfinished registrations for this email.
    await db.db.pending_registrations.delete_many({"email": str(req.email)})

    await db.db.pending_registrations.insert_one(pending_doc)

    # Email verification only.
    otp_doc = {
        "registration_ref": registration_ref,
        "email": str(req.email),
        "email_otp_hash": None,
        "email_expires_at": None,
        "email_verified": False,
        "email_attempts": 0,
        "email_last_sent_at": None,
        "created_at": now
    }

    await db.db.otp_verifications.insert_one(otp_doc)

    return {
        "status": "success",
        "message": "Registration initiated. Please verify your email address.",
        "registration_ref": registration_ref,
        "email": str(req.email)
    }


# =====================================================
# 2. SEND EMAIL OTP
# =====================================================

@router.post("/send-email-otp")
async def send_email_verification_otp(req: SendEmailOtpRequest):

    otp_record = await db.db.otp_verifications.find_one({
        "registration_ref": req.registration_ref
    })

    if not otp_record:
        raise HTTPException(
            status_code=404,
            detail="Invalid or expired registration reference."
        )

    if otp_record.get("email_verified"):
        return {
            "status": "success",
            "message": "Email is already verified."
        }

    last_sent = otp_record.get("email_last_sent_at")
    allowed, seconds_rem = check_resend_cooldown(last_sent)

    if not allowed:
        raise HTTPException(
            status_code=429,
            detail=f"Please wait {seconds_rem} seconds before requesting another OTP."
        )

    pending = await db.db.pending_registrations.find_one({
        "registration_ref": req.registration_ref
    })

    if not pending:
        raise HTTPException(
            status_code=404,
            detail="Pending registration not found or expired."
        )

    otp = generate_6digit_otp()
    otp_h = hash_otp(otp, req.registration_ref)
    exp = get_otp_expiry()
    now = get_ist_time()

    # Store OTP hash before attempting delivery.
    await db.db.otp_verifications.update_one(
        {"registration_ref": req.registration_ref},
        {
            "$set": {
                "email_otp_hash": otp_h,
                "email_expires_at": exp,
                "email_attempts": 0,
                "email_last_sent_at": now
            }
        }
    )

    email_sent = await send_email_otp(
        otp_record["email"],
        otp,
        name=pending.get("full_name", "User")
    )

    if not email_sent:
        # Invalidate the undelivered OTP and allow another attempt.
        await db.db.otp_verifications.update_one(
            {"registration_ref": req.registration_ref},
            {
                "$set": {
                    "email_otp_hash": None,
                    "email_expires_at": None,
                    "email_last_sent_at": None
                }
            }
        )

        raise HTTPException(
            status_code=503,
            detail="Unable to send verification email. Please try again later."
        )

    return {
        "status": "success",
        "message": "Verification OTP sent successfully to your email.",
        "resend_cooldown_seconds": settings.RESEND_COOLDOWN_SECONDS
    }


# =====================================================
# 3. VERIFY EMAIL OTP
# =====================================================

@router.post("/verify-email-otp")
async def verify_email_otp(req: VerifyEmailOtpRequest):

    otp_record = await db.db.otp_verifications.find_one({
        "registration_ref": req.registration_ref
    })

    if not otp_record:
        raise HTTPException(
            status_code=404,
            detail="Invalid or expired registration reference."
        )

    if otp_record.get("email_verified"):
        return {
            "status": "success",
            "message": "Email is already verified."
        }

    if (
        not otp_record.get("email_otp_hash")
        or not otp_record.get("email_expires_at")
    ):
        raise HTTPException(
            status_code=400,
            detail="No active email OTP. Please request an OTP first."
        )

    exp = otp_record["email_expires_at"]

    if exp.tzinfo is None:
        exp = exp.replace(tzinfo=timezone.utc)

    if get_ist_time() > exp:
        raise HTTPException(
            status_code=400,
            detail="Email OTP has expired. Please request a new OTP."
        )

    attempts = otp_record.get("email_attempts", 0) + 1

    if attempts > settings.MAX_VERIFICATION_ATTEMPTS:
        raise HTTPException(
            status_code=429,
            detail="Maximum verification attempts exceeded. Request a new OTP."
        )

    await db.db.otp_verifications.update_one(
        {"registration_ref": req.registration_ref},
        {"$set": {"email_attempts": attempts}}
    )

    if not verify_otp_hash(
        req.otp,
        otp_record["email_otp_hash"],
        req.registration_ref
    ):
        raise HTTPException(
            status_code=400,
            detail=f"Invalid OTP. {settings.MAX_VERIFICATION_ATTEMPTS - attempts} attempts remaining."
        )

    await db.db.otp_verifications.update_one(
        {"registration_ref": req.registration_ref},
        {
            "$set": {
                "email_verified": True,
                "email_otp_hash": None
            }
        }
    )

    return {
        "status": "success",
        "message": "Email verified successfully!"
    }


# =====================================================
# 4. COMPLETE REGISTRATION
# =====================================================

@router.post("/complete-registration")
async def complete_registration(req: CompleteRegistrationRequest):

    otp_record = await db.db.otp_verifications.find_one({
        "registration_ref": req.registration_ref
    })

    if not otp_record:
        raise HTTPException(
            status_code=404,
            detail="Registration reference not found."
        )

    if not otp_record.get("email_verified"):
        raise HTTPException(
            status_code=400,
            detail="Email verification is incomplete."
        )

    pending = await db.db.pending_registrations.find_one({
        "registration_ref": req.registration_ref
    })

    if not pending:
        raise HTTPException(
            status_code=404,
            detail="Pending registration data expired or invalid."
        )

    # Check for duplicate email again before account creation.
    existing_user = await db.db.users.find_one({"email": pending["email"]})
    if existing_user:
        raise HTTPException(
            status_code=409,
            detail="Email address is already registered."
        )

    now = get_ist_time()

    user_doc = {
        "full_name": pending["full_name"],
        "email": pending["email"],
        "password_hash": pending["password_hash"],
        "email_verified": True,
        "account_status": "active",
        "created_at": now,
        "updated_at": now
    }

    result = await db.db.users.insert_one(user_doc)
    user_id = str(result.inserted_id)

    # Remove temporary registration records.
    await db.db.pending_registrations.delete_one({
        "registration_ref": req.registration_ref
    })

    await db.db.otp_verifications.delete_one({
        "registration_ref": req.registration_ref
    })

    # Generate login tokens.
    access_token = create_access_token({"sub": user_id})
    refresh_token = create_refresh_token({"sub": user_id})

    rf_hash = hash_token(refresh_token)
    rf_exp = now + timedelta(
        days=settings.REFRESH_TOKEN_EXPIRE_DAYS
    )

    await db.db.refresh_sessions.insert_one({
        "user_id": user_id,
        "refresh_token_hash": rf_hash,
        "expires_at": rf_exp,
        "created_at": now,
        "revoked_at": None
    })

    return {
        "status": "success",
        "message": "Account activated successfully!",
        "access_token": access_token,
        "refresh_token": refresh_token,
        "token_type": "bearer",
        "expires_in": settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60,
        "user": {
            "id": user_id,
            "full_name": pending["full_name"],
            "email": pending["email"],
            "email_verified": True,
            "account_status": "active",
            "created_at": now.isoformat()
        }
    }


# =====================================================
# 5. LOGIN
# =====================================================

@router.post("/login", response_model=TokenResponse)
async def login(req: LoginRequest):

    identifier = req.identifier.strip()

    user = await db.db.users.find_one({
        "$or": [
            {"email": identifier},
        ]
    })

    if not user:
        raise HTTPException(
            status_code=401,
            detail="Invalid login credentials."
        )

    if not verify_password(req.password, user["password_hash"]):
        raise HTTPException(
            status_code=401,
            detail="Invalid login credentials."
        )

    if user.get("account_status") != "active":
        raise HTTPException(
            status_code=403,
            detail="Account is inactive or suspended."
        )

    user_id = str(user["_id"])

    access_token = create_access_token({"sub": user_id})
    refresh_token = create_refresh_token({"sub": user_id})

    rf_hash = hash_token(refresh_token)
    now = get_ist_time()

    rf_exp = now + timedelta(
        days=settings.REFRESH_TOKEN_EXPIRE_DAYS
    )

    await db.db.refresh_sessions.insert_one({
        "user_id": user_id,
        "refresh_token_hash": rf_hash,
        "expires_at": rf_exp,
        "created_at": now,
        "revoked_at": None
    })

    return TokenResponse(
        access_token=access_token,
        refresh_token=refresh_token,
        token_type="bearer",
        expires_in=settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60,
        user={
            "id": user_id,
            "full_name": user["full_name"],
            "email": user["email"],
            "email_verified": user.get("email_verified", False),
            "account_status": user.get("account_status", "active"),
            "created_at": user.get("created_at").isoformat() if hasattr(user.get("created_at"), "isoformat") else (str(user.get("created_at")) if user.get("created_at") else None)
        }
    )


# =====================================================
# 6. REFRESH ACCESS TOKEN
# =====================================================

@router.post("/refresh")
async def refresh_access_token(req: RefreshTokenRequest):

    payload = decode_token(req.refresh_token)

    if not payload or payload.get("type") != "refresh":
        raise HTTPException(
            status_code=401,
            detail="Invalid or expired refresh token."
        )

    user_id = payload.get("sub")
    rf_hash = hash_token(req.refresh_token)

    session = await db.db.refresh_sessions.find_one({
        "user_id": user_id,
        "refresh_token_hash": rf_hash,
        "revoked_at": None
    })

    if not session:
        raise HTTPException(
            status_code=401,
            detail="Refresh token revoked or session invalid."
        )

    now = get_ist_time()

    await db.db.refresh_sessions.update_one(
        {"_id": session["_id"]},
        {"$set": {"revoked_at": now}}
    )

    new_access_token = create_access_token({"sub": user_id})
    new_refresh_token = create_refresh_token({"sub": user_id})

    new_rf_hash = hash_token(new_refresh_token)
    rf_exp = now + timedelta(
        days=settings.REFRESH_TOKEN_EXPIRE_DAYS
    )

    await db.db.refresh_sessions.insert_one({
        "user_id": user_id,
        "refresh_token_hash": new_rf_hash,
        "expires_at": rf_exp,
        "created_at": now,
        "revoked_at": None
    })

    return {
        "status": "success",
        "access_token": new_access_token,
        "refresh_token": new_refresh_token,
        "token_type": "bearer",
        "expires_in": settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60
    }


# =====================================================
# 7. LOGOUT
# =====================================================

@router.post("/logout")
async def logout(req: RefreshTokenRequest):

    rf_hash = hash_token(req.refresh_token)
    now = get_ist_time()

    await db.db.refresh_sessions.update_many(
        {"refresh_token_hash": rf_hash},
        {"$set": {"revoked_at": now}}
    )

    return {
        "status": "success",
        "message": "Logged out successfully."
    }


# =====================================================
# 8. FORGOT PASSWORD - EMAIL OTP ONLY
# =====================================================

@router.post("/forgot-password")
async def forgot_password(req: ForgotPasswordRequest):

    user = await db.db.users.find_one({
        "email": str(req.email)
    })

    generic_msg = (
        "If an account exists with this email, "
        "a password reset OTP has been sent."
    )

    if not user:
        return {"status": "success", "message": generic_msg}

    otp_ref = f"reset_{user['_id']}"

    existing_otp = await db.db.otp_verifications.find_one({
        "registration_ref": otp_ref
    })

    if existing_otp:
        allowed, seconds_rem = check_resend_cooldown(
            existing_otp.get("email_last_sent_at")
        )

        if not allowed:
            raise HTTPException(
                status_code=429,
                detail=f"Please wait {seconds_rem} seconds before requesting another OTP."
            )

    otp = generate_6digit_otp()
    otp_h = hash_otp(otp, otp_ref)
    exp = get_otp_expiry()
    now = get_ist_time()

    await db.db.otp_verifications.update_one(
        {"registration_ref": otp_ref},
        {
            "$set": {
                "registration_ref": otp_ref,
                "user_id": str(user["_id"]),
                "email": user["email"],
                "email_otp_hash": otp_h,
                "email_expires_at": exp,
                "email_attempts": 0,
                "email_last_sent_at": now,
                "created_at": now
            }
        },
        upsert=True
    )

    email_sent = await send_email_otp(
        user["email"],
        otp,
        name=user.get("full_name", "User")
    )

    if not email_sent:
        await db.db.otp_verifications.update_one(
            {"registration_ref": otp_ref},
            {
                "$set": {
                    "email_otp_hash": None,
                    "email_expires_at": None,
                    "email_last_sent_at": None
                }
            }
        )

        raise HTTPException(
            status_code=503,
            detail="Unable to send password reset email. Please try again later."
        )

    return {"status": "success", "message": generic_msg}


# =====================================================
# 9. VERIFY PASSWORD RESET OTP
# =====================================================

@router.post("/verify-reset-otp")
async def verify_reset_otp(req: VerifyResetOtpRequest):

    user = await db.db.users.find_one({
        "email": str(req.email)
    })

    if not user:
        raise HTTPException(
            status_code=400,
            detail="Invalid OTP or recovery request expired."
        )

    otp_ref = f"reset_{user['_id']}"

    otp_record = await db.db.otp_verifications.find_one({
        "registration_ref": otp_ref
    })

    if not otp_record or not otp_record.get("email_otp_hash"):
        raise HTTPException(
            status_code=400,
            detail="Invalid OTP or recovery request expired."
        )

    exp = otp_record["email_expires_at"]

    if exp.tzinfo is None:
        exp = exp.replace(tzinfo=timezone.utc)

    if get_ist_time() > exp:
        raise HTTPException(
            status_code=400,
            detail="Reset OTP has expired. Request a new OTP."
        )

    attempts = otp_record.get("email_attempts", 0) + 1

    if attempts > settings.MAX_VERIFICATION_ATTEMPTS:
        raise HTTPException(
            status_code=429,
            detail="Maximum reset OTP attempts exceeded. Request a new OTP."
        )

    await db.db.otp_verifications.update_one(
        {"registration_ref": otp_ref},
        {"$set": {"email_attempts": attempts}}
    )

    if not verify_otp_hash(
        req.otp,
        otp_record["email_otp_hash"],
        otp_ref
    ):
        raise HTTPException(
            status_code=400,
            detail="Invalid OTP verification code."
        )

    reset_token = create_access_token(
        {
            "sub": str(user["_id"]),
            "purpose": "password_reset"
        },
        expires_delta=timedelta(minutes=15)
    )

    await db.db.otp_verifications.delete_one({
        "registration_ref": otp_ref
    })

    return {
        "status": "success",
        "message": "OTP verified. Proceed to reset your password.",
        "reset_token": reset_token
    }


# =====================================================
# 10. RESET PASSWORD
# =====================================================

@router.post("/reset-password")
async def reset_password(req: ResetPasswordRequest):

    payload = decode_token(req.reset_token)

    if not payload or payload.get("purpose") != "password_reset":
        raise HTTPException(
            status_code=400,
            detail="Invalid or expired password reset token."
        )

    user_id = payload.get("sub")

    try:
        object_id = ObjectId(user_id)
    except Exception:
        raise HTTPException(
            status_code=400,
            detail="Invalid password reset token."
        )

    hashed_pwd = hash_password(req.new_password)
    now = get_ist_time()

    result = await db.db.users.update_one(
        {"_id": object_id},
        {
            "$set": {
                "password_hash": hashed_pwd,
                "updated_at": now
            }
        }
    )

    if result.matched_count == 0:
        raise HTTPException(
            status_code=404,
            detail="User not found."
        )

    await db.db.refresh_sessions.update_many(
        {"user_id": user_id},
        {"$set": {"revoked_at": now}}
    )

    return {
        "status": "success",
        "message": "Password updated successfully. Please log in again."
    }


# =====================================================
# 11. GET CURRENT USER PROFILE
# =====================================================

@router.get("/me", response_model=UserResponse)
async def get_current_user_profile(
    current_user: dict = Depends(get_current_user)
):

    return UserResponse(
        id=str(current_user["_id"]),
        full_name=current_user["full_name"],
        email=current_user["email"],
        email_verified=current_user.get("email_verified", False),
        account_status=current_user.get("account_status", "active"),
        created_at=current_user.get("created_at")
    )
