package com.example.secureloginapp

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.secureloginapp.data.api.RetrofitClient
import com.example.secureloginapp.data.model.ForgotPasswordRequest
import com.example.secureloginapp.data.model.VerifyResetOtpRequest
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var etResetIdentifier: TextInputEditText
    private lateinit var btnSendResetOtp: Button
    private lateinit var layoutOtpSection: LinearLayout
    private lateinit var otpFields: List<EditText>
    private lateinit var btnVerifyResetOtp: Button

    private var identifier: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        etResetIdentifier = findViewById(R.id.etResetIdentifier)
        btnSendResetOtp = findViewById(R.id.btnSendResetOtp)
        layoutOtpSection = findViewById(R.id.layoutOtpSection)
        btnVerifyResetOtp = findViewById(R.id.btnVerifyResetOtp)

        otpFields = listOf(
            findViewById(R.id.otp1),
            findViewById(R.id.otp2),
            findViewById(R.id.otp3),
            findViewById(R.id.otp4),
            findViewById(R.id.otp5),
            findViewById(R.id.otp6)
        )

        setupOtpInputs()

        btnSendResetOtp.setOnClickListener {
            sendResetOtp()
        }

        btnVerifyResetOtp.setOnClickListener {
            verifyResetOtp()
        }
    }

    private fun sendResetOtp() {
        identifier = etResetIdentifier.text.toString().trim()
        if (identifier.isEmpty()) {
            etResetIdentifier.error = "Enter Email or Mobile Number"
            etResetIdentifier.requestFocus()
            return
        }

        btnSendResetOtp.isEnabled = false
        btnSendResetOtp.text = "Sending OTP..."

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@ForgotPasswordActivity)
                val response = api.forgotPassword(ForgotPasswordRequest(identifier))

                btnSendResetOtp.isEnabled = true
                btnSendResetOtp.text = "Send Recovery OTP"

                if (response.isSuccessful) {
                    Toast.makeText(this@ForgotPasswordActivity, "Recovery OTP sent!", Toast.LENGTH_SHORT).show()
                    layoutOtpSection.visibility = View.VISIBLE
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Failed to send reset OTP."
                    Toast.makeText(this@ForgotPasswordActivity, extractErrorMessage(errorMsg), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                btnSendResetOtp.isEnabled = true
                btnSendResetOtp.text = "Send Recovery OTP"
                Toast.makeText(this@ForgotPasswordActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun verifyResetOtp() {
        val otp = otpFields.joinToString("") { it.text.toString().trim() }
        if (otp.length != 6 || !otp.all { it.isDigit() }) {
            Toast.makeText(this, "Enter all 6 digits.", Toast.LENGTH_SHORT).show()
            return
        }

        btnVerifyResetOtp.isEnabled = false
        btnVerifyResetOtp.text = "Verifying..."

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@ForgotPasswordActivity)
                val response = api.verifyResetOtp(VerifyResetOtpRequest(identifier, otp))

                btnVerifyResetOtp.isEnabled = true
                btnVerifyResetOtp.text = "Verify OTP & Proceed"

                if (response.isSuccessful && response.body() != null) {
                    val resetToken = response.body()!!.resetToken
                    Toast.makeText(this@ForgotPasswordActivity, "OTP Verified!", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this@ForgotPasswordActivity, ResetPasswordActivity::class.java).apply {
                        putExtra("reset_token", resetToken)
                    }
                    startActivity(intent)
                    finish()
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Invalid OTP."
                    Toast.makeText(this@ForgotPasswordActivity, extractErrorMessage(errorMsg), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                btnVerifyResetOtp.isEnabled = true
                btnVerifyResetOtp.text = "Verify OTP & Proceed"
                Toast.makeText(this@ForgotPasswordActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
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

    private fun extractErrorMessage(rawJson: String): String {
        return try {
            val jsonObj = org.json.JSONObject(rawJson)
            jsonObj.optString("detail", "Error verifying OTP.")
        } catch (e: Exception) {
            "Error verifying OTP."
        }
    }
}
