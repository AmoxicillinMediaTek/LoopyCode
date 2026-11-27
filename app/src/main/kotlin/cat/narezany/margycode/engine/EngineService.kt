package cat.narezany.margycode.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import cat.narezany.margycode.R
import cat.narezany.margycode.tr
import cat.narezany.margycode.ui.MainActivity

/**
 * Keeps the app, and opencode with it, awake while a model is answering, so
 * leaving the app doesn't freeze a long answer halfway.
 */
class EngineService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(
            NotificationChannel(CHANNEL, tr("Работа модели", "Model at work"), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(tr("Модель отвечает", "The model is answering"))
            .setContentText(tr("MargyCode доделает ответ в фоне", "MargyCode finishes the answer in the background"))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(1, n)
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "work"
        private var on = false

        fun start(context: Context) {
            if (on) return
            on = true
            runCatching { context.startForegroundService(Intent(context, EngineService::class.java)) }
        }

        fun stop(context: Context) {
            if (!on) return
            on = false
            context.stopService(Intent(context, EngineService::class.java))
        }
    }
}
