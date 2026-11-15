package com.example.secureloginapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.secureloginapp.data.api.RetrofitClient
import com.example.secureloginapp.data.local.SessionManager
import com.example.secureloginapp.data.model.RefreshTokenRequest
import com.example.secureloginapp.utils.IstDateTime
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager

    private lateinit var layoutLoggedOut: LinearLayout
    private lateinit var layoutLoggedIn: LinearLayout

    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var btnLogout: Button

    private lateinit var tvWelcomeUser: TextView
    private lateinit var tvUserEmail: TextView
    private lateinit var tvUserPhone: TextView
    private lateinit var tvCreatedAt: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sessionManager = SessionManager(this)

        layoutLoggedOut = findViewById(R.id.layoutLoggedOut)
        layoutLoggedIn = findViewById(R.id.layoutLoggedIn)

        btnLogin = findViewById(R.id.btnLogin)
        btnRegister = findViewById(R.id.btnRegister)
        btnLogout = findViewById(R.id.btnLogout)

        tvWelcomeUser = findViewById(R.id.tvWelcomeUser)
        tvUserEmail = findViewById(R.id.tvUserEmail)
        tvUserPhone = findViewById(R.id.tvUserPhone)
        tvCreatedAt = findViewById(R.id.tvCreatedAt)

        btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnLogout.setOnClickListener {
            performLogout()
        }

        updateUIState()
    }

    override fun onResume() {
        super.onResume()
        updateUIState()
    }

    private fun updateUIState() {
        if (sessionManager.isLoggedIn()) {
            layoutLoggedOut.visibility = View.GONE
            layoutLoggedIn.visibility = View.VISIBLE

            tvWelcomeUser.text = "Welcome, ${sessionManager.getUserName()}!"
            tvUserEmail.text = sessionManager.getUserEmail()

            val phone = sessionManager.getUserPhone()
            if (phone.isNotBlank()) {
                tvUserPhone.visibility = View.VISIBLE
                tvUserPhone.text = phone
            } else {
                tvUserPhone.visibility = View.GONE
            }

            val rawCreatedAt = sessionManager.getCreatedAt()
            val formattedDate = IstDateTime.format(rawCreatedAt)
            tvCreatedAt.text = "Member Since: $formattedDate"
        } else {
            layoutLoggedOut.visibility = View.VISIBLE
            layoutLoggedIn.visibility = View.GONE
        }
    }

    private fun performLogout() {
        val refreshToken = sessionManager.getRefreshToken()
        sessionManager.clearSession()
        updateUIState()
        Toast.makeText(this, "Logged out successfully.", Toast.LENGTH_SHORT).show()

        if (!refreshToken.isNullOrEmpty()) {
            lifecycleScope.launch {
                try {
                    val api = RetrofitClient.getApiService(this@MainActivity)
                    api.logout(RefreshTokenRequest(refreshToken))
                } catch (e: Exception) {
                    // Ignore background logout errors
                }
            }
        }
    }
}
