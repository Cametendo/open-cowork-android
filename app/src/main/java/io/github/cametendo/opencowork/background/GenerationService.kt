package io.github.cametendo.opencowork.background

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.github.cametendo.opencowork.OpenCoworkApp
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the process alive while a chat reply is streaming
 * or a Cowork task is running, so closing (or swiping away) the app doesn't cancel
 * the prompt. The actual work lives in the app-scoped repositories; this service
 * only shows the ongoing notification and stops itself when nothing is running.
 */
class GenerationService : LifecycleService() {
    companion object {
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, GenerationService::class.java))
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as OpenCoworkApp).container
        promote(container.notifier.ongoing(container.activeWork.value.ifEmpty { listOf("Working…") }))
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenCowork:generation")
            .apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) }

        lifecycleScope.launch {
            container.activeWork.collect { work ->
                if (work.isEmpty()) {
                    ServiceCompat.stopForeground(this@GenerationService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    promote(container.notifier.ongoing(work))
                }
            }
        }
    }

    private fun promote(notification: android.app.Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        runCatching { ServiceCompat.startForeground(this, Notifier.ONGOING_ID, notification, type) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return Service.START_NOT_STICKY
    }

    /** Android 15+ caps dataSync services; stop gracefully when the system says so. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
