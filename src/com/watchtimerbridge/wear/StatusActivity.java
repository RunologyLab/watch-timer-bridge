package com.watchtimerbridge.wear;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** On-watch setup, including an explicit user-controlled notification access step. */
public final class StatusActivity extends Activity {
    // These platform actions arrived after the API 23 android.jar used by our
    // small standalone build, so spell out their documented constant values.
    private static final String LISTENER_DETAIL = "android.settings.NOTIFICATION_LISTENER_DETAIL_SETTINGS";
    private static final String LISTENER_COMPONENT = "android.provider.extra.NOTIFICATION_LISTENER_COMPONENT_NAME";
    private TextView status;
    private Button offsetButton;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(18 * getResources().getDisplayMetrics().density);
        column.setPadding(padding, padding, padding, padding);
        scroll.addView(column);

        TextView title = new TextView(this);
        title.setText("Watch Timer Bridge 1.0.6");
        title.setTextColor(Color.rgb(142, 235, 151));
        title.setTextSize(21);
        column.addView(title);

        TextView explanation = new TextView(this);
        explanation.setText("Shows the countdown from Samsung Timer in a watch face complication. " +
                "Android's notification access can include notifications from all apps; " +
                "this app reads only Samsung Timer and does not send data off the watch.\n\n" +
                "1. Open notification access settings below and enable Watch Timer Bridge.\n" +
                "2. Choose 'Watch Timer Countdown' in a SHORT_TEXT watch face slot.");
        explanation.setTextColor(Color.WHITE);
        explanation.setTextSize(15);
        column.addView(explanation);

        Button permission = new Button(this);
        permission.setText("Open notification access");
        permission.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { openNotificationAccess(); }
        });
        column.addView(permission);

        Button appSettings = new Button(this);
        appSettings.setText("Open app info (fallback)");
        appSettings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                try { startActivity(intent); }
                catch (RuntimeException error) {
                    status.setText("App info unavailable on this watch. Use the ADB setup command in the README.");
                }
            }
        });
        column.addView(appSettings);

        Button refresh = new Button(this);
        refresh.setText("Refresh status");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { refreshStatus(); }
        });
        column.addView(refresh);

        Button forceUpdate = new Button(this);
        forceUpdate.setText("Send face update now");
        forceUpdate.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                long deadline = TimerComplicationService.findEndTime(StatusActivity.this);
                TimerComplicationService.pushActive(StatusActivity.this, deadline);
                TimerComplicationService.requestSystemUpdate(StatusActivity.this, "手动诊断刷新");
                status.postDelayed(new Runnable() {
                    @Override public void run() { refreshStatus(); }
                }, 650L);
            }
        });
        column.addView(forceUpdate);

        offsetButton = new Button(this);
        updateOffsetButton();
        offsetButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                int current = TimerComplicationService.getOffsetSeconds(StatusActivity.this);
                int next = current == -2 ? -1 : current == -1 ? 0 : current == 0 ? -3 : -2;
                TimerComplicationService.setOffsetSeconds(StatusActivity.this, next);
                updateOffsetButton();
                refreshStatus();
            }
        });
        column.addView(offsetButton);

        status = new TextView(this);
        status.setTextColor(Color.rgb(142, 235, 151));
        status.setTextSize(15);
        column.addView(status);
        setContentView(scroll);
        refreshStatus();
    }

    private void openNotificationAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            Intent detail = new Intent(LISTENER_DETAIL);
            detail.putExtra(LISTENER_COMPONENT,
                    new ComponentName(this, TimerListener.class).flattenToString());
            try { startActivity(detail); return; }
            catch (RuntimeException ignored) { /* This watch omits the detail page. */ }
        }
        try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); return; }
        catch (RuntimeException ignored) { /* Some watches omit the list page too. */ }
        status.setText("This watch has no notification access settings shortcut. " +
                "Try Settings > Apps > Special access > Notification access, " +
                "or use the ADB setup command in the README.");
    }

    @Override protected void onResume() {
        super.onResume();
        if (status != null) refreshStatus();
    }

    private void refreshStatus() {
        ComponentName service = new ComponentName(this, TimerListener.class);
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        boolean permitted = TimerListener.connected ||
                (enabled != null && enabled.contains(service.flattenToString()));
        if (!permitted) {
            status.setText("Notification access: not enabled\n" + diagnostic());
        } else if (!TimerListener.connected) {
            status.setText("Notification access: enabled. Listener not connected.\n" + diagnostic());
        } else {
            long end = TimerComplicationService.findEndTime(this);
            long seconds = Math.max(0L, (end - System.currentTimeMillis() + 999L) / 1000L);
            status.setText((end > 0 ? "Connected. Samsung Timer: " + seconds + "s remaining"
                    : "Connected. No active Samsung Timer.") + "\n" + diagnostic());
        }
    }

    private void updateOffsetButton() {
        offsetButton.setText("Countdown correction: " +
                TimerComplicationService.getOffsetSeconds(this) + "s (tap to adjust)");
    }

    private String diagnostic() {
        return "\nLast event: " + TimerListener.lastEvent +
                "\nLast scan: " + TimerListener.lastScan +
                "\nActive slots: " + TimerComplicationService.activeSlotCount() +
                "\nLast system callback: " + TimerComplicationService.lastCallback +
                "\nLast data delivery: " + TimerComplicationService.lastDelivery +
                "\nLast update request: " + TimerComplicationService.lastRequest;
    }
}
