package com.example.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles tapping Exit on the persistent top-bar quick control notification,
 * immediately removing it from the status/notification bar.
 */
class QuickBarActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_EXIT_QUICK_BAR) {
            QuickControlNotificationManager.markExitedAndDismiss(context)
        }
    }

    companion object {
        const val ACTION_EXIT_QUICK_BAR = "com.example.capture.ACTION_EXIT_QUICK_BAR"
    }
}
