package com.flow.health;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Calendar;

public class MainActivity extends Activity {
    private WebView web;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(8, 13, 21));
        getWindow().setNavigationBarColor(Color.rgb(8, 13, 21));
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(8, 13, 21));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new FlowBridge(), "FlowNative");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    public class FlowBridge {
        @JavascriptInterface public void pickTime(String kind, int minutes) {
            runOnUiThread(() -> new TimePickerDialog(MainActivity.this, (v, h, m) ->
                    web.evaluateJavascript("window.onNativeTime('" + kind + "'," + (h * 60 + m) + ")", null),
                    minutes / 60, minutes % 60, true).show());
        }

        @JavascriptInterface public void setReminders(boolean enabled, int sleep, int wake) {
            runOnUiThread(() -> {
                if (enabled && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 81);
                }
                scheduleReminder(901, "wake", normalize(wake + 15), enabled);
                scheduleReminder(902, "wind", normalize(sleep - 45), enabled);
            });
        }
    }

    private void scheduleReminder(int code, String kind, int minute, boolean enabled) {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent i = new Intent(this, ReminderReceiver.class).putExtra("kind", kind);
        PendingIntent pi = PendingIntent.getBroadcast(this, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (am == null) return;
        am.cancel(pi);
        if (!enabled) return;
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, minute / 60); c.set(Calendar.MINUTE, minute % 60); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
    }

    private static int normalize(int m) { m %= 1440; return m < 0 ? m + 1440 : m; }

    public static class ReminderReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            String channelId = "flow_rhythm";
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(channelId, "Nhịp Flow", NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("Nhắc nhẹ theo nhịp ngủ và thức của bạn");
                nm.createNotificationChannel(ch);
            }
            boolean wind = i != null && "wind".equals(i.getStringExtra("kind"));
            String title = wind ? "Hạ nhịp thôi" : "Mở ngày theo nhịp của bạn";
            String text = wind ? "Giảm ánh sáng, cất caffeine và chọn một việc nhẹ để kết ngày." : "Một ít nước và ánh sáng là đủ để khởi động.";
            Intent open = new Intent(c, MainActivity.class);
            PendingIntent content = PendingIntent.getActivity(c, wind ? 912 : 911, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, channelId) : new Notification.Builder(c);
            b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(title).setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text)).setAutoCancel(true).setContentIntent(content);
            nm.notify(wind ? 1002 : 1001, b.build());
        }
    }
}
