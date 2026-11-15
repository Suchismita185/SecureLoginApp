package com.example.secureloginapp

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.secureloginapp.data.api.RetrofitClient
import com.example.secureloginapp.data.model.RegisterRequest
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var etFullName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText

    private lateinit var btnCreateAccount: Button
    private lateinit var tvGoToLogin: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        etFullName = findViewById(R.id.etFullName)
        etEmail = findViewById(R.id.etEmail)
        etPhone = findViewById(R.id.etPhone)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)

        btnCreateAccount = findViewById(R.id.btnCreateAccount)
        tvGoToLogin = findViewById(R.id.tvGoToLogin)

        btnCreateAccount.setOnClickListener {
            validateAndRegister()
        }

        tvGoToLogin.setOnClickListener {
            finish()
        }
    }

    private fun validateAndRegister() {
        val name = etFullName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        var phone = etPhone.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()

        if (name.length < 2) {
            etFullName.error = "Full Name must contain at least 2 characters"
            etFullName.requestFocus()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Enter a valid email address"
            etEmail.requestFocus()
            return
        }

        // Standardize phone format to include country code if missing
        val digits = phone.filter { it.isDigit() }
        if (digits.length == 10) {
            phone = "+91$digits"
        } else if (!phone.startsWith("+")) {
            phone = "+$digits"
        }

        if (digits.length < 8) {
            etPhone.error = "Enter a valid phone number with country code"
            etPhone.requestFocus()
            return
        }

        val strongPassword = password.length >= 8 &&
                password.any { it.isUpperCase() } &&
                password.any { it.isLowerCase() } &&
                password.any { it.isDigit() } &&
                password.any { !it.isLetterOrDigit() }

        if (!strongPassword) {
            etPassword.error = "Use 8+ chars with uppercase, lowercase, number & symbol"
            etPassword.requestFocus()
            return
        }

        if (password != confirmPassword) {
            etConfirmPassword.error = "Passwords do not match"
            etConfirmPassword.requestFocus()
            return
        }

        // Execute API registration request
        btnCreateAccount.isEnabled = false
        btnCreateAccount.text = "Creating Account..."

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@RegisterActivity)
                val req = RegisterRequest(
                    fullName = name,
                    email = email,
                    phoneNumber = phone,
                    password = password,
                    confirmPassword = confirmPassword
                )

                val response = api.registerUser(req)
                btnCreateAccount.isEnabled = true
                btnCreateAccount.text = "Create Account"

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    Toast.makeText(this@RegisterActivity, "Registration started! Verify Email OTP.", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this@RegisterActivity, EmailOtpActivity::class.java).apply {
                        putExtra("registration_ref", body.registrationRef)
                        putExtra("email", body.email)
                        putExtra("phone_number", body.phoneNumber)
                    }
                    startActivity(intent)
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Registration failed."
                    Toast.makeText(this@RegisterActivity, extractErrorMessage(errorMsg), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                btnCreateAccount.isEnabled = true
                btnCreateAccount.text = "Create Account"
                Toast.makeText(this@RegisterActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun extractErrorMessage(rawJson: String): String {
        return try {
            val jsonObj = org.json.JSONObject(rawJson)
            jsonObj.optString("detail", "Error occurred during registration.")
        } catch (e: Exception) {
            "Error occurred during registration."
        }
    }
}
