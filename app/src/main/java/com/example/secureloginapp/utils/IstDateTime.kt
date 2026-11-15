package com.example.secureloginapp.utils

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object IstDateTime {

    private val istZone = ZoneId.of("Asia/Kolkata")

    private val outputFormatter =
        DateTimeFormatter.ofPattern(
            "dd MMM yyyy, hh:mm a",
            Locale.ENGLISH
        )

    fun format(timestamp: String?): String {

        if (timestamp.isNullOrBlank()) {
            return "N/A"
        }

        return try {
            OffsetDateTime.parse(timestamp)
                .atZoneSameInstant(istZone)
                .format(outputFormatter)

        } catch (e: Exception) {
            "Invalid date"
        }
    }
}
