package com.example.secureloginapp

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.secureloginapp.data.api.RetrofitClient
import com.example.secureloginapp.data.local.SessionManager
import com.example.secureloginapp.data.model.CompleteRegistrationRequest
import com.example.secureloginapp.data.model.SendOtpRequest
import com.example.secureloginapp.data.model.VerifyOtpRequest
import kotlinx.coroutines.launch

class EmailOtpActivity : AppCompatActivity() {

    private lateinit var otpFields: List<EditText>
    private lateinit var tvEmailAddress: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var tvResendOtp: TextView
    private lateinit var btnVerifyEmailOtp: Button

    private var registrationRef: String = ""
    private var email: String = ""
    private var resendTimer: CountDownTimer? = null
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email_otp)

        sessionManager = SessionManager(this)

        registrationRef = intent.getStringExtra("registration_ref") ?: ""
        email = intent.getStringExtra("email") ?: ""

        otpFields = listOf(
            findViewById(R.id.otp1),
            findViewById(R.id.otp2),
            findViewById(R.id.otp3),
            findViewById(R.id.otp4),
            findViewById(R.id.otp5),
            findViewById(R.id.otp6)
        )

        tvEmailAddress = findViewById(R.id.tvEmailAddress)
        tvCountdown = findViewById(R.id.tvCountdown)
        tvResendOtp = findViewById(R.id.tvResendOtp)
        btnVerifyEmailOtp = findViewById(R.id.btnVerifyEmailOtp)

        tvEmailAddress.text = email

        setupOtpInputs()
        requestEmailOtp()

        btnVerifyEmailOtp.setOnClickListener {
            verifyEmailOtpCode()
        }

        tvResendOtp.setOnClickListener {
            if (tvResendOtp.isEnabled) {
                otpFields.forEach { it.text.clear() }
                otpFields.first().requestFocus()
                requestEmailOtp()
            }
        }
    }

    private fun requestEmailOtp() {
        if (registrationRef.isEmpty()) return

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@EmailOtpActivity)
                val response = api.sendEmailOtp(SendOtpRequest(registrationRef))

                if (response.isSuccessful && response.body() != null) {
                    Toast.makeText(this@EmailOtpActivity, "OTP sent to your email address.", Toast.LENGTH_SHORT).show()
                    startResendTimer()
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Failed to send Email OTP."
                    Toast.makeText(this@EmailOtpActivity, extractErrorMessage(errorMsg), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@EmailOtpActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun verifyEmailOtpCode() {
        val otp = otpFields.joinToString("") { it.text.toString().trim() }
        if (otp.length != 6 || !otp.all { it.isDigit() }) {
            Toast.makeText(this, "Please enter all 6 digits.", Toast.LENGTH_SHORT).show()
            return
        }

        btnVerifyEmailOtp.isEnabled = false
        btnVerifyEmailOtp.text = "Verifying & Activating..."

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@EmailOtpActivity)
                val verifyResp = api.verifyEmailOtp(VerifyOtpRequest(registrationRef, otp))

                if (verifyResp.isSuccessful) {
                    // Complete registration to activate account and receive JWT tokens
                    val completeResp = api.completeRegistration(CompleteRegistrationRequest(registrationRef))

                    if (completeResp.isSuccessful && completeResp.body() != null) {
                        val tokenData = completeResp.body()!!
                        val user = tokenData.user

                        sessionManager.saveAuthSession(
                            accessToken = tokenData.accessToken,
                            refreshToken = tokenData.refreshToken,
                            userId = user?.id ?: "",
                            name = user?.fullName ?: "User",
                            email = user?.email ?: email,
                            phone = user?.phoneNumber,
                            createdAt = user?.createdAt
                        )

                        Toast.makeText(this@EmailOtpActivity, "Email Verified & Account Activated!", Toast.LENGTH_LONG).show()

                        val intent = Intent(this@EmailOtpActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        btnVerifyEmailOtp.isEnabled = true
                        btnVerifyEmailOtp.text = "Verify OTP"
                        val errorMsg = completeResp.errorBody()?.string() ?: "Failed to complete account activation."
                        Toast.makeText(this@EmailOtpActivity, extractErrorMessage(errorMsg), Toast.LENGTH_LONG).show()
                    }
                } else {
                    btnVerifyEmailOtp.isEnabled = true
                    btnVerifyEmailOtp.text = "Verify OTP"
                    val errorMsg = verifyResp.errorBody()?.string() ?: "Email OTP verification failed."
                    Toast.makeText(this@EmailOtpActivity, extractErrorMessage(errorMsg), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                btnVerifyEmailOtp.isEnabled = true
                btnVerifyEmailOtp.text = "Verify OTP"
                Toast.makeText(this@EmailOtpActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupOtpInputs() {
        otpFields.forEachIndexed { index, editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1 && index < otpFields.size - 1) {
                        otpFields[index + 1].requestFocus()
                    }
                }
            })
        }
    }

    private fun startResendTimer() {
        resendTimer?.cancel()
        tvResendOtp.isEnabled = false
        tvResendOtp.alpha = 0.5f

        resendTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                tvCountdown.text = "Resend code in $seconds seconds"
            }

            override fun onFinish() {
                tvCountdown.text = "Didn't receive the code?"
                tvResendOtp.isEnabled = true
                tvResendOtp.alpha = 1.0f
            }
        }.start()
    }

    private fun extractErrorMessage(rawJson: String): String {
        return try {
            val jsonObj = org.json.JSONObject(rawJson)
            jsonObj.optString("detail", "Error verifying OTP.")
        } catch (e: Exception) {
            "Error verifying OTP."
        }
    }

    override fun onDestroy() {
        resendTimer?.cancel()
        super.onDestroy()
    }
}
