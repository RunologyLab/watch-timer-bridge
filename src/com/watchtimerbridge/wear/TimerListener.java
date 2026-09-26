package com.watchtimerbridge.wear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Only Samsung Timer events trigger a complication refresh; no notifications are stored. */
public final class TimerListener extends NotificationListenerService {
    private static final String SAMSUNG_TIMER = "com.samsung.android.watch.timer";
    static volatile String lastEvent = "No Samsung Timer notification event yet";
    static volatile String lastScan = "No timer scan yet";
    static volatile boolean connected;
    static volatile TimerListener instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastRequestedEnd = Long.MIN_VALUE;
    private boolean screenReceiverRegistered;
    private boolean longTickScheduled;
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                stopLongTimerTick();
                // The system's dynamic text can keep the ambient complication
                // current without one-second updates while the screen is dark.
                if (lastRequestedEnd > 0)
                    TimerComplicationService.pushActive(TimerListener.this, lastRequestedEnd);
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                lastRequestedEnd = Long.MIN_VALUE;
                handler.removeCallbacks(sendChangedTimer);
                handler.post(sendChangedTimer);
            }
        }
    };

    private final Runnable longTimerTick = new Runnable() {
        @Override public void run() {
            longTickScheduled = false;
            long end = TimerComplicationService.findEndTime(TimerListener.this);
            if (TimerComplicationService.needsLongTimerTick(TimerListener.this, end)) {
                TimerComplicationService.pushLongTimerTick(TimerListener.this, end);
                ensureLongTimerTick(end);
            } else {
                // Crossing below one hour restores the self-updating stopwatch.
                TimerComplicationService.pushActive(TimerListener.this, end);
            }
        }
    };

    private void stopLongTimerTick() {
        handler.removeCallbacks(longTimerTick);
        longTickScheduled = false;
    }

    void ensureLongTimerTick() {
        ensureLongTimerTick(TimerComplicationService.findEndTime(this));
    }

    private void ensureLongTimerTick(long end) {
        if (!TimerComplicationService.needsLongTimerTick(this, end)) {
            stopLongTimerTick();
            return;
        }
        if (longTickScheduled) return;
        long remaining = end + TimerComplicationService.getOffsetSeconds(this) * 1000L
                - System.currentTimeMillis();
        long delay = remaining % 1000L;
        if (delay == 0) delay = 1000L;
        longTickScheduled = true;
        handler.postDelayed(longTimerTick, delay + 15L);
    }

    private final Runnable sendChangedTimer = new Runnable() {
        @Override public void run() {
            long currentEnd = TimerComplicationService.findEndTime(TimerListener.this);
            lastScan = currentEnd > 0 ? "Timer deadline found" : "No running Timer deadline found";
            ensureLongTimerTick(currentEnd);
            // Samsung may repost every second; send only meaningful changes.
            if (lastRequestedEnd != Long.MIN_VALUE &&
                (currentEnd == lastRequestedEnd ||
                 (currentEnd > 0 && lastRequestedEnd > 0 &&
                  Math.abs(currentEnd - lastRequestedEnd) < 1500L))) return;
            lastRequestedEnd = currentEnd;
            TimerComplicationService.pushActive(TimerListener.this, currentEnd);
            TimerComplicationService.requestSystemUpdate(TimerListener.this,
                    currentEnd > 0 ? "倒计时已更新" : "倒计时已结束或取消");
        }
    };

    private void scheduleRefresh() {
        handler.removeCallbacks(sendChangedTimer);
        handler.postDelayed(sendChangedTimer, 400L);
    }

    @Override public void onListenerConnected() {
        instance = this;
        connected = true;
        lastEvent = "Notification listener connected";
        lastRequestedEnd = Long.MIN_VALUE;
        if (!screenReceiverRegistered) {
            IntentFilter screen = new IntentFilter();
            screen.addAction(Intent.ACTION_SCREEN_ON);
            screen.addAction(Intent.ACTION_SCREEN_OFF);
            registerReceiver(screenReceiver, screen);
            screenReceiverRegistered = true;
        }
        scheduleRefresh();
    }

    public void onListenerDisconnected() {
        instance = null;
        connected = false;
        lastEvent = "Notification listener disconnected";
        handler.removeCallbacks(sendChangedTimer);
        stopLongTimerTick();
        if (screenReceiverRegistered) {
            unregisterReceiver(screenReceiver);
            screenReceiverRegistered = false;
        }
        TimerComplicationService.requestSystemUpdate(this, "通知读取服务已断开");
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(sendChangedTimer);
        stopLongTimerTick();
        if (screenReceiverRegistered) {
            unregisterReceiver(screenReceiver);
            screenReceiverRegistered = false;
        }
        if (instance == this) {
            instance = null;
            connected = false;
        }
        super.onDestroy();
    }

    @Override public void onNotificationPosted(StatusBarNotification notification) {
        if (SAMSUNG_TIMER.equals(notification.getPackageName())) {
            lastEvent = "Samsung Timer notification posted at " +
                    android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis());
            scheduleRefresh();
        }
    }

    @Override public void onNotificationRemoved(StatusBarNotification notification) {
        if (SAMSUNG_TIMER.equals(notification.getPackageName())) {
            lastEvent = "Samsung Timer notification removed at " +
                    android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis());
            scheduleRefresh();
        }
    }
}
