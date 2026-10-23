package com.campuspulse.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Manifest-এ declare করা static receiver। Custom broadcast NEW_NOTICE ধরে। */
public class NoticeAlertReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!NoticeAlerts.ACTION_NEW_NOTICE.equals(intent.getAction())) return;
        // app সামনে খোলা থাকলে Dashboard এর dynamic receiver Snackbar দেখায়, notification দরকার নেই
        if (CampusPulseApp.isForeground()) return;
        NoticeAlerts.show(context, intent);
    }
}