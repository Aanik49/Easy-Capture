package com.example.capture

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.RecordingPhase

/**
 * Manages the persistent top-bar Quick Control Notification (Record, Screenshot, Tools, Home, Exit)
 * so the user can start recording or take a 1080p screenshot anytime from the notification shade,
 * and completely remove it from the top bar whenever they tap Exit.
 */
object QuickControlNotificationManager {

    private const val LEGACY_CHANNEL_ID = "easy_capture_quick_bar"
    private const val CHANNEL_ID = "easy_capture_quick_bar_active"
    private const val NOTIFICATION_ID = 4202

    const val EXTRA_QUICK_ACTION = "extra_quick_action"
    const val QUICK_ACTION_RECORD = "quick_action_record"
    const val QUICK_ACTION_SCREENSHOT = "quick_action_screenshot"
    const val QUICK_ACTION_TOOLS = "quick_action_tools"
    const val QUICK_ACTION_HOME = "quick_action_home"

    @Volatile
    private var dismissedByExit: Boolean = false

    @Volatile
    private var enabledPreference: Boolean = true

    fun setEnabledPreference(context: Context, enabled: Boolean) {
        enabledPreference = enabled
        if (enabled) {
            dismissedByExit = false
            showIfAllowed(context)
        } else {
            cancelNotification(context)
        }
    }

    /**
     * Called when the user opens Easy Capture: clears any prior Exit dismissal and displays the quick bar
     * (unless disabled in Settings or a recording foreground notification is currently active).
     */
    fun onAppOpened(context: Context, enabled: Boolean) {
        enabledPreference = enabled
        dismissedByExit = false
        if (enabled) {
            showIfAllowed(context)
        } else {
            cancelNotification(context)
        }
    }

    fun showIfAllowed(context: Context) {
        if (!enabledPreference || dismissedByExit) return
        val phase = CaptureStateHolder.state.value.phase
        if (phase == RecordingPhase.RECORDING || phase == RecordingPhase.PAUSED) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureChannel(context)
        val remoteViews = buildRemoteViews(context)

        val openHomeIntent = createActivityPendingIntent(
            context = context,
            requestCode = 200,
            quickAction = QUICK_ACTION_HOME
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_record)
            .setContentTitle(context.getString(R.string.app_name))
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setContentIntent(openHomeIntent)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Temporarily hides the idle quick bar while the live recording foreground notification is active.
     */
    fun hideDuringRecording(context: Context) {
        cancelNotification(context)
    }

    /**
     * Restores the quick bar after recording completes if the user did not tap Exit.
     */
    fun restoreAfterRecording(context: Context) {
        if (!dismissedByExit && enabledPreference) {
            showIfAllowed(context)
        }
    }

    /**
     * Called when the user taps Exit on the top notification bar: immediately removes it from top bar.
     */
    fun markExitedAndDismiss(context: Context) {
        dismissedByExit = true
        cancelNotification(context)
    }

    private fun cancelNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
    }

    private fun buildRemoteViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.notification_quick_bar)

        views.setOnClickPendingIntent(
            R.id.notif_btn_record,
            createActivityPendingIntent(context, 201, QUICK_ACTION_RECORD)
        )
        views.setOnClickPendingIntent(
            R.id.notif_btn_screenshot,
            createActivityPendingIntent(context, 202, QUICK_ACTION_SCREENSHOT)
        )
        views.setOnClickPendingIntent(
            R.id.notif_btn_tools,
            createActivityPendingIntent(context, 203, QUICK_ACTION_TOOLS)
        )
        views.setOnClickPendingIntent(
            R.id.notif_btn_home,
            createActivityPendingIntent(context, 204, QUICK_ACTION_HOME)
        )

        val exitBroadcast = Intent(context, QuickBarActionReceiver::class.java).apply {
            action = QuickBarActionReceiver.ACTION_EXIT_QUICK_BAR
        }
        val exitPendingIntent = PendingIntent.getBroadcast(
            context,
            205,
            exitBroadcast,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.notif_btn_exit, exitPendingIntent)

        return views
    }

    private fun createActivityPendingIntent(
        context: Context,
        requestCode: Int,
        quickAction: String
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_QUICK_ACTION, quickAction)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            runCatching { nm.deleteNotificationChannel(LEGACY_CHANNEL_ID) }
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.quick_bar_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.quick_bar_channel_desc)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(channel)
        }
    }
}
