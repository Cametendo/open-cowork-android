package io.github.cametendo.opencowork.background

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.cametendo.opencowork.MainActivity
import io.github.cametendo.opencowork.R
import io.github.cametendo.opencowork.data.ToolCall

/** All notifications the app posts. */
class Notifier(private val context: Context) {
    companion object {
        const val CHANNEL_WORK = "generation"
        const val CHANNEL_ALERTS = "alerts"
        const val ONGOING_ID = 1
        const val EXTRA_OPEN_CHAT = "open_chat"
        const val EXTRA_OPEN_COWORK = "open_cowork"
    }

    private val nm = NotificationManagerCompat.from(context)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val sys = context.getSystemService(NotificationManager::class.java)
            sys.createNotificationChannel(
                NotificationChannel(CHANNEL_WORK, context.getString(R.string.channel_generation), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.channel_generation_desc)
                    setShowBadge(false)
                },
            )
            sys.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_alerts_desc)
                },
            )
        }
    }

    private fun canPost() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openIntent(extraKey: String?, id: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (extraKey != null) putExtra(extraKey, id)
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun ongoing(lines: List<String>): Notification {
        val title = if (lines.size == 1) lines[0] else "${lines.size} tasks running"
        return NotificationCompat.Builder(context, CHANNEL_WORK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(if (lines.size == 1) "Keeps going even if you close the app" else lines.joinToString(" · "))
            .setStyle(if (lines.size > 1) NotificationCompat.InboxStyle().also { s -> lines.take(6).forEach(s::addLine) } else null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(0, 0, true)
            .setContentIntent(openIntent(null, null, 0))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun alertId(prefix: String, id: String) = (prefix + id).hashCode()

    fun replyReady(chatId: String, title: String, preview: String) {
        if (!canPost()) return
        val n = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { "Reply ready" })
            .setContentText(preview.take(200))
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview.take(1000)))
            .setAutoCancel(true)
            .setContentIntent(openIntent(EXTRA_OPEN_CHAT, chatId, alertId("c", chatId)))
            .build()
        runCatching { nm.notify(alertId("chat", chatId), n) }
    }

    fun approval(sessionId: String, title: String, call: ToolCall?) {
        if (!canPost()) return
        val (question, detail) = describeApproval(call)
        fun action(label: String, approved: Boolean, always: Boolean, code: Int): NotificationCompat.Action {
            val intent = Intent(context, ApprovalReceiver::class.java).apply {
                putExtra(ApprovalReceiver.EXTRA_SESSION, sessionId)
                putExtra(ApprovalReceiver.EXTRA_TOOL_CALL, call?.id)
                putExtra(ApprovalReceiver.EXTRA_APPROVED, approved)
                putExtra(ApprovalReceiver.EXTRA_ALWAYS, always)
            }
            val pi = PendingIntent.getBroadcast(context, alertId("a$code", sessionId), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return NotificationCompat.Action.Builder(0, label, pi).build()
        }
        val b = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Approval needed · $title")
            .setContentText(question)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$question\n\n${detail.take(600)}"))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent(EXTRA_OPEN_COWORK, sessionId, alertId("o", sessionId)))
        if (call != null) {
            b.addAction(action("Allow", approved = true, always = false, code = 1))
            b.addAction(action("Allow all", approved = true, always = true, code = 2))
            b.addAction(action("Deny", approved = false, always = false, code = 3))
        }
        runCatching { nm.notify(alertId("approval", sessionId), b.build()) }
    }

    fun cancelApproval(sessionId: String) = nm.cancel(alertId("approval", sessionId))

    fun taskEnded(sessionId: String, title: String, status: String, error: String?) {
        if (!canPost()) return
        val headline = when (status) {
            "completed" -> "Task finished"
            "cancelled" -> "Task stopped"
            else -> "Task failed"
        }
        val n = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$headline · $title")
            .setContentText(error?.take(200) ?: "Open the task to review the changes")
            .setAutoCancel(true)
            .setContentIntent(openIntent(EXTRA_OPEN_COWORK, sessionId, alertId("e", sessionId)))
            .build()
        runCatching { nm.notify(alertId("ended", sessionId), n) }
    }
}

/** Short question + detail text for an approval request. */
fun describeApproval(call: ToolCall?): Pair<String, String> = when (call?.name) {
    null -> "Cowork is waiting for your approval" to ""
    "run_command" -> "Run this command?" to call.arg("command")
    "write_file" -> "Write ${call.arg("path")}?" to call.arg("content")
    "edit_file" -> "Edit ${call.arg("path")}?" to "- ${call.arg("old_string")}\n+ ${call.arg("new_string")}"
    else -> "Allow ${call.name}?" to call.arguments.toString()
}
