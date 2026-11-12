package com.aritxonly.myhypermodifier;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** A user-triggered tile; no foreground service, polling, or persistent state receiver. */
public final class CoverAdaptationTileService extends TileService {
    private BroadcastReceiver receiver;

    @Override public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setSubtitle("需启用并重启手机");
            tile.updateTile();
        }
        if (receiver == null) {
            receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent state) {
                    Tile target = getQsTile();
                    if (target == null) return;
                    boolean active = state.getBooleanExtra("active", false);
                    target.setState(!state.getBooleanExtra("enabled", false) ? Tile.STATE_UNAVAILABLE
                            : active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
                    target.setSubtitle(active ? "点击恢复应用布局" : "竖屏 9:16 + 侧栏");
                    target.updateTile();
                }
            };
            registerReceiver(receiver, new IntentFilter(CoverAdaptationContract.STATE),
                    "android.permission.MANAGE_ACTIVITY_TASKS", null, Context.RECEIVER_EXPORTED);
        }
        CoverAdaptationContract.send(this, CoverAdaptationContract.QUERY);
    }

    @Override public void onClick() {
        super.onClick();
        unlockAndRun(() -> {
            Intent intent = new Intent(this, CoverAdaptationActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            } else startActivityAndCollapse(intent);
        });
    }

    @Override public void onStopListening() {
        if (receiver != null) { unregisterReceiver(receiver); receiver = null; }
        super.onStopListening();
    }

    @Override public void onDestroy() {
        if (receiver != null) { unregisterReceiver(receiver); receiver = null; }
        super.onDestroy();
    }
}
