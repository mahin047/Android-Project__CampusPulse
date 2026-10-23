package com.campuspulse.app;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

public class NoticeAlerts {

    public static final String ACTION_NEW_NOTICE = "com.campuspulse.app.action.NEW_NOTICE";
    public static final String EXTRA_ID = "n_id";
    public static final String CH_NORMAL = "notices";
    public static final String CH_URGENT = "notices_urgent";

    /** নতুন notice এর system notification বানায়। Tap করলে Dashboard → Notices → Detail, Back ঠিকমতো চলে। */
    public static void show(Context ctx, Intent src) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return;
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;

        String id = src.getStringExtra(EXTRA_ID);
        String title = src.getStringExtra(NoticeDetailActivity.EXTRA_TITLE);
        String body = src.getStringExtra(NoticeDetailActivity.EXTRA_BODY);
        String category = src.getStringExtra(NoticeDetailActivity.EXTRA_CATEGORY);
        String priority = src.getStringExtra(NoticeDetailActivity.EXTRA_PRIORITY);
        boolean urgent = "urgent".equalsIgnoreCase(priority);

        // detail screen এর extras broadcast এর extras হুবহু এক, তাই সরাসরি copy
        Intent detail = new Intent(ctx, NoticeDetailActivity.class).putExtras(src);
        int code = id == null ? 0 : id.hashCode();

        PendingIntent pi = TaskStackBuilder.create(ctx)
                .addNextIntent(new Intent(ctx, DashboardActivity.class))
                .addNextIntent(new Intent(ctx, NoticesActivity.class))
                .addNextIntent(detail)
                .getPendingIntent(code, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String text = title == null ? "A new notice was published" : title;
        NotificationCompat.Builder nb = new NotificationCompat.Builder(ctx, urgent ? CH_URGENT : CH_NORMAL)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(urgent ? "Urgent notice" : "New notice")
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(body == null || body.isEmpty() ? text : text + "\n" + body))
                .setSubText(category)
                .setColor(NoticeModel.priorityColor(priority))
                .setPriority(urgent ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);
        if (pi != null) nb.setContentIntent(pi);

        try {
            NotificationManagerCompat.from(ctx).notify(code, nb.build());
        } catch (SecurityException ignored) { }
    }
}