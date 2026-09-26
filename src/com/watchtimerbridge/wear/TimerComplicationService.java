package com.watchtimerbridge.wear;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.support.wearable.complications.ComplicationText;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import java.text.DateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Supplies a time-dependent countdown to a SHORT_TEXT Watch Face Format slot. */
public final class TimerComplicationService extends Service {
    private static final String PROVIDER = "android.support.wearable.complications.IComplicationProvider";
    private static final String MANAGER = "android.support.wearable.complications.IComplicationManager";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    // The system supplies a manager binder when a slot activates. Keeping that
    // binder lets the notification listener update that slot immediately;
    // relying on the update-request broadcast alone can wait for screen wake.
    private static final Map<Integer, IBinder> ACTIVE_MANAGERS = new ConcurrentHashMap<>();
    // The built-in Chronometer truncates seconds while Wear OS's time-difference
    // text rounds up. The correction is configurable because OEM displays can
    // add another second of apparent difference.
    private static final String PREFS = "timer_display";
    private static final String OFFSET = "countdown_offset_seconds";
    static final int DEFAULT_OFFSET_SECONDS = -1;
    static volatile String lastDelivery = "No complication data sent yet";
    static volatile String lastCallback = "No active complication callback yet";
    static volatile String lastRequest = "No refresh broadcast yet";

    static int activeSlotCount() { return ACTIVE_MANAGERS.size(); }

    static int getOffsetSeconds(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(OFFSET, DEFAULT_OFFSET_SECONDS);
    }

    static void setOffsetSeconds(Context context, int seconds) {
        if (seconds < -3 || seconds > 0) throw new IllegalArgumentException("offset out of range");
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(OFFSET, seconds).apply();
        long endTime = findEndTime(context);
        pushActive(context, endTime);
        requestSystemUpdate(context, "倒计时校准已调整");
    }

    static void record(Context context, String message) {
        String now = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date());
        android.content.SharedPreferences prefs = context.getSharedPreferences("provider_diagnostics", MODE_PRIVATE);
        String previous = prefs.getString("events", "");
        String current = now + " " + message + "\n" + previous;
        if (current.length() > 1500) current = current.substring(0, 1500);
        prefs.edit().putString("events", current).commit();
        android.util.Log.i("WatchTimerBridge", message);
    }

    private void record(String message) { record(this, message); }

    static void pushActive(final Context context, final long endTime) {
        if (ACTIVE_MANAGERS.isEmpty())
            lastDelivery = "Timer changed, but no active complication manager was cached";
        MAIN.post(new Runnable() {
            @Override public void run() {
                for (Map.Entry<Integer, IBinder> slot : ACTIVE_MANAGERS.entrySet()) {
                    IBinder manager = slot.getValue();
                    if (!manager.isBinderAlive()) {
                        ACTIVE_MANAGERS.remove(slot.getKey(), manager);
                        continue;
                    }
                    sendUpdate(context, slot.getKey(), manager, endTime, false);
                }
            }
        });
    }

    /** Update already active slots once per second while the display is awake. */
    static void pushLongTimerTick(final Context context, final long endTime) {
        MAIN.post(new Runnable() {
            @Override public void run() {
                for (Map.Entry<Integer, IBinder> slot : ACTIVE_MANAGERS.entrySet()) {
                    IBinder manager = slot.getValue();
                    if (!manager.isBinderAlive()) {
                        ACTIVE_MANAGERS.remove(slot.getKey(), manager);
                    } else {
                        sendUpdate(context, slot.getKey(), manager, endTime, true);
                    }
                }
            }
        });
    }

    static boolean needsLongTimerTick(Context context, long endTime) {
        PowerManager power = (PowerManager) context.getSystemService(POWER_SERVICE);
        return activeSlotCount() > 0 && (power == null || power.isInteractive()) &&
                CountdownFormatter.needsFullText(endTime + getOffsetSeconds(context) * 1000L
                        - System.currentTimeMillis());
    }

    /** Ask Wear OS to bind this provider again, including after its Service has stopped. */
    static void requestSystemUpdate(Context context, String reason) {
        Intent request = new Intent("android.support.wearable.complications.ACTION_REQUEST_UPDATE_ALL");
        request.setPackage("com.google.android.wearable.app");
        request.putExtra("android.support.wearable.complications.EXTRA_PROVIDER_COMPONENT",
                new ComponentName(context, TimerComplicationService.class));
        PendingIntent identity = PendingIntent.getActivity(context, 0,
                new Intent(context, StatusActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        request.putExtra("android.support.wearable.complications.EXTRA_PENDING_INTENT", identity);
        try {
            context.sendBroadcast(request);
            lastRequest = "Broadcast sent at " + DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date());
            record(context, "已请求系统刷新表盘：" + reason);
        } catch (RuntimeException error) {
            lastRequest = "Broadcast failed: " + error.getClass().getSimpleName();
            record(context, "请求系统刷新失败：" + error.getClass().getSimpleName());
        }
    }

    @Override public void onCreate() { super.onCreate(); record("数据源服务启动"); }

    @Override public IBinder onBind(Intent intent) {
        record("系统绑定数据源：" + (intent == null ? "(无)" : intent.getAction()));
        return binder;
    }

    private final Binder binder = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == IBinder.INTERFACE_TRANSACTION) {
                reply.writeString(PROVIDER);
                return true;
            }
            if (code >= 1 && code <= 5) data.enforceInterface(PROVIDER);
            if (code >= 1 && code <= 5) record("收到请求 code=" + code);
            switch (code) {
                case 1: { // onUpdate(id, type, manager)
                    final int id = data.readInt();
                    final int type = data.readInt();
                    final IBinder manager = data.readStrongBinder();
                    record("onUpdate id=" + id + " type=" + type + " manager=" + (manager != null));
                    lastCallback = "Update id=" + id + " at " +
                            DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date()) +
                            ", manager=" + (manager != null);
                    if (manager != null) ACTIVE_MANAGERS.put(id, manager);
                    MAIN.post(new Runnable() {
                        @Override public void run() { guardedUpdate(id, type, manager, "onUpdate"); }
                    });
                    reply.writeNoException();
                    return true;
                }
                case 2: { // onComplicationDeactivated
                    final int id = data.readInt();
                    record("onDeactivated id=" + id);
                    ACTIVE_MANAGERS.remove(id);
                    reply.writeNoException();
                    return true;
                }
                case 3: { // onComplicationActivated
                    final int id = data.readInt();
                    final int type = data.readInt();
                    final IBinder manager = data.readStrongBinder();
                    record("onActivated id=" + id + " type=" + type + " manager=" + (manager != null));
                    lastCallback = "Activated id=" + id + " at " +
                            DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date()) +
                            ", manager=" + (manager != null);
                    if (manager != null) ACTIVE_MANAGERS.put(id, manager);
                    MAIN.post(new Runnable() {
                        @Override public void run() { guardedUpdate(id, type, manager, "onActivated"); }
                    });
                    reply.writeNoException();
                    return true;
                }
                case 4: // getApiVersion
                    reply.writeNoException();
                    reply.writeInt(1);
                    return true;
                case 5: // getComplicationPreviewData(type)
                    int previewType = data.readInt();
                    reply.writeNoException();
                    reply.writeInt(1);
                    writeWireData(TimerComplicationService.this, reply, -1L, "05:00");
                    record("已返回预览数据 type=" + previewType);
                    return true;
                default: return super.onTransact(code, data, reply, flags);
            }
        }
    };

    private void guardedUpdate(int id, int type, IBinder manager, String reason) {
        try {
            record("开始正式更新：" + reason + " id=" + id + " type=" + type);
            update(id, type, manager);
            TimerListener listener = TimerListener.instance;
            if (listener != null) listener.ensureLongTimerTick();
        } catch (Throwable error) {
            record("正式更新失败：" + error.getClass().getName() + " " + String.valueOf(error.getMessage()));
            android.util.Log.e("WatchTimerBridge", "Complication update failed", error);
        }
    }

    private void update(int id, int type, IBinder manager) {
        if (manager == null) { record("无法发送：manager 为 null"); return; }
        long endTime;
        try {
            endTime = findEndTime(this);
        } catch (Throwable error) {
            record("读取三星 Timer 失败：" + error.getClass().getName() + " " + String.valueOf(error.getMessage()));
            endTime = -1L;
        }
        sendUpdate(this, id, manager, endTime, false);
    }

    private static void sendUpdate(Context context, int id, IBinder manager, long endTime,
                                   boolean quiet) {
        if (!quiet) record(context, "准备发送：" + (endTime > 0 ? "剩余 " +
                ((endTime + getOffsetSeconds(context) * 1000L - System.currentTimeMillis()) / 1000) +
                " 秒 (校准 " + getOffsetSeconds(context) + " 秒)" : "没有运行中的 Timer"));
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(MANAGER);
            data.writeInt(id);
            data.writeInt(1); // non-null ComplicationData
            writeWireData(context, data, endTime, null);
            boolean delivered = manager.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0);
            if (!delivered) {
                lastDelivery = "Manager declined slot " + id;
                record(context, "发送失败：系统不接受 manager transact");
                return;
            }
            reply.readException();
            lastDelivery = "Manager accepted slot " + id + " at " +
                    DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date()) +
                    (endTime > 0 ? ", timer running" : ", no timer");
            if (!quiet) record(context, "系统已接收短文本数据");
        } catch (Throwable error) {
            lastDelivery = "Manager update failed: " + error.getClass().getSimpleName();
            if (!quiet) record(context, "发送异常：" + error.getClass().getSimpleName() + " " + String.valueOf(error.getMessage()));
            android.util.Log.w("WatchTimerBridge", "Could not update Timer complication", error);
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private static void writeWireData(Context context, Parcel out, long endTime, String previewText) {
        out.writeInt(3); // TYPE_SHORT_TEXT
        Bundle fields = new Bundle();
        if (previewText != null) {
            fields.putParcelable("SHORT_TEXT", new ComplicationText(previewText));
        } else if (endTime + getOffsetSeconds(context) * 1000L > System.currentTimeMillis()) {
            long adjustedEnd = endTime + getOffsetSeconds(context) * 1000L;
            long remaining = adjustedEnd - System.currentTimeMillis();
            PowerManager power = (PowerManager) context.getSystemService(POWER_SERVICE);
            if (CountdownFormatter.needsFullText(remaining) &&
                    (power == null || power.isInteractive())) {
                // The stock stopwatch representation drops seconds at one hour.
                // Refresh this plain text while the display is interactive.
                fields.putParcelable("SHORT_TEXT", new ComplicationText(
                        CountdownFormatter.format(remaining)));
            } else {
                fields.putParcelable("SHORT_TEXT", new ComplicationText(adjustedEnd));
            }
        } else {
            // A single text element can repaint on data changes. WFF faces that
            // conditionally swap a drawn icon for text can otherwise keep the
            // old branch until the screen turns off and on again.
            fields.putParcelable("SHORT_TEXT", new ComplicationText("⏱︎"));
        }
        Intent launch = context.getPackageManager().getLaunchIntentForPackage("com.samsung.android.watch.timer");
        if (launch != null) {
            PendingIntent action = PendingIntent.getActivity(context, 0, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            fields.putParcelable("TAP_ACTION", action);
        }
        out.writeBundle(fields);
    }

    static long findEndTime(Context context) {
        TimerListener listener = TimerListener.instance;
        if (listener == null) return -1L;
        StatusBarNotification[] active;
        try { active = listener.getActiveNotifications(); }
        catch (RuntimeException error) { return -1L; }
        if (active == null) return -1L;
        for (StatusBarNotification notification : active) {
            if (!"com.samsung.android.watch.timer".equals(notification.getPackageName())) continue;
            Notification n = notification.getNotification();
            if (n == null || n.extras == null) continue;
            Object custom;
            try { custom = n.extras.get("customDisplayBundle"); }
            catch (RuntimeException error) { continue; }
            if (!(custom instanceof Bundle)) continue;
            Object remote = findNested((Bundle) custom, "cardChronometerRemoteView", 0);
            if (!(remote instanceof RemoteViews)) continue;
            try {
                View root = ((RemoteViews) remote).apply(context, new FrameLayout(context));
                long base = findChronometerBase(root);
                if (base > SystemClock.elapsedRealtime())
                    return System.currentTimeMillis() + base - SystemClock.elapsedRealtime();
            } catch (RuntimeException | LinkageError error) {
                android.util.Log.w("WatchTimerBridge", "Timer RemoteViews unavailable", error);
            }
        }
        return -1L;
    }

    private static Object findNested(Bundle bundle, String key, int depth) {
        if (bundle == null || depth > 3) return null;
        try {
            if (bundle.containsKey(key)) return bundle.get(key);
            for (String childKey : bundle.keySet()) {
                Object child = bundle.get(childKey);
                if (child instanceof Bundle) {
                    Object found = findNested((Bundle) child, key, depth + 1);
                    if (found != null) return found;
                }
            }
        } catch (RuntimeException ignored) { }
        return null;
    }

    private static long findChronometerBase(View view) {
        if (view instanceof Chronometer) {
            Chronometer chrono = (Chronometer) view;
            long base = chrono.getBase();
            chrono.stop();
            return base;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); ++i) {
                long base = findChronometerBase(group.getChildAt(i));
                if (base > 0) return base;
            }
        }
        return -1;
    }

}
