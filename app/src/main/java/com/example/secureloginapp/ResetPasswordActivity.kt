package com.example.secureloginapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.secureloginapp.data.api.RetrofitClient
import com.example.secureloginapp.data.model.ResetPasswordRequest
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var etNewPassword: TextInputEditText
    private lateinit var etConfirmNewPassword: TextInputEditText
    private lateinit var btnResetPassword: Button
    private var resetToken: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)

        resetToken = intent.getStringExtra("reset_token") ?: ""

        etNewPassword = findViewById(R.id.etNewPassword)
        etConfirmNewPassword = findViewById(R.id.etConfirmNewPassword)
        btnResetPassword = findViewById(R.id.btnResetPassword)

        btnResetPassword.setOnClickListener {
            performPasswordReset()
        }
    }

    private fun performPasswordReset() {
        val newPassword = etNewPassword.text.toString()
        val confirmNewPassword = etConfirmNewPassword.text.toString()

        val strongPassword = newPassword.length >= 8 &&
                newPassword.any { it.isUpperCase() } &&
                newPassword.any { it.isLowerCase() } &&
                newPassword.any { it.isDigit() } &&
                newPassword.any { !it.isLetterOrDigit() }

        if (!strongPassword) {
            etNewPassword.error = "Use 8+ chars with uppercase, lowercase, number & symbol"
            etNewPassword.requestFocus()
            return
        }

        if (newPassword != confirmNewPassword) {
            etConfirmNewPassword.error = "Passwords do not match"
            etConfirmNewPassword.requestFocus()
            return
        }

        btnResetPassword.isEnabled = false
        btnResetPassword.text = "Resetting Password..."

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@ResetPasswordActivity)
                val req = ResetPasswordRequest(
                    resetToken = resetToken,
                    newPassword = newPassword,
                    confirmNewPassword = confirmNewPassword
                )
                val response = api.resetPassword(req)

                btnResetPassword.isEnabled = true
                btnResetPassword.text = "Reset Password"

                if (response.isSuccessful) {
                    Toast.makeText(this@ResetPasswordActivity, "Password reset successful! Please log in.", Toast.LENGTH_LONG).show()

                    val intent = Intent(this@ResetPasswordActivity, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Password reset failed."
                    Toast.makeText(this@ResetPasswordActivity, extractErrorMessage(errorMsg), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                btnResetPassword.isEnabled = true
                btnResetPassword.text = "Reset Password"
                Toast.makeText(this@ResetPasswordActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun extractErrorMessage(rawJson: String): String {
        return try {
            val jsonObj = org.json.JSONObject(rawJson)
            jsonObj.optString("detail", "Password reset failed.")
        } catch (e: Exception) {
            "Password reset failed."
        }
    }
}
