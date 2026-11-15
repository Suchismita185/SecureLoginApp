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

class MobileOtpActivity : AppCompatActivity() {

    private lateinit var otpFields: List<EditText>
    private lateinit var tvPhoneNumber: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var tvResendOtp: TextView
    private lateinit var btnVerifyMobileOtp: Button

    private var registrationRef: String = ""
    private var email: String = ""
    private var phoneNumber: String = ""
    private var resendTimer: CountDownTimer? = null
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mobile_otp)

        sessionManager = SessionManager(this)

        registrationRef = intent.getStringExtra("registration_ref") ?: ""
        email = intent.getStringExtra("email") ?: ""
        phoneNumber = intent.getStringExtra("phone_number") ?: ""

        otpFields = listOf(
            findViewById(R.id.otp1),
            findViewById(R.id.otp2),
            findViewById(R.id.otp3),
            findViewById(R.id.otp4),
            findViewById(R.id.otp5),
            findViewById(R.id.otp6)
        )

        tvPhoneNumber = findViewById(R.id.tvPhoneNumber)
        tvCountdown = findViewById(R.id.tvCountdown)
        tvResendOtp = findViewById(R.id.tvResendOtp)
        btnVerifyMobileOtp = findViewById(R.id.btnVerifyMobileOtp)

        tvPhoneNumber.text = phoneNumber

        setupOtpInputs()
        requestMobileOtp()

        btnVerifyMobileOtp.setOnClickListener {
            verifyMobileOtpCode()
        }

        tvResendOtp.setOnClickListener {
            if (tvResendOtp.isEnabled) {
                otpFields.forEach { it.text.clear() }
                otpFields.first().requestFocus()
                requestMobileOtp()
            }
        }
    }

    private fun requestMobileOtp() {
        if (registrationRef.isEmpty()) return

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@MobileOtpActivity)
                val response = api.sendPhoneOtp(SendOtpRequest(registrationRef))

                if (response.isSuccessful && response.body() != null) {
                    Toast.makeText(this@MobileOtpActivity, "SMS OTP sent to mobile number.", Toast.LENGTH_SHORT).show()
                    startResendTimer()
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Failed to send Mobile OTP."
                    Toast.makeText(this@MobileOtpActivity, extractErrorMessage(errorMsg), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MobileOtpActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun verifyMobileOtpCode() {
        val otp = otpFields.joinToString("") { it.text.toString().trim() }
        if (otp.length != 6 || !otp.all { it.isDigit() }) {
            Toast.makeText(this, "Please enter all 6 digits.", Toast.LENGTH_SHORT).show()
            return
        }

        btnVerifyMobileOtp.isEnabled = false
        btnVerifyMobileOtp.text = "Verifying & Activating..."

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@MobileOtpActivity)
                val verifyResp = api.verifyPhoneOtp(VerifyOtpRequest(registrationRef, otp))

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
                            phone = user?.phoneNumber ?: phoneNumber,
                            createdAt = user?.createdAt
                        )

                        Toast.makeText(this@MobileOtpActivity, "Registration Complete & Account Activated!", Toast.LENGTH_LONG).show()

                        val intent = Intent(this@MobileOtpActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        btnVerifyMobileOtp.isEnabled = true
                        btnVerifyMobileOtp.text = "Verify Mobile & Finish"
                        val errorMsg = completeResp.errorBody()?.string() ?: "Failed to complete account activation."
                        Toast.makeText(this@MobileOtpActivity, extractErrorMessage(errorMsg), Toast.LENGTH_LONG).show()
                    }
                } else {
                    btnVerifyMobileOtp.isEnabled = true
                    btnVerifyMobileOtp.text = "Verify Mobile & Finish"
                    val errorMsg = verifyResp.errorBody()?.string() ?: "Mobile OTP verification failed."
                    Toast.makeText(this@MobileOtpActivity, extractErrorMessage(errorMsg), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                btnVerifyMobileOtp.isEnabled = true
                btnVerifyMobileOtp.text = "Verify Mobile & Finish"
                Toast.makeText(this@MobileOtpActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
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
                tvCountdown.text = "Resend SMS in $seconds seconds"
            }

            override fun onFinish() {
                tvCountdown.text = "Didn't receive the SMS?"
                tvResendOtp.isEnabled = true
                tvResendOtp.alpha = 1.0f
            }
        }.start()
    }

    private fun extractErrorMessage(rawJson: String): String {
        return try {
            val jsonObj = org.json.JSONObject(rawJson)
            jsonObj.optString("detail", "Error verifying Mobile OTP.")
        } catch (e: Exception) {
            "Error verifying Mobile OTP."
        }
    }

    override fun onDestroy() {
        resendTimer?.cancel()
        super.onDestroy()
    }
}
