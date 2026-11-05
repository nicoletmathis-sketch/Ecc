package com.autoradio.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Minimal home screen for an Android car head unit.
 *
 * Five tiles (Waze, Spotify, Phone, Messages, CarPlay) that simply launch the
 * matching app. No service, no background work, no third-party library: once
 * another app is in front, this activity costs almost nothing.
 *
 * Each tile tries, in order: the app chosen by the user (long press), a list
 * of well-known package names, then a generic system intent. If nothing
 * matches, the user is asked to pick the app.
 */
public class MainActivity extends Activity {

    private static final String PREFS = "slots";

    private static final class Slot {
        final String key;
        final int viewId;
        final int labelRes;
        final String[] packages;
        final Intent fallback;

        Slot(String key, int viewId, int labelRes, String[] packages, Intent fallback) {
            this.key = key;
            this.viewId = viewId;
            this.labelRes = labelRes;
            this.packages = packages;
            this.fallback = fallback;
        }
    }

    private Slot[] slots;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        slots = new Slot[] {
            new Slot("waze", R.id.tile_waze, R.string.tile_waze,
                new String[] {"com.waze"},
                null),
            new Slot("spotify", R.id.tile_spotify, R.string.tile_spotify,
                new String[] {"com.spotify.music", "com.spotify.lite"},
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)),
            new Slot("phone", R.id.tile_phone, R.string.tile_phone,
                // Bluetooth phone apps found on common head units, then stock dialers
                new String[] {
                    "com.syu.bt", "com.microntek.bluetooth", "com.ts.bluetooth",
                    "com.google.android.dialer", "com.android.dialer"
                },
                new Intent(Intent.ACTION_DIAL)),
            new Slot("messages", R.id.tile_messages, R.string.tile_messages,
                new String[] {
                    "com.google.android.apps.messaging", "com.android.mms",
                    "com.syu.bt", "com.microntek.bluetooth"
                },
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)),
            new Slot("carplay", R.id.tile_carplay, R.string.tile_carplay,
                // Apps driving CarPlay dongles / built-in CarPlay boxes (ZLink, AutoKit, ...)
                new String[] {"com.zjinnova.zlink", "cn.manstep.phonemirrorBox"},
                null),
        };

        for (final Slot slot : slots) {
            View tile = findViewById(slot.viewId);
            tile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    open(slot);
                }
            });
            tile.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    pickApp(slot);
                    return true;
                }
            });
        }

        findViewById(R.id.btn_apps).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickApp(null);
            }
        });
        // Long press on the clock = Android settings (handy without a status bar)
        findViewById(R.id.clock).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                start(new Intent(Settings.ACTION_SETTINGS));
                return true;
            }
        });
    }

    @Override
    public void onBackPressed() {
        // Home screen: back does nothing.
    }

    private void open(Slot slot) {
        Intent intent = resolve(slot);
        if (intent == null || !start(intent)) {
            Toast.makeText(this, getString(R.string.not_found, getString(slot.labelRes)),
                Toast.LENGTH_SHORT).show();
            pickApp(slot);
        }
    }

    private Intent resolve(Slot slot) {
        PackageManager pm = getPackageManager();
        String chosen = prefs.getString(slot.key, null);
        if (chosen != null) {
            Intent i = pm.getLaunchIntentForPackage(chosen);
            if (i != null) {
                return i;
            }
        }
        for (String pkg : slot.packages) {
            Intent i = pm.getLaunchIntentForPackage(pkg);
            if (i != null) {
                return i;
            }
        }
        if (slot.fallback != null && slot.fallback.resolveActivity(pm) != null) {
            return slot.fallback;
        }
        return null;
    }

    private boolean start(Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
            overridePendingTransition(0, 0);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    /**
     * Lists launchable apps. With a slot: assigns the chosen app to it.
     * Without: just opens the chosen app (simple app drawer).
     */
    private void pickApp(final Slot slot) {
        final PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(query, 0);

        final List<String> packages = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        final List<String[]> rows = new ArrayList<>();
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) {
                continue;
            }
            rows.add(new String[] {String.valueOf(ri.loadLabel(pm)), pkg});
        }
        final Collator collator = Collator.getInstance();
        Collections.sort(rows, new Comparator<String[]>() {
            @Override
            public int compare(String[] a, String[] b) {
                return collator.compare(a[0], b[0]);
            }
        });
        for (String[] row : rows) {
            labels.add(row[0]);
            packages.add(row[1]);
        }

        String title = slot == null
            ? getString(R.string.pick_app)
            : getString(R.string.pick_app_for, getString(slot.labelRes));

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setItems(labels.toArray(new CharSequence[0]), (dialog, which) -> {
                String pkg = packages.get(which);
                if (slot != null) {
                    prefs.edit().putString(slot.key, pkg).apply();
                    Toast.makeText(this,
                        getString(R.string.assigned, getString(slot.labelRes), labels.get(which)),
                        Toast.LENGTH_SHORT).show();
                } else {
                    Intent i = pm.getLaunchIntentForPackage(pkg);
                    if (i != null) {
                        start(i);
                    }
                }
            })
            .show();
    }
}
