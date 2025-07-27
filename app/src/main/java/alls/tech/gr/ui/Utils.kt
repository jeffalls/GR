package alls.tech.gr.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.widget.Toast
import alls.tech.gr.GR
import alls.tech.gr.core.Config
import alls.tech.gr.core.Logger
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object Utils {
    fun getId(name: String, defType: String, context: Context): Int {
        return context.resources.getIdentifier(name, defType, context.packageName)
    }

    fun createButtonDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = 12f
        }
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = GR.context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        // GR.showToast(Toast.LENGTH_LONG, "$label copied to clipboard.")
    }

    fun formatEpochSeconds(epochSec: Long): String {
        val formatter = DateTimeFormatter.ofPattern(Config.get(
            "date_format", "yyyy-MM-dd") as String)
        return try {
            val instant = Instant.ofEpochSecond(epochSec)
            val dt = LocalDateTime.ofInstant(instant, ZoneOffset.UTC)
            dt.format(formatter)
        } catch (e: Exception) {
            Logger.e("Error formatting date: $epochSec with format: $formatter")
            Logger.writeRaw(e.stackTraceToString())
            "Unknown"
        }
    }
}