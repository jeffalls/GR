package alls.tech.gr.bridge

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import alls.tech.gr.core.Logger
import alls.tech.gr.core.LogSource

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Logger.d("Notification action received: ${intent.action}", LogSource.BRIDGE)

        try {
            when (intent.action) {
                "alls.tech.gr.COPY_ACTION" -> {
                    val data = intent.getStringExtra("data") ?: return

                    Handler(Looper.getMainLooper()).post {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Grindr+ Data", data)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied to clipboard: $data", Toast.LENGTH_SHORT).show()
                    }
                }

                "alls.tech.gr.VIEW_PROFILE_ACTION" -> {
                    val profileId = intent.getStringExtra("profileId") ?: return
                    val appIntent = Intent().apply {
                        setClassName(
                            context.packageName,
                            "${context.packageName}.manager.MainActivity"
                        )
                        putExtra("action", "VIEW_PROFILE")
                        putExtra("profileId", profileId)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(appIntent)
                }

                "alls.tech.gr.CUSTOM_ACTION" -> {
                    val data = intent.getStringExtra("data") ?: return
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Custom action: $data", Toast.LENGTH_SHORT).show()
                    }
                }

                "alls.tech.gr.DEFAULT_ACTION" -> {
                    val data = intent.getStringExtra("data") ?: return
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Action performed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("Error handling notification action: ${e.message}", LogSource.BRIDGE)
            Logger.writeRaw(e.stackTraceToString())
        }
    }
}