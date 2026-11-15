package com.example.secureloginapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.secureloginapp.data.api.RetrofitClient
import com.example.secureloginapp.data.local.SessionManager
import com.example.secureloginapp.data.model.LoginRequest
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var etIdentifier: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: Button
    private lateinit var tvForgotPassword: TextView
    private lateinit var tvGoToRegister: TextView
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        sessionManager = SessionManager(this)

        if (sessionManager.isLoggedIn()) {
            navigateToDashboard()
            return
        }

        etIdentifier = findViewById(R.id.etIdentifier)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        tvGoToRegister = findViewById(R.id.tvGoToRegister)

        btnLogin.setOnClickListener {
            performLogin()
        }

        tvForgotPassword.setOnClickListener {
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            startActivity(intent)
        }

        tvGoToRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    private fun performLogin() {
        val identifier = etIdentifier.text.toString().trim()
        val password = etPassword.text.toString()

        if (identifier.isEmpty()) {
            etIdentifier.error = "Enter Email or Mobile Number"
            etIdentifier.requestFocus()
            return
        }

        if (password.isEmpty()) {
            etPassword.error = "Enter Password"
            etPassword.requestFocus()
            return
        }

        btnLogin.isEnabled = false
        btnLogin.text = "Logging in..."

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@LoginActivity)
                val response = api.login(LoginRequest(identifier = identifier, password = password))

                btnLogin.isEnabled = true
                btnLogin.text = "Login"

                if (response.isSuccessful && response.body() != null) {
                    val tokenData = response.body()!!
                    val user = tokenData.user

                    sessionManager.saveAuthSession(
                        accessToken = tokenData.accessToken,
                        refreshToken = tokenData.refreshToken,
                        userId = user?.id ?: "",
                        name = user?.fullName ?: "User",
                        email = user?.email ?: "",
                        phone = user?.phoneNumber ?: "",
                        createdAt = user?.createdAt
                    )

                    Toast.makeText(this@LoginActivity, "Login Successful!", Toast.LENGTH_SHORT).show()
                    navigateToDashboard()
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Login failed."
                    Toast.makeText(this@LoginActivity, extractErrorMessage(errorMsg), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                btnLogin.isEnabled = true
                btnLogin.text = "Login"
                Toast.makeText(this@LoginActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun extractErrorMessage(rawJson: String): String {
        return try {
            val jsonObj = org.json.JSONObject(rawJson)
            jsonObj.optString("detail", "Invalid credentials or login failed.")
        } catch (e: Exception) {
            "Invalid credentials or login failed."
        }
    }
}
