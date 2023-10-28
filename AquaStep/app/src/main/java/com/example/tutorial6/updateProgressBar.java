package com.example.tutorial6;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationManagerCompat;

public class updateProgressBar extends BroadcastReceiver {

    public static String ACTION_CANCEL = "actionUpdateProgressBar";

    public updateProgressBar() {}

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(ACTION_CANCEL)) {
            TerminalFragment.drankCupsText.setText((int)Math.floor(TerminalFragment.counterProgressBar) + "/" + TerminalFragment.dailyTarget);
            TerminalFragment.progressBar.setProgress((int) Math.floor(TerminalFragment.counterProgressBar));
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            notificationManager.cancel(TerminalFragment.notificationCounter);
        }
    }
}